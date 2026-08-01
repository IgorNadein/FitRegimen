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
import android.net.Uri
import com.fasterxml.jackson.core.StreamReadFeature
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import de.tadris.fitness.Instance
import de.tadris.fitness.R
import de.tadris.fitness.data.AppDatabase
import de.tadris.fitness.data.ProgramCompletion
import de.tadris.fitness.data.WorkoutData
import de.tadris.fitness.data.backup.model.DataContainer
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Reads a backup from [input] and writes the result into the local DB.
 */
class BackupReader(
    private val context: Context,
    private val input: Uri,
    private val replace: Boolean,
    private val listener: BackupWriter.ExportStatusListener
) {

    private val database: AppDatabase = Instance.getInstance(context).db
    private val mapper = jsonMapper {
        addModule(kotlinModule())
        configure(StreamReadFeature.IGNORE_UNDEFINED, true)
        configure(StreamReadFeature.AUTO_CLOSE_SOURCE, false)
        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }

    @Throws(IOException::class, UnsupportedVersionException::class)
    fun read() {
        listener.onStatusChanged(0, context.getString(R.string.loadingFile))

        if (checkIfLegacyBackup()) {
            delegateToLegacyBackup()
            return
        }

        val version = readVersion()
        if (version > BackupWriter.VERSION) {
            throw UnsupportedVersionException("Version $version is unsupported!")
        }

        restoreDatabase()
        listener.onStatusChanged(100, context.getString(R.string.finished))
    }

    private fun delegateToLegacyBackup(){
        listener.onStatusChanged(0, context.getString(R.string.loadingFile))
        val legacyReader = LegacyBackupReader(
            context,
            input,
            replace,
            object : LegacyBackupReader.ImportStatusListener {
                override fun onStatusChanged(progress: Int, action: String?) {
                    listener.onStatusChanged(progress, action)
                }
            }
        )
        legacyReader.restoreData()
    }

    @Throws(IOException::class)
    private fun checkIfLegacyBackup(): Boolean {
        useZipInputStream { zipStream ->
            val firstEntry = zipStream.nextEntry
            if (firstEntry != null) {
                // Old backups have "data.xml" as the first (and only) entry
                return firstEntry.name == "data.xml"
            }
        }
        return false
    }

    @Throws(IOException::class)
    private fun readVersion(): Int {
        forEachZipEntry { entry, stream ->
            if (entry.name == BackupWriter.FILE_VERSION) {
                val versionString = stream.readBytes().toString(Charsets.UTF_8)
                stream.closeEntry()
                return versionString.toInt()
            }
        }
        throw IOException("Version file not found in backup")
    }

    @Throws(IOException::class)
    private fun openInputStream(): InputStream {
        return context.contentResolver.openInputStream(input)
            ?: throw IOException("Failed to open input stream for Uri: $input")
    }

    @Throws(IOException::class)
    private fun restoreDatabase() {
        database.runInTransaction {
            if (replace) {
                listener.onStatusChanged(10, context.getString(R.string.restorePreparing))
                database.clearAllDataTables()
            }

            var workoutCount = 0
            var processedWorkouts = 0
            var pendingProgramCompletions: List<PendingProgramCompletion> = emptyList()

            forEachZipEntry { entry, _ ->
                if (isWorkoutFile(entry.name)) {
                    workoutCount++
                }
            }

            // Second pass: restore data
            forEachZipEntry { entry, stream ->
                when {
                    entry.name == BackupWriter.FILE_DATA -> {
                        listener.onStatusChanged(20, context.getString(R.string.customWorkoutTypesTitle))
                        pendingProgramCompletions = restoreExtraData(stream)
                    }
                    isWorkoutFile(entry.name) -> {
                        val progress = 20 + (80 * processedWorkouts / workoutCount.coerceAtLeast(1))
                        listener.onStatusChanged(progress, context.getString(R.string.workouts))
                        restoreWorkout(stream)
                        processedWorkouts++
                    }
                }
            }

            // Workout ids referenced by completions only exist once every workout_%d.json
            // entry above has been processed, so this must run after that loop, not inside
            // restoreExtraData() (which sees data.json before any workout file).
            restorePendingProgramCompletions(pendingProgramCompletions)
        }
    }

    private data class PendingProgramCompletion(val newTaskId: Long, val original: ProgramCompletion)

    private fun restorePendingProgramCompletions(pending: List<PendingProgramCompletion>) {
        for (item in pending) {
            val workoutId = item.original.workoutId?.takeIf { database.workoutDao().findById(it) != null }
            database.trainingProgramDao().insertCompletion(
                item.original.copy(id = 0, programTaskId = item.newTaskId, workoutId = workoutId)
            )
        }
    }

    private inline fun forEachZipEntry(action: (ZipEntry, ZipInputStream) -> Unit){
        useZipInputStream { zipStream ->
            var entry = zipStream.nextEntry

            // First pass: count workout files
            while (entry != null) {
                action(entry, zipStream)
                zipStream.closeEntry()
                entry = zipStream.nextEntry
            }
        }
    }

    private inline fun useZipInputStream(action: (ZipInputStream) -> Unit){
        openInputStream().use { fileStream ->
            ZipInputStream(fileStream).use { zipStream ->
                action(zipStream)
            }
        }
    }

    private fun isWorkoutFile(name: String) = name.startsWith("workout_") && name.endsWith(".json")

    @Throws(IOException::class)
    private fun restoreExtraData(zipStream: ZipInputStream): List<PendingProgramCompletion> {
        val dataContainer = mapper.readValue(zipStream, DataContainer::class.java)

        // Restore interval sets
        for (container in dataContainer.intervalSets) {
            val set = container.set
            if (replace || database.intervalDao().getSet(set.id) == null) {
                database.intervalDao().insertIntervalSet(set)
            }

            for (interval in container.intervals) {
                if (replace || database.intervalDao().findById(interval.id) == null) {
                    database.intervalDao().insertInterval(interval)
                }
            }
        }

        // Restore workout types
        for (type in dataContainer.workoutTypes) {
            if (replace || database.workoutTypeDao().findById(type.id) == null) {
                database.workoutTypeDao().insert(type)
            }
        }

        // Restore training programs. Ids aren't preserved (autoGenerate PKs aren't
        // portable across devices/imports the way Workout's manually-assigned id is),
        // so tasks/completions are re-inserted with remapped foreign keys instead.
        val pendingCompletions = mutableListOf<PendingProgramCompletion>()
        val programDao = database.trainingProgramDao()
        for (programContainer in dataContainer.programs) {
            val program = programContainer.program
            if (!replace && programDao.getPrograms().any { it.uuid == program.uuid }) {
                continue // already restored by a previous merge of this same backup
            }
            for (type in programContainer.customWorkoutTypes) {
                if (replace || database.workoutTypeDao().findById(type.id) == null) {
                    database.workoutTypeDao().insert(type)
                }
            }
            val newProgramId = programDao.insertProgram(program.copy(id = 0))
            val taskIdMap = mutableMapOf<Long, Long>()
            for (task in programContainer.tasks) {
                val newTaskId = programDao.insertTask(task.copy(id = 0, programId = newProgramId))
                taskIdMap[task.id] = newTaskId
            }
            for (completion in programContainer.completions) {
                val newTaskId = taskIdMap[completion.programTaskId] ?: continue
                pendingCompletions.add(PendingProgramCompletion(newTaskId, completion))
            }
        }
        return pendingCompletions
    }

    @Throws(IOException::class)
    private fun restoreWorkout(zipStream: ZipInputStream) {
        val workoutData = mapper.readValue(zipStream, WorkoutData::class.java)

        // Only import unknown workouts on merge
        if (replace || database.workoutDao().findById(workoutData.workout.id) == null) {
            // Reset sample/pause ids to 0 so Room auto-generates them, avoiding conflicts
            // when merging backups from different devices that share sequential id ranges.
            val cleanedData = workoutData.copy(
                samples = workoutData.samples.map { it.copy(id = 0) },
                pauses = workoutData.pauses.map { it.copy(id = 0) },
                events = workoutData.events.map { it.copy(id = 0) },
                repetitionSamples = workoutData.repetitionSamples.map { it.copy(id = 0) },
                heartRateSamples = workoutData.heartRateSamples.map { it.copy(id = 0) },
            )
            database.workoutDao().insertWorkoutData(cleanedData)
        }
    }

    internal class UnsupportedVersionException(message: String?) : Exception(message)

}