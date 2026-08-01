/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 * This file was added in FitRegimen, a fork of FitoTrack.
 */
package de.tadris.fitness.data.backup

import android.content.Context
import com.fasterxml.jackson.core.StreamReadFeature
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import de.tadris.fitness.Instance
import de.tadris.fitness.data.NutritionDay
import de.tadris.fitness.data.NutritionItem
import de.tadris.fitness.data.NutritionMeal
import de.tadris.fitness.data.NutritionPlan
import de.tadris.fitness.data.MetricKind
import de.tadris.fitness.data.SetType
import de.tadris.fitness.data.isSupportedExerciseVideoUrl
import de.tadris.fitness.data.backup.model.PROGRAM_CONTAINER_FORMAT_VERSION
import de.tadris.fitness.data.backup.model.NUTRITION_PLAN_CONTAINER_FORMAT_VERSION
import de.tadris.fitness.data.backup.model.NUTRITION_PLAN_CONTAINER_TYPE
import de.tadris.fitness.data.backup.model.NutritionPlanContainer
import de.tadris.fitness.data.backup.model.ProgramContainer
import de.tadris.fitness.data.backup.model.ExerciseExport
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

object NutritionPlanImporter {

    private val mapper = jsonMapper {
        addModule(kotlinModule())
        configure(StreamReadFeature.IGNORE_UNDEFINED, true)
        configure(StreamReadFeature.AUTO_CLOSE_SOURCE, false)
        enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
    }

    class InvalidNutritionPlanFileException(message: String) : IOException(message)

    fun read(inputStream: InputStream): NutritionPlanContainer {
        val bytes = try {
            inputStream.readLimitedNutritionPlanBytes()
        } catch (error: IOException) {
            throw InvalidNutritionPlanFileException(error.message ?: "Unable to read nutrition plan")
        }
        val parsed = try {
            val root = mapper.readTree(bytes)
            if (root == null || !root.isObject) {
                throw InvalidNutritionPlanFileException("Nutrition-plan file must be a JSON object")
            }
            if (!root.hasNonNull("type")) {
                throw InvalidNutritionPlanFileException("Nutrition-plan file type is missing")
            }
            if (!root.hasNonNull("formatVersion")) {
                throw InvalidNutritionPlanFileException("Nutrition-plan formatVersion is missing")
            }
            mapper.treeToValue(root, NutritionPlanContainer::class.java)
        } catch (error: InvalidNutritionPlanFileException) {
            throw error
        } catch (error: Exception) {
            throw InvalidNutritionPlanFileException("Not valid JSON: ${error.message}")
        }
        if (parsed.type != NUTRITION_PLAN_CONTAINER_TYPE) {
            throw InvalidNutritionPlanFileException(
                "Not a FitRegimen nutrition plan (expected type \"$NUTRITION_PLAN_CONTAINER_TYPE\")"
            )
        }
        if (parsed.formatVersion != NUTRITION_PLAN_CONTAINER_FORMAT_VERSION) {
            throw InvalidNutritionPlanFileException(
                "Unsupported formatVersion ${parsed.formatVersion}, expected " +
                    NUTRITION_PLAN_CONTAINER_FORMAT_VERSION
            )
        }
        try {
            validateNutritionPlanContainer(parsed, allowPrivateFields = false)
        } catch (error: IOException) {
            throw InvalidNutritionPlanFileException(error.message ?: "Invalid nutrition plan")
        }
        return parsed
    }

    /** Imports a standalone/shared plan. Private full-backup fields are rejected. */
    fun import(context: Context, container: NutritionPlanContainer): Long =
        importInternal(context, container, preservePrivateFields = false)

    /** Full-backup-only entry point. Its input has already passed archive preflight. */
    internal fun importFromBackup(context: Context, container: NutritionPlanContainer): Long =
        importInternal(context, container, preservePrivateFields = true)

