/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 * This file was added in FitRegimen, a fork of FitoTrack.
 */
package de.tadris.fitness.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NutritionPlanDao {

    @Query("SELECT * FROM nutrition_plan ORDER BY updatedAt DESC, id DESC")
    fun getPlansFlow(): Flow<List<NutritionPlan>>

    @Query("SELECT * FROM nutrition_plan ORDER BY updatedAt DESC, id DESC")
    fun getPlans(): List<NutritionPlan>

    @Query("SELECT * FROM nutrition_plan WHERE id = :id LIMIT 1")
    fun findPlanById(id: Long): NutritionPlan?

    @Query("SELECT * FROM nutrition_plan WHERE uuid = :uuid LIMIT 1")
    fun findPlanByUuid(uuid: String): NutritionPlan?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertPlan(plan: NutritionPlan): Long

    /** Prevents a full-backup snapshot from observing a newly created zero-day draft. */
    @Transaction
    fun insertPlanWithFirstDay(plan: NutritionPlan): Long {
        val planId = insertPlan(plan)
        insertDay(NutritionDay(planId = planId, orderIndex = 0))
        return planId
    }

    @Update
    fun updatePlan(plan: NutritionPlan)

    @Query(
        "UPDATE nutrition_plan " +
            "SET updatedAt = MAX(createdAt, updatedAt, :now) " +
            "WHERE id = :planId"
    )
    fun touchPlanAt(planId: Long, now: Long)

    /** Monotonic even after importing a backup whose timestamps are in the future. */
    @Transaction
    fun touchPlan(planId: Long) {
        touchPlanAt(planId, System.currentTimeMillis())
    }

    @Delete
    fun deletePlan(plan: NutritionPlan)

    @Query("SELECT * FROM nutrition_day WHERE planId = :planId ORDER BY orderIndex ASC, id ASC")
    fun getDaysOfPlan(planId: Long): List<NutritionDay>

    @Query("SELECT * FROM nutrition_day WHERE planId = :planId ORDER BY orderIndex ASC, id ASC")
    fun getDaysOfPlanFlow(planId: Long): Flow<List<NutritionDay>>

    @Query("SELECT * FROM nutrition_day WHERE id = :id LIMIT 1")
    fun findDayById(id: Long): NutritionDay?

    @Insert
    fun insertDay(day: NutritionDay): Long

    @Update
    fun updateDay(day: NutritionDay)

    @Update
    fun rawUpdateDays(days: List<NutritionDay>)

    @Query("UPDATE nutrition_day SET orderIndex = -id WHERE planId = :planId")
    fun moveDayOrderIndicesToTemporaryRange(planId: Long)

    /** Avoids UNIQUE(planId, orderIndex) collisions while swapping two rows. */
    @Transaction
    fun reorderDays(planId: Long, orderedDays: List<NutritionDay>) {
        requireCompleteReorder(
            expectedIds = getDaysOfPlan(planId).map(NutritionDay::id),
            rows = orderedDays.map { it.id to it.planId },
            parentId = planId,
            label = "nutrition days",
        )
        moveDayOrderIndicesToTemporaryRange(planId)
        rawUpdateDays(orderedDays.mapIndexed { index, day -> day.copy(orderIndex = index) })
    }

    @Delete
    fun deleteDay(day: NutritionDay)

    @Query("SELECT * FROM nutrition_meal WHERE dayId = :dayId ORDER BY orderIndex ASC, id ASC")
    fun getMealsOfDay(dayId: Long): List<NutritionMeal>

    @Query("SELECT * FROM nutrition_meal WHERE dayId = :dayId ORDER BY orderIndex ASC, id ASC")
    fun getMealsOfDayFlow(dayId: Long): Flow<List<NutritionMeal>>

    @Query("SELECT * FROM nutrition_meal WHERE id = :id LIMIT 1")
    fun findMealById(id: Long): NutritionMeal?

    @Insert
    fun insertMeal(meal: NutritionMeal): Long

    @Update
    fun updateMeal(meal: NutritionMeal)

    @Update
    fun rawUpdateMeals(meals: List<NutritionMeal>)

    @Query("UPDATE nutrition_meal SET orderIndex = -id WHERE dayId = :dayId")
    fun moveMealOrderIndicesToTemporaryRange(dayId: Long)

    /** Avoids UNIQUE(dayId, orderIndex) collisions while swapping two rows. */
    @Transaction
    fun reorderMeals(dayId: Long, orderedMeals: List<NutritionMeal>) {
        requireCompleteReorder(
            expectedIds = getMealsOfDay(dayId).map(NutritionMeal::id),
            rows = orderedMeals.map { it.id to it.dayId },
            parentId = dayId,
            label = "nutrition meals",
        )
        moveMealOrderIndicesToTemporaryRange(dayId)
        rawUpdateMeals(orderedMeals.mapIndexed { index, meal -> meal.copy(orderIndex = index) })
    }

    @Delete
    fun deleteMeal(meal: NutritionMeal)

    @Query("SELECT * FROM nutrition_item WHERE mealId = :mealId ORDER BY orderIndex ASC, id ASC")
    fun getItemsOfMeal(mealId: Long): List<NutritionItem>

    @Query("SELECT * FROM nutrition_item WHERE mealId = :mealId ORDER BY orderIndex ASC, id ASC")
    fun getItemsOfMealFlow(mealId: Long): Flow<List<NutritionItem>>

    @Query("SELECT * FROM nutrition_item WHERE id = :id LIMIT 1")
    fun findItemById(id: Long): NutritionItem?

    @Insert
    fun insertItem(item: NutritionItem): Long

    @Update
    fun updateItem(item: NutritionItem)

    @Update
    fun rawUpdateItems(items: List<NutritionItem>)

    @Query("UPDATE nutrition_item SET orderIndex = -id WHERE mealId = :mealId")
    fun moveItemOrderIndicesToTemporaryRange(mealId: Long)

    /** Avoids UNIQUE(mealId, orderIndex) collisions while swapping two rows. */
    @Transaction
    fun reorderItems(mealId: Long, orderedItems: List<NutritionItem>) {
        requireCompleteReorder(
            expectedIds = getItemsOfMeal(mealId).map(NutritionItem::id),
            rows = orderedItems.map { it.id to it.mealId },
            parentId = mealId,
            label = "nutrition items",
        )
        moveItemOrderIndicesToTemporaryRange(mealId)
        rawUpdateItems(orderedItems.mapIndexed { index, item -> item.copy(orderIndex = index) })
    }

    @Delete
    fun deleteItem(item: NutritionItem)

    @Query("SELECT profileUuid FROM nutrition_plan_profile WHERE planId = :planId ORDER BY profileUuid")
    fun getProfileUuids(planId: Long): List<String>

    @Query("DELETE FROM nutrition_plan_profile WHERE planId = :planId")
    fun deleteProfileUuids(planId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertPlanProfiles(profiles: List<NutritionPlanProfile>)

    @Transaction
    fun replaceProfileUuids(planId: Long, profileUuids: Collection<String>) {
        deleteProfileUuids(planId)
        val normalized = profileUuids.asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .map { NutritionPlanProfile(planId, it) }
            .toList()
        if (normalized.isNotEmpty()) insertPlanProfiles(normalized)
    }

    private fun requireCompleteReorder(
        expectedIds: List<Long>,
        rows: List<Pair<Long, Long>>,
        parentId: Long,
        label: String,
    ) {
        require(rows.all { (id, rowParentId) -> id > 0L && rowParentId == parentId }) {
            "Every $label row must be persisted and belong to parent $parentId"
        }
        require(rows.map { it.first }.toSet() == expectedIds.toSet() &&
            rows.size == expectedIds.size
        ) {
            "A reorder must contain every $label row exactly once"
        }
    }
}
