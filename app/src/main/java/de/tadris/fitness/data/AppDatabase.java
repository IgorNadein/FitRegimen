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

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(version = 29, entities = {
        Workout.class,
        RouteSample.class,
        WorkoutPause.class,
        RepetitionSample.class,
        HeartRateSample.class,
        WorkoutEvent.class,
        Interval.class,
        IntervalSet.class,
        WorkoutType.class,
        ExportTargetConfiguration.class,
        TrainingProgram.class,
        Exercise.class,
        ProgramDay.class,
        ProgramExercise.class,
        ProgramSet.class,
        ProgramCompletion.class,
        ProgramOccurrenceSummary.class,
        SetLog.class,
        TrainingSession.class,
        SessionExercise.class,
        SessionSet.class,
        FitnessProfile.class,
        NutritionPlan.class,
        NutritionDay.class,
        NutritionMeal.class,
        NutritionItem.class,
        NutritionPlanProfile.class,
}, exportSchema = false)
@TypeConverters({
        MetricKindConverter.class,
        SetTypeConverter.class,
        MeasurementSourceConverter.class,
        CompletionRuleConverter.class,
        TrainingSessionStatusConverter.class,
        TrainingSessionPhaseConverter.class,
        SessionExerciseStatusConverter.class,
        SessionSetStatusConverter.class,
})
public abstract class AppDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "fitregimen";

    public abstract WorkoutDao workoutDao();

    public abstract WorkoutTypeDao workoutTypeDao();

    public abstract IntervalDao intervalDao();

    public abstract ExportTargetDao exportTargetDao();

    public abstract TrainingProgramDao trainingProgramDao();

    public abstract ExerciseDao exerciseDao();

    public abstract TrainingSessionDao trainingSessionDao();

    public abstract FitnessProfileDao fitnessProfileDao();

    public abstract NutritionPlanDao nutritionPlanDao();

    public long getLastWorkoutTimeByType(String type) {
        Workout workout = workoutDao().getLastWorkoutByType(type);
        if (workout != null) {
            return workout.getStart();
        } else {
            return 0;
        }
    }

    public void clearAllDataTables() {
        runInTransaction(() -> {
            SupportSQLiteDatabase db = getOpenHelper().getWritableDatabase();
            db.execSQL("DELETE FROM heart_rate_sample");
            db.execSQL("DELETE FROM workout_event");
            db.execSQL("DELETE FROM repetition_sample");
            db.execSQL("DELETE FROM route_sample");
            db.execSQL("DELETE FROM workout_pause");
            db.execSQL("DELETE FROM interval");
            db.execSQL("DELETE FROM interval_set");
            db.execSQL("DELETE FROM workout_type");
            db.execSQL("DELETE FROM nutrition_item");
            db.execSQL("DELETE FROM nutrition_meal");
            db.execSQL("DELETE FROM nutrition_day");
            db.execSQL("DELETE FROM nutrition_plan_profile");
            db.execSQL("DELETE FROM nutrition_plan");
            db.execSQL("DELETE FROM session_set");
            db.execSQL("DELETE FROM session_exercise");
            db.execSQL("DELETE FROM training_session");
            db.execSQL("DELETE FROM set_log");
            db.execSQL("DELETE FROM program_occurrence_summary");
            db.execSQL("DELETE FROM program_completion");
            db.execSQL("DELETE FROM program_set");
            db.execSQL("DELETE FROM program_exercise");
            db.execSQL("DELETE FROM program_day");
            db.execSQL("DELETE FROM training_program");
            db.execSQL("DELETE FROM exercise");
            db.execSQL("DELETE FROM fitness_profile");
            db.execSQL("DELETE FROM workout");
        });
    }

    /**
     * Converts the v20 flat program_task model into the v21 day/exercise/set tree.
     * Exercise ids deliberately reuse task ids, making completion remapping exact and
     * avoiding a lossy Java-side id map inside the Room migration.
     */
    private static void migratePrograms20To21(@NonNull SupportSQLiteDatabase db) {
        db.execSQL("ALTER TABLE program_completion RENAME TO program_completion_v20");
        db.execSQL("ALTER TABLE program_task RENAME TO program_task_v20");
        db.execSQL("ALTER TABLE training_program RENAME TO training_program_v20");
        // SQLite keeps index names when a table is renamed; release names reused by v21.
        db.execSQL("DROP INDEX IF EXISTS index_program_completion_workoutId");
        db.execSQL("DROP INDEX IF EXISTS index_program_completion_programTaskId");
        db.execSQL("DROP INDEX IF EXISTS index_program_task_programId");

        db.execSQL("CREATE TABLE training_program (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                "uuid TEXT NOT NULL," +
                "name TEXT NOT NULL," +
                "description TEXT," +
                "startDate INTEGER," +
                "createdAt INTEGER NOT NULL)");
        db.execSQL("INSERT INTO training_program (id, uuid, name, description, startDate, createdAt) " +
                "SELECT id, uuid, name, description, startDate, createdAt FROM training_program_v20");

        db.execSQL("CREATE TABLE exercise (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                "uuid TEXT NOT NULL," +
                "name TEXT NOT NULL," +
                "metricKind TEXT NOT NULL," +
                "workoutTypeId TEXT," +
                "description TEXT," +
                "tips TEXT," +
                "videoUrl TEXT," +
                "imagePath TEXT," +
                "hidden INTEGER NOT NULL)");
        db.execSQL("INSERT INTO exercise " +
                "(id, uuid, name, metricKind, workoutTypeId, description, tips, videoUrl, imagePath, hidden) " +
                "SELECT task.id, 'legacy-program-task-' || task.id, " +
                "COALESCE((SELECT title FROM workout_type WHERE id = task.workoutTypeId), task.workoutTypeId), " +
                "CASE WHEN task.targetDistanceMeters IS NOT NULL THEN 'DISTANCE_DURATION' " +
                "WHEN task.targetDurationMillis IS NOT NULL AND task.targetReps IS NULL THEN 'DURATION' " +
                "ELSE 'REPS' END, " +
                "task.workoutTypeId, NULL, NULL, NULL, NULL, 0 FROM program_task_v20 task");

        db.execSQL("CREATE TABLE program_day (" +
                "programId INTEGER NOT NULL," +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                "orderIndex INTEGER NOT NULL," +
                "name TEXT," +
                "isRest INTEGER NOT NULL," +
                "FOREIGN KEY (programId) REFERENCES training_program(id) ON DELETE CASCADE)");
        db.execSQL("CREATE INDEX index_program_day_programId ON program_day (programId)");
        db.execSQL("WITH RECURSIVE " +
                "bounds(programId, maxDays) AS (" +
                "SELECT program.id, MAX(program.cycleLengthDays, COALESCE((" +
                "SELECT MAX(task.dayIndex) + 1 FROM program_task_v20 task WHERE task.programId = program.id), 0)) " +
                "FROM training_program_v20 program), " +
                "days(programId, dayIndex, maxDays) AS (" +
                "SELECT programId, 0, maxDays FROM bounds WHERE maxDays > 0 " +
                "UNION ALL SELECT programId, dayIndex + 1, maxDays FROM days WHERE dayIndex + 1 < maxDays) " +
                "INSERT INTO program_day (programId, orderIndex, name, isRest) " +
                "SELECT days.programId, days.dayIndex, (" +
                "SELECT task.dayTitle FROM program_task_v20 task " +
                "WHERE task.programId = days.programId AND task.dayIndex = days.dayIndex " +
                "AND task.dayTitle IS NOT NULL ORDER BY task.orderIndex, task.id LIMIT 1), " +
                "CASE WHEN EXISTS (SELECT 1 FROM program_task_v20 task " +
                "WHERE task.programId = days.programId AND task.dayIndex = days.dayIndex) THEN 0 ELSE 1 END " +
                "FROM days ORDER BY days.programId, days.dayIndex");

        db.execSQL("CREATE TABLE program_exercise (" +
                "dayId INTEGER NOT NULL," +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                "orderIndex INTEGER NOT NULL," +
                "exerciseId INTEGER NOT NULL," +
                "note TEXT," +
                "restSeconds INTEGER," +
                "supersetGroup INTEGER," +
                "FOREIGN KEY (dayId) REFERENCES program_day(id) ON DELETE CASCADE," +
                "FOREIGN KEY (exerciseId) REFERENCES exercise(id))");
        db.execSQL("CREATE INDEX index_program_exercise_dayId ON program_exercise (dayId)");
        db.execSQL("CREATE INDEX index_program_exercise_exerciseId ON program_exercise (exerciseId)");
        db.execSQL("INSERT INTO program_exercise " +
                "(dayId, id, orderIndex, exerciseId, note, restSeconds, supersetGroup) " +
                "SELECT day.id, task.id, task.orderIndex, task.id, task.note, NULL, NULL " +
                "FROM program_task_v20 task INNER JOIN program_day day " +
                "ON day.programId = task.programId AND day.orderIndex = task.dayIndex");

        db.execSQL("CREATE TABLE program_set (" +
                "programExerciseId INTEGER NOT NULL," +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                "orderIndex INTEGER NOT NULL," +
                "type TEXT NOT NULL," +
                "reps INTEGER," +
                "repsMax INTEGER," +
                "weightKg REAL," +
                "durationMillis INTEGER," +
                "distanceMeters REAL," +
                "FOREIGN KEY (programExerciseId) REFERENCES program_exercise(id) ON DELETE CASCADE)");
        db.execSQL("CREATE INDEX index_program_set_programExerciseId ON program_set (programExerciseId)");
        db.execSQL("WITH RECURSIVE set_rows(programExerciseId, setIndex, setCount, reps, durationMillis, distanceMeters) AS (" +
                "SELECT id, 0, CASE WHEN targetSets IS NULL OR targetSets < 1 THEN 1 ELSE targetSets END, " +
                "targetReps, targetDurationMillis, targetDistanceMeters FROM program_task_v20 " +
                "UNION ALL SELECT programExerciseId, setIndex + 1, setCount, reps, durationMillis, distanceMeters " +
                "FROM set_rows WHERE setIndex + 1 < setCount) " +
                "INSERT INTO program_set " +
                "(programExerciseId, orderIndex, type, reps, repsMax, weightKg, durationMillis, distanceMeters) " +
                "SELECT programExerciseId, setIndex, 'NORMAL', reps, NULL, NULL, durationMillis, distanceMeters " +
                "FROM set_rows ORDER BY programExerciseId, setIndex");

        db.execSQL("CREATE TABLE program_completion (" +
                "programExerciseId INTEGER NOT NULL," +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                "occurrenceDate INTEGER NOT NULL," +
                "status INTEGER NOT NULL," +
                "workoutId INTEGER," +
                "completedAt INTEGER NOT NULL," +
                "FOREIGN KEY (programExerciseId) REFERENCES program_exercise(id) ON DELETE CASCADE," +
                "FOREIGN KEY (workoutId) REFERENCES workout(id) ON DELETE SET NULL)");
        db.execSQL("CREATE UNIQUE INDEX index_program_completion_programExerciseId_occurrenceDate " +
                "ON program_completion (programExerciseId, occurrenceDate)");
        db.execSQL("CREATE INDEX index_program_completion_workoutId ON program_completion (workoutId)");
        db.execSQL("INSERT OR IGNORE INTO program_completion " +
                "(programExerciseId, id, occurrenceDate, status, workoutId, completedAt) " +
                "SELECT programTaskId, id, occurrenceDate, status, workoutId, 0 " +
                "FROM program_completion_v20 ORDER BY id");

        db.execSQL("CREATE TABLE set_log (" +
                "completionId INTEGER NOT NULL," +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                "orderIndex INTEGER NOT NULL," +
                "type TEXT NOT NULL," +
                "reps INTEGER," +
                "weightKg REAL," +
                "durationMillis INTEGER," +
                "distanceMeters REAL," +
                "targetReps INTEGER," +
                "targetRepsMax INTEGER," +
                "targetWeightKg REAL," +
                "targetDurationMillis INTEGER," +
                "targetDistanceMeters REAL," +
                "FOREIGN KEY (completionId) REFERENCES program_completion(id) ON DELETE CASCADE)");
        db.execSQL("CREATE INDEX index_set_log_completionId ON set_log (completionId)");

        db.execSQL("DROP TABLE program_completion_v20");
        db.execSQL("DROP TABLE program_task_v20");
        db.execSQL("DROP TABLE training_program_v20");
    }

    public static AppDatabase provideDatabase(Context context) {
        return Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, DATABASE_NAME)
                .addMigrations(new Migration(1, 2) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase database) {
                        try {
                            database.beginTransaction();

                            database.execSQL("ALTER table workout add descent REAL NOT NULL DEFAULT 0;");
                            database.execSQL("ALTER table workout add ascent REAL NOT NULL DEFAULT 0");

                            database.execSQL("ALTER TABLE workout_sample RENAME TO workout_sample2;");

                            database.execSQL("CREATE TABLE workout_sample (" +
                                    "id INTEGER NOT NULL DEFAULT NULL PRIMARY KEY," +
                                    "relativeTime INTEGER NOT NULL DEFAULT NULL," +
                                    "elevation REAL NOT NULL DEFAULT NULL," +
                                    "absoluteTime INTEGER NOT NULL DEFAULT NULL," +
                                    "lat REAL NOT NULL DEFAULT NULL," +
                                    "lon REAL NOT NULL DEFAULT NULL," +
                                    "speed REAL NOT NULL DEFAULT NULL," +
                                    "workout_id INTEGER NOT NULL DEFAULT NULL," +
                                    "FOREIGN KEY (workout_id) REFERENCES workout(id) ON DELETE CASCADE);");

                            database.execSQL("INSERT INTO workout_sample (id, relativeTime, elevation, absoluteTime, lat, lon, speed, workout_id) " +
                                    "SELECT id, relativeTime, elevation, absoluteTime, lat, lon, speed, workout_id FROM workout_sample2");

                            database.execSQL("DROP TABLE workout_sample2");

                            database.setTransactionSuccessful();
                        } finally {
                            database.endTransaction();
                        }
                    }
                }, new Migration(2, 3) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase database) {
                        try {
                            database.beginTransaction();

                            database.execSQL("ALTER table workout add COLUMN edited INTEGER not null default 0");

                            database.setTransactionSuccessful();
                        } finally {
                            database.endTransaction();
                        }
                    }
                }, new Migration(3, 4) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase database) {
                        try {
                            database.beginTransaction();

                            database.execSQL("CREATE TABLE interval (\n" +
                                    "    id integer NOT NULL primary key,\n" +
                                    "    name text,\n" +
                                    "    delay_millis integer NOT NULL,\n" +
                                    "    queue_id integer NOT NULL,\n" +
                                    "   FOREIGN KEY (queue_id) \n" +
                                    "      REFERENCES interval_queue (id) \n" +
                                    "         ON DELETE CASCADE \n" +
                                    "         ON UPDATE NO ACTION" +
                                    ");");

                            database.execSQL("CREATE TABLE interval_queue (\n" +
                                    "    id integer NOT NULL primary key,\n" +
                                    "    name text,\n" +
                                    "    state integer not null\n" +
                                    ");");

                            database.setTransactionSuccessful();
                        } finally {
                            database.endTransaction();
                        }
                    }
                }, new Migration(3, 5) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase database) {
                        try {
                            database.beginTransaction();

                            database.execSQL("CREATE TABLE interval (\n" +
                                    "    id integer NOT NULL primary key,\n" +
                                    "    name text,\n" +
                                    "    delay_millis integer NOT NULL,\n" +
                                    "    set_id integer NOT NULL,\n" +
                                    "   FOREIGN KEY (set_id) \n" +
                                    "      REFERENCES interval_set (id) \n" +
                                    "         ON DELETE CASCADE \n" +
                                    "         ON UPDATE NO ACTION" +
                                    ");");

                            database.execSQL("CREATE TABLE interval_set (\n" +
                                    "    id integer NOT NULL primary key,\n" +
                                    "    name text,\n" +
                                    "    state integer not null\n" +
                                    ");");

                            database.setTransactionSuccessful();
                        } finally {
                            database.endTransaction();
                        }
                    }
                }, new Migration(4, 5) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase database) {
                        try {
                            database.beginTransaction();

                            database.execSQL("drop table interval");

                            database.execSQL("drop table interval_queue");

                            database.execSQL("CREATE TABLE interval (\n" +
                                    "    id integer NOT NULL primary key,\n" +
                                    "    name text,\n" +
                                    "    delay_millis integer NOT NULL,\n" +
                                    "    set_id integer NOT NULL,\n" +
                                    "   FOREIGN KEY (set_id) \n" +
                                    "      REFERENCES interval_set (id) \n" +
                                    "         ON DELETE CASCADE \n" +
                                    "         ON UPDATE NO ACTION" +
                                    ");");

                            database.execSQL("CREATE TABLE interval_set (\n" +
                                    "    id integer NOT NULL primary key,\n" +
                                    "    name text,\n" +
                                    "    state integer not null\n" +
                                    ");");

                            database.setTransactionSuccessful();
                        } finally {
                            database.endTransaction();
                        }
                    }
                }, new Migration(5, 6) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase database) {
                        try {
                            database.beginTransaction();

                            database.execSQL("ALTER table workout add COLUMN interval_set_used_id INTEGER not null default 0");

                            database.setTransactionSuccessful();
                        } finally {
                            database.endTransaction();
                        }
                    }
                }, new Migration(6, 7) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase database) {
                        try {
                            database.beginTransaction();

                            database.execSQL("ALTER table workout add COLUMN interval_set_include_pauses INTEGER not null default 0");

                            database.setTransactionSuccessful();
                        } finally {
                            database.endTransaction();
                        }
                    }
                }, new Migration(7, 8) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase database) {
                        try {
                            database.beginTransaction();

                            database.execSQL("ALTER table workout_sample add COLUMN pressure REAL not null default 0");

                            database.setTransactionSuccessful();
                        } finally {
                            database.endTransaction();
                        }
                    }
                }, new Migration(8, 9) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase database) {
                        try {
                            database.beginTransaction();

                            database.execSQL("ALTER table workout_sample add COLUMN elevation_msl REAL not null default 0;");

                            database.execSQL("UPDATE workout_sample set elevation_msl = elevation;");

                            database.setTransactionSuccessful();
                        } finally {
                            database.endTransaction();
                        }
                    }
                }, new Migration(9, 10) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase database) {
                        try {
                            database.beginTransaction();

                            database.execSQL("ALTER table workout_sample add COLUMN heart_rate INTEGER not null default 0;");
                            database.execSQL("ALTER table workout add COLUMN avg_heart_rate INTEGER not null default 0;");
                            database.execSQL("ALTER table workout add COLUMN max_heart_rate INTEGER not null default 0;");

                            database.setTransactionSuccessful();
                        } finally {
                            database.endTransaction();
                        }
                    }
                }, new Migration(10, 11) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase database) {
                        try {
                            database.beginTransaction();

                            database.execSQL("CREATE TABLE workout_type (\n" +
                                    "    id text NOT NULL primary key,\n" +
                                    "    icon text,\n" +
                                    "    title text,\n" +
                                    "    min_distance integer NOT NULL,\n" +
                                    "    color integer NOT NULL,\n" +
                                    "    met integer NOT NULL\n" +
                                    ");");

                            database.setTransactionSuccessful();
                        } finally {
                            database.endTransaction();
                        }
                    }
                }, new Migration(11, 12) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase database) {
                        try {
                            database.beginTransaction();

                            database.execSQL("create index index_workout_sample_workout_id on workout_sample (workout_id)");
                            database.execSQL("create index index_interval_set_id on interval (set_id)");

                            database.setTransactionSuccessful();
                        } finally {
                            database.endTransaction();
                        }
                    }
                }, new Migration(12, 13) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase database) {
                        try {
                            database.beginTransaction();

                            // Add new columns
                            database.execSQL("ALTER table workout add COLUMN min_elevation_msl REAL not null default 0;");
                            database.execSQL("ALTER table workout add COLUMN max_elevation_msl REAL not null default 0;");

                            // Calculate min and max elevation for the new columns
                            // What it does: take the min/max value from samples. If no samples exist for the workout, then it's 0
                            database.execSQL("update workout set min_elevation_msl = " +
                                    "(select min(elevation_msl) from workout_sample where workout_id = workout.id) " +
                                    "where min_elevation_msl=0 and (select count(id) from workout_sample where workout_id = workout.id) > 0");
                            database.execSQL("update workout set max_elevation_msl = " +
                                    "(select max(elevation_msl) from workout_sample where workout_id = workout.id) " +
                                    "where max_elevation_msl=0 and (select count(id) from workout_sample where workout_id = workout.id) > 0");

                            database.setTransactionSuccessful();
                        } finally {
                            database.endTransaction();
                        }
                    }
                }, new Migration(13, 14) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase database) {
                        try {
                            database.beginTransaction();

                            database.execSQL("ALTER TABLE workout RENAME TO workout2;");

                            database.execSQL("CREATE TABLE workout (" +
                                    "id INTEGER NOT NULL DEFAULT NULL PRIMARY KEY," +
                                    "start INTEGER NOT NULL DEFAULT 0," +
                                    "`end` INTEGER NOT NULL DEFAULT 0," +
                                    "duration INTEGER NOT NULL DEFAULT 0," +
                                    "pauseDuration INTEGER NOT NULL DEFAULT 0," +
                                    "comment TEXT DEFAULT NULL," +
                                    "length INTEGER NOT NULL DEFAULT 0," +
                                    "avgSpeed REAL NOT NULL DEFAULT 0," +
                                    "topSpeed REAL NOT NULL DEFAULT 0," +
                                    "avgPace REAL NOT NULL DEFAULT 0," +
                                    "workoutType TEXT DEFAULT NULL," +
                                    "min_elevation_msl REAL NOT NULL DEFAULT 0," +
                                    "max_elevation_msl REAL NOT NULL DEFAULT 0," +
                                    "ascent REAL NOT NULL DEFAULT 0," +
                                    "descent REAL NOT NULL DEFAULT 0," +
                                    "calorie INTEGER NOT NULL DEFAULT 0," +
                                    "edited INTEGER NOT NULL DEFAULT 0," +
                                    "interval_set_used_id INTEGER NOT NULL DEFAULT 0," +
                                    "avg_heart_rate INTEGER NOT NULL DEFAULT 0," +
                                    "max_heart_rate INTEGER NOT NULL DEFAULT 0);");

                            database.execSQL("INSERT INTO workout (id,start,`end`,duration,pauseDuration,comment,length,avgSpeed,topSpeed,avgPace,avgPace,workoutType,min_elevation_msl,max_elevation_msl,ascent,descent,calorie,edited,interval_set_used_id,avg_heart_rate,max_heart_rate) " +
                                    "SELECT id,start,`end`,duration,pauseDuration,comment,length,avgSpeed,topSpeed,avgPace,avgPace,workoutType,min_elevation_msl,max_elevation_msl,ascent,descent,calorie,edited,interval_set_used_id,avg_heart_rate,max_heart_rate FROM workout2");

                            database.execSQL("DROP TABLE workout2");

                            database.execSQL("ALTER table workout_sample add COLUMN interval_triggered INTEGER not null default -1;");

                            database.setTransactionSuccessful();
                        } finally {
                            database.endTransaction();
                        }
                    }
                }, new Migration(14, 15) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase database) {
                        try {
                            database.beginTransaction();

                            database.execSQL("CREATE TABLE indoor_workout (" +
                                    "id INTEGER NOT NULL DEFAULT NULL PRIMARY KEY," +
                                    "start INTEGER NOT NULL DEFAULT 0," +
                                    "`end` INTEGER NOT NULL DEFAULT 0," +
                                    "duration INTEGER NOT NULL DEFAULT 0," +
                                    "pauseDuration INTEGER NOT NULL DEFAULT 0," +
                                    "comment TEXT DEFAULT NULL," +
                                    "workoutType TEXT DEFAULT NULL," +
                                    "calorie INTEGER NOT NULL DEFAULT 0," +
                                    "edited INTEGER NOT NULL DEFAULT 0," +
                                    "repetitions INTEGER NOT NULL DEFAULT 0," +
                                    "avgFrequency REAL NOT NULL DEFAULT 0," +
                                    "maxFrequency REAL NOT NULL DEFAULT 0," +
                                    "avgIntensity REAL NOT NULL DEFAULT 0," +
                                    "maxIntensity REAL NOT NULL DEFAULT 0," +
                                    "interval_set_used_id INTEGER NOT NULL DEFAULT 0," +
                                    "avg_heart_rate INTEGER NOT NULL DEFAULT 0," +
                                    "max_heart_rate INTEGER NOT NULL DEFAULT 0);");

                            database.execSQL("CREATE TABLE indoor_sample (" +
                                    "id INTEGER NOT NULL DEFAULT NULL PRIMARY KEY," +
                                    "absoluteTime INTEGER NOT NULL DEFAULT 0," +
                                    "relativeTime INTEGER NOT NULL DEFAULT 0," +
                                    "heart_rate INTEGER NOT NULL DEFAULT 0," +
                                    "interval_triggered INTEGER NOT NULL DEFAULT 0," +
                                    "workout_id INTEGER NOT NULL DEFAULT 0," +
                                    "intensity REAL NOT NULL DEFAULT 0," +
                                    "frequency REAL NOT NULL DEFAULT 0," +
                                    "repetitions INTEGER NOT NULL DEFAULT 0," +
                                    "absoluteEndTime INTEGER NOT NULL DEFAULT 0," +
                                    "   FOREIGN KEY (workout_id) \n" +
                                    "      REFERENCES indoor_workout (id) \n" +
                                    "         ON DELETE CASCADE \n" +
                                    "         ON UPDATE NO ACTION" +
                                    ");");

                            database.execSQL("ALTER table workout_type add COLUMN type TEXT default 'gps';");

                            database.execSQL("create index index_indoor_sample_workout_id on indoor_sample (workout_id)");

                            database.setTransactionSuccessful();
                        } finally {
                            database.endTransaction();
                        }
                    }
                }, new Migration(15, 16) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase database) {
                        try {
                            database.beginTransaction();

                            database.execSQL("CREATE TABLE export_target_config (\n" +
                                    "    id integer NOT NULL primary key,\n" +
                                    "    source text,\n" +
                                    "    type text,\n" +
                                    "    data text\n" +
                                    ");");

                            database.setTransactionSuccessful();
                        } finally {
                            database.endTransaction();
                        }
                    }
                }, new Migration(16, 17) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        try {
                            db.beginTransaction();

                            // HANDLE WORKOUT

                            db.execSQL("ALTER TABLE workout RENAME TO workout2;");

                            db.execSQL("CREATE TABLE workout (" +
                                    "id INTEGER NOT NULL DEFAULT NULL PRIMARY KEY," +
                                    "uuid TEXT NOT NULL," +
                                    "start INTEGER NOT NULL," +
                                    "`end` INTEGER NOT NULL," +
                                    "activeDuration INTEGER NOT NULL," +
                                    "comment TEXT DEFAULT NULL," +
                                    "distance REAL NOT NULL," +
                                    "repetitions INTEGER NOT NULL," +
                                    "energy REAL NOT NULL," +
                                    "workoutTypeId TEXT NOT NULL," +
                                    "intervalSetUsedId INTEGER NOT NULL);");

                            db.execSQL("INSERT INTO workout (id,uuid,start,`end`,activeDuration,comment,distance,repetitions,energy,workoutTypeId,intervalSetUsedId) \n" +
                                    "SELECT id,\n" +
                                    "       lower(hex(randomblob(4)) || '-' ||\n" +
                                    "             hex(randomblob(2)) || '-' ||\n" +
                                    "             '4' || substr(hex(randomblob(2)),2) || '-' ||\n" +
                                    "             substr('89ab',abs(random()) % 4 + 1,1) || substr(hex(randomblob(2)),2) || '-' ||\n" +
                                    "             hex(randomblob(6))),\n" +
                                    "       start,`end`,duration,comment,length,0,calorie,workoutType,interval_set_used_id\n" +
                                    "FROM workout2;\n");


                            // HANDLE INDOOR WORKOUT
                            db.execSQL("INSERT INTO workout (id,uuid,start,`end`,activeDuration,comment,distance,repetitions,energy,workoutTypeId,intervalSetUsedId) \n" +
                                    "SELECT id,\n" +
                                    "       lower(hex(randomblob(4)) || '-' ||\n" +
                                    "             hex(randomblob(2)) || '-' ||\n" +
                                    "             '4' || substr(hex(randomblob(2)),2) || '-' ||\n" +
                                    "             substr('89ab',abs(random()) % 4 + 1,1) || substr(hex(randomblob(2)),2) || '-' ||\n" +
                                    "             hex(randomblob(6))),\n" +
                                    "       start,`end`,duration,comment,0.0,repetitions,calorie,workoutType,-1\n" +
                                    "FROM indoor_workout;\n");

                            // HANDLE ROUTE SAMPLE

                            db.execSQL("CREATE TABLE route_sample (" +
                                    "id INTEGER NOT NULL PRIMARY KEY," +
                                    "timestamp INTEGER NOT NULL," +
                                    "relativeTime INTEGER NOT NULL," +
                                    "latitude REAL NOT NULL," +
                                    "longitude REAL NOT NULL," +
                                    "elevation REAL NOT NULL," +
                                    "elevationMSL REAL NOT NULL," +
                                    "speed REAL NOT NULL," +
                                    "pressure REAL NOT NULL," +
                                    "workoutId INTEGER NOT NULL," +
                                    "FOREIGN KEY (workoutId) REFERENCES workout(id) ON DELETE CASCADE);");

                            db.execSQL("insert into route_sample (id,timestamp,relativeTime,latitude,longitude,elevation,elevationMSL,speed,pressure,workoutId)\n" +
                                    "select id,absoluteTime,relativeTime,lat,lon,elevation,elevation_msl,speed,pressure,workout_id from workout_sample;");

                            // HANDLE PAUSE

                            db.execSQL("CREATE TABLE workout_pause (" +
                                    "id INTEGER NOT NULL PRIMARY KEY," +
                                    "startDate INTEGER NOT NULL," +
                                    "endDate INTEGER NOT NULL," +
                                    "userInitiated INTEGER NOT NULL," +
                                    "workoutId INTEGER NOT NULL," +
                                    "FOREIGN KEY (workoutId) REFERENCES workout(id) ON DELETE CASCADE);");

                            // HANDLE REPETITION SAMPLES

                            db.execSQL("CREATE TABLE repetition_sample (" +
                                    "id INTEGER NOT NULL PRIMARY KEY," +
                                    "timestamp INTEGER NOT NULL," +
                                    "relativeTime INTEGER NOT NULL," +
                                    "timestampEnd INTEGER NOT NULL," +
                                    "repetitionTypeId INTEGER NOT NULL," +
                                    "repetitions INTEGER NOT NULL," +
                                    "intensity REAL NOT NULL," +
                                    "frequency REAL NOT NULL," +
                                    "workoutId INTEGER NOT NULL," +
                                    "FOREIGN KEY (workoutId) REFERENCES workout(id) ON DELETE CASCADE);");

                            db.execSQL("insert into repetition_sample (id,timestamp,relativeTime,timestampEnd,repetitionTypeId,repetitions,intensity,frequency,workoutId)\n" +
                                    "select id,absoluteTime,relativeTime,absoluteEndTime,0,repetitions,intensity,frequency,workout_id from indoor_sample;");

                            // HANDLE HR SAMPLES

                            db.execSQL("create table heart_rate_sample (" +
                                    "id INTEGER not null primary key," +
                                    "timestamp integer not null," +
                                    "relativeTime integer not null," +
                                    "heartRate integer not null," +
                                    "workoutId integer not null," +
                                    "FOREIGN key (workoutId) references workout(id) on delete cascade);");

                            db.execSQL("insert into heart_rate_sample(id,timestamp,relativeTime,heartRate,workoutId)\n" +
                                    "select id,absoluteTime,relativeTime,heart_rate,workout_id from workout_sample where heart_rate > 0;");
                            db.execSQL("insert into heart_rate_sample(id,timestamp,relativeTime,heartRate,workoutId)\n" +
                                    "select id,absoluteTime,relativeTime,heart_rate,workout_id from indoor_sample where heart_rate > 0;");

                            // HANDLE WORKOUT EVENT

                            db.execSQL("create table workout_event (" +
                                    "id integer not null primary key," +
                                    "timestamp integer not null," +
                                    "relativeTime integer not null," +
                                    "typeId text not null," +
                                    "workoutId integer not null," +
                                    "FOREIGN key (workoutId) references workout(id) on delete cascade);");

                            db.execSQL("insert into workout_event (id,timestamp,relativeTime,typeId,workoutId)\n" +
                                    "select id,absoluteTime,relativeTime,'interval',workout_id from workout_sample where interval_triggered > 0");
                            db.execSQL("insert into workout_event (id,timestamp,relativeTime,typeId,workoutId)\n" +
                                    "select id,absoluteTime,relativeTime,'interval',workout_id from indoor_sample where interval_triggered > 0");

                            // delete old tables
                            db.execSQL("DROP TABLE workout2;");
                            db.execSQL("drop table workout_sample;");
                            db.execSQL("drop table indoor_workout;");
                            db.execSQL("drop table indoor_sample;");

                            db.setTransactionSuccessful();
                        } finally {
                            db.endTransaction();
                        }
                    }
                }, new Migration(17, 18) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        try {
                            db.beginTransaction();

                            db.execSQL("create index index_route_sample_workoutId on route_sample (workoutId)");
                            db.execSQL("create index index_heart_rate_sample_workoutId on heart_rate_sample (workoutId)");
                            db.execSQL("create index index_workout_event_workoutId on workout_event (workoutId)");
                            db.execSQL("create index index_workout_pause_workoutId on workout_pause (workoutId)");
                            db.execSQL("create index index_repetition_sample_workoutId on repetition_sample (workoutId)");

                            db.setTransactionSuccessful();
                        } finally {
                            db.endTransaction();
                        }
                    }
                }, new Migration(18, 19) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        try {
                            db.beginTransaction();

                            // Recreate all child tables with AUTOINCREMENT so Room's schema hash matches.
                            // Pattern: rename → create with AUTOINCREMENT → copy → drop → index.

                            db.execSQL("ALTER TABLE route_sample RENAME TO route_sample_old");
                            db.execSQL("CREATE TABLE route_sample (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, timestamp INTEGER NOT NULL, relativeTime INTEGER NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, elevation REAL NOT NULL, elevationMSL REAL NOT NULL, speed REAL NOT NULL, pressure REAL NOT NULL, workoutId INTEGER NOT NULL, FOREIGN KEY (workoutId) REFERENCES workout(id) ON DELETE CASCADE)");
                            db.execSQL("INSERT INTO route_sample (id, timestamp, relativeTime, latitude, longitude, elevation, elevationMSL, speed, pressure, workoutId) SELECT id, timestamp, relativeTime, latitude, longitude, elevation, elevationMSL, speed, pressure, workoutId FROM route_sample_old");
                            db.execSQL("DROP TABLE route_sample_old");
                            db.execSQL("CREATE INDEX index_route_sample_workoutId ON route_sample (workoutId)");

                            db.execSQL("ALTER TABLE workout_pause RENAME TO workout_pause_old");
                            db.execSQL("CREATE TABLE workout_pause (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, startDate INTEGER NOT NULL, endDate INTEGER NOT NULL, userInitiated INTEGER NOT NULL, workoutId INTEGER NOT NULL, FOREIGN KEY (workoutId) REFERENCES workout(id) ON DELETE CASCADE)");
                            db.execSQL("INSERT INTO workout_pause (id, startDate, endDate, userInitiated, workoutId) SELECT id, startDate, endDate, userInitiated, workoutId FROM workout_pause_old");
                            db.execSQL("DROP TABLE workout_pause_old");
                            db.execSQL("CREATE INDEX index_workout_pause_workoutId ON workout_pause (workoutId)");

                            db.execSQL("ALTER TABLE repetition_sample RENAME TO repetition_sample_old");
                            db.execSQL("CREATE TABLE repetition_sample (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, timestamp INTEGER NOT NULL, relativeTime INTEGER NOT NULL, timestampEnd INTEGER NOT NULL, repetitionTypeId INTEGER NOT NULL, repetitions INTEGER NOT NULL, intensity REAL NOT NULL, frequency REAL NOT NULL, workoutId INTEGER NOT NULL, FOREIGN KEY (workoutId) REFERENCES workout(id) ON DELETE CASCADE)");
                            db.execSQL("INSERT INTO repetition_sample (id, timestamp, relativeTime, timestampEnd, repetitionTypeId, repetitions, intensity, frequency, workoutId) SELECT id, timestamp, relativeTime, timestampEnd, repetitionTypeId, repetitions, intensity, frequency, workoutId FROM repetition_sample_old");
                            db.execSQL("DROP TABLE repetition_sample_old");
                            db.execSQL("CREATE INDEX index_repetition_sample_workoutId ON repetition_sample (workoutId)");

                            db.execSQL("ALTER TABLE heart_rate_sample RENAME TO heart_rate_sample_old");
                            db.execSQL("CREATE TABLE heart_rate_sample (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, timestamp INTEGER NOT NULL, relativeTime INTEGER NOT NULL, heartRate INTEGER NOT NULL, workoutId INTEGER NOT NULL, FOREIGN KEY (workoutId) REFERENCES workout(id) ON DELETE CASCADE)");
                            db.execSQL("INSERT INTO heart_rate_sample (id, timestamp, relativeTime, heartRate, workoutId) SELECT id, timestamp, relativeTime, heartRate, workoutId FROM heart_rate_sample_old");
                            db.execSQL("DROP TABLE heart_rate_sample_old");
                            db.execSQL("CREATE INDEX index_heart_rate_sample_workoutId ON heart_rate_sample (workoutId)");

                            db.execSQL("ALTER TABLE workout_event RENAME TO workout_event_old");
                            db.execSQL("CREATE TABLE workout_event (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, timestamp INTEGER NOT NULL, relativeTime INTEGER NOT NULL, typeId TEXT NOT NULL, workoutId INTEGER NOT NULL, FOREIGN KEY (workoutId) REFERENCES workout(id) ON DELETE CASCADE)");
                            db.execSQL("INSERT INTO workout_event (id, timestamp, relativeTime, typeId, workoutId) SELECT id, timestamp, relativeTime, typeId, workoutId FROM workout_event_old");
                            db.execSQL("DROP TABLE workout_event_old");
                            db.execSQL("CREATE INDEX index_workout_event_workoutId ON workout_event (workoutId)");

                            db.setTransactionSuccessful();
                        } finally {
                            db.endTransaction();
                        }
                    }
                }, new Migration(19, 20) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        try {
                            db.beginTransaction();

                            db.execSQL("CREATE TABLE training_program (" +
                                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                                    "uuid TEXT NOT NULL," +
                                    "name TEXT NOT NULL," +
                                    "description TEXT," +
                                    "cycleLengthDays INTEGER NOT NULL," +
                                    "startDate INTEGER," +
                                    "createdAt INTEGER NOT NULL)");

                            db.execSQL("CREATE TABLE program_task (" +
                                    "programId INTEGER NOT NULL," +
                                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                                    "dayIndex INTEGER NOT NULL," +
                                    "dayTitle TEXT," +
                                    "orderIndex INTEGER NOT NULL," +
                                    "workoutTypeId TEXT NOT NULL," +
                                    "targetSets INTEGER," +
                                    "targetReps INTEGER," +
                                    "targetDurationMillis INTEGER," +
                                    "targetDistanceMeters REAL," +
                                    "note TEXT," +
                                    "FOREIGN KEY (programId) REFERENCES training_program(id) ON DELETE CASCADE)");
                            db.execSQL("CREATE INDEX index_program_task_programId ON program_task (programId)");

                            db.execSQL("CREATE TABLE program_completion (" +
                                    "programTaskId INTEGER NOT NULL," +
                                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                                    "occurrenceDate INTEGER NOT NULL," +
                                    "workoutId INTEGER," +
                                    "status INTEGER NOT NULL," +
                                    "FOREIGN KEY (programTaskId) REFERENCES program_task(id) ON DELETE CASCADE," +
                                    "FOREIGN KEY (workoutId) REFERENCES workout(id) ON DELETE SET NULL)");
                            db.execSQL("CREATE INDEX index_program_completion_programTaskId ON program_completion (programTaskId)");
                            db.execSQL("CREATE INDEX index_program_completion_workoutId ON program_completion (workoutId)");

                            db.setTransactionSuccessful();
                        } finally {
                            db.endTransaction();
                        }
                    }
                }, new Migration(20, 21) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        try {
                            db.beginTransaction();

                            migratePrograms20To21(db);

                            db.setTransactionSuccessful();
                        } finally {
                            db.endTransaction();
                        }
                    }
                }, new Migration(21, 22) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        try {
                            db.beginTransaction();

                            db.execSQL("ALTER TABLE program_exercise ADD COLUMN measurementSource TEXT NOT NULL DEFAULT 'USER_INPUT'");
                            db.execSQL("ALTER TABLE program_exercise ADD COLUMN completionRule TEXT NOT NULL DEFAULT 'USER_CONFIRM'");
                            db.execSQL("ALTER TABLE program_exercise ADD COLUMN prepareSeconds INTEGER NOT NULL DEFAULT 0");
                            db.execSQL("ALTER TABLE program_exercise ADD COLUMN transitionSeconds INTEGER");

                            // Existing recordable entries retain their recorder intent. Pure duration
                            // entries become deterministic clock timers; everything else stays manual.
                            db.execSQL("UPDATE program_exercise SET " +
                                    "measurementSource = 'RECORDER', completionRule = 'METRIC_TARGET_REACHED' " +
                                    "WHERE exerciseId IN (SELECT id FROM exercise WHERE workoutTypeId IS NOT NULL AND workoutTypeId != '')");
                            db.execSQL("UPDATE program_exercise SET completionRule = 'DURATION_REACHED' " +
                                    "WHERE exerciseId IN (SELECT id FROM exercise WHERE " +
                                    "metricKind IN ('DURATION', 'WEIGHT_DURATION') " +
                                    "AND workoutTypeId IS NOT NULL AND workoutTypeId != '')");
                            db.execSQL("UPDATE program_exercise SET " +
                                    "measurementSource = 'CLOCK', completionRule = 'DURATION_REACHED' " +
                                    "WHERE exerciseId IN (SELECT id FROM exercise WHERE " +
                                    "metricKind IN ('DURATION', 'WEIGHT_DURATION', 'DISTANCE_DURATION') " +
                                    "AND (workoutTypeId IS NULL OR workoutTypeId = ''))");

                            db.execSQL("CREATE TABLE training_session (" +
                                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                                    "uuid TEXT NOT NULL," +
                                    "sourceProgramId INTEGER," +
                                    "sourceDayId INTEGER," +
                                    "occurrenceDate INTEGER," +
                                    "zoneId TEXT," +
                                    "nameSnapshot TEXT NOT NULL," +
                                    "descriptionSnapshot TEXT," +
                                    "status TEXT NOT NULL," +
                                    "phase TEXT NOT NULL," +
                                    "currentExerciseIndex INTEGER NOT NULL," +
                                    "currentSetIndex INTEGER NOT NULL," +
                                    "startedAt INTEGER NOT NULL," +
                                    "updatedAt INTEGER NOT NULL," +
                                    "completedAt INTEGER," +
                                    "phaseStartedAt INTEGER," +
                                    "phaseDeadlineAt INTEGER," +
                                    "pausedRemainingMillis INTEGER," +
                                    "FOREIGN KEY (sourceProgramId) REFERENCES training_program(id) ON DELETE SET NULL," +
                                    "FOREIGN KEY (sourceDayId) REFERENCES program_day(id) ON DELETE SET NULL)");
                            db.execSQL("CREATE UNIQUE INDEX index_training_session_uuid ON training_session (uuid)");
                            db.execSQL("CREATE INDEX index_training_session_sourceProgramId ON training_session (sourceProgramId)");
                            db.execSQL("CREATE INDEX index_training_session_sourceDayId ON training_session (sourceDayId)");
                            db.execSQL("CREATE INDEX index_training_session_status ON training_session (status)");

                            db.execSQL("CREATE TABLE session_exercise (" +
                                    "sessionId INTEGER NOT NULL," +
                                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                                    "orderIndex INTEGER NOT NULL," +
                                    "sourceProgramExerciseId INTEGER," +
                                    "sourceExerciseId INTEGER," +
                                    "exerciseUuidSnapshot TEXT NOT NULL," +
                                    "nameSnapshot TEXT NOT NULL," +
                                    "metricKindSnapshot TEXT NOT NULL," +
                                    "workoutTypeIdSnapshot TEXT," +
                                    "descriptionSnapshot TEXT," +
                                    "tipsSnapshot TEXT," +
                                    "videoUrlSnapshot TEXT," +
                                    "imagePathSnapshot TEXT," +
                                    "noteSnapshot TEXT," +
                                    "measurementSource TEXT NOT NULL," +
                                    "completionRule TEXT NOT NULL," +
                                    "prepareSeconds INTEGER NOT NULL," +
                                    "restSeconds INTEGER," +
                                    "transitionSeconds INTEGER," +
                                    "status TEXT NOT NULL," +
                                    "startedAt INTEGER," +
                                    "completedAt INTEGER," +
                                    "FOREIGN KEY (sessionId) REFERENCES training_session(id) ON DELETE CASCADE," +
                                    "FOREIGN KEY (sourceProgramExerciseId) REFERENCES program_exercise(id) ON DELETE SET NULL," +
                                    "FOREIGN KEY (sourceExerciseId) REFERENCES exercise(id) ON DELETE SET NULL)");
                            db.execSQL("CREATE INDEX index_session_exercise_sessionId ON session_exercise (sessionId)");
                            db.execSQL("CREATE INDEX index_session_exercise_sourceProgramExerciseId ON session_exercise (sourceProgramExerciseId)");
                            db.execSQL("CREATE INDEX index_session_exercise_sourceExerciseId ON session_exercise (sourceExerciseId)");
                            db.execSQL("CREATE UNIQUE INDEX index_session_exercise_sessionId_orderIndex " +
                                    "ON session_exercise (sessionId, orderIndex)");

                            db.execSQL("CREATE TABLE session_set (" +
                                    "sessionExerciseId INTEGER NOT NULL," +
                                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                                    "orderIndex INTEGER NOT NULL," +
                                    "sourceProgramSetId INTEGER," +
                                    "type TEXT NOT NULL," +
                                    "status TEXT NOT NULL," +
                                    "completionSource TEXT," +
                                    "targetReps INTEGER," +
                                    "targetRepsMax INTEGER," +
                                    "targetWeightKg REAL," +
                                    "targetDurationMillis INTEGER," +
                                    "targetDistanceMeters REAL," +
                                    "actualReps INTEGER," +
                                    "actualWeightKg REAL," +
                                    "actualDurationMillis INTEGER," +
                                    "actualDistanceMeters REAL," +
                                    "linkedWorkoutId INTEGER," +
                                    "startedAt INTEGER," +
                                    "completedAt INTEGER," +
                                    "FOREIGN KEY (sessionExerciseId) REFERENCES session_exercise(id) ON DELETE CASCADE," +
                                    "FOREIGN KEY (sourceProgramSetId) REFERENCES program_set(id) ON DELETE SET NULL," +
                                    "FOREIGN KEY (linkedWorkoutId) REFERENCES workout(id) ON DELETE SET NULL)");
                            db.execSQL("CREATE INDEX index_session_set_sessionExerciseId ON session_set (sessionExerciseId)");
                            db.execSQL("CREATE INDEX index_session_set_sourceProgramSetId ON session_set (sourceProgramSetId)");
                            db.execSQL("CREATE INDEX index_session_set_linkedWorkoutId ON session_set (linkedWorkoutId)");
                            db.execSQL("CREATE UNIQUE INDEX index_session_set_sessionExerciseId_orderIndex " +
                                    "ON session_set (sessionExerciseId, orderIndex)");

                            db.setTransactionSuccessful();
                        } finally {
                            db.endTransaction();
                        }
                    }
                }, new Migration(22, 23) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        db.execSQL("ALTER TABLE training_session " +
                                "ADD COLUMN accumulatedPausedMillis INTEGER NOT NULL DEFAULT 0");
                    }
                }, new Migration(23, 24) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS program_occurrence_summary (" +
                                "dayId INTEGER NOT NULL," +
                                "occurrenceDate INTEGER NOT NULL," +
                                "summaryWorkoutId INTEGER NOT NULL," +
                                "PRIMARY KEY (dayId, occurrenceDate)," +
                                "FOREIGN KEY (dayId) REFERENCES program_day(id) ON DELETE CASCADE," +
                                "FOREIGN KEY (summaryWorkoutId) REFERENCES workout(id) ON DELETE CASCADE)");
                        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS " +
                                "index_program_occurrence_summary_summaryWorkoutId " +
                                "ON program_occurrence_summary (summaryWorkoutId)");
                    }
                }, new Migration(24, 25) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        db.execSQL("ALTER TABLE exercise ADD COLUMN met REAL");
                        db.execSQL("ALTER TABLE exercise ADD COLUMN secondsPerRep REAL");
                    }
                }, new Migration(25, 26) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        db.execSQL("ALTER TABLE training_program ADD COLUMN icon TEXT");
                        db.execSQL("ALTER TABLE workout ADD COLUMN programUuid TEXT");
                        db.execSQL("ALTER TABLE workout ADD COLUMN programName TEXT");
                        db.execSQL("ALTER TABLE workout ADD COLUMN iconOverride TEXT");
                    }
                }, new Migration(26, 27) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        // SharedPreferences cannot be read from a Room Migration. The
                        // idempotent repository bootstrap inserts their legacy weight
                        // and step length immediately after the database opens.
                        db.execSQL("CREATE TABLE fitness_profile (" +
                                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                                "uuid TEXT NOT NULL," +
                                "name TEXT NOT NULL," +
                                "isActive INTEGER NOT NULL," +
                                "birthDateEpochDay INTEGER," +
                                "heightCm REAL," +
                                "weightKg REAL," +
                                "stepLengthCm REAL," +
                                "gender TEXT," +
                                "fitnessLevel TEXT," +
                                "notes TEXT," +
                                "customAttributesJson TEXT," +
                                "createdAt INTEGER NOT NULL," +
                                "updatedAt INTEGER NOT NULL)");
                        db.execSQL("CREATE UNIQUE INDEX index_fitness_profile_uuid " +
                                "ON fitness_profile (uuid)");
                        db.execSQL("CREATE INDEX index_fitness_profile_isActive " +
                                "ON fitness_profile (isActive)");
                    }
                }, new Migration(27, 28) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        // Nullable snapshots keep every pre-v28 row valid. New rows capture
                        // identity and measurements once, so later profile edits cannot
                        // rewrite historical calories or distance assumptions.
                        db.execSQL("ALTER TABLE training_session ADD COLUMN profileUuidSnapshot TEXT");
                        db.execSQL("ALTER TABLE training_session ADD COLUMN userWeightKgSnapshot REAL");
                        db.execSQL("ALTER TABLE training_session ADD COLUMN stepLengthMetersSnapshot REAL");
                        db.execSQL("ALTER TABLE program_completion ADD COLUMN profileUuidSnapshot TEXT");
                        db.execSQL("ALTER TABLE program_completion ADD COLUMN userWeightKgSnapshot REAL");
                        db.execSQL("ALTER TABLE workout ADD COLUMN profileUuid TEXT");
                        db.execSQL("ALTER TABLE workout ADD COLUMN userWeightKgSnapshot REAL");
                        db.execSQL("ALTER TABLE workout ADD COLUMN stepLengthMetersSnapshot REAL");
                        db.execSQL("CREATE INDEX index_workout_profileUuid ON workout (profileUuid)");
                    }
                }, new Migration(28, 29) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        db.execSQL("CREATE TABLE nutrition_plan (" +
                                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                                "uuid TEXT NOT NULL," +
                                "name TEXT NOT NULL," +
                                "description TEXT," +
                                "objective TEXT," +
                                "preferences TEXT," +
                                "restrictions TEXT," +
                                "linkedTrainingProgramUuid TEXT," +
                                "startDateEpochDay INTEGER," +
                                "createdAt INTEGER NOT NULL," +
                                "updatedAt INTEGER NOT NULL)");
                        db.execSQL("CREATE UNIQUE INDEX index_nutrition_plan_uuid " +
                                "ON nutrition_plan (uuid)");

                        db.execSQL("CREATE TABLE nutrition_day (" +
                                "planId INTEGER NOT NULL," +
                                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                                "orderIndex INTEGER NOT NULL," +
                                "name TEXT," +
                                "notes TEXT," +
                                "energyKcal REAL," +
                                "proteinGrams REAL," +
                                "fatGrams REAL," +
                                "carbsGrams REAL," +
                                "fiberGrams REAL," +
                                "waterMl REAL," +
                                "FOREIGN KEY (planId) REFERENCES nutrition_plan(id) ON DELETE CASCADE)");
                        db.execSQL("CREATE INDEX index_nutrition_day_planId " +
                                "ON nutrition_day (planId)");
                        db.execSQL("CREATE UNIQUE INDEX index_nutrition_day_planId_orderIndex " +
                                "ON nutrition_day (planId, orderIndex)");

                        db.execSQL("CREATE TABLE nutrition_meal (" +
                                "dayId INTEGER NOT NULL," +
                                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                                "orderIndex INTEGER NOT NULL," +
                                "name TEXT NOT NULL," +
                                "plannedTimeMinutes INTEGER," +
                                "notes TEXT," +
                                "FOREIGN KEY (dayId) REFERENCES nutrition_day(id) ON DELETE CASCADE)");
                        db.execSQL("CREATE INDEX index_nutrition_meal_dayId " +
                                "ON nutrition_meal (dayId)");
                        db.execSQL("CREATE UNIQUE INDEX index_nutrition_meal_dayId_orderIndex " +
                                "ON nutrition_meal (dayId, orderIndex)");

                        db.execSQL("CREATE TABLE nutrition_item (" +
                                "mealId INTEGER NOT NULL," +
                                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                                "orderIndex INTEGER NOT NULL," +
                                "name TEXT NOT NULL," +
                                "quantity REAL," +
                                "unit TEXT," +
                                "weightGrams REAL," +
                                "energyKcal REAL," +
                                "proteinGrams REAL," +
                                "fatGrams REAL," +
                                "carbsGrams REAL," +
                                "fiberGrams REAL," +
                                "notes TEXT," +
                                "FOREIGN KEY (mealId) REFERENCES nutrition_meal(id) ON DELETE CASCADE)");
                        db.execSQL("CREATE INDEX index_nutrition_item_mealId " +
                                "ON nutrition_item (mealId)");
                        db.execSQL("CREATE UNIQUE INDEX index_nutrition_item_mealId_orderIndex " +
                                "ON nutrition_item (mealId, orderIndex)");

                        // Profile UUID intentionally has no FK. Nutrition plans from a full
                        // backup remain usable even when a selected profile is unavailable.
                        db.execSQL("CREATE TABLE nutrition_plan_profile (" +
                                "planId INTEGER NOT NULL," +
                                "profileUuid TEXT NOT NULL," +
                                "PRIMARY KEY (planId, profileUuid)," +
                                "FOREIGN KEY (planId) REFERENCES nutrition_plan(id) ON DELETE CASCADE)");
                        db.execSQL("CREATE INDEX index_nutrition_plan_profile_profileUuid " +
                                "ON nutrition_plan_profile (profileUuid)");
                    }
                })
                .allowMainThreadQueries()
                .build();
    }
}
