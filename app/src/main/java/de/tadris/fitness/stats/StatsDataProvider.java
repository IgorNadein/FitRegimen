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

package de.tadris.fitness.stats;

import android.content.Context;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.tadris.fitness.Instance;
import de.tadris.fitness.data.Workout;
import de.tadris.fitness.data.WorkoutType;

public class StatsDataProvider {

    private final Context context;

    public StatsDataProvider(Context context) {
        this.context = context;
    }

    public StatsDataTypes.DataPoint getFirstData(WorkoutProperty requestedProperty, List<WorkoutType> workoutTypes) throws NoDataException {
        ArrayList<StatsDataTypes.DataPoint> points = getData(requestedProperty, workoutTypes);
        if (points.isEmpty())
            throw new NoDataException();
        return points.get(0);
    }

    public StatsDataTypes.DataPoint getLastData(WorkoutProperty requestedProperty, List<WorkoutType> workoutTypes) throws NoDataException {
        ArrayList<StatsDataTypes.DataPoint> points = getData(requestedProperty, workoutTypes);
        if (points.isEmpty())
            throw new NoDataException();
        return points.get(points.size() - 1);
    }

    public ArrayList<StatsDataTypes.DataPoint> getData(WorkoutProperty requestedProperty, List<WorkoutType> workoutTypes) {
        return getData(requestedProperty, workoutTypes, null);
    }

    public ArrayList<StatsDataTypes.DataPoint> getData(WorkoutProperty requestedProperty, List<WorkoutType> workoutTypes, @Nullable StatsDataTypes.TimeSpan timeSpan) {
        ArrayList<StatsDataTypes.DataPoint> data = new ArrayList<>();
        Map<String, WorkoutType> programDisplayTypes = new HashMap<>();
        List<Workout> workouts = Instance.getInstance(context).db.workoutDao().getVisibleWorkouts();
        for (Workout workout : workouts) {
            if ((timeSpan == null) || timeSpan.contains(workout.getStart())) {
                WorkoutType type = workout.getWorkoutType(context);
                if (workoutTypes.contains(type)) { // in separate if cause getWorkoutType is sometimes costly
                    // check if property can provide data for workout
                    try {
                        double val = getPropertyValue(requestedProperty, workout);
                        WorkoutType displayType = type;
                        String programUuid = workout.getProgramUuid();
                        if (programUuid != null && !programUuid.trim().isEmpty()) {
                            displayType = programDisplayTypes.get(programUuid);
                            if (displayType == null) {
                                displayType = workout.getDisplayWorkoutType(context);
                                programDisplayTypes.put(programUuid, displayType);
                            }
                        }
                        data.add(new StatsDataTypes.DataPoint(displayType, workout.getId(), workout.getStart(), val));
                    } catch (Exception e) {
                        // This should never happen, cause it is checked by the if clauses above
                        e.printStackTrace();
                    }
                }
            }
        }

        Collections.sort(data, StatsDataTypes.DataPoint.timeComparator);
        return data;
    }

    private double getPropertyValue(WorkoutProperty property, Workout workout) throws Exception {
        switch (property.getType()) {
            case BASE:
                return getBasePropertyValue(property, workout);
            case GPS:
                return getGPSPropertyValue(property, workout);
            case INDOOR:
                return getIndoorPropertyValue(property, workout);
            default:
                throw new IllegalArgumentException("Property type unknown");
        }
    }

    private double getBasePropertyValue(WorkoutProperty property, Workout workout) throws Exception {
        switch (property) {
            case NUMBER:
                return 1;
            case START:
                return workout.getStart();
            case END:
                return workout.getEnd();
            case DURATION:
                return workout.getActiveDuration();
            case PAUSE_DURATION:
                return workout.getPauseDuration();
            case CALORIE:
                return workout.getKcal();
            default:
                throw new Exception("Property is no BaseProperty");
        }
    }

    private double getGPSPropertyValue(WorkoutProperty property, Workout workout) throws Exception {
        switch (property) {
            case LENGTH:
                return workout.getDistance();
            case AVG_SPEED:
                return workout.getAverageSpeedActive();
            case AVG_PACE:
                return workout.getAveragePaceActive();
            default:
                throw new Exception("Property is no GPS Property");
        }
    }

    private double getIndoorPropertyValue(WorkoutProperty property, Workout workout) throws Exception {
        switch (property) {
            case AVG_FREQUENCY:
                return workout.getAverageFrequency();
            case REPETITIONS:
                return workout.getRepetitions();
            default:
                throw new Exception("Property is no Indoor Property");
        }
    }
}
