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

package de.tadris.fitness.data.preferences

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import androidx.collection.ArraySet
import androidx.core.content.edit
import de.tadris.fitness.BuildConfig
import de.tadris.fitness.data.RecordingType
import de.tadris.fitness.data.WorkoutType
import de.tadris.fitness.data.WorkoutTypeManager
import de.tadris.fitness.stats.aggregation.AggregationSpan
import java.util.Calendar

class UserPreferences(private val context: Context) : PreferenceProvider {

    override val preferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    @JvmField
    val recordingScreenInformationPreferences: RecordingScreenInformationPreferences =
        RecordingScreenInformationPreferences(this.preferences)
    val sharingScreenInformationPreferences: SharingScreenInformationPreferences =
        SharingScreenInformationPreferences(this.preferences)

    val userWeight: Int
        get() = preferences.getInt(USER_WEIGHT, DEFAULT_USER_WEIGHT_KG)

    val spokenUpdateTimePeriod: Int
        get() = preferences.getInt(VOICE_ANNOUNCEMENTS_INTERVAL_TIME, 0)

    val spokenUpdateDistancePeriod: Float
        get() = preferences.getFloat(VOICE_ANNOUNCEMENTS_INTERVAL_DISTANCE, 0f)

    var mapStyle by StringSharedPreference("mapStyle", "osm.mapnik")

    var trackStyle by StringSharedPreference("trackStyle", "purple_rain")

    var trackStyleMode by StringSharedPreference("trackStyleUsage", STYLE_USAGE_DIAGRAM)

    var intervalsIncludePauses by BooleanSharedPreference("intervalsIncludePause", true)

    @Deprecated("")
    fun getIdOfDisplayedInformation(mode: String?, slot: Int): String {
        var defValue = ""
        if (RecordingType.INDOOR.id == mode) {
            when (slot) {
                0 -> defValue = "avg_frequency"
                1 -> defValue = "energy_burned"
                2 -> defValue = "current_intensity"
                3 -> defValue = "pause_duration"
            }
        } else {
            when (slot) {
                0 -> defValue = "distance"
                1 -> defValue = "energy_burned"
                2 -> defValue = "avgSpeedMotion"
                3 -> defValue = "pause_duration"
            }
        }
        return preferences.getString("information_display_" + mode + "_" + slot, defValue)!!
    }

    fun setIdOfDisplayedInformation(mode: String?, slot: Int, id: String?) {
        preferences.edit { putString("information_display_" + mode + "_" + slot, id) }
    }

    var dateFormatSetting by StringSharedPreference("dateFormat", "system")

    var timeFormatSetting by StringSharedPreference("timeFormat", "system")

    val firstDayOfWeek: Int
        get() {
            if (preferences.getString(FIRST_DAY_OF_WEEK_VARIABLE, "system") != "system") {
                try {
                    return preferences.getString(FIRST_DAY_OF_WEEK_VARIABLE, "0")!!.toInt()
                } catch (nfe: NumberFormatException) {
                }
            }
            return Calendar.getInstance().firstDayOfWeek
        }

    var distanceUnitSystemId by StringSharedPreference("unitSystem", "1")

    var energyUnit by StringSharedPreference("energyUnit", "kcal")

    var themeSetting by StringSharedPreference("themeSetting", "0")

    var useSystemColors by BooleanSharedPreference("useSystemColors", false)

    var showOnLockScreen by BooleanSharedPreference("showOnLockScreen", false)

    var zoomWithVolumeButtons by BooleanSharedPreference("zoomWithVolumeButtons", true)

    var askForComment by BooleanSharedPreference(ASK_FOR_COMMENT, DEFAULT_ASK_FOR_COMMENT)

    val autoBackupIntervalHours: Int
        get() = preferences.getString("autoBackupInterval", "168")!!.toInt()

    val offlineMapDirectoryName by NullableStringSharedPreference("offlineMapDirectoryName")

    var firstStart by BooleanSharedPreference("firstStart", true)

    /** Version of the derived program-workout projection applied to existing data. */
    var programWorkoutProjectionVersion by IntSharedPreference("programWorkoutProjectionVersion", 0)

    var autoTimeout by IntSharedPreference("autoTimeoutPeriod", 20)

    var useAutoPause by BooleanSharedPreference("autoPause", true)

