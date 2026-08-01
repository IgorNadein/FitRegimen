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

package de.tadris.fitness.ui.workout.show

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import de.tadris.fitness.Instance
import de.tadris.fitness.R
import de.tadris.fitness.data.WorkoutData
import de.tadris.fitness.stats.StatsProvider
import de.tadris.fitness.ui.charts.ChartStyles
import de.tadris.fitness.ui.charts.formatter.SpeedFormatter
import de.tadris.fitness.ui.charts.formatter.TimeFormatter
import de.tadris.fitness.ui.charts.marker.DisplayValueMarker
import de.tadris.fitness.ui.sections.SectionListModel
import java.util.concurrent.TimeUnit

@Composable
internal fun SpeedHistogram(
    data: WorkoutData,
    modifier: Modifier = Modifier,
) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    AndroidView(
        factory = { ctx ->
            val chart = BarChart(ctx)
            val instance = Instance.getInstance(ctx)
            val distanceUnitUtils = instance.distanceUnitUtils

            val sectionListModel = SectionListModel(data)
            sectionListModel.setCriterion(SectionListModel.SectionCriterion.TIME)
            sectionListModel.sectionLength = 9213.0
            val sections = sectionListModel.getSectionList()

            if (sections.isNotEmpty()) {
                val values = sections.map { 1.0 / it.pace }
                val weights = sections.map { it.getTime(true) }

                val dataSet =
                    StatsProvider(ctx).createWeightedHistogramData(values, weights, 15, "")
                dataSet.color = primaryColor
                val barData = BarData(dataSet)
                chart.data = barData

                val timeFormatter = TimeFormatter(TimeUnit.MILLISECONDS)
                val speedFormatter = SpeedFormatter(distanceUnitUtils)
                ChartStyles.defaultHistogram(chart, ctx, speedFormatter, timeFormatter)
                ChartStyles.setXAxisLabel(chart, distanceUnitUtils.speedUnit, ctx)
                ChartStyles.setYAxisLabel(chart, ctx.getString(R.string.timeMinuteShort), ctx)

                // Override text colors with M3 theme colors
                chart.xAxis.textColor = textColor
                chart.axisLeft.textColor = textColor
                chart.axisRight.textColor = textColor
                chart.description.textColor = textColor
                chart.legend.textColor = textColor

                chart.marker = DisplayValueMarker(
                    ctx,
                    chart.axisLeft.valueFormatter,
                    chart.legend.entries[0].label,
                    barData
                )
                chart.invalidate()
            }
            chart
        },
        update = { chart ->
            chart.data?.dataSets?.forEach { dataSet ->
                (dataSet as? BarDataSet)?.color = primaryColor
            }
            chart.xAxis.textColor = textColor
            chart.axisLeft.textColor = textColor
            chart.axisRight.textColor = textColor
            chart.description.textColor = textColor
            chart.legend.textColor = textColor
            chart.invalidate()
        },
        modifier = modifier,
    )
}
