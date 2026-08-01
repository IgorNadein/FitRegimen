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

package de.tadris.fitness.ui.dashboard

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.tadris.fitness.Instance
import de.tadris.fitness.R
import de.tadris.fitness.data.Workout
import de.tadris.fitness.data.WorkoutType
import de.tadris.fitness.data.WorkoutTypeManager
import de.tadris.fitness.ui.theme.FitoTrackTheme
import de.tadris.fitness.ui.theme.spacing
import de.tadris.fitness.util.Icon
import java.util.Date

@Composable
internal fun WorkoutListItem(
    workout: Workout,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val instance = Instance.getInstance(context)
    val workoutType = workout.getWorkoutType(context)
    val displayWorkoutType = workout.getDisplayWorkoutType(context)
    val displayComment = workout.comment
        ?.removePrefix("${workout.programName} · ")
        ?.takeUnless { it == workout.programName }

    val dateText = instance.userDateTimeUtils.formatDateTime(Date(workout.start))
    val distanceText = when {
        workout.workoutTypeId == WorkoutTypeManager.WORKOUT_TYPE_ID_PROGRAM_TRAINING &&
            workout.distance <= 0.0 && workout.repetitions <= 0 ->
            LocalResources.current.getString(R.string.guided_history_completed)

        workout.distance == 0.0 && workout.repetitions > 0 -> {
            val reps = workout.repetitions
            "$reps ${LocalResources.current.getQuantityString(workoutType.repeatingExerciseName, reps)}"
        }

        else -> instance.distanceUnitUtils.getDistance(workout.distance.toInt())
    }
    val durationText = instance.distanceUnitUtils.getHourMinuteTime(workout.activeDuration)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardDefaults.shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            modifier = Modifier.padding(MaterialTheme.spacing.defaultFull),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WorkoutListIcon(displayWorkoutType)
            Spacer(modifier = Modifier.width(MaterialTheme.spacing.defaultFull))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayWorkoutType.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val comment = displayComment
                    if (comment != null) {
                        Spacer(Modifier.padding(MaterialTheme.spacing.defaultFull))
                        Text(
                            text = comment,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Right,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(MaterialTheme.spacing.defaultHalf))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = distanceText,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = durationText,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkoutListIcon(workoutType: WorkoutType) {
    Surface(
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        color = Color(workoutType.color),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(Icon.getIcon(workoutType.icon)),
                contentDescription = workoutType.title,
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
@Preview
private fun WorkoutListItemPreview() {
    val workout = Workout(
        id = 0,
        uuid = "",
        start = System.currentTimeMillis() - 3_600_000,
        end = System.currentTimeMillis(),
        activeDuration = 3_600_000,
        comment = "Morning run\nSecond line Morning run Morning run Morning run ",
        distance = 10_000.0,
        repetitions = 0,
        energy = 1_500_000.0,
        workoutTypeId = "running",
        intervalSetUsedId = -1,
    )
    FitoTrackTheme {
        WorkoutListItem(workout, onClick = {}, onLongClick = {})
    }
}
