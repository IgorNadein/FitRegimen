/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 */
package de.tadris.fitness.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TrainingSessionDao {

    @Query("SELECT * FROM training_session WHERE id = :id")
    fun findSessionById(id: Long): TrainingSession?

    @Query("SELECT * FROM training_session WHERE uuid = :uuid LIMIT 1")
    fun findSessionByUuid(uuid: String): TrainingSession?

    @Query("SELECT * FROM training_session WHERE id = :id")
    fun observeSession(id: Long): Flow<TrainingSession?>

    @Query(
        "SELECT * FROM training_session " +
            "WHERE status IN ('ACTIVE', 'PAUSED') ORDER BY updatedAt DESC"
    )
    fun getRunningSessions(): List<TrainingSession>

    @Query("SELECT * FROM training_session ORDER BY startedAt DESC")
    fun getSessions(): List<TrainingSession>

    @Query(
        "SELECT * FROM training_session WHERE sourceDayId = :sourceDayId " +
            "AND occurrenceDate = :occurrenceDate AND status IN ('ACTIVE', 'PAUSED') " +
            "ORDER BY updatedAt DESC LIMIT 1"
    )
    fun findRunningSessionForDay(sourceDayId: Long, occurrenceDate: Long): TrainingSession?

    @Query(
        "SELECT * FROM training_session WHERE sourceDayId = :sourceDayId " +
            "AND occurrenceDate = :occurrenceDate AND status IN ('ACTIVE', 'PAUSED') " +
            "ORDER BY updatedAt DESC LIMIT 1"
    )
    fun observeRunningSessionForDay(
        sourceDayId: Long,
        occurrenceDate: Long,
    ): Flow<TrainingSession?>

    @Query(
        "SELECT * FROM training_session WHERE sourceDayId = :sourceDayId " +
            "AND occurrenceDate = :occurrenceDate ORDER BY startedAt DESC"
    )
    fun getSessionsForDayOccurrence(sourceDayId: Long, occurrenceDate: Long): List<TrainingSession>

    @Insert
    fun insertSession(session: TrainingSession): Long

    @Update
    fun updateSession(session: TrainingSession)

    @Query("SELECT * FROM session_exercise WHERE sessionId = :sessionId ORDER BY orderIndex ASC")
    fun getExercisesOfSession(sessionId: Long): List<SessionExercise>

    @Query("SELECT * FROM session_exercise WHERE sessionId = :sessionId ORDER BY orderIndex ASC")
    fun observeExercisesOfSession(sessionId: Long): Flow<List<SessionExercise>>

    @Query(
        "SELECT * FROM session_exercise " +
            "WHERE sessionId = :sessionId AND orderIndex = :orderIndex LIMIT 1"
    )
    fun findExerciseAt(sessionId: Long, orderIndex: Int): SessionExercise?

    @Query("SELECT * FROM session_exercise WHERE id = :id")
    fun findSessionExerciseById(id: Long): SessionExercise?

    @Insert
    fun insertSessionExercise(exercise: SessionExercise): Long

    @Update
    fun updateSessionExercise(exercise: SessionExercise)

    @Query(
        "SELECT * FROM session_set " +
            "WHERE sessionExerciseId = :sessionExerciseId ORDER BY orderIndex ASC"
    )
    fun getSetsOfExercise(sessionExerciseId: Long): List<SessionSet>

    @Query(
        "SELECT * FROM session_set " +
            "WHERE sessionExerciseId = :sessionExerciseId ORDER BY orderIndex ASC"
    )
    fun observeSetsOfExercise(sessionExerciseId: Long): Flow<List<SessionSet>>

    @Query(
        "SELECT * FROM session_set " +
            "WHERE sessionExerciseId = :sessionExerciseId AND orderIndex = :orderIndex LIMIT 1"
    )
    fun findSetAt(sessionExerciseId: Long, orderIndex: Int): SessionSet?

    @Query("SELECT * FROM session_set WHERE id = :id")
    fun findSessionSetById(id: Long): SessionSet?

    @Insert
    fun insertSessionSet(set: SessionSet): Long

    @Update
    fun updateSessionSet(set: SessionSet)
}
