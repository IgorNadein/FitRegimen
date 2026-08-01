/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 */
package de.tadris.fitness.data.backup

import de.tadris.fitness.data.FitnessProfileDraft
import de.tadris.fitness.data.FitnessProfileValidator
import de.tadris.fitness.data.MetricKind
import de.tadris.fitness.data.CompletionRule
import de.tadris.fitness.data.MeasurementSource
import de.tadris.fitness.data.SessionExerciseStatus
import de.tadris.fitness.data.SessionSetStatus
import de.tadris.fitness.data.SetType
import de.tadris.fitness.data.TrainingSessionPhase
import de.tadris.fitness.data.TrainingSessionStatus
import de.tadris.fitness.data.PROGRAM_COMPLETION_STATUS_COMPLETED
import de.tadris.fitness.data.PROGRAM_COMPLETION_STATUS_SKIPPED
import de.tadris.fitness.data.RecordingType
import de.tadris.fitness.data.WorkoutType
import de.tadris.fitness.data.backup.model.ExerciseCatalogExport
import de.tadris.fitness.data.backup.model.FitnessProfileExport
import de.tadris.fitness.data.backup.model.IntervalSetContainer
import de.tadris.fitness.data.backup.model.TrainingSessionExport
import de.tadris.fitness.data.backup.model.ProgramContainer
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import de.tadris.fitness.util.Icon

internal const val EXERCISE_MEDIA_ARCHIVE_PREFIX = "exercise_media/"
internal const val RESTORED_EXERCISE_IMAGE_DIRECTORY = "exercise_images/restored"
internal const val MIN_MODERN_BACKUP_VERSION = 4

internal data class ValidatedModernBackupManifest(
    val version: Int,
    val entryNames: Set<String>,
)

@Throws(IOException::class)
internal fun requireUniqueBackupUuids(label: String, values: List<String>) {
    val normalized = values.map(String::trim)
    if (normalized.any(String::isEmpty) || normalized.distinct().size != normalized.size) {
        throw IOException("Database contains missing or duplicate $label UUIDs")
    }
}

/** Pure ZIP-manifest validation. No database mutation may happen before this succeeds. */
@Throws(IOException::class)
internal fun validateModernBackupManifest(
    entryNames: List<String>,
    versionText: String?,
    currentVersion: Int = BackupWriter.VERSION,
): ValidatedModernBackupManifest {
    if (entryNames.isEmpty()) throw IOException("Backup archive is empty")
    val duplicates = entryNames.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
    if (duplicates.isNotEmpty()) {
        throw IOException("Backup archive contains duplicate entries: ${duplicates.sorted().joinToString()}")
    }
    entryNames.forEach { name ->
        if (!isSafeBackupArchivePath(name)) throw IOException("Unsafe backup entry path: $name")
    }
    if (entryNames.count { it == BackupWriter.FILE_VERSION } != 1) {
        throw IOException("Backup must contain exactly one ${BackupWriter.FILE_VERSION}")
    }
    if (entryNames.count { it == BackupWriter.FILE_DATA } != 1) {
        throw IOException("Backup must contain exactly one ${BackupWriter.FILE_DATA}")
    }
    val normalizedVersion = versionText?.trim().orEmpty()
    val version = normalizedVersion.toIntOrNull()
        ?: throw IOException("Backup version is missing or invalid")
    if (version !in MIN_MODERN_BACKUP_VERSION..currentVersion) {
        throw BackupReader.UnsupportedVersionException("Version $version is unsupported")
    }
    return ValidatedModernBackupManifest(version, entryNames.toSet())
}

internal fun isSafeBackupArchivePath(path: String): Boolean {
    if (path.isBlank() || path.indexOf('\u0000') >= 0 || '\\' in path || path.startsWith('/')) return false
    val segments = path.split('/')
    return segments.none { it.isEmpty() || it == "." || it == ".." }
}