    private fun importInternal(
        context: Context,
        container: NutritionPlanContainer,
        preservePrivateFields: Boolean,
    ): Long {
        validateNutritionPlanContainer(
            container = container,
            allowPrivateFields = preservePrivateFields,
            requireUuid = preservePrivateFields,
            requirePrivateTimestamps = preservePrivateFields,
        )
        val normalized = container.normalizedForPersistence()
        val db = Instance.getInstance(context).db
        return db.runInTransaction<Long> {
            val dao = db.nutritionPlanDao()
            val now = System.currentTimeMillis()
            val requestedUuid = normalized.plan.uuid.orEmpty()
            val uuid = if (preservePrivateFields && requestedUuid.isNotEmpty() &&
                dao.findPlanByUuid(requestedUuid) == null
            ) {
                requestedUuid
            } else {
                nextUniqueUuid { dao.findPlanByUuid(it) != null }
            }
            val planId = dao.insertPlan(
                NutritionPlan(
                    uuid = uuid,
                    name = normalized.plan.name,
                    description = normalized.plan.description,
                    objective = normalized.plan.objective,
                    preferences = normalized.plan.preferences,
                    restrictions = normalized.plan.restrictions,
                    linkedTrainingProgramUuid = normalized.plan.linkedTrainingProgramUuid,
                    startDateEpochDay = if (preservePrivateFields) {
                        normalized.startDateEpochDay
                    } else {
                        null
                    },
                    createdAt = if (preservePrivateFields) normalized.createdAt!! else now,
                    updatedAt = if (preservePrivateFields) normalized.updatedAt!! else now,
                )
            )
            normalized.days.forEachIndexed { dayIndex, day ->
                val dayId = dao.insertDay(
                    NutritionDay(
                        planId = planId,
                        orderIndex = dayIndex,
                        name = day.name,
                        notes = day.notes,
                        energyKcal = day.energyKcal,
                        proteinGrams = day.proteinGrams,
                        fatGrams = day.fatGrams,
                        carbsGrams = day.carbsGrams,
                        fiberGrams = day.fiberGrams,
                        waterMl = day.waterMl,
                    )
                )
                day.meals.forEachIndexed { mealIndex, meal ->
                    val mealId = dao.insertMeal(
                        NutritionMeal(
                            dayId = dayId,
                            orderIndex = mealIndex,
                            name = meal.name,
                            plannedTimeMinutes = meal.plannedTimeMinutes,
                            notes = meal.notes,
                        )
                    )
                    meal.items.forEachIndexed { itemIndex, item ->
                        dao.insertItem(
                            NutritionItem(
                                mealId = mealId,
                                orderIndex = itemIndex,
                                name = item.name,
                                quantity = item.quantity,
                                unit = item.unit,
                                weightGrams = item.weightGrams,
                                energyKcal = item.energyKcal,
                                proteinGrams = item.proteinGrams,
                                fatGrams = item.fatGrams,
                                carbsGrams = item.carbsGrams,
                                fiberGrams = item.fiberGrams,
                                notes = item.notes,
                            )
                        )
                    }
                }
            }
            if (preservePrivateFields) dao.replaceProfileUuids(planId, normalized.profileUuids)
            planId
        }
    }

    private fun nextUniqueUuid(isOccupied: (String) -> Boolean): String {
        repeat(16) {
            val candidate = UUID.randomUUID().toString()
            if (!isOccupied(candidate)) return candidate
        }
        error("Unable to allocate a nutrition-plan UUID")
    }

