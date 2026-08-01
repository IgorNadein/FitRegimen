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
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(version = 20, entities = {
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
        ProgramTask.class,
        ProgramCompletion.class,
}, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "fitregimen";

    public abstract WorkoutDao workoutDao();

    public abstract WorkoutTypeDao workoutTypeDao();

    public abstract IntervalDao intervalDao();

    public abstract ExportTargetDao exportTargetDao();

    public abstract TrainingProgramDao trainingProgramDao();

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
            db.execSQL("DELETE FROM program_completion");
            db.execSQL("DELETE FROM program_task");
            db.execSQL("DELETE FROM training_program");
            db.execSQL("DELETE FROM workout");
        });
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
                })
                .allowMainThreadQueries()
                .build();
    }
}