/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 */
package de.tadris.fitness.data.backup

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.fasterxml.jackson.core.StreamReadFeature
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import de.tadris.fitness.Instance
import de.tadris.fitness.R
import de.tadris.fitness.data.AppDatabase
import de.tadris.fitness.data.Exercise
import de.tadris.fitness.data.FitnessProfile
import de.tadris.fitness.data.FitnessProfileDraft
import de.tadris.fitness.data.FitnessProfileSnapshot
import de.tadris.fitness.data.Interval
import de.tadris.fitness.data.IntervalSet
import de.tadris.fitness.data.MetricKind
import de.tadris.fitness.data.ProgramCompletion
import de.tadris.fitness.data.ProgramOccurrenceHistoryRepository
import de.tadris.fitness.data.PROGRAM_OCCURRENCE_WORKOUT_UUID_PREFIX
import de.tadris.fitness.data.SetLog
import de.tadris.fitness.data.SetType
import de.tadris.fitness.data.TrainingSessionRepository
import de.tadris.fitness.data.TrainingSessionStatus
import de.tadris.fitness.data.WorkoutData
import de.tadris.fitness.data.WorkoutType
import de.tadris.fitness.data.WorkoutTypeManager
import de.tadris.fitness.data.isSupportedExerciseVideoUrl
import de.tadris.fitness.data.backup.model.CompletionExport
import de.tadris.fitness.data.backup.model.DataContainer
import de.tadris.fitness.data.backup.model.ExerciseCatalogExport
import de.tadris.fitness.data.backup.model.ExerciseExport
import de.tadris.fitness.data.backup.model.FitnessProfileExport
import de.tadris.fitness.data.backup.model.ProgramContainer
import de.tadris.fitness.data.backup.model.TrainingSessionExport
import de.tadris.fitness.data.backup.model.toEntity
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/** Reads a backup from [input] and writes the result into the local DB. */
class BackupReader(
    private val context: Context,
    private val input: Uri,
    private val replace: Boolean,
    private val listener: BackupWriter.ExportStatusListener,
) {

    private val database: AppDatabase = Instance.getInstance(context).db
    private val importedWorkoutIdMap = mutableMapOf<Long, Long>()
    private val importedIntervalSetIdMap = mutableMapOf<Long, Long>()
    private val importedWorkoutTypeIdMap = mutableMapOf<String, String>()
    private val importedProgramUuidMap = mutableMapOf<String, String>()
    private val importedProgramContentIdentityMap = mutableMapOf<String, String>()
    private val mapper = jsonMapper {
        addModule(kotlinModule())
        configure(StreamReadFeature.IGNORE_UNDEFINED, true)
        configure(StreamReadFeature.AUTO_CLOSE_SOURCE, false)
        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
    }

    @Throws(IOException::class, UnsupportedVersionException::class)
    fun read() {
        listener.onStatusChanged(0, context.getString(R.string.loadingFile))
        val stagedArchive = stageInputArchive()
        try {
            if (checkIfLegacyBackup(stagedArchive)) {
                delegateToLegacyBackup()
                return
            }

            val preflight = preflightModernBackup(stagedArchive)
            val restoredMedia = materializeExerciseMedia(preflight)
            var databaseCommitted = false
            try {
                importedWorkoutIdMap.clear()
                importedIntervalSetIdMap.clear()
                importedWorkoutTypeIdMap.clear()
                importedProgramUuidMap.clear()
                importedProgramContentIdentityMap.clear()
                restoreDatabase(preflight, restoredMedia.pathByArchiveEntry)
                databaseCommitted = true

                // Preferences are not transactional, so apply only after Room commits.
                // Old backups have no preferences block and keep current settings.
                val instance = Instance.getInstance(context)
                preflight.data.preferences?.let { preferences ->
                    PortablePreferencesBackup.restore(
                        preferences = instance.userPreferences.preferences,
                        export = PortablePreferencesBackup.remapWorkoutTypeIds(
                            preferences,
                            importedWorkoutTypeIdMap,
                        ),
                        replace = replace,
                    )
                    instance.distanceUnitUtils.setUnit()
                    instance.energyUnitUtils.setUnit()
                    // Re-schedule only existing local targets and never trigger an
                    // immediate network/directory export during backup import.
                    instance.planner.refreshAutoBackupSchedule()
                    Handler(Looper.getMainLooper()).post {
                        instance.themes.updateDarkModeSetting()
                    }
                }
                // The manager caches custom rows and otherwise retains deleted or
                // pre-import definitions for the rest of the process lifetime.
                WorkoutTypeManager.getInstance().reloadCustomTypes(context)
                // A profile-less replacement gets the app's standard average user.
                ensureProfileAfterRestore(preflight.data.profiles.isNotEmpty())
                removeUnusedMaterializedMedia(preflight.data, restoredMedia)
            } finally {
                if (!databaseCommitted) restoredMedia.createdFiles.forEach(File::delete)
                preflight.stagedMedia.values.forEach(File::delete)
                preflight.stagingDirectory.delete()
            }
            listener.onStatusChanged(100, context.getString(R.string.finished))
        } finally {
            stagedArchive.delete()
        }
    }

    private fun delegateToLegacyBackup() {
        listener.onStatusChanged(0, context.getString(R.string.loadingFile))
        val legacyReader = LegacyBackupReader(
            context,
            input,
            replace,
            object : LegacyBackupReader.ImportStatusListener {
                override fun onStatusChanged(progress: Int, action: String?) {
                    listener.onStatusChanged(progress, action)
                }
            },
        )
        legacyReader.restoreData()
        WorkoutTypeManager.getInstance().reloadCustomTypes(context)
        ensureProfileAfterRestore(hadProfiles = false)
    }

    private fun ensureProfileAfterRestore(hadProfiles: Boolean) {
        val instance = Instance.getInstance(context)
        if (replace && !hadProfiles) {
            instance.fitnessProfileRepository.ensureStandardDefaultProfile()
        } else {
            instance.fitnessProfileRepository.ensureDefaultProfile()
        }
    }

    private data class ModernBackupPreflight(
        val archive: File,
        val manifest: ValidatedModernBackupManifest,
        val data: DataContainer,
        val validatedProfiles: List<Pair<FitnessProfileExport, FitnessProfileDraft>>,
        val stagingDirectory: File,
        val stagedMedia: Map<String, File>,
    )

    private data class RestoredExerciseMedia(
        val pathByArchiveEntry: Map<String, String>,
        val createdFiles: List<File>,
    )

    @Throws(IOException::class)
    private fun stageInputArchive(): File {
        val staged = Files.createTempFile(context.cacheDir.toPath(), "fitregimen_import_", ".zip").toFile()
        try {
            openInputStream().use { source -> staged.outputStream().use(source::copyTo) }
            return staged
        } catch (error: Exception) {
            staged.delete()
            if (error is IOException) throw error
            throw IOException("Unable to stage backup", error)
        }
    }

    @Throws(IOException::class)
    private fun checkIfLegacyBackup(archive: File): Boolean = useZipInputStream(archive) { zipStream ->
        zipStream.nextEntry?.name == "data.xml"
    }

    /**
     * Fully scans the immutable staged ZIP before a Room transaction starts. This validates
     * ZIP CRCs, parses data.json, and stages private media without touching live app data.
     */
    @Throws(IOException::class, UnsupportedVersionException::class)
    private fun preflightModernBackup(archive: File): ModernBackupPreflight {
        val stagingDirectory = Files.createTempDirectory(context.cacheDir.toPath(), "exercise_restore_").toFile()
        val names = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        val stagedMedia = linkedMapOf<String, File>()
        var versionText: String? = null
        var data: DataContainer? = null
        try {
            forEachZipEntry(archive) { entry, stream ->
                val name = entry.name
                names += name
                if (!seen.add(name)) throw IOException("Duplicate backup entry: $name")
                if (!isSafeBackupArchivePath(name)) throw IOException("Unsafe backup entry path: $name")
                when {
                    name == BackupWriter.FILE_VERSION -> {
                        val bytes = stream.readLimitedBytes(MAX_VERSION_BYTES)
                        versionText = bytes.toString(Charsets.UTF_8)
                    }
                    name == BackupWriter.FILE_DATA -> {
                        data = mapper.readValue(stream, DataContainer::class.java)
                    }
                    name.startsWith(EXERCISE_MEDIA_ARCHIVE_PREFIX) -> {
                        val target = File.createTempFile("media_", ".bin", stagingDirectory)
                        stagedMedia[name] = target
                        stream.copyToLimited(target, MAX_MEDIA_ENTRY_BYTES)
                    }
                    else -> stream.drain()
                }
            }
            val manifest = validateModernBackupManifest(names, versionText)
            val parsedData = data ?: throw IOException("Backup data is missing")
            validateBackupIntervalSets(parsedData.intervalSets)
            validateBackupWorkoutTypes(manifest.version, parsedData.workoutTypes)
            val validatedProfiles = validateBackupProfiles(manifest.version, parsedData.profiles)
            validateExerciseCatalog(parsedData.exerciseCatalog, manifest.entryNames)
            validateBackupPrograms(manifest.version, parsedData.programs)
            validateBackupNutritionPlans(manifest.version, parsedData.nutritionPlans)
            parsedData.preferences?.let(PortablePreferencesBackup::decode)
            validateBackupTrainingSessions(
                version = manifest.version,
                sessions = parsedData.sessions,
                manifestEntries = manifest.entryNames,
            )
            return ModernBackupPreflight(
                archive = archive,
                manifest = manifest,
                data = parsedData,
                validatedProfiles = validatedProfiles,
                stagingDirectory = stagingDirectory,
                stagedMedia = stagedMedia,
            )
        } catch (error: Exception) {
            stagedMedia.values.forEach(File::delete)
            stagingDirectory.delete()
            when (error) {
                is UnsupportedVersionException -> throw error
                is IOException -> throw error
                else -> throw IOException("Backup preflight failed", error)
            }
        }
    }

    private fun materializeExerciseMedia(preflight: ModernBackupPreflight): RestoredExerciseMedia {
        val referencedEntries = preflight.data.exerciseCatalog
            .mapNotNull(ExerciseCatalogExport::imageArchivePath)
            .plus(
                preflight.data.sessions.flatMap { session ->
                    session.exercises.mapNotNull { it.imageArchivePathSnapshot }
                }
            )
            .distinct()
        val pathByEntry = linkedMapOf<String, String>()
        val created = mutableListOf<File>()
        try {
            referencedEntries.forEach { archivePath ->
                val staged = preflight.stagedMedia[archivePath]
                    ?: throw IOException("Exercise media is missing: $archivePath")
                var relativePath: String
                var target: File
                do {
                    relativePath = restoredExerciseImagePath(archivePath, UUID.randomUUID().toString())
                    target = resolvePrivateFile(relativePath)
                        ?: throw IOException("Unsafe restored exercise media path")
                } while (target.exists())
                target.parentFile?.let { parent ->
                    if (!parent.exists() && !parent.mkdirs()) {
                        throw IOException("Cannot create exercise media directory")
                    }
                }
                created += target
                staged.copyTo(target, overwrite = false)
                pathByEntry[archivePath] = relativePath
            }
            return RestoredExerciseMedia(pathByEntry, created)
        } catch (error: Exception) {
            created.forEach(File::delete)
            if (error is IOException) throw error
            throw IOException("Unable to restore exercise media", error)
        }
    }

    private fun removeUnusedMaterializedMedia(
        data: DataContainer,
        restoredMedia: RestoredExerciseMedia,
    ) {
        val retainedPaths = data.exerciseCatalog.mapNotNull { export ->
            database.exerciseDao().findByUuid(export.uuid.trim())?.imagePath
        }.plus(
            database.trainingSessionDao().getSessions().flatMap { session ->
                database.trainingSessionDao().getExercisesOfSession(session.id)
                    .mapNotNull { it.imagePathSnapshot }
            }
        ).toSet()
        restoredMedia.createdFiles.filterNot { file ->
            val relative = file.relativeToOrNull(context.filesDir)?.invariantSeparatorsPath
            relative != null && relative in retainedPaths
        }.forEach(File::delete)
    }

    @Throws(IOException::class)
    private fun openInputStream(): InputStream = context.contentResolver.openInputStream(input)
        ?: throw IOException("Failed to open input stream for Uri: $input")

    @Throws(IOException::class)
    private fun restoreDatabase(
        preflight: ModernBackupPreflight,
        mediaPaths: Map<String, String>,
    ) {
        database.runInTransaction {
            if (replace) {
                listener.onStatusChanged(10, context.getString(R.string.restorePreparing))
                database.clearAllDataTables()
            }

            listener.onStatusChanged(20, context.getString(R.string.customWorkoutTypesTitle))
            val pendingExtraData = restoreExtraData(
                dataContainer = preflight.data,
                validatedProfiles = preflight.validatedProfiles,
                mediaPaths = mediaPaths,
            )

            val workoutCount = preflight.manifest.entryNames.count(::isWorkoutFile)
            var processedWorkouts = 0
            forEachZipEntry(preflight.archive) { entry, stream ->
                if (isWorkoutFile(entry.name)) {
                    val progress = 20 + (80 * processedWorkouts / workoutCount.coerceAtLeast(1))
                    listener.onStatusChanged(progress, context.getString(R.string.workouts))
                    restoreWorkout(stream)
                    processedWorkouts++
                }
            }

            restorePendingProgramCompletions(pendingExtraData.completions)
            restorePendingTrainingSessions(
                pending = pendingExtraData.sessions,
                imagePathByExerciseUuid = pendingExtraData.imagePathByExerciseUuid,
                imagePathByArchiveEntry = mediaPaths,
            )
            // program_occurrence_summary and its Workout are deterministic read
            // models. Rebuilding after id remapping prevents missing mappings and
            // duplicate statistics rows from old numeric day ids.
            val profileSnapshot = if (replace && preflight.data.profiles.isEmpty()) {
                FitnessProfileSnapshot(
                    userWeightKg = 80.0,
                    stepLengthMeters = 0.79,
                )
            } else {
                Instance.getInstance(context)
                    .fitnessProfileRepository
                    .captureActiveSnapshot()
            }
            ProgramOccurrenceHistoryRepository(
                db = database,
                userWeightKg = { profileSnapshot.userWeightKg ?: 80.0 },
                profileSnapshot = { profileSnapshot },
            ).rebuildAll()
        }
    }

    private data class PendingExtraData(
        val completions: List<PendingProgramCompletion> = emptyList(),
        val sessions: List<TrainingSessionExport> = emptyList(),
        val imagePathByExerciseUuid: Map<String, String> = emptyMap(),
    )

    private data class PendingProgramCompletion(
        val programExerciseId: Long,
        val export: CompletionExport,
    )

    private fun restorePendingProgramCompletions(pending: List<PendingProgramCompletion>) {
        val dao = database.trainingProgramDao()
        for (item in pending) {
            val workoutId = item.export.workoutUuid?.let { database.workoutDao().findByUuid(it)?.id }
                ?: item.export.workoutId?.let(importedWorkoutIdMap::get)
            val existing = dao.findCompletion(item.programExerciseId, item.export.occurrenceDate)
            if (existing != null && !shouldApplyImportedCompletion(
                    existingCompletedAt = existing.completedAt,
                    existingHasLogs = dao.getLogsOfCompletion(existing.id).isNotEmpty(),
                    incoming = item.export,
                )
            ) {
                continue
            }
            val completion = ProgramCompletion(
                    programExerciseId = item.programExerciseId,
                    id = existing?.id ?: 0,
                    occurrenceDate = item.export.occurrenceDate,
                    status = item.export.status,
                    workoutId = workoutId ?: existing?.workoutId,
                    completedAt = item.export.completedAt,
                    profileUuidSnapshot = item.export.profileUuidSnapshot
                        ?: existing?.profileUuidSnapshot,
                    userWeightKgSnapshot = item.export.userWeightKgSnapshot
                        ?: existing?.userWeightKgSnapshot,
            )
            val completionId = if (existing == null) {
                dao.insertCompletion(completion)
            } else {
                dao.updateCompletion(completion)
                dao.deleteLogsOfCompletion(existing.id)
                existing.id
            }
            item.export.setLogs.forEachIndexed { index, log ->
                dao.insertSetLog(
                    SetLog(
                        completionId = completionId,
                        orderIndex = index,
                        type = SetType.findByName(log.type.trim().uppercase()),
                        reps = log.reps,
                        weightKg = log.weightKg,
                        durationMillis = log.durationMillis,
                        distanceMeters = log.distanceMeters,
                        targetReps = log.targetReps,
                        targetRepsMax = log.targetRepsMax,
                        targetWeightKg = log.targetWeightKg,
                        targetDurationMillis = log.targetDurationMillis,
                        targetDistanceMeters = log.targetDistanceMeters,
                    ),
                )
            }
        }
    }

    private fun restorePendingTrainingSessions(
        pending: List<TrainingSessionExport>,
        imagePathByExerciseUuid: Map<String, String>,
        imagePathByArchiveEntry: Map<String, String>,
    ) {
        val dao = database.trainingSessionDao()
        val workoutDao = database.workoutDao()
        val programDao = database.trainingProgramDao()
        val exerciseDao = database.exerciseDao()
        for (export in pending) {
            if (dao.findSessionByUuid(export.uuid) != null) continue
            val sourceProgram = export.sourceProgramUuid
                ?.let { importedProgramUuidMap[it] ?: it }
                ?.let { uuid -> programDao.getPrograms().firstOrNull { it.uuid == uuid } }
            val sourceDay = sourceProgram
                ?.let { program -> export.sourceDayIndex?.let(programDao.getDaysOfProgram(program.id)::getOrNull) }
            val sessionId = dao.insertSession(
                export.toEntity(
                    sourceProgramId = sourceProgram?.id,
                    sourceDayId = sourceDay?.id,
                )
            )
            export.exercises.forEachIndexed { exerciseIndex, exerciseExport ->
                val catalogExercise = exerciseDao.findByUuid(exerciseExport.exerciseUuidSnapshot)
                val sourceProgramExercise = sourceDay
                    ?.let { day ->
                        exerciseExport.sourceProgramExerciseIndex
                            ?.let(programDao.getExercisesOfDay(day.id)::getOrNull)
                    }
                    ?.takeIf { sourceEntry ->
                        exerciseDao.findById(sourceEntry.exerciseId)?.uuid ==
                            exerciseExport.exerciseUuidSnapshot
                    }
                val restoredImagePath = exerciseExport.imageArchivePathSnapshot
                    ?.let(imagePathByArchiveEntry::get)
                    ?: imagePathByExerciseUuid[exerciseExport.exerciseUuidSnapshot]
                val restoredExercise = exerciseExport.copy(
                    workoutTypeIdSnapshot = exerciseExport.workoutTypeIdSnapshot
                        ?.let(::remapWorkoutTypeId),
                ).toEntity(
                    sessionId = sessionId,
                    orderIndex = exerciseIndex,
                    sourceProgramExerciseId = sourceProgramExercise?.id,
                    sourceExerciseId = catalogExercise?.id,
                    restoredImagePath = restoredImagePath,
                )
                val exerciseId = dao.insertSessionExercise(restoredExercise)
                exerciseExport.sets.forEachIndexed { setIndex, setExport ->
                    val sourceProgramSet = sourceProgramExercise
                        ?.let { entry ->
                            setExport.sourceProgramSetIndex
                                ?.let(programDao.getSetsOfProgramExercise(entry.id)::getOrNull)
                        }
                    dao.insertSessionSet(
                        setExport.toEntity(
                            sessionExerciseId = exerciseId,
                            orderIndex = setIndex,
                            workoutIdForUuid = { uuid -> workoutDao.findByUuid(uuid)?.id },
                            sourceProgramSetId = sourceProgramSet?.id,
                        ),
                    )
                }
            }
            if (
                export.status.equals(TrainingSessionStatus.COMPLETED.name, ignoreCase = true) ||
                export.status.equals(TrainingSessionStatus.ABORTED.name, ignoreCase = true)
            ) {
                TrainingSessionRepository(database).finishSession(
                    sessionId = sessionId,
                    now = export.completedAt ?: export.updatedAt,
                )
            }
        }
    }

    private inline fun forEachZipEntry(
        archive: File,
        action: (ZipEntry, ZipInputStream) -> Unit,
    ) {
        useZipInputStream(archive) { zipStream ->
            var entry = zipStream.nextEntry
            while (entry != null) {
                action(entry, zipStream)
                zipStream.closeEntry()
                entry = zipStream.nextEntry
            }
        }
    }

    private inline fun <T> useZipInputStream(archive: File, action: (ZipInputStream) -> T): T =
        archive.inputStream().use { fileStream -> ZipInputStream(fileStream).use(action) }

    private fun isWorkoutFile(name: String): Boolean =
        name.startsWith("workout_") && name.endsWith(".json")

    @Throws(IOException::class)
    private fun restoreExtraData(
        dataContainer: DataContainer,
        validatedProfiles: List<Pair<FitnessProfileExport, FitnessProfileDraft>>,
        mediaPaths: Map<String, String>,
    ): PendingExtraData {
        restoreFitnessProfiles(validatedProfiles)

        restoreIntervalSets(dataContainer)

        restoreWorkoutTypes(dataContainer.workoutTypes)

        val restoredImages = restoreExerciseCatalog(dataContainer.exerciseCatalog, mediaPaths)

        val pendingCompletions = mutableListOf<PendingProgramCompletion>()
        val backedUpActiveProfile = validatedProfiles.firstOrNull { (export, _) -> export.isActive }
        val programDao = database.trainingProgramDao()
        for (rawProgramContainer in dataContainer.programs) {
            val sourceProgramContainer = ProgramImporter.normalizeForImport(rawProgramContainer)
            val programContainer = resolveProgramExerciseDefinitions(
                source = sourceProgramContainer,
                remapped = remapProgramWorkoutTypes(sourceProgramContainer),
            )
            val importedProgramUuid = programContainer.program.uuid?.trim()?.takeIf(String::isNotEmpty)
            val sourceDefinitionIdentity = trainingProgramDefinitionFingerprint(sourceProgramContainer)
            val expectedDefinitionIdentity = trainingProgramDefinitionFingerprint(programContainer)
            val mergeDecision = importedProgramUuid?.let { sourceUuid ->
                resolveProgramBackupMerge(
                    sourceUuid = sourceUuid,
                    sourceDefinitionIdentity = sourceDefinitionIdentity,
                    replace = replace,
                    candidateState = { candidateUuid ->
                        val candidate = programDao.getPrograms()
                            .firstOrNull { it.uuid == candidateUuid }
                        if (candidate == null) {
                            ProgramBackupCandidateState.MISSING
                        } else {
                            val candidateIdentity = trainingProgramDefinitionFingerprint(
                                ProgramExporter.buildContainer(
                                    context = context,
                                    program = candidate,
                                    includeCompletions = true,
                                )
                            )
                            if (candidateIdentity == expectedDefinitionIdentity) {
                                ProgramBackupCandidateState.EQUIVALENT
                            } else {
                                ProgramBackupCandidateState.DIVERGENT
                            }
                        }
                    },
                )
            }
            val targetProgramId: Long
            val targetProgramUuid: String
            if (mergeDecision is ProgramBackupMergeDecision.Reuse) {
                val existingProgram = programDao.getPrograms()
                    .firstOrNull { it.uuid == mergeDecision.targetUuid }
                    ?: throw IOException("Resolved training program cannot be found")
                targetProgramId = existingProgram.id
                targetProgramUuid = existingProgram.uuid
            } else {
                // A divergent source UUID is preserved under a deterministic UUID
                // derived from the complete source definition.
                targetProgramId = ProgramImporter.import(context, programContainer)
                val restored = programDao.findProgramById(targetProgramId)
                    ?: throw IOException("Restored program cannot be found")
                targetProgramUuid = (mergeDecision as? ProgramBackupMergeDecision.Import)
                    ?.targetUuid
                    ?: restored.uuid
                programDao.updateProgram(
                    restored.copy(
                        uuid = targetProgramUuid,
                        startDate = programContainer.program.startDate,
                        createdAt = programContainer.program.createdAt
                            ?.takeIf { it > 0L }
                            ?: restored.createdAt,
                    ),
                )
            }
            importedProgramUuid?.let {
                importedProgramUuidMap[it] = targetProgramUuid
                importedProgramContentIdentityMap[it] = sourceDefinitionIdentity
            }
            val days = programDao.getDaysOfProgram(targetProgramId)
            for (completionExport in programContainer.completions) {
                val day = days.getOrNull(completionExport.dayIndex)
                    ?: throw IOException("Program completion references a missing day")
                val programExercise = programDao.getExercisesOfDay(day.id)
                    .getOrNull(completionExport.exerciseIndex)
                    ?: throw IOException("Program completion references a missing exercise")
                pendingCompletions += PendingProgramCompletion(
                    programExerciseId = programExercise.id,
                    export = completionExport.copy(
                        profileUuidSnapshot = completionExport.profileUuidSnapshot
                            ?: backedUpActiveProfile?.first?.uuid,
                        userWeightKgSnapshot = completionExport.userWeightKgSnapshot
                            ?: backedUpActiveProfile?.second?.weightKg,
                    ),
                )
            }
        }
        // Nutrition plan trees are restored only after profiles and training programs.
        // Profiles retain their portable UUID during restore; training programs may be
        // assigned a new UUID on a divergent merge and therefore use the explicit map.
        dataContainer.nutritionPlans.forEach { container ->
            val remapped = container.remapLinkedTrainingProgramUuid(importedProgramUuidMap)
            val decision = resolveNutritionBackupMerge(
                source = container,
                incoming = remapped,
                linkedProgramContentIdentity = container.plan.linkedTrainingProgramUuid
                    ?.let(importedProgramContentIdentityMap::get),
                replace = replace,
                findByUuid = { uuid ->
                    database.nutritionPlanDao().findPlanByUuid(uuid)?.let { existing ->
                        NutritionPlanExporter.buildContainer(
                            context = context,
                            plan = existing,
                            includePrivateLinks = true,
                        )
                    }
                },
            )
            if (decision is NutritionBackupMergeDecision.Import) {
                NutritionPlanImporter.importFromBackup(context, decision.container)
            }
        }
        return PendingExtraData(
            completions = pendingCompletions,
            sessions = dataContainer.sessions,
            imagePathByExerciseUuid = restoredImages,
        )
    }

    /**
     * ProgramImporter normally enriches a catalog collision and keeps local non-blank
     * metadata. A full backup instead needs a portable copy of a divergent definition;
     * otherwise the restored program can never match its own source fingerprint.
     */
    private fun resolveProgramExerciseDefinitions(
        source: ProgramContainer,
        remapped: ProgramContainer,
    ): ProgramContainer {
        val exerciseDao = database.exerciseDao()
        val sourceByUuid = source.exercises.associateBy { it.uuid.orEmpty() }
        val targetUuidBySourceUuid = linkedMapOf<String, String>()
        val resolvedExercises = remapped.exercises.map { incoming ->
            val sourceUuid = incoming.uuid.orEmpty()
            val sourceExercise = sourceByUuid[sourceUuid] ?: incoming
            val sourceIdentity = trainingExerciseDefinitionFingerprint(
                exercise = sourceExercise,
                customWorkoutTypes = source.customWorkoutTypes,
            )
            val expectedIdentity = trainingExerciseDefinitionFingerprint(
                exercise = incoming,
                customWorkoutTypes = remapped.customWorkoutTypes,
            )
            fun existingIdentity(existing: Exercise): String =
                trainingExerciseDefinitionFingerprint(
                    exercise = existing.toProgramExerciseExport(),
                    customWorkoutTypes = existing.workoutTypeId
                        ?.let(database.workoutTypeDao()::findById)
                        ?.let { listOf(it) }
                        .orEmpty(),
                )

            val kind = MetricKind.findByName(incoming.metricKind.trim().uppercase())
            val collision = exerciseDao.findByUuid(sourceUuid)
                ?: exerciseDao.findByNameAndKind(incoming.name.trim(), kind.name)
            val targetUuid = when {
                collision == null -> sourceUuid
                existingIdentity(collision) == expectedIdentity -> collision.uuid
                else -> {
                    var resolvedUuid: String? = null
                    for (attempt in 0 until MAX_BACKUP_EXERCISE_UUID_ATTEMPTS) {
                        val candidateUuid = deterministicTrainingExerciseBackupCopyUuid(
                            sourceUuid = sourceUuid,
                            sourceDefinitionIdentity = sourceIdentity,
                            attempt = attempt,
                        )
                        val candidate = exerciseDao.findByUuid(candidateUuid)
                        when {
                            candidate == null -> {
                                exerciseDao.insert(incoming.toBackupExercise(candidateUuid))
                                resolvedUuid = candidateUuid
                            }
                            existingIdentity(candidate) == expectedIdentity -> {
                                resolvedUuid = candidateUuid
                            }
                        }
                        if (resolvedUuid != null) break
                    }
                    resolvedUuid
                        ?: throw IOException("Unable to allocate a backup exercise UUID")
                }
            }
            targetUuidBySourceUuid[sourceUuid] = targetUuid
            incoming.copy(uuid = targetUuid)
        }
        return remapped.copy(
            exercises = resolvedExercises,
            days = remapped.days.map { day ->
                day.copy(
                    exercises = day.exercises.map { entry ->
                        entry.copy(
                            exerciseUuid = targetUuidBySourceUuid[entry.exerciseUuid]
                                ?: entry.exerciseUuid,
                        )
                    },
                )
            },
        )
    }

    private fun Exercise.toProgramExerciseExport(): ExerciseExport = ExerciseExport(
        uuid = uuid,
        name = name,
        metricKind = metricKind.name,
        workoutTypeId = workoutTypeId,
        description = description,
        tips = tips,
        videoUrl = videoUrl,
        met = met,
        secondsPerRep = secondsPerRep,
    )

    private fun ExerciseExport.toBackupExercise(uuid: String): Exercise = Exercise(
        uuid = uuid,
        name = name.trim(),
        metricKind = MetricKind.findByName(metricKind.trim().uppercase()),
        workoutTypeId = workoutTypeId?.takeIf(String::isNotBlank),
        description = description?.trim()?.takeIf(String::isNotEmpty),
        tips = tips?.trim()?.takeIf(String::isNotEmpty),
        videoUrl = videoUrl?.trim()?.takeIf(::isSupportedExerciseVideoUrl),
        met = met?.takeIf { it.isFinite() && it in 0.5..25.0 },
        secondsPerRep = secondsPerRep?.takeIf { it.isFinite() && it in 0.1..60.0 },
    )

    /**
     * Interval ids predate portable UUIDs. On merge, remap collisions as a whole
     * instead of silently attaching incoming intervals to an unrelated local set.
     */
    private fun restoreIntervalSets(dataContainer: DataContainer) {
        val dao = database.intervalDao()
        dataContainer.intervalSets.forEach { container ->
            val incomingSet = container.set
            val incomingIntervals = container.intervals.orEmpty()
            val existingSet = dao.getSet(incomingSet.id)
            if (!replace && existingSet != null && intervalSetMatchesBackup(
                    existingSet,
                    dao.getAllIntervalsOfSet(existingSet.id).toList(),
                    incomingSet,
                    incomingIntervals,
                )
            ) {
                importedIntervalSetIdMap[incomingSet.id] = existingSet.id
                return@forEach
            }

            val targetSetId = if (replace || existingSet == null) {
                incomingSet.id
            } else {
                firstAvailableBackupEntityId(
                    seed = maxOf(System.currentTimeMillis(), incomingSet.id.safeIncrement()),
                    isOccupied = { candidate -> dao.getSet(candidate) != null },
                )
            }
            val restoredSet = IntervalSet().also {
                it.id = targetSetId
                it.name = incomingSet.name
                it.state = incomingSet.state
            }
            dao.insertIntervalSet(restoredSet)
            importedIntervalSetIdMap[incomingSet.id] = targetSetId

            incomingIntervals.forEach { incoming ->
                val targetIntervalId = if (replace || dao.findById(incoming.id) == null) {
                    incoming.id
                } else {
                    firstAvailableBackupEntityId(
                        seed = maxOf(System.currentTimeMillis(), incoming.id.safeIncrement()),
                        isOccupied = { candidate -> dao.findById(candidate) != null },
                    )
                }
                dao.insertInterval(
                    Interval().also {
                        it.id = targetIntervalId
                        it.setId = targetSetId
                        it.name = incoming.name
                        it.delayMillis = incoming.delayMillis
                    }
                )
            }
        }
    }

    private fun intervalSetMatchesBackup(
        existingSet: IntervalSet,
        existingIntervals: List<Interval>,
        incomingSet: IntervalSet,
        incomingIntervals: List<Interval>,
    ): Boolean {
        if (existingSet.name != incomingSet.name || existingSet.state != incomingSet.state) return false
        val existingById = existingIntervals.associateBy(Interval::id)
        return existingIntervals.size == incomingIntervals.size && incomingIntervals.all { incoming ->
            existingById[incoming.id]?.let { existing ->
                existing.name == incoming.name && existing.delayMillis == incoming.delayMillis
            } == true
        }
    }

    /** Custom type ids are chosen by users, so equal strings are not global identities. */
    private fun restoreWorkoutTypes(types: List<WorkoutType>) {
        val dao = database.workoutTypeDao()
        types.forEach { incoming ->
            val importedId = incoming.id.trim()
            if (importedId.isEmpty()) throw IOException("A custom workout type has no id")
            val targetId = resolveBackupWorkoutTypeId(
                incoming = incoming,
                reservedIds = BUILT_IN_WORKOUT_TYPE_IDS,
                findById = dao::findById,
            )
            if (dao.findById(targetId) == null) {
                dao.insert(copyWorkoutTypeWithId(incoming, targetId))
            }
            importedWorkoutTypeIdMap[importedId] = targetId
        }
    }

    private fun remapWorkoutTypeId(importedId: String): String =
        importedWorkoutTypeIdMap[importedId] ?: importedId

    private fun remapProgramWorkoutTypes(
        container: de.tadris.fitness.data.backup.model.ProgramContainer,
    ) = container.copy(
        customWorkoutTypes = container.customWorkoutTypes.map { type ->
            copyWorkoutTypeWithId(type, remapWorkoutTypeId(type.id))
        },
        exercises = container.exercises.map { exercise ->
            exercise.copy(workoutTypeId = exercise.workoutTypeId?.let(::remapWorkoutTypeId))
        },
    )

    private fun restoreExerciseCatalog(
        exports: List<ExerciseCatalogExport>,
        mediaPaths: Map<String, String>,
    ): Map<String, String> {
        val dao = database.exerciseDao()
        val imagePathByUuid = linkedMapOf<String, String>()
        exports.forEach { rawExport ->
            val export = rawExport.copy(
                workoutTypeId = rawExport.workoutTypeId?.let(::remapWorkoutTypeId),
            )
            val uuid = export.uuid.trim()
            val existing = dao.findByUuid(uuid)
            val restoredImagePath = export.imageArchivePath?.let { archivePath ->
                mediaPaths[archivePath] ?: throw IOException("Exercise media was not materialized: $archivePath")
            }
            val importedExercise = export.toEntity(restoredImagePath)
            val exercise = if (existing == null) {
                val id = dao.insert(importedExercise)
                dao.findById(id) ?: throw IOException("Restored exercise cannot be found")
            } else {
                ProgramImporter.enrichExistingExercise(existing, importedExercise)
                    .copy(imagePath = existing.imagePath ?: importedExercise.imagePath)
                    .also { enriched ->
                        if (enriched != existing) dao.update(enriched)
                    }
            }
            exercise.imagePath?.let { imagePathByUuid[uuid] = it }
        }
        return imagePathByUuid
    }

    /** Restores sensitive profile data only from a validated full backup. */
    private fun restoreFitnessProfiles(
        validated: List<Pair<FitnessProfileExport, FitnessProfileDraft>>,
    ) {
        if (validated.isEmpty()) return
        val dao = database.fitnessProfileDao()
        var restoredActiveUuid: String? = null
        validated.forEach { (export, draft) ->
            val uuid = export.uuid.trim()
            if (dao.findByUuid(uuid) != null) {
                if (replace) throw IOException("Fitness profile UUID collision during replacement restore")
                return@forEach
            }
            val now = System.currentTimeMillis()
            dao.insert(
                FitnessProfile(
                    uuid = uuid,
                    name = draft.name,
                    isActive = replace && export.isActive,
                    birthDateEpochDay = draft.birthDateEpochDay,
                    heightCm = draft.heightCm,
                    weightKg = draft.weightKg,
                    stepLengthCm = draft.stepLengthCm,
                    gender = draft.gender,
                    fitnessLevel = draft.fitnessLevel,
                    notes = draft.notes,
                    customAttributesJson = draft.customAttributesJson,
                    createdAt = export.createdAt.takeIf { it > 0 } ?: now,
                    updatedAt = export.updatedAt.takeIf { it > 0 } ?: now,
                ),
            )
            if (replace && export.isActive) restoredActiveUuid = uuid
        }
        if (replace) {
            val activeUuid = restoredActiveUuid
                ?: throw IOException("Backup active fitness profile was not restored")
            if (dao.getActive()?.uuid != activeUuid || dao.countActive() != 1) {
                throw IOException("Backup active fitness profile was restored ambiguously")
            }
        } else if (dao.getActive() == null) {
            dao.getOldest()?.let { dao.activate(it.id, System.currentTimeMillis()) }
                ?: throw IOException("No active fitness profile after merge")
        }
    }

    @Throws(IOException::class)
    private fun restoreWorkout(zipStream: ZipInputStream) {
        val workoutData = mapper.readValue(zipStream, WorkoutData::class.java)
        val workoutDao = database.workoutDao()
        // The occurrence UUID embeds a local numeric day id and therefore is not
        // portable. Its completions are authoritative and rebuild it after import.
        if (workoutData.workout.uuid.startsWith(PROGRAM_OCCURRENCE_WORKOUT_UUID_PREFIX)) return
        workoutDao.findByUuid(workoutData.workout.uuid)?.let { existing ->
            importedWorkoutIdMap[workoutData.workout.id] = existing.id
            return
        }
        val importedId = workoutData.workout.id
        val targetId = if (workoutDao.findById(importedId) == null) {
            importedId
        } else {
            val afterImported = if (importedId in 1 until Long.MAX_VALUE) importedId + 1 else 1L
            firstAvailableWorkoutId(
                seed = maxOf(System.currentTimeMillis(), afterImported),
                isOccupied = { candidate -> workoutDao.findById(candidate) != null },
            )
        }
        workoutDao.insertWorkoutData(
            workoutData.remapForBackupImport(
                newWorkoutId = targetId,
                intervalSetIdForImportedId = { importedIntervalSetId ->
                    importedIntervalSetIdMap[importedIntervalSetId] ?: importedIntervalSetId
                },
                workoutTypeIdForImportedId = ::remapWorkoutTypeId,
            ).let { remapped ->
                remapped.copy(
                    workout = remapped.workout.copy(
                        programUuid = remapped.workout.programUuid
                            ?.let { importedProgramUuidMap[it] ?: it },
                    )
                )
            }
        )
        importedWorkoutIdMap[importedId] = targetId
    }

    private fun resolvePrivateFile(relativePath: String): File? = runCatching {
        if (File(relativePath).isAbsolute) return@runCatching null
        val root = context.filesDir.canonicalFile
        val candidate = File(root, relativePath).canonicalFile
        candidate.takeIf { it.path.startsWith(root.path + File.separator) }
    }.getOrNull()

    private fun InputStream.readLimitedBytes(limit: Long): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) throw IOException("Backup entry exceeds its size limit")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun InputStream.copyToLimited(target: File, limit: Long) {
        target.outputStream().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val read = read(buffer)
                if (read < 0) break
                total += read
                if (total > limit) throw IOException("Exercise media exceeds its size limit")
                output.write(buffer, 0, read)
            }
        }
    }

    private fun InputStream.drain() {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            if (read(buffer) < 0) return
        }
    }

    internal class UnsupportedVersionException(message: String?) : IOException(message)

    private companion object {
        const val MAX_VERSION_BYTES = 64L
        const val MAX_MEDIA_ENTRY_BYTES = 100L * 1024L * 1024L
        const val MAX_BACKUP_EXERCISE_UUID_ATTEMPTS = 1_024
    }
}

