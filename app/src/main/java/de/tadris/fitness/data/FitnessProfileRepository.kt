/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack.
 * This file was added in FitRegimen, a fork of FitoTrack.
 */
package de.tadris.fitness.data

import de.tadris.fitness.data.preferences.UserMeasurements
import de.tadris.fitness.data.preferences.UserPreferences
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow

/**
 * Single mutation boundary for profiles. In addition to enforcing validation and
 * one active profile, it mirrors the active measurements to legacy preferences.
 * That bridge keeps every existing synchronous calorie and stride calculation
 * profile-aware without a risky all-at-once async migration.
 */
class FitnessProfileRepository @JvmOverloads constructor(
    private val db: AppDatabase,
    private val preferences: UserPreferences,
    private val defaultProfileName: String = "User",
    private val clock: () -> Long = System::currentTimeMillis,
    private val uuidProvider: () -> String = { UUID.randomUUID().toString() },
) {
    private val dao = db.fitnessProfileDao()

    val profiles: Flow<List<FitnessProfile>> = dao.observeAll()
    val activeProfile: Flow<FitnessProfile?> = dao.observeActive()

    fun getProfiles(): List<FitnessProfile> = dao.getAll()

    fun getActiveProfile(): FitnessProfile? = dao.getActive()

    fun findById(id: Long): FitnessProfile? = dao.findById(id)

    fun findByUuid(uuid: String): FitnessProfile? = dao.findByUuid(uuid)

    fun create(
        draft: FitnessProfileDraft,
        makeActive: Boolean = false,
    ): FitnessProfile {
        val value = FitnessProfileValidator.requireValid(draft)
        val now = clock()
        val created = db.runInTransaction<FitnessProfile> {
            val shouldActivate = makeActive || dao.getAll().isEmpty()
            val id = dao.insert(value.toEntity(now = now, uuid = nextUniqueUuid()))
            if (shouldActivate) {
                dao.activate(id, now) ?: error("Unable to activate inserted fitness profile")
            } else {
                dao.findById(id) ?: error("Unable to read inserted fitness profile")
            }
        }
        syncLegacyMeasurementsFromActive()
        return created
    }

    fun update(id: Long, draft: FitnessProfileDraft): FitnessProfile {
        val value = FitnessProfileValidator.requireValid(draft)
        val updated = db.runInTransaction<FitnessProfile> {
            val existing = dao.findById(id) ?: throw NoSuchElementException("Fitness profile $id not found")
            val result = value.toEntity(
                id = existing.id,
                uuid = existing.uuid,
                isActive = existing.isActive,
                createdAt = existing.createdAt,
                now = clock(),
            )
            check(dao.update(result) == 1) { "Unable to update fitness profile $id" }
            result
        }
        if (updated.isActive) syncLegacyMeasurementsFromActive()
        return updated
    }

    /** Compatibility overload for callers editing a Room row directly. */
    fun update(profile: FitnessProfile): FitnessProfile = update(profile.id, profile.toDraft())

    fun setActive(id: Long): FitnessProfile {
        val selected = dao.activate(id, clock())
            ?: throw NoSuchElementException("Fitness profile $id not found")
        syncLegacyMeasurements(selected)
        return selected
    }

    fun delete(id: Long) {
        val fallback = dao.deleteAndSelectFallback(id, clock())
        if (fallback == null) {
            ensureStandardDefaultProfile()
        } else {
            syncLegacyMeasurements(fallback)
        }
    }

    /**
     * Idempotent startup/restore migration. The first profile receives the exact
     * weight and step length stored by older versions of the app.
     */
    fun ensureDefaultProfile(): FitnessProfile = ensureDefaultProfile(useLegacyMeasurements = true)

    /** Creates the same average user as a clean install, without reusing deleted/private values. */
    fun ensureStandardDefaultProfile(): FitnessProfile =
        ensureDefaultProfile(useLegacyMeasurements = false)

    private fun ensureDefaultProfile(useLegacyMeasurements: Boolean): FitnessProfile {
        val selected = db.runInTransaction<FitnessProfile> {
            val profiles = dao.getAll()
            if (profiles.isEmpty()) {
                val now = clock()
                val initial = if (useLegacyMeasurements) {
                    legacyDraft(defaultProfileName)
                } else {
                    FitnessProfileLegacyBridge.fromLegacy(
                        name = defaultProfileName,
                        weightKg = FitnessProfileLegacyBridge.DEFAULT_WEIGHT_KG,
                        stepLengthMeters = FitnessProfileLegacyBridge.DEFAULT_STEP_LENGTH_METERS,
                    )
                }
                val id = dao.insert(initial.toEntity(now = now, uuid = nextUniqueUuid(), isActive = true))
                dao.findById(id) ?: error("Unable to create default fitness profile")
            } else {
                val current = dao.getActive()
                val active = if (current == null || dao.countActive() != 1) {
                    dao.activate(current?.id ?: profiles.first().id, clock())
                } else {
                    current
                }
                active ?: error("Unable to select an active fitness profile")
            }
        }
        syncLegacyMeasurements(selected)
        return selected
    }

    fun effectiveMeasurements(): UserMeasurements =
        getActiveProfile()?.let(FitnessProfileLegacyBridge::effectiveMeasurements)
            ?: UserMeasurements(preferences.userWeight, preferences.stepLength)

    /** Captures one stable identity/measurement tuple for persisted history. */
    fun captureActiveSnapshot(): FitnessProfileSnapshot {
        val active = getActiveProfile()
        val fallback = effectiveMeasurements()
        return FitnessProfileSnapshot(
            profileUuid = active?.uuid?.takeIf(String::isNotBlank),
            userWeightKg = active?.weightKg
                ?.takeIf { it.isFinite() && it > 0.0 }
                ?: fallback.weight.toDouble(),
            stepLengthMeters = active?.stepLengthCm
                ?.takeIf { it.isFinite() && it > 0.0 }
                ?.div(100.0)
                ?: fallback.stepLength.toDouble(),
        )
    }

    /** Re-applies the active row after a database restore has committed. */
    fun syncLegacyMeasurementsFromActive() {
        dao.getActive()?.let(::syncLegacyMeasurements)
    }

    private fun legacyDraft(name: String): FitnessProfileDraft = FitnessProfileLegacyBridge.fromLegacy(
        name = name,
        weightKg = preferences.userWeight,
        stepLengthMeters = preferences.stepLength,
    )

    private fun syncLegacyMeasurements(profile: FitnessProfile) {
        val measurements = FitnessProfileLegacyBridge.effectiveMeasurements(profile)
        preferences.setLegacyMeasurements(measurements.weight, measurements.stepLength)
    }

    private fun nextUniqueUuid(): String {
        repeat(8) {
            val candidate = uuidProvider().trim()
            if (candidate.isNotEmpty() && dao.findByUuid(candidate) == null) return candidate
        }
        error("Unable to allocate a unique fitness profile UUID")
    }

    private fun FitnessProfileDraft.toEntity(
        id: Long = 0,
        uuid: String,
        isActive: Boolean = false,
        createdAt: Long = clock(),
        now: Long,
    ) = FitnessProfile(
        id = id,
        uuid = uuid,
        name = name,
        isActive = isActive,
        birthDateEpochDay = birthDateEpochDay,
        heightCm = heightCm,
        weightKg = weightKg,
        stepLengthCm = stepLengthCm,
        gender = gender,
        fitnessLevel = fitnessLevel,
        notes = notes,
        customAttributesJson = customAttributesJson,
        createdAt = createdAt,
        updatedAt = now,
    )
}

/** Pure, unit-testable conversion used by both startup migration and fallback reads. */
object FitnessProfileLegacyBridge {
    const val DEFAULT_WEIGHT_KG = UserPreferences.DEFAULT_USER_WEIGHT_KG
    const val DEFAULT_STEP_LENGTH_METERS = UserPreferences.DEFAULT_STEP_LENGTH_METERS

    fun fromLegacy(
        name: String,
        weightKg: Int,
        stepLengthMeters: Float,
    ): FitnessProfileDraft = FitnessProfileDraft(
        name = name,
        weightKg = weightKg.toDouble(),
        stepLengthCm = stepLengthMeters.toDouble() * 100.0,
    )

    fun effectiveMeasurements(profile: FitnessProfile): UserMeasurements = UserMeasurements(
        weight = profile.weightKg
            ?.takeIf(Double::isFinite)
            ?.roundToInt()
            ?.coerceAtLeast(1)
            ?: DEFAULT_WEIGHT_KG,
        stepLength = profile.stepLengthCm
            ?.takeIf(Double::isFinite)
            ?.div(100.0)
            ?.toFloat()
            ?.takeIf { it > 0f }
            ?: DEFAULT_STEP_LENGTH_METERS,
    )
}
