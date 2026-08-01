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

package de.tadris.fitness.util.unit;

import android.content.Context;

import de.tadris.fitness.R;
import de.tadris.fitness.data.preferences.UserPreferences;

public class DistanceUnitUtils extends UnitUtils {

    private DistanceUnitSystem distanceUnitSystem;
    private final UserPreferences userPreferences;

    public DistanceUnitUtils(Context context) {
        this(context, new UserPreferences(context.getApplicationContext()));
    }

    public DistanceUnitUtils(Context context, UserPreferences userPreferences) {
        super(context);
        this.userPreferences = userPreferences;
        setUnit();
    }

    public void setUnit() {
        String id = userPreferences.getDistanceUnitSystemId();
        distanceUnitSystem = getUnitById(Integer.parseInt(id));
    }

    private DistanceUnitSystem getUnitById(int id) {
        switch (id) {
            default:
            case 1:
                return new Metric();
            case 2:
                return new MetricPhysical();
            case 3:
                return new Imperial();
            case 4:
                return new ImperialWithMeters();
            case 5:
                return new USCustomary();
        }
    }

    public String getHourMinuteTime(long time) {
        return getHourMinuteTime(time, false);
    }

    public String getHourMinuteTime(long time, boolean useLongUnits) {
        long seks = time / 1000;
        long mins = seks / 60;
        int hours = (int) mins / 60;
        int remainingMinutes = (int) mins % 60;

        if (hours > 0) {
            if (useLongUnits) {
                String hourText = hours == 1 ? getString(R.string.timeHourSingular) : getString(R.string.timeHourPlural);
                return hours + " " + hourText + " " + getMinuteText(remainingMinutes);
            } else {
                return hours + " h " + remainingMinutes + " m";
            }
        } else {
            if (useLongUnits) {
                return getMinuteText(remainingMinutes);
            } else {
                return remainingMinutes + " min";
            }
        }
    }

    public String getHourMinuteSecondTime(long time) {
        long totalSecs = time / 1000;
        long totalMins = totalSecs / 60;
        long hours = totalMins / 60;
        long mins = totalMins % 60;
        long secs = totalSecs % 60;
        String minStr = (mins < 10 ? "0" : "") + mins;
        String sekStr = (secs < 10 ? "0" : "") + secs;
        return hours + ":" + minStr + ":" + sekStr;
    }

    public String getMinuteSecondTime(long time) {
        long totalSecs = time / 1000;
        long totalMins = totalSecs / 60;
        long mins = totalMins % 60;
        long secs = totalSecs % 60;
        String minStr = (mins < 10 ? "0" : "") + mins;
        String sekStr = (secs < 10 ? "0" : "") + secs;
        return minStr + ":" + sekStr;
    }

    public String getMinuteSecondTime(long time, boolean useLongTime) {
        if (!useLongTime) {
            return getMinuteSecondTime(time);
        }
        long totalSecs = time / 1000;
        long totalMins = totalSecs / 60;
        long mins = totalMins % 60;
        long secs = totalSecs % 60;
        String minStr = "" + mins + " " + context.getText(R.string.timeMinuteShort);
        String sekStr = "" + secs + " " + context.getText(R.string.timeSecondsShort);
        if (secs > 0) {
            return minStr + " " + sekStr;
        } else {
            return minStr;
        }
    }

    public String getPace(double metricPace) {
        return getPace(metricPace, false);
    }

    public String getPace(double metricPace, boolean useLongUnits) {
        return getPace(metricPace, useLongUnits, true);
    }