    var useAverageForCurrentSpeed
            by BooleanSharedPreference("useAverageForCurrentSpeed", false)

    var timeForCurrentSpeed
            by IntSharedPreference("timeForCurrentSpeed", 15)

    fun hasLowerTargetSpeedLimit() =
        preferences.getBoolean("hasLowerTargetSpeedLimit", false)

    fun setHasLowerTargetSpeedLimit(hasLowerSpeedLimit: Boolean) {
        preferences.edit { putBoolean("hasLowerTargetSpeedLimit", hasLowerSpeedLimit) }
    }

    var lowerTargetSpeedLimit by FloatSharedPreference("lowerTargetSpeedLimit", 2f)

    fun hasUpperTargetSpeedLimit() =
        preferences.getBoolean("hasUpperTargetSpeedLimit", false)

    fun setHasUpperTargetSpeedLimit(hasUpperSpeedLimit: Boolean) {
        preferences.edit { putBoolean("hasUpperTargetSpeedLimit", hasUpperSpeedLimit) }
    }

    var upperTargetSpeedLimit by FloatSharedPreference("upperTargetSpeedLimit", 3f)

    var stepLength by FloatSharedPreference(STEP_LENGTH, DEFAULT_STEP_LENGTH_METERS)

    /**
     * Compatibility bridge for code that still reads measurements synchronously.
     * [de.tadris.fitness.data.FitnessProfileRepository] is the sole profile
     * mutation boundary and mirrors its active profile through this method.
     */
    fun setLegacyMeasurements(weightKg: Int, stepLengthMeters: Float) {
        preferences.edit {
            putInt(USER_WEIGHT, weightKg)
            putFloat(STEP_LENGTH, stepLengthMeters)
        }
    }

    val lastVersionCode: Int
        get() = preferences.getInt("lastVersion", BuildConfig.VERSION_CODE)

    fun updateLastVersionCode() {
        preferences.edit { putInt("lastVersion", BuildConfig.VERSION_CODE) }
    }

    var statisticsAggregationSpan: AggregationSpan?
        get() = AggregationSpan.fromInt(
            preferences.getInt(STATISTICS_AGGREGATION_SPAN, AggregationSpan.MONTH.toInt())
        )
        set(span) {
            preferences.edit { putInt(STATISTICS_AGGREGATION_SPAN, span!!.toInt()) }
        }

    val statisticsSelectedTypes: MutableList<WorkoutType?>
        get() {
            val typeIDs: MutableSet<String?> = preferences.getStringSet(
                STATISTICS_SELECTED_TYPES,
                ArraySet()
            )!!
            return typeIDs.mapTo(ArrayList()) {
                WorkoutTypeManager.getInstance().getWorkoutTypeById(context, it)
            }
        }

    fun setStatisticsSelectedTypes(types: MutableList<WorkoutType>) {
        val typeIDs: MutableSet<String?> = ArraySet()
        for (type in types) {
            typeIDs.add(type.id)
        }
        preferences.edit { putStringSet(STATISTICS_SELECTED_TYPES, typeIDs) }
    }

    val measurements: UserMeasurements
        get() = UserMeasurements(userWeight, stepLength)

    companion object {
        const val USER_WEIGHT: String = "weight"
        const val DEFAULT_USER_WEIGHT_KG: Int = 80
        const val DEFAULT_STEP_LENGTH_METERS: Float = 0.79f
        private const val FIRST_DAY_OF_WEEK_VARIABLE = "firstDayOfWeek"
        const val STEP_LENGTH: String = "stepLength"
        const val STATISTICS_AGGREGATION_SPAN: String = "statisticsAggregationSpan"
        const val STATISTICS_SELECTED_TYPES: String = "statisticsSelectedTypes"
        const val VOICE_ANNOUNCEMENTS_INTERVAL_TIME: String = "spokenUpdateTimePeriod"
        const val VOICE_ANNOUNCEMENTS_INTERVAL_DISTANCE: String = "spokenUpdateDistancePeriod2"
        const val ASK_FOR_COMMENT: String = "askForComment"
        const val DEFAULT_ASK_FOR_COMMENT: Boolean = true

        const val STYLE_USAGE_ALWAYS: String = "always"
        const val STYLE_USAGE_DIAGRAM: String = "diagram"
        const val STYLE_USAGE_NEVER: String = "never"
    }
}
