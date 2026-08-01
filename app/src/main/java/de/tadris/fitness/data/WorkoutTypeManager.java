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

package de.tadris.fitness.data;

import android.content.Context;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import de.tadris.fitness.Instance;
import de.tadris.fitness.R;
import de.tadris.fitness.util.Icon;

public class WorkoutTypeManager {


    public static final String WORKOUT_TYPE_ID_OTHER = "other";
    public static final String WORKOUT_TYPE_ID_PROGRAM_TRAINING = "program_training";
    public static final String WORKOUT_TYPE_ID_RUNNING = "running";
    public static final String WORKOUT_TYPE_ID_WALKING = "walking";
    public static final String WORKOUT_TYPE_ID_HIKING = "hiking";
    public static final String WORKOUT_TYPE_ID_CYCLING = "cycling";
    public static final String WORKOUT_TYPE_ID_INLINE_SKATING = "inline_skating";
    public static final String WORKOUT_TYPE_ID_SKATEBOARDING = "skateboarding";
    public static final String WORKOUT_TYPE_ID_ROWING = "rowing";
    public static final String WORKOUT_TYPE_ID_SWIMMING = "swimming";
    public static final String WORKOUT_TYPE_ID_TREADMILL = "treadmill";
    public static final String WORKOUT_TYPE_ID_ROPE_SKIPPING = "rope_skipping";
    public static final String WORKOUT_TYPE_ID_TRAMPOLINE_JUMPING = "trampoline_jumping";
    public static final String WORKOUT_TYPE_ID_PUSH_UPS = "push-ups";
    public static final String WORKOUT_TYPE_ID_PULL_UPS = "pull-ups";

    private final List<WorkoutType> allWorkoutTypes = new ArrayList<>();

    private static WorkoutTypeManager instance;

    private WorkoutTypeManager() {
    }

    public static WorkoutTypeManager getInstance() {
        if (instance == null)
            instance = new WorkoutTypeManager();
        return instance;
    }

    public synchronized WorkoutType getWorkoutTypeById(Context context, String id) {
        buildPresets(context);

        WorkoutType retType = null;
        for (WorkoutType type : allWorkoutTypes) {
            if (type.id.equals(id)) {
                retType = type;
            }
        }

        if (retType == null) {
            List<WorkoutType> newTypes = checkForNewTypes(context);

            for (WorkoutType type : newTypes) {
                if (type.id.equals(id)) {
                    retType = type;
                }
            }
        }

        if (retType == null && !WORKOUT_TYPE_ID_OTHER.equals(id)) {
            retType = getWorkoutTypeById(context, WORKOUT_TYPE_ID_OTHER); // Default to 'Other' type
        }

        return retType;
    }

    public synchronized List<WorkoutType> getAllTypesSorted(Context context) {
        List<WorkoutType> list = getAllTypes(context);
        AppDatabase db = Instance.getInstance(context).db;
        Collections.sort(list, (o1, o2) -> -Long.compare(db.getLastWorkoutTimeByType(o1.id), db.getLastWorkoutTimeByType(o2.id)));

        return list;
    }

    public synchronized List<WorkoutType> getAllTypes(Context context) {
        buildPresets(context);
        checkForNewTypes(context);
        return new ArrayList<>(allWorkoutTypes); // Return clone to avoid tampering from outside...
    }

    /** Rebuilds built-ins and custom rows after backup replacement/merge. */
    public synchronized void reloadCustomTypes(Context context) {
        allWorkoutTypes.clear();
        buildPresets(context);
        checkForNewTypes(context);
    }

    private List<WorkoutType> checkForNewTypes(Context context) {
        WorkoutType[] fromDatabase = Instance.getInstance(context).db.workoutTypeDao().findAll();
        List<String> existingIDs = new ArrayList<>();
        for (WorkoutType existingType : allWorkoutTypes) {
            existingIDs.add(existingType.id);
        }

        List<WorkoutType> newTypes = new ArrayList<>();
        for (WorkoutType typeFromDb : fromDatabase) {
            if (!existingIDs.contains(typeFromDb.id)) {
                newTypes.add(typeFromDb);
            }
        }
        allWorkoutTypes.addAll(newTypes);
        return newTypes;
    }

