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

package de.tadris.fitness.data.autoexport;

import android.content.Context;

import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

import de.tadris.fitness.Instance;
import de.tadris.fitness.data.ExportTargetConfiguration;
import de.tadris.fitness.data.Workout;
import de.tadris.fitness.data.autoexport.source.ExportSource;

public class AutoExportPlanner {

    private final Context context;

    public AutoExportPlanner(Context context) {
        this.context = context;
    }

    public void planAutoBackup() {
        for (ExportTargetConfiguration configuration : getConfigurations(ExportSource.EXPORT_SOURCE_BACKUP)) {
            planAutoBackupFor(configuration);
            planOneTimeBackupFor(configuration);
        }
    }

    /**
     * Refreshes only the periodic WorkManager schedule after portable preferences
     * were restored. Unlike {@link #planAutoBackup()}, this deliberately does not
     * send an immediate backup to a previously configured local target.
     */
    public void refreshAutoBackupSchedule() {
        for (ExportTargetConfiguration configuration : getConfigurations(ExportSource.EXPORT_SOURCE_BACKUP)) {
            planAutoBackupFor(configuration);
        }
    }

    public void planAutoBackupFor(ExportTargetConfiguration configuration) {
        Data data = new Data.Builder()
                .putString(AutoExporter.DATA_SOURCE_TYPE, ExportSource.EXPORT_SOURCE_BACKUP)
                .putLong(AutoExporter.DATA_TARGET_CONFIG_ID, configuration.id)
                .build();
        int hourInterval = Instance.getInstance(context).userPreferences.getAutoBackupIntervalHours();
        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(AutoExporter.class,
                hourInterval, TimeUnit.HOURS,
                1, TimeUnit.HOURS)
                .setConstraints(configuration.getTargetImplementation().getConstraints())
                .setInputData(data)
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(String.valueOf(configuration.id), ExistingPeriodicWorkPolicy.UPDATE, workRequest);
    }

    private void planOneTimeBackupFor(ExportTargetConfiguration configuration) {
        Data data = new Data.Builder()
                .putString(AutoExporter.DATA_SOURCE_TYPE, ExportSource.EXPORT_SOURCE_BACKUP)
                .putLong(AutoExporter.DATA_TARGET_CONFIG_ID, configuration.id)
                .build();
        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(AutoExporter.class)
                .setConstraints(configuration.getTargetImplementation().getConstraints())
                .setInputData(data)
                .build();
        WorkManager.getInstance(context).enqueue(workRequest);
    }

    public void onWorkoutRecorded(Workout workout) {
        for (ExportTargetConfiguration configuration : getConfigurations(ExportSource.EXPORT_SOURCE_WORKOUT_GPX)) {
            planWorkoutExportFor(workout, configuration);
        }
    }

    private void planWorkoutExportFor(Workout workout, ExportTargetConfiguration configuration) {
        Data data = new Data.Builder()
                .putString(AutoExporter.DATA_SOURCE_TYPE, ExportSource.EXPORT_SOURCE_WORKOUT_GPX)
                .putString(AutoExporter.DATA_SOURCE_DATA, String.valueOf(workout.getId()))
                .putLong(AutoExporter.DATA_TARGET_CONFIG_ID, configuration.id)
                .build();
        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(AutoExporter.class)
                .setConstraints(configuration.getTargetImplementation().getConstraints())
                .setInputData(data)
                .build();
        WorkManager.getInstance(context).enqueue(workRequest);
    }

    private ExportTargetConfiguration[] getConfigurations(String source) {
        return Instance.getInstance(context).db.exportTargetDao().findAllFor(source);
    }

}