    private fun InputStream.readLimitedNutritionPlanBytes(): ByteArray {
        val output = ByteArrayOutputStream(minOf(MAX_NUTRITION_PLAN_FILE_BYTES, 16 * 1024))
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            if (read == 0) {
                val byte = read()
                if (byte < 0) break
                total++
                if (total > MAX_NUTRITION_PLAN_FILE_BYTES) {
                    throw IOException("Nutrition-plan file exceeds 8 MiB")
                }
                output.write(byte)
                continue
            }
            total += read
            if (total > MAX_NUTRITION_PLAN_FILE_BYTES) {
                throw IOException("Nutrition-plan file exceeds 8 MiB")
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}

/** Rebinds a full-backup plan after a colliding training program received a new UUID. */
internal fun NutritionPlanContainer.remapLinkedTrainingProgramUuid(
    importedProgramUuidMap: Map<String, String>,
): NutritionPlanContainer = copy(
    plan = plan.copy(
        linkedTrainingProgramUuid = plan.linkedTrainingProgramUuid
            ?.let { importedProgramUuidMap[it] ?: it },
    ),
)

/** Canonical shape written to Room; profile-link order has no semantic meaning. */
internal fun NutritionPlanContainer.normalizedForPersistence(): NutritionPlanContainer = copy(
    plan = plan.copy(
        uuid = plan.uuid.normalizedText(),
        name = plan.name.trim(),
        description = plan.description.normalizedText(),
        objective = plan.objective.normalizedText(),
        preferences = plan.preferences.normalizedText(),
        restrictions = plan.restrictions.normalizedText(),
        linkedTrainingProgramUuid = plan.linkedTrainingProgramUuid.normalizedText(),
    ),
    days = days.map { day ->
        day.copy(
            name = day.name.normalizedText(),
            notes = day.notes.normalizedText(),
            meals = day.meals.map { meal ->
                meal.copy(
                    name = meal.name.trim(),
                    notes = meal.notes.normalizedText(),
                    items = meal.items.map { item ->
                        item.copy(
                            name = item.name.trim(),
                            unit = item.unit.normalizedText(),
                            notes = item.notes.normalizedText(),
                        )
                    },
                )
            },
        )
    },
    profileUuids = profileUuids.map(String::trim).sorted(),
)

/** Idempotent backup-merge equality: local UUID allocation is intentionally ignored. */
internal fun nutritionPlanContainersEquivalent(
    existing: NutritionPlanContainer,
    incoming: NutritionPlanContainer,
): Boolean {
    val normalizedExisting = existing.normalizedForPersistence()
    val normalizedIncoming = incoming.normalizedForPersistence()
    return normalizedExisting.copy(
        updatedAt = null,
        plan = normalizedExisting.plan.copy(uuid = null),
    ) == normalizedIncoming.copy(
        updatedAt = null,
        plan = normalizedIncoming.plan.copy(uuid = null),
    )
}

internal sealed interface NutritionBackupMergeDecision {
    data object Skip : NutritionBackupMergeDecision
    data class Import(val container: NutritionPlanContainer) : NutritionBackupMergeDecision
}

/**
 * Resolves backup identity without content-based false deduplication.
 *
 * Replacement always preserves every distinct source UUID. An unmodified link
 * checks only that exact source UUID. A divergent collision or a restore-local
 * program-link remap receives a source-derived UUID, allowing repeated merges
 * without confusing a remap with a genuine source-link change.
 */
internal fun resolveNutritionBackupMerge(
    incoming: NutritionPlanContainer,
    source: NutritionPlanContainer = incoming,
    linkedProgramContentIdentity: String? = null,
    replace: Boolean,
    findByUuid: (String) -> NutritionPlanContainer?,
): NutritionBackupMergeDecision {
    val sourceUuid = source.plan.uuid?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: error("A full-backup nutrition plan must have a UUID")
    if (replace) return NutritionBackupMergeDecision.Import(incoming)

    val sourceLink = source.plan.linkedTrainingProgramUuid.normalizedText()
    val importedLink = incoming.plan.linkedTrainingProgramUuid.normalizedText()
    val linkWasRemapped = sourceLink != importedLink
    if (!linkWasRemapped) {
        val sameIdentity = findByUuid(sourceUuid)
            ?: return NutritionBackupMergeDecision.Import(incoming)
        if (nutritionPlanContainersEquivalent(sameIdentity, incoming)) {
            return NutritionBackupMergeDecision.Skip
        }
    }

    // A remapped link must use source-derived identity even when sourceUuid is free.
    // Otherwise a later merge that maps the same source program to another local UUID
    // cannot distinguish the prior import from a genuine source-link change.
    repeat(MAX_DETERMINISTIC_BACKUP_UUID_ATTEMPTS) { attempt ->
        val derivedUuid = deterministicNutritionBackupCopyUuid(
            sourceUuid = sourceUuid,
            source = source,
            linkedProgramContentIdentity = linkedProgramContentIdentity,
            attempt = attempt,
        )
        val existingCopy = findByUuid(derivedUuid)
        if (existingCopy == null) {
            return NutritionBackupMergeDecision.Import(
                incoming.copy(plan = incoming.plan.copy(uuid = derivedUuid))
            )
        }
        if (nutritionPlanContainersEquivalent(existingCopy, incoming)) {
            return NutritionBackupMergeDecision.Skip
        }
    }
    error("Unable to allocate a deterministic nutrition-plan backup UUID")
}

private fun deterministicNutritionBackupCopyUuid(
    sourceUuid: String,
    source: NutritionPlanContainer,
    linkedProgramContentIdentity: String?,
    attempt: Int,
): String {
    val normalized = source.normalizedForPersistence()
    val canonical = normalized.copy(
        updatedAt = null,
        plan = normalized.plan.copy(uuid = null),
    )
    val contentHash = MessageDigest.getInstance("SHA-256")
        .digest(NUTRITION_FINGERPRINT_MAPPER.writeValueAsBytes(canonical))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
    val seed = buildString {
        append("fitregimen-nutrition-backup-copy-v1\u0000")
        append(sourceUuid)
        append('\u0000')
        append(contentHash)
        append('\u0000')
        append(linkedProgramContentIdentity.orEmpty())
        append('\u0000')
        append(attempt)
    }
    return UUID.nameUUIDFromBytes(seed.toByteArray(Charsets.UTF_8)).toString()
}

private const val MAX_DETERMINISTIC_BACKUP_UUID_ATTEMPTS = 1_024
private val NUTRITION_FINGERPRINT_MAPPER = JsonMapper.builder()
    .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
    .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
    .build()

/**
 * Stable identity of the linked program definition from the source backup.
 * Mutable execution state is excluded: completions and startDate must not fork
 * an otherwise unchanged nutrition-plan link on every later backup.
 */
internal fun trainingProgramDefinitionFingerprint(container: ProgramContainer): String {
    val normalized = ProgramImporter.normalizeForImport(container)
    val customTypeTokenById = normalized.customWorkoutTypes.associate { type ->
        type.id to canonicalWorkoutTypeToken(type)
    }
    val canonicalExercises = normalized.exercises.map { exercise ->
        canonicalExerciseDefinition(exercise, customTypeTokenById)
    }
    val canonicalExerciseByUuid = canonicalExercises.associateBy { it.uuid.orEmpty() }
    val canonical = normalized.copy(
        formatVersion = PROGRAM_CONTAINER_FORMAT_VERSION,
        program = normalized.program.copy(
            uuid = null,
            name = normalized.program.name.trim(),
            description = normalized.program.description?.takeIf(String::isNotBlank),
            icon = normalized.program.icon?.trim()?.takeIf(String::isNotEmpty),
            startDate = null,
        ),
        days = normalized.days.map { day ->
            day.copy(
                name = day.name?.takeIf(String::isNotBlank),
                exercises = day.exercises.map { entry ->
                    val exercise = canonicalExerciseByUuid[entry.exerciseUuid]
                    val kind = MetricKind.findByName(exercise?.metricKind)
                    val policy = ProgramImporter.executionPolicyFor(
                        entry = entry,
                        metricKind = kind,
                        workoutTypeId = exercise?.workoutTypeId,
                    )
                    entry.copy(
                        note = entry.note?.takeIf(String::isNotBlank),
                        restSeconds = entry.restSeconds?.coerceAtLeast(0),
                        measurementSource = policy.measurementSource.name,
                        completionRule = policy.completionRule.name,
                        prepareSeconds = entry.prepareSeconds?.coerceAtLeast(0) ?: 0,
                        transitionSeconds = entry.transitionSeconds?.coerceAtLeast(0),
                        sets = entry.sets.map { set ->
                            set.copy(type = SetType.findByName(set.type.trim().uppercase()).name)
                        },
                    )
                },
            )
        },
        exercises = canonicalExercises
            .sortedWith(compareBy({ it.uuid.orEmpty() }, { it.name }, { it.metricKind })),
        customWorkoutTypes = normalized.customWorkoutTypes
            .map { type ->
                copyWorkoutTypeWithId(type, customTypeTokenById.getValue(type.id)).also {
                    // Android resource ids are runtime-only and not part of a portable type.
                    it.repeatingExerciseName = -1
                }
            }
            .distinctBy { it.id }
            .sortedBy { it.id },
        completions = emptyList(),
        tasks = emptyList(),
    )
    return MessageDigest.getInstance("SHA-256")
        .digest(NUTRITION_FINGERPRINT_MAPPER.writeValueAsBytes(canonical))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
}

internal fun canonicalWorkoutTypeToken(type: de.tadris.fitness.data.WorkoutType): String {
    val values = listOf(
        type.title.orEmpty(),
        type.minDistance.toString(),
        type.color.toString(),
        type.icon.orEmpty(),
        type.MET.toString(),
        type.recordingType.orEmpty(),
    )
    val seed = buildString {
        values.forEach { value ->
            append(value.length)
            append(':')
            append(value)
            append('\u0000')
        }
    }
    val hash = MessageDigest.getInstance("SHA-256")
        .digest(seed.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
    return "custom-definition-$hash"
}

private fun canonicalExerciseDefinition(
    exercise: ExerciseExport,
    customTypeTokenById: Map<String, String>,
): ExerciseExport = exercise.copy(
    name = exercise.name.trim(),
    metricKind = MetricKind.findByName(exercise.metricKind.trim().uppercase()).name,
    workoutTypeId = exercise.workoutTypeId
        ?.let { id -> customTypeTokenById[id] ?: id }
        ?.takeIf(String::isNotBlank),
    description = exercise.description?.trim()?.takeIf(String::isNotEmpty),
    tips = exercise.tips?.trim()?.takeIf(String::isNotEmpty),
    videoUrl = exercise.videoUrl?.trim()?.takeIf(::isSupportedExerciseVideoUrl),
    met = exercise.met?.takeIf { it.isFinite() && it in 0.5..25.0 },
    secondsPerRep = exercise.secondsPerRep
        ?.takeIf { it.isFinite() && it in 0.1..60.0 },
)

internal fun trainingExerciseDefinitionFingerprint(
    exercise: ExerciseExport,
    customWorkoutTypes: List<de.tadris.fitness.data.WorkoutType> = emptyList(),
): String {
    val customTypeTokenById = customWorkoutTypes.associate { type ->
        type.id to canonicalWorkoutTypeToken(type)
    }
    val canonical = canonicalExerciseDefinition(exercise, customTypeTokenById)
        .copy(uuid = null)
    return MessageDigest.getInstance("SHA-256")
        .digest(NUTRITION_FINGERPRINT_MAPPER.writeValueAsBytes(canonical))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
}

internal fun trainingProgramDefinitionsEquivalent(
    existing: ProgramContainer,
    incoming: ProgramContainer,
): Boolean = trainingProgramDefinitionFingerprint(existing) ==
    trainingProgramDefinitionFingerprint(incoming)

internal enum class ProgramBackupCandidateState {
    MISSING,
    EQUIVALENT,
    DIVERGENT,
}

internal sealed interface ProgramBackupMergeDecision {
    val targetUuid: String

    data class Reuse(override val targetUuid: String) : ProgramBackupMergeDecision
    data class Import(override val targetUuid: String) : ProgramBackupMergeDecision
}

/** Pure identity resolver used by full-backup program restore. */
internal fun resolveProgramBackupMerge(
    sourceUuid: String,
    sourceDefinitionIdentity: String,
    replace: Boolean,
    candidateState: (String) -> ProgramBackupCandidateState,
): ProgramBackupMergeDecision {
    require(sourceUuid.isNotBlank()) { "A full-backup training program must have a UUID" }
    if (replace) return ProgramBackupMergeDecision.Import(sourceUuid)

    when (candidateState(sourceUuid)) {
        ProgramBackupCandidateState.MISSING -> return ProgramBackupMergeDecision.Import(sourceUuid)
        ProgramBackupCandidateState.EQUIVALENT -> return ProgramBackupMergeDecision.Reuse(sourceUuid)
        ProgramBackupCandidateState.DIVERGENT -> Unit
    }
    repeat(MAX_DETERMINISTIC_BACKUP_UUID_ATTEMPTS) { attempt ->
        val derivedUuid = deterministicProgramBackupCopyUuid(
            sourceUuid = sourceUuid,
            sourceDefinitionIdentity = sourceDefinitionIdentity,
            attempt = attempt,
        )
        when (candidateState(derivedUuid)) {
            ProgramBackupCandidateState.MISSING -> {
                return ProgramBackupMergeDecision.Import(derivedUuid)
            }
            ProgramBackupCandidateState.EQUIVALENT -> {
                return ProgramBackupMergeDecision.Reuse(derivedUuid)
            }
            ProgramBackupCandidateState.DIVERGENT -> Unit
        }
    }
    error("Unable to allocate a deterministic training-program backup UUID")
}

private fun deterministicProgramBackupCopyUuid(
    sourceUuid: String,
    sourceDefinitionIdentity: String,
    attempt: Int,
): String {
    val seed = buildString {
        append("fitregimen-program-backup-copy-v1\u0000")
        append(sourceUuid)
        append('\u0000')
        append(sourceDefinitionIdentity)
        append('\u0000')
        append(attempt)
    }
    return UUID.nameUUIDFromBytes(seed.toByteArray(Charsets.UTF_8)).toString()
}

internal fun deterministicTrainingExerciseBackupCopyUuid(
    sourceUuid: String,
    sourceDefinitionIdentity: String,
    attempt: Int,
): String {
    val seed = buildString {
        append("fitregimen-exercise-backup-copy-v1\u0000")
        append(sourceUuid)
        append('\u0000')
        append(sourceDefinitionIdentity)
        append('\u0000')
        append(attempt)
    }
    return UUID.nameUUIDFromBytes(seed.toByteArray(Charsets.UTF_8)).toString()
}

private fun String?.normalizedText(): String? = this?.trim()?.takeIf(String::isNotEmpty)