    public String getPace(double metricPace, boolean useLongUnits, boolean appendUnit) {
        if (!Double.isInfinite(metricPace)) {
            double one = distanceUnitSystem.getDistanceFromKilometers(1);
            double secondsTotal = 60 * metricPace / one;
            int minutes = (int) secondsTotal / 60;
            int seconds = (int) secondsTotal % 60;
            if (useLongUnits) {
                return getMinuteText(minutes) + " " + getString(R.string.and) +
                        " " + getSecondsText(seconds) + " " + getString(R.string.per) +
                        " " + getString(distanceUnitSystem.getLongDistanceUnitTitle(false));
            } else {
                String withoutUnit = minutes + ":" + (seconds < 10 ? "0" : "") + seconds;
                if (appendUnit) {
                    return withoutUnit + " " + getPaceUnit();
                } else {
                    return withoutUnit;
                }
            }
        } else {
            return "-";
        }
    }

    public String getPaceUnit() {
        return "min/" + distanceUnitSystem.getLongDistanceUnit();
    }
    public String getSpeedUnit(){return distanceUnitSystem.getSpeedUnit();}

    public String getDistance(int distanceInMeters) {
        return getDistance(distanceInMeters, false);
    }

    public String getDistance(int distanceInMeters, boolean useLongUnitNames) {
        if (distanceInMeters >= 1000) {
            String lengthInLongUnit = round(distanceUnitSystem.getDistanceFromKilometers((double) distanceInMeters / 1000d), 2);
            if (useLongUnitNames) {
                return lengthInLongUnit + " " + getString(distanceUnitSystem.getLongDistanceUnitTitle(distanceInMeters> 1000 ));
            } else {
                return lengthInLongUnit + " " + distanceUnitSystem.getLongDistanceUnit();
            }
        } else {
            int value = (int) distanceUnitSystem.getDistanceFromMeters(distanceInMeters);
            if (useLongUnitNames) {
                return value + " " + getString(distanceUnitSystem.getShortDistanceUnitTitle(value != 1));
            } else {
                return value + " " + distanceUnitSystem.getShortDistanceUnit();
            }
        }
    }

    public String getDistanceWithoutUnit(int distanceInMeters, boolean useLongUnit, int precision) {
        if (useLongUnit) {
            return round(distanceUnitSystem.getDistanceFromKilometers((double) distanceInMeters / 1000d), precision);
        } else {
            return round(distanceUnitSystem.getDistanceFromMeters(distanceInMeters),0);
        }
    }

    public String getElevation(int elevationInMeters) {
        return getElevation(elevationInMeters, false);
    }

    public String getElevation(int elevationInMeters, boolean useLongUnitNames) {
        int value = (int) distanceUnitSystem.getElevationFromMeters(elevationInMeters);
        if (useLongUnitNames) {
            return value + " " + getString(distanceUnitSystem.getElevationUnitTitle(value != 1));
        } else {
            return value + " " + distanceUnitSystem.getElevationUnit();
        }
    }

    public String getSpeed(double speed) {
        return getSpeed(speed, false);
    }

    /**
     * @param speed speed in m/s
     * @return speed in km/h
     */
    public String getSpeed(double speed, boolean useLongNames) {
        String value = getSpeedString(speed);
        if (useLongNames) {
            return value + " " + getString(distanceUnitSystem.getSpeedUnitTitle());
        } else {
            return value + " " + distanceUnitSystem.getSpeedUnit();
        }
    }

    public double getSpeedWithoutUnit(double speed) {
        return distanceUnitSystem.getSpeedFromMeterPerSecond(speed);
    }

    public String getSpeedString(double speed) {
        return round(getSpeedWithoutUnit(speed), 2);
    }

    private String getHourText(int hours) {
        return hours + " " + (hours == 1 ? getString(R.string.timeHourSingular) : getString(R.string.timeHourPlural));
    }

    private String getMinuteText(int minutes) {
        return minutes + " " + (minutes == 1 ? getString(R.string.timeMinuteSingular) : getString(R.string.timeMinutePlural));
    }

    private String getSecondsText(int seconds) {
        return seconds + " " + (seconds == 1 ? getString(R.string.timeSecondsSingular) : getString(R.string.timeSecondsPlural));
    }

    public DistanceUnitSystem getDistanceUnitSystem() {
        return distanceUnitSystem;
    }
}