internal fun firstAvailableBackupEntityId(
    seed: Long,
    isOccupied: (Long) -> Boolean,
): Long = firstAvailableWorkoutId(seed, isOccupied)

private fun Long.safeIncrement(): Long = if (this in 1 until Long.MAX_VALUE) this + 1 else 1L

internal val BUILT_IN_WORKOUT_TYPE_IDS = setOf(
    WorkoutTypeManager.WORKOUT_TYPE_ID_RUNNING,
    WorkoutTypeManager.WORKOUT_TYPE_ID_WALKING,
    WorkoutTypeManager.WORKOUT_TYPE_ID_HIKING,
    WorkoutTypeManager.WORKOUT_TYPE_ID_CYCLING,
    WorkoutTypeManager.WORKOUT_TYPE_ID_INLINE_SKATING,
    WorkoutTypeManager.WORKOUT_TYPE_ID_SKATEBOARDING,
    WorkoutTypeManager.WORKOUT_TYPE_ID_ROWING,
    WorkoutTypeManager.WORKOUT_TYPE_ID_SWIMMING,
    WorkoutTypeManager.WORKOUT_TYPE_ID_OTHER,
    WorkoutTypeManager.WORKOUT_TYPE_ID_PROGRAM_TRAINING,
    WorkoutTypeManager.WORKOUT_TYPE_ID_TREADMILL,
    WorkoutTypeManager.WORKOUT_TYPE_ID_ROPE_SKIPPING,
    WorkoutTypeManager.WORKOUT_TYPE_ID_TRAMPOLINE_JUMPING,
    WorkoutTypeManager.WORKOUT_TYPE_ID_PUSH_UPS,
    WorkoutTypeManager.WORKOUT_TYPE_ID_PULL_UPS,
)

