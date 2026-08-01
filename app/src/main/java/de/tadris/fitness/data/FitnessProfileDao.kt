/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack.
 * This file was added in FitRegimen, a fork of FitoTrack.
 */
package de.tadris.fitness.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FitnessProfileDao {

    @Query(
        "SELECT * FROM fitness_profile " +
            "ORDER BY isActive DESC, name COLLATE NOCASE ASC, createdAt ASC"
    )
    fun observeAll(): Flow<List<FitnessProfile>>

    @Query(
        "SELECT * FROM fitness_profile WHERE isActive = 1 " +
            "ORDER BY updatedAt DESC, id ASC LIMIT 1"
    )
    fun observeActive(): Flow<FitnessProfile?>

    @Query(
        "SELECT * FROM fitness_profile " +
            "ORDER BY isActive DESC, name COLLATE NOCASE ASC, createdAt ASC"
    )
    fun getAll(): List<FitnessProfile>

    @Query(
        "SELECT * FROM fitness_profile WHERE isActive = 1 " +
            "ORDER BY updatedAt DESC, id ASC LIMIT 1"
    )
    fun getActive(): FitnessProfile?

    @Query("SELECT * FROM fitness_profile WHERE id = :id LIMIT 1")
    fun findById(id: Long): FitnessProfile?

    @Query("SELECT * FROM fitness_profile WHERE uuid = :uuid LIMIT 1")
    fun findByUuid(uuid: String): FitnessProfile?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(profile: FitnessProfile): Long

    @Update
    fun update(profile: FitnessProfile): Int

    @Query("DELETE FROM fitness_profile WHERE id = :id")
    fun deleteById(id: Long): Int

    @Query("UPDATE fitness_profile SET isActive = 0 WHERE isActive = 1")
    fun clearActive()

    @Query(
        "UPDATE fitness_profile SET isActive = 1, updatedAt = :updatedAt " +
            "WHERE id = :id"
    )
    fun markActive(id: Long, updatedAt: Long): Int

    @Query("SELECT * FROM fitness_profile ORDER BY createdAt ASC, id ASC LIMIT 1")
    fun getOldest(): FitnessProfile?

    @Query("SELECT COUNT(*) FROM fitness_profile WHERE isActive = 1")
    fun countActive(): Int

    /** Enforces exactly one active row through a single database transaction. */
    @Transaction
    fun activate(id: Long, updatedAt: Long): FitnessProfile? {
        if (findById(id) == null) return null
        clearActive()
        if (markActive(id, updatedAt) != 1) return null
        return findById(id)
    }

    /** Removes a row and selects a deterministic fallback if it was active. */
    @Transaction
    fun deleteAndSelectFallback(id: Long, updatedAt: Long): FitnessProfile? {
        if (deleteById(id) == 0) return getActive()
        getActive()?.let { return it }
        val fallback = getOldest() ?: return null
        return activate(fallback.id, updatedAt)
    }
}
