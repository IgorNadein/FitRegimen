/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack
 *
 * FitoTrack is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     FitoTrack is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.tadris.fitness.data.backup

import android.content.Context
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.json.JsonMapper
import de.tadris.fitness.Instance
import de.tadris.fitness.R
import de.tadris.fitness.data.WorkoutData
import de.tadris.fitness.data.PROGRAM_OCCURRENCE_WORKOUT_UUID_PREFIX
import de.tadris.fitness.data.backup.model.DataContainer
import de.tadris.fitness.data.backup.model.IntervalSetContainer
import de.tadris.fitness.data.backup.model.toCatalogBackupExport
import de.tadris.fitness.data.backup.model.toBackupExport
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupWriter(
    private val context: Context,
    private val output: File,
    private val listener: ExportStatusListener
) {

    companion object {
        internal const val VERSION = 8
        internal const val FILE_VERSION = "version.txt"
        internal const val FILE_DATA = "data.json"
        internal const val FILE_WORKOUT = "workout_%d.json"
    }

    private val db = Instance.getInstance(context).db
    private val mapper = JsonMapper.builder()
        .disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET)
        .build()

    fun write() {
        val destination = output.absoluteFile
        val parent = destination.parentFile
            ?: throw java.io.IOException("Backup destination has no parent directory")
        if (!parent.exists() && !parent.mkdirs()) {
            throw java.io.IOException("Cannot create backup directory: $parent")
        }
        val temporary = File.createTempFile(".${destination.name}.", ".tmp", parent)
        var published = false
        try {
            FileOutputStream(temporary).use { fileStream ->
                ZipOutputStream(fileStream).use { zipStream ->
                    // One Room transaction gives every DAO query the same snapshot. The ZIP is
                    // written to a sibling temporary file, so an exception cannot replace the
                    // last known-good backup with a recognizable but incomplete archive.
                    db.runInTransaction {
                        listener.onStatusChanged(0, context.getString(R.string.initialising))
                        writeVersionFile(zipStream)
                        listener.onStatusChanged(0, context.getString(R.string.customWorkoutTypesTitle))
                        val media = writeExtraData(zipStream)
                        writeExerciseMedia(zipStream, media)
                        listener.onStatusChanged(0, context.getString(R.string.workouts))
                        writeWorkouts(zipStream)
                    }
                    zipStream.finish()
                    zipStream.flush()
                    fileStream.fd.sync()
                }
            }
            publishTemporaryFile(temporary, destination)
            published = true
            listener.onStatusChanged(100, context.getString(R.string.finished))
        } finally {
            if (!published) temporary.delete()
        }
    }

    private fun writeVersionFile(zipStream: ZipOutputStream) {
        zipStream.putNextEntry(ZipEntry(FILE_VERSION))
        zipStream.write(VERSION.toString().toByteArray())
        zipStream.closeEntry()
    }

    private data class ExerciseMediaSource(
        val archivePath: String,
        val source: File,
    )

    private fun writeExtraData(zipStream: ZipOutputStream): List<ExerciseMediaSource> {
        val intervalSets = db.intervalDao().allSets.map {
            IntervalSetContainer(it, db.intervalDao().getAllIntervalsOfSet(it.id).toList())
        }
        val programRows = db.trainingProgramDao().getPrograms()
        requireUniqueBackupUuids("training program", programRows.map { it.uuid })
        val programs = programRows.map { program ->
            ProgramExporter.buildContainer(context, program, includeCompletions = true)
        }
        val media = mutableListOf<ExerciseMediaSource>()
        val archivePathByCanonicalSource = linkedMapOf<String, String>()
        fun registerMedia(relativePath: String?, portableKey: String, ordinal: Int): String? {
            val source = relativePath
                ?.let(::resolvePrivateExerciseMedia)
                ?.takeIf(File::isFile)
                ?: return null
            val canonicalSource = source.canonicalPath
            archivePathByCanonicalSource[canonicalSource]?.let { return it }
            val archivePath = exerciseMediaArchivePath(portableKey, relativePath, ordinal)
            archivePathByCanonicalSource[canonicalSource] = archivePath
            media += ExerciseMediaSource(archivePath, source)
            return archivePath
        }
        val exerciseRows = db.exerciseDao().getAllExercises()
        requireUniqueBackupUuids("exercise", exerciseRows.map { it.uuid })
        val exerciseCatalog = exerciseRows.mapIndexed { index, exercise ->
            val archivePath = registerMedia(exercise.imagePath, exercise.uuid, index)
            exercise.toCatalogBackupExport(archivePath)
        }
        val sessionDao = db.trainingSessionDao()
        val workoutDao = db.workoutDao()
        val programDao = db.trainingProgramDao()
        val sessionRows = sessionDao.getSessions()
        requireUniqueBackupUuids("training session", sessionRows.map { it.uuid })
        val sessions = sessionRows.mapIndexed { sessionIndex, session ->
            val exercises = sessionDao.getExercisesOfSession(session.id).map { exercise ->
                SessionExerciseWithSets(
                    exercise = exercise,
                    sets = sessionDao.getSetsOfExercise(exercise.id),
                )
            }
            val portableSetIndexById = exercises.flatMap { tree ->
                tree.sets.mapNotNull { sessionSet ->
                    val sourceSet = sessionSet.sourceProgramSetId?.let(programDao::findSetById)
                    sourceSet
                        ?.takeIf {
                            it.programExerciseId == tree.exercise.sourceProgramExerciseId
                        }
                        ?.let { set ->
                            programDao.getSetsOfProgramExercise(set.programExerciseId)
                                .indexOfFirst { it.id == set.id }
                                .takeIf { it >= 0 }
                                ?.let { index -> set.id to index }
                        }
                }
            }.toMap()
            session.toBackupExport(
                exercises = exercises,
                workoutUuidForId = { workoutId -> workoutDao.findById(workoutId)?.uuid },
                sourceProgramUuidForId = { programId ->
                    programDao.findProgramById(programId)?.uuid
                },
                sourceDayIndexForId = { dayId ->
                    programDao.findDayById(dayId)?.let { day ->
                        day.takeIf { it.programId == session.sourceProgramId }?.let {
                            programDao.getDaysOfProgram(day.programId).indexOfFirst { it.id == dayId }
                                .takeIf { it >= 0 }
                        }
                    }
                },
                sourceProgramExerciseIndexForId = { programExerciseId ->
                    programDao.findProgramExerciseById(programExerciseId)?.let { entry ->
                        entry.takeIf { it.dayId == session.sourceDayId }?.let {
                            programDao.getExercisesOfDay(entry.dayId)
                                .indexOfFirst { it.id == programExerciseId }
                                .takeIf { it >= 0 }
                        }
                    }
                },
                sourceProgramSetIndexForId = portableSetIndexById::get,
                imageArchivePathForExercise = { exercise ->
                    registerMedia(
                        relativePath = exercise.imagePathSnapshot,
                        portableKey = "session-${session.uuid}-${exercise.exerciseUuidSnapshot}",
                        ordinal = sessionIndex * 10_000 + exercise.orderIndex,
                    )
                },
            )
        }
        val container = DataContainer(
            intervalSets = intervalSets,
            workoutTypes = db.workoutTypeDao().findAll().toList(),
            programs = programs,
            sessions = sessions,
            profiles = db.fitnessProfileDao().getAll().map { it.toBackupExport() },
            exerciseCatalog = exerciseCatalog,
            preferences = PortablePreferencesBackup.export(
                Instance.getInstance(context).userPreferences.preferences.all,
            ),
        )
        zipStream.putNextEntry(ZipEntry(FILE_DATA))
        mapper.writeValue(zipStream, container)
        zipStream.closeEntry()
        return media
    }

    private fun writeExerciseMedia(
        zipStream: ZipOutputStream,
        media: List<ExerciseMediaSource>,
    ) {
        media.forEach { item ->
            zipStream.putNextEntry(ZipEntry(item.archivePath))
            try {
                item.source.inputStream().use { input -> input.copyTo(zipStream) }
            } finally {
                zipStream.closeEntry()
            }
        }
    }

    private fun writeWorkouts(zipStream: ZipOutputStream) {
        val dao = db.workoutDao()
        // Program occurrence summaries embed a local numeric day id in their UUID.
        // Completions are the source of truth and BackupReader rebuilds the summary.
        val allWorkouts = dao.getWorkouts()
        requireUniqueBackupUuids("workout", allWorkouts.map { it.uuid })
        val workouts = allWorkouts.filterNot {
            it.uuid.startsWith(PROGRAM_OCCURRENCE_WORKOUT_UUID_PREFIX)
        }
        workouts.forEachIndexed { index, workout ->
            zipStream.putNextEntry(ZipEntry(FILE_WORKOUT.format(index)))
            val data = WorkoutData.fromWorkout(dao, workout)
            mapper.writeValue(zipStream, data)
            zipStream.closeEntry()
            listener.onStatusChanged(100 * index / workouts.size, context.getString(R.string.workouts))
        }
    }

    private fun resolvePrivateExerciseMedia(relativePath: String): File? = runCatching {
        if (File(relativePath).isAbsolute) return@runCatching null
        val root = context.filesDir.canonicalFile
        val candidate = File(root, relativePath).canonicalFile
        candidate.takeIf { it.path.startsWith(root.path + File.separator) }
    }.getOrNull()

    private fun publishTemporaryFile(temporary: File, destination: File) {
        try {
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    fun interface ExportStatusListener {

        fun onStatusChanged(progress: Int, action: String?)

    }

}