internal fun workoutTypesEquivalent(first: WorkoutType?, second: WorkoutType): Boolean =
    first != null && first.title == second.title && first.minDistance == second.minDistance &&
        first.color == second.color && first.icon == second.icon && first.MET == second.MET &&
        first.recordingType == second.recordingType

internal fun uniqueImportedWorkoutTypeId(
    importedId: String,
    isOccupied: (String) -> Boolean,
): String {
    val base = importedId.take(80).ifBlank { "custom" }
    var ordinal = 1
    while (true) {
        val suffix = if (ordinal == 1) "_imported" else "_imported_$ordinal"
        val candidate = base + suffix
        if (!isOccupied(candidate)) return candidate
        check(ordinal < Int.MAX_VALUE) { "No workout type id is available" }
        ordinal++
    }
}

/**
 * Reuses the first equivalent derived row before allocating another suffix.
 * This keeps repeated merge restores idempotent and their portable id remap stable.
 */
internal fun resolveBackupWorkoutTypeId(
    incoming: WorkoutType,
    reservedIds: Set<String>,
    findById: (String) -> WorkoutType?,
): String {
    val importedId = incoming.id.trim()
    require(importedId.isNotEmpty()) { "A custom workout type must have an id" }

    fun canUse(candidate: String): Boolean {
        if (candidate in reservedIds) return false
        val existing = findById(candidate)
        return existing == null || workoutTypesEquivalent(existing, incoming)
    }

    if (canUse(importedId)) return importedId
    val base = importedId.take(80).ifBlank { "custom" }
    var ordinal = 1
    while (true) {
        val suffix = if (ordinal == 1) "_imported" else "_imported_$ordinal"
        val candidate = base + suffix
        if (canUse(candidate)) return candidate
        check(ordinal < Int.MAX_VALUE) { "No workout type id is available" }
        ordinal++
    }
}

internal fun copyWorkoutTypeWithId(source: WorkoutType, id: String): WorkoutType = WorkoutType(
    id,
    source.title,
    source.minDistance,
    source.color,
    source.icon,
    source.MET,
    source.recordingType,
    source.repeatingExerciseName,
)

internal fun shouldApplyImportedCompletion(
    existingCompletedAt: Long,
    existingHasLogs: Boolean,
    incoming: CompletionExport,
): Boolean = incoming.completedAt > existingCompletedAt ||
    (incoming.completedAt == existingCompletedAt && !existingHasLogs && incoming.setLogs.isNotEmpty())