/** Deterministic, filesystem-independent archive path for one private image/GIF. */
internal fun exerciseMediaArchivePath(
    exerciseUuid: String,
    localImagePath: String,
    ordinal: Int,
): String {
    val key = "$exerciseUuid\u0000$localImagePath\u0000$ordinal"
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(key.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
    val extension = File(localImagePath).extension
        .lowercase()
        .takeIf { it.matches(Regex("[a-z0-9]{1,10}")) }
        ?: "bin"
    return "$EXERCISE_MEDIA_ARCHIVE_PREFIX$digest.$extension"
}

internal fun restoredExerciseImagePath(archivePath: String, nonce: String): String {
    require(isSafeBackupArchivePath(archivePath))
    require(archivePath.startsWith(EXERCISE_MEDIA_ARCHIVE_PREFIX))
    val extension = File(archivePath).extension
        .lowercase()
        .takeIf { it.matches(Regex("[a-z0-9]{1,10}")) }
        ?: "bin"
    val safeNonce = nonce.filter(Char::isLetterOrDigit).take(64).ifEmpty { "media" }
    return "$RESTORED_EXERCISE_IMAGE_DIRECTORY/$safeNonce.$extension"
}

/** Strict v7 validation: a malformed profile backup must never degrade into a default profile. */
@Throws(IOException::class)
internal fun validateBackupProfiles(
    version: Int,
    exports: List<FitnessProfileExport>,
): List<Pair<FitnessProfileExport, FitnessProfileDraft>> {
    // A backup without profiles is valid compatibility input: after the Room
    // transaction the repository creates the same average/default user used by
    // older app versions. Once profiles are present, however, restore is strict.
    if (version < 7 || exports.isEmpty()) return emptyList()
    val normalizedUuids = exports.map { it.uuid.trim() }
    if (normalizedUuids.any(String::isEmpty)) throw IOException("A fitness profile has no UUID")
    if (normalizedUuids.distinct().size != normalizedUuids.size) {
        throw IOException("Backup contains duplicate fitness profile UUIDs")
    }
    if (exports.count(FitnessProfileExport::isActive) != 1) {
        throw IOException("Backup must contain exactly one active fitness profile")
    }
    return exports.map { export ->
        val draft = try {
            FitnessProfileValidator.requireValid(export.toDraft())
        } catch (error: IllegalArgumentException) {
            throw IOException("Backup contains an invalid fitness profile", error)
        }
        export to draft
    }
}

@Throws(IOException::class)
internal fun validateExerciseCatalog(
    exports: List<ExerciseCatalogExport>,
    manifestEntries: Set<String>,
) {
    val uuids = exports.map { it.uuid.trim() }
    if (uuids.any(String::isEmpty) || uuids.distinct().size != uuids.size) {
        throw IOException("Backup exercise catalog contains missing or duplicate UUIDs")
    }
    exports.forEach { export ->
        if (export.name.isBlank()) throw IOException("A catalog exercise has no name")
        val metric = export.metricKind.trim().uppercase()
        if (MetricKind.entries.none { it.name == metric }) {
            throw IOException("Unknown exercise metric kind: ${export.metricKind}")
        }
        if (export.met != null && !export.met.isFinite()) throw IOException("Invalid exercise MET value")
        if (export.secondsPerRep != null && !export.secondsPerRep.isFinite()) {
            throw IOException("Invalid exercise seconds-per-repetition value")
        }
        export.imageArchivePath?.let { path ->
            if (!isSafeBackupArchivePath(path) || !path.startsWith(EXERCISE_MEDIA_ARCHIVE_PREFIX)) {
                throw IOException("Unsafe exercise media path: $path")
            }
            if (path !in manifestEntries) throw IOException("Exercise media is missing: $path")
        }
    }
}

@Throws(IOException::class)
internal fun validateBackupIntervalSets(exports: List<IntervalSetContainer>) {
    val setIds = exports.map { it.set.id }
    if (setIds.distinct().size != setIds.size) {
        throw IOException("Backup contains duplicate interval-set ids")
    }
    val intervalIds = mutableSetOf<Long>()
    exports.forEach { container ->
        container.intervals.orEmpty().forEach { interval ->
            if (!intervalIds.add(interval.id)) throw IOException("Backup contains duplicate interval ids")
            if (interval.setId != container.set.id) {
                throw IOException("An interval references a different interval set")
            }
            if (interval.delayMillis < 0L) throw IOException("An interval has a negative duration")
        }
    }
}

@Throws(IOException::class)
internal fun validateBackupWorkoutTypes(version: Int, types: List<WorkoutType>) {
    val ids = types.map { it.id.trim() }
    if (ids.any(String::isEmpty) || ids.distinct().size != ids.size) {
        throw IOException("Backup contains missing or duplicate workout-type ids")
    }
    if (version < 8) return
    val recordingTypeIds = RecordingType.values().map(RecordingType::id).toSet()
    val iconNames = Icon.values().map(Icon::name).toSet() + "list"
    types.forEach { type ->
        if (type.title.isNullOrBlank()) throw IOException("A custom workout type has no title")
        if (type.recordingType !in recordingTypeIds) {
            throw IOException("A custom workout type has an unknown recording mode")
        }
        if (type.icon !in iconNames) throw IOException("A custom workout type has an unknown icon")
    }
}

/** Strict checks for writer-v8 program trees; older backup shapes stay compatible. */
@Throws(IOException::class)
internal fun validateBackupPrograms(version: Int, exports: List<ProgramContainer>) {
    val normalized = exports.map(ProgramImporter::normalizeForImport)
    if (version >= 8) {
        val programUuids = normalized.map { it.program.uuid?.trim().orEmpty() }
        if (programUuids.any(String::isEmpty) || programUuids.distinct().size != programUuids.size) {
            throw IOException("Backup contains missing or duplicate training-program UUIDs")
        }
    }
    normalized.forEach { container ->
        if (container.program.name.isBlank()) throw IOException("A training program has no name")
        val exerciseUuids = container.exercises.map { it.uuid?.trim().orEmpty() }
        if (version >= 8 &&
            (exerciseUuids.any(String::isEmpty) || exerciseUuids.distinct().size != exerciseUuids.size)
        ) {
            throw IOException("A training program has missing or duplicate exercise UUIDs")
        }
        val definedExercises = exerciseUuids.filter(String::isNotEmpty).toSet()
        container.exercises.forEach { exercise ->
            if (exercise.name.isBlank()) throw IOException("A program exercise has no name")
            requireKnownEnum<MetricKind>(exercise.metricKind, "program exercise metric")
            requireFiniteOrNull(exercise.met, "exercise MET")
            requireFiniteOrNull(exercise.secondsPerRep, "exercise repetition duration")
        }
        container.days.forEach { day ->
            day.exercises.forEach { exercise ->
                if (exercise.exerciseUuid.isBlank() || exercise.exerciseUuid !in definedExercises) {
                    throw IOException("A program day references an undefined exercise")
                }
                exercise.measurementSource?.let {
                    requireKnownEnum<MeasurementSource>(it, "program measurement source")
                }
                exercise.completionRule?.let {
                    requireKnownEnum<CompletionRule>(it, "program completion rule")
                }
                if (exercise.restSeconds != null && exercise.restSeconds < 0 ||
                    exercise.prepareSeconds != null && exercise.prepareSeconds < 0 ||
                    exercise.transitionSeconds != null && exercise.transitionSeconds < 0
                ) {
                    throw IOException("A program exercise has a negative timer")
                }
                exercise.sets.forEach { set ->
                    requireKnownEnum<SetType>(set.type, "program set type")
                    if (set.reps != null && set.reps < 0 || set.repsMax != null && set.repsMax < 0 ||
                        set.durationMillis != null && set.durationMillis < 0 ||
                        set.distanceMeters != null && set.distanceMeters < 0.0 ||
                        set.weightKg != null && !set.weightKg.isFinite()
                    ) {
                        throw IOException("A program set has invalid targets")
                    }
                    requireFiniteOrNull(set.distanceMeters, "program set distance")
                }
            }
        }
        container.completions.forEach { completion ->
            if (completion.dayIndex !in container.days.indices ||
                completion.exerciseIndex !in container.days[completion.dayIndex].exercises.indices
            ) {
                throw IOException("A completion references a missing program exercise")
            }
            if (completion.status != PROGRAM_COMPLETION_STATUS_COMPLETED &&
                completion.status != PROGRAM_COMPLETION_STATUS_SKIPPED
            ) {
                throw IOException("A completion has an unknown status")
            }
            requireFinitePositiveOrNull(completion.userWeightKgSnapshot, "completion weight")
            completion.setLogs.forEach { log ->
                requireKnownEnum<SetType>(log.type, "completion set type")
                listOf(
                    log.weightKg,
                    log.distanceMeters,
                    log.targetWeightKg,
                    log.targetDistanceMeters,
                ).forEach { requireFiniteOrNull(it, "completion set measurement") }
            }
        }
    }
}

/** Validates the complete guided-history tree before Room or private files are touched. */
@Throws(IOException::class)
internal fun validateBackupTrainingSessions(
    version: Int,
    sessions: List<TrainingSessionExport>,
    manifestEntries: Set<String>,
) {
    val uuids = sessions.map { it.uuid.trim() }
    if (uuids.any(String::isEmpty) || uuids.distinct().size != uuids.size) {
        throw IOException("Backup contains missing or duplicate training-session UUIDs")
    }
    sessions.forEach { session ->
        if (session.nameSnapshot.isBlank()) throw IOException("A training session has no name")
        requireKnownEnum<TrainingSessionStatus>(session.status, "training-session status")
        requireKnownEnum<TrainingSessionPhase>(session.phase, "training-session phase")
        if ((session.sourceProgramUuid == null) != (session.sourceDayIndex == null)) {
            throw IOException("A training session has an incomplete portable program source")
        }
        session.sourceProgramUuid?.let {
            if (it.isBlank() || session.sourceDayIndex!! < 0) {
                throw IOException("A training session has an invalid portable program source")
            }
        }
        requireFinitePositiveOrNull(session.userWeightKgSnapshot, "session weight")
        requireFinitePositiveOrNull(session.stepLengthMetersSnapshot, "session step length")
        session.exercises.forEach { exercise ->
            if (exercise.exerciseUuidSnapshot.isBlank() || exercise.nameSnapshot.isBlank()) {
                throw IOException("A session exercise has no portable identity or name")
            }
            requireKnownEnum<MetricKind>(exercise.metricKindSnapshot, "session exercise metric")
            requireKnownEnum<MeasurementSource>(exercise.measurementSource, "measurement source")
            requireKnownEnum<CompletionRule>(exercise.completionRule, "completion rule")
            requireKnownEnum<SessionExerciseStatus>(exercise.status, "session exercise status")
            exercise.sourceProgramExerciseIndex?.let { index ->
                if (index < 0 || session.sourceProgramUuid == null) {
                    throw IOException("A session exercise has an invalid portable program source")
                }
            }
            exercise.imageArchivePathSnapshot?.let { path ->
                if (!isSafeBackupArchivePath(path) ||
                    !path.startsWith(EXERCISE_MEDIA_ARCHIVE_PREFIX) ||
                    path !in manifestEntries
                ) {
                    throw IOException("Invalid session exercise media path: $path")
                }
            }
            if (version >= 8 && exercise.imagePathSnapshot != null) {
                throw IOException("Backup contains an app-private session media path")
            }
            exercise.sets.forEach { set ->
                requireKnownEnum<SetType>(set.type, "session set type")
                requireKnownEnum<SessionSetStatus>(set.status, "session set status")
                set.completionSource?.let {
                    requireKnownEnum<MeasurementSource>(it, "session set completion source")
                }
                set.sourceProgramSetIndex?.let { index ->
                    if (index < 0 || exercise.sourceProgramExerciseIndex == null) {
                        throw IOException("A session set has an invalid portable program source")
                    }
                }
                set.linkedWorkoutUuid?.takeIf(String::isBlank)?.let {
                    throw IOException("A session set has a blank linked-workout UUID")
                }
                requireFiniteOrNull(set.targetWeightKg, "target set weight")
                requireFiniteOrNull(set.targetDistanceMeters, "target set distance")
                requireFiniteOrNull(set.actualWeightKg, "actual set weight")
                requireFiniteOrNull(set.actualDistanceMeters, "actual set distance")
            }
        }
    }
}

private inline fun <reified T : Enum<T>> requireKnownEnum(value: String, label: String) {
    val normalized = value.trim().uppercase()
    if (enumValues<T>().none { it.name == normalized }) throw IOException("Unknown $label: $value")
}

private fun requireFiniteOrNull(value: Double?, label: String) {
    if (value != null && !value.isFinite()) throw IOException("Invalid $label")
}

private fun requireFinitePositiveOrNull(value: Double?, label: String) {
    if (value != null && (!value.isFinite() || value <= 0.0)) throw IOException("Invalid $label")
}
