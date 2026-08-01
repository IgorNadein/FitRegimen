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

package de.tadris.fitness;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;

import de.tadris.fitness.data.AppDatabase;
import de.tadris.fitness.data.FitnessProfileRepository;
import de.tadris.fitness.data.RecordingType;
import de.tadris.fitness.data.Workout;
import de.tadris.fitness.data.WorkoutType;
import de.tadris.fitness.data.WorkoutTypeManager;
import de.tadris.fitness.data.autoexport.AutoExportPlanner;
import de.tadris.fitness.data.preferences.UserPreferences;
import de.tadris.fitness.recording.RecorderService;
import de.tadris.fitness.util.DataManager;
import de.tadris.fitness.util.FitoTrackThemes;
import de.tadris.fitness.util.PermissionUtils;
import de.tadris.fitness.util.ShortcutsUtils;
import de.tadris.fitness.util.UserDateTimeUtils;
import de.tadris.fitness.util.WorkoutLogger;
import de.tadris.fitness.util.unit.DistanceUnitUtils;
import de.tadris.fitness.util.unit.EnergyUnitUtils;

public class Instance {

    private static final String TAG = "Instance";

    private static volatile Instance instance;

    /**
     * Please use getInstance with context. Without context could return null if no Instance was created
     */
    @Nullable
    @Deprecated
    public static Instance getInstance() {
        return getInstance(null);
    }

    public static Instance getInstance(Context context) {
        if (context == null) {
            Log.v(TAG, "no Context Provided");
        }
        Instance current = instance;
        if (current == null && context != null) {
            synchronized (Instance.class) {
                current = instance;
                if (current == null) {
                    current = new Instance(context.getApplicationContext());
                    instance = current;
                }
            }
        }
        return current;
    }

    public final AppDatabase db;
    public final FitnessProfileRepository fitnessProfileRepository;
    public final UserPreferences userPreferences;
    public final FitoTrackThemes themes;
    public final UserDateTimeUtils userDateTimeUtils;
    public final DistanceUnitUtils distanceUnitUtils;
    public final EnergyUnitUtils energyUnitUtils;
    public final AutoExportPlanner planner;
    public final WorkoutLogger logger;
    public final ShortcutsUtils shortcuts;

    private Instance(Context context) {
        userPreferences = new UserPreferences(context);
        themes = new FitoTrackThemes(context);
        userDateTimeUtils = new UserDateTimeUtils(userPreferences);
        distanceUnitUtils = new DistanceUnitUtils(context, userPreferences);
        energyUnitUtils = new EnergyUnitUtils(context, userPreferences);
        db = AppDatabase.provideDatabase(context);
        fitnessProfileRepository = new FitnessProfileRepository(
                db,
                userPreferences,
                context.getString(R.string.fitness_profile_default_name)
        );
        fitnessProfileRepository.ensureDefaultProfile();
        planner = new AutoExportPlanner(context);
        logger = new WorkoutLogger(context);
        shortcuts = new ShortcutsUtils(context);

        startBackgroundClean(context);
        checkUnfinishedRecording(context);
    }

    private void startBackgroundClean(Context context) {
        DataManager.cleanFilesASync(context);
    }

    // Checks if a workout was recorded before app exit and resume it in that case
    private void checkUnfinishedRecording(Context context) {
        Workout workout = db.workoutDao().findRunningWorkout();
        WorkoutLogger.log(TAG, "Checking for unfinished workout. Result=" + (workout != null));
        if (workout == null) return;

        if (workout.getStart() == -1) {
            // Workout was never started (app was killed while on idle recording screen)
            db.workoutDao().deleteWorkout(workout);
            WorkoutLogger.log(TAG, "Deleted unstarted workout");
            return;
        }

        WorkoutType type = WorkoutTypeManager.getInstance().getWorkoutTypeById(context, workout.getWorkoutTypeId());
        boolean isGps = RecordingType.findById(type.recordingType) == RecordingType.GPS;
        boolean hasPermission;
        if (isGps) {
            hasPermission = PermissionUtils.checkPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    || PermissionUtils.checkPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION);
        } else {
            hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                    || PermissionUtils.checkPermission(context, Manifest.permission.ACTIVITY_RECOGNITION);
        }
        if (hasPermission) {
            context.startForegroundService(new Intent(context, RecorderService.class));
        }
    }

}