    private void buildPresets(Context context) {
        if (!allWorkoutTypes.isEmpty()) return; // Don't build a second time
        allWorkoutTypes.addAll(Arrays.asList(new WorkoutType(WORKOUT_TYPE_ID_RUNNING,
                context.getString(R.string.workoutTypeRunning),
                5,
                context.getResources().getColor(R.color.colorPrimaryRunning),
                Icon.RUNNING.name,
                -1, RecordingType.GPS.id),
                new WorkoutType(WORKOUT_TYPE_ID_WALKING,
                        context.getString(R.string.workoutTypeWalking),
                        5,
                        context.getResources().getColor(R.color.colorPrimaryRunning),
                        Icon.WALKING.name,
                        -1, RecordingType.GPS.id),
                new WorkoutType(WORKOUT_TYPE_ID_HIKING,
                        context.getString(R.string.workoutTypeHiking),
                        5,
                        context.getResources().getColor(R.color.colorPrimaryHiking),
                        Icon.HIKING.name,
                        -1, RecordingType.GPS.id),
                new WorkoutType(WORKOUT_TYPE_ID_CYCLING,
                        context.getString(R.string.workoutTypeCycling),
                        10,
                        context.getResources().getColor(R.color.colorPrimaryBicycling),
                        Icon.CYCLING.name,
                        -1, RecordingType.GPS.id),
                new WorkoutType(WORKOUT_TYPE_ID_INLINE_SKATING,
                        context.getString(R.string.workoutTypeInlineSkating),
                        7,
                        context.getResources().getColor(R.color.colorPrimarySkating),
                        Icon.INLINE_SKATING.name,
                        -1, RecordingType.GPS.id),
                new WorkoutType(WORKOUT_TYPE_ID_SKATEBOARDING,
                        context.getString(R.string.workoutTypeSkateboarding),
                        7,
                        context.getResources().getColor(R.color.colorPrimarySkating),
                        Icon.SKATEBOARDING.name,
                        -1, RecordingType.GPS.id),
                new WorkoutType(WORKOUT_TYPE_ID_ROWING,
                        context.getString(R.string.workoutTypeRowing),
                        7,
                        context.getResources().getColor(R.color.colorPrimaryWaterSports),
                        Icon.ROWING.name,
                        -1, RecordingType.GPS.id),
                new WorkoutType(WORKOUT_TYPE_ID_SWIMMING,
                        context.getString(R.string.workoutTypeSwimming),
                        4,
                        context.getResources().getColor(R.color.colorPrimaryWaterSports),
                        Icon.POOL.name,
                        8, RecordingType.GPS.id),
                new WorkoutType(WORKOUT_TYPE_ID_OTHER,
                        context.getString(R.string.workoutTypeOther),
                        7,
                        context.getResources().getColor(R.color.colorPrimary),
                        Icon.OTHER.name,
                        0, RecordingType.GPS.id),
                new WorkoutType(WORKOUT_TYPE_ID_PROGRAM_TRAINING,
                        context.getString(R.string.workoutTypeProgramTraining),
                        1,
                        context.getResources().getColor(R.color.colorPrimary),
                        Icon.ACTIVITY.name,
                        5, RecordingType.INDOOR.id,
                        R.plurals.workoutRepetition),
                new WorkoutType(WORKOUT_TYPE_ID_TREADMILL,
                        context.getString(R.string.workoutTypeTreadmill),
                        5,
                        context.getResources().getColor(R.color.colorPrimaryRunning),
                        Icon.RUNNING.name,
                        -1, RecordingType.INDOOR.id,
                        R.plurals.workoutStep),
                new WorkoutType(WORKOUT_TYPE_ID_ROPE_SKIPPING,
                        context.getString(R.string.workoutTypeRopeSkipping),
                        3,
                        context.getResources().getColor(R.color.colorPrimary),
                        Icon.ROPE_SKIPPING.name,
                        11, RecordingType.INDOOR.id,
                        R.plurals.workoutJump),
                new WorkoutType(WORKOUT_TYPE_ID_TRAMPOLINE_JUMPING,
                        context.getString(R.string.workoutTypeTrampolineJumping),
                        3,
                        context.getResources().getColor(R.color.colorPrimary),
                        Icon.TRAMPOLINE_JUMPING.name,
                        4, RecordingType.INDOOR.id,
                        R.plurals.workoutJump),
                new WorkoutType(WORKOUT_TYPE_ID_PUSH_UPS,
                        context.getString(R.string.workoutTypePushUps),
                        1,
                        context.getResources().getColor(R.color.colorPrimary),
                        Icon.PUSH_UPS.name,
                        6, RecordingType.INDOOR.id,
                        R.plurals.workoutPushUp),
                new WorkoutType(WORKOUT_TYPE_ID_PULL_UPS,
                        context.getString(R.string.workoutTypePullUps),
                        1,
                        context.getResources().getColor(R.color.colorPrimary),
                        Icon.PULL_UPS.name,
                        6, RecordingType.INDOOR.id,
                        R.plurals.workoutPullUp)));
    }

}
