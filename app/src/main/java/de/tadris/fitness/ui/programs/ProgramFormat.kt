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
 *
 * This file was added in FitRegimen, a fork of FitoTrack.
 */
package de.tadris.fitness.ui.programs

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import de.tadris.fitness.R
import de.tadris.fitness.data.MetricKind
import de.tadris.fitness.data.ProgramSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun kindLabel(kind: MetricKind): String = stringResource(
    when (kind) {
        MetricKind.WEIGHT_REPS -> R.string.kindWeightReps
        MetricKind.REPS -> R.string.kindReps
        MetricKind.DURATION -> R.string.kindDuration
        MetricKind.WEIGHT_DURATION -> R.string.kindWeightDuration
        MetricKind.DISTANCE_DURATION -> R.string.kindDistanceDuration
    }
)

@Composable
fun kindExample(kind: MetricKind): String = stringResource(
    when (kind) {
        MetricKind.WEIGHT_REPS -> R.string.kindWeightRepsExample
        MetricKind.REPS -> R.string.kindRepsExample
        MetricKind.DURATION -> R.string.kindDurationExample
        MetricKind.WEIGHT_DURATION -> R.string.kindWeightDurationExample
        MetricKind.DISTANCE_DURATION -> R.string.kindDistanceDurationExample
    }
)

fun formatDecimal(value: Double): String =
    if (value == Math.floor(value)) value.toInt().toString() else "%.1f".format(value)

fun formatMinutes(durationMillis: Long): String = formatDecimal(durationMillis / 60_000.0)

/**
 * Compact human summary of planned sets, masked by the exercise kind:
 * "3 × 8–12 · 60 kg", "12/10/8", "3 × 1.5 min", "5 km · 30 min".
 */
@Composable
fun setsSummary(sets: List<ProgramSet>, kind: MetricKind): String {
    if (sets.isEmpty()) return ""
    val parts = mutableListOf<String>()
    if (kind.usesReps) {
        val repTexts = sets.map { set ->
            (set.reps?.toString() ?: "?") + (set.repsMax?.let { "–$it" } ?: "")
        }
        parts += if (repTexts.distinct().size == 1) {
            "${sets.size} × ${repTexts.first()}"
        } else {
            repTexts.joinToString("/")
        }
    }
    if (kind.usesWeight) {
        val weightTexts = sets.mapNotNull { it.weightKg }.map { formatDecimal(it) }
        if (weightTexts.isNotEmpty()) {
            val joined = if (weightTexts.distinct().size == 1) weightTexts.first() else weightTexts.joinToString("/")
            parts += "$joined " + stringResource(R.string.weightKgLabel)
        }
    }
    if (kind.usesDistance) {
        val distanceTexts = sets.mapNotNull { it.distanceMeters }.map { formatDecimal(it / 1000.0) }
        if (distanceTexts.isNotEmpty()) {
            val joined = if (distanceTexts.distinct().size == 1) distanceTexts.first() else distanceTexts.joinToString("/")
            parts += "$joined " + stringResource(R.string.kilometersLabel)
        }
    }
    if (kind.usesDuration) {
        val durationTexts = sets.mapNotNull { it.durationMillis }.map { formatMinutes(it) }
        if (durationTexts.isNotEmpty()) {
            val prefix = if (!kind.usesReps && !kind.usesDistance && sets.size > 1) "${sets.size} × " else ""
            val joined = if (durationTexts.distinct().size == 1) durationTexts.first() else durationTexts.joinToString("/")
            parts += "$prefix$joined " + stringResource(R.string.minutesLabel)
        }
    }
    return parts.joinToString(" · ")
}

/** Resolves only a relative path contained by app-private storage. */
fun exerciseImageFile(context: Context, imagePath: String): File? = runCatching {
    if (File(imagePath).isAbsolute) return@runCatching null
    val root = context.filesDir.canonicalFile
    val candidate = File(root, imagePath).canonicalFile
    candidate.takeIf { it.path.startsWith(root.path + File.separator) }
}.getOrNull()

/**
 * Displays the original private exercise media file. On API 28+ ImageDecoder keeps
 * animated GIF/WebP files animated; older devices gracefully show a static bitmap.
 * The binary is never loaded into Room and a missing/corrupt file simply renders
 * nothing, allowing instructions to remain the primary fallback.
 */
@Composable
fun ExerciseMedia(
    imagePath: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val context = LocalContext.current
    val drawable = produceState<Drawable?>(initialValue = null, imagePath) {
        value = withContext(Dispatchers.IO) {
            val file = exerciseImageFile(context, imagePath)
            if (file?.isFile != true) return@withContext null
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeDrawable(ImageDecoder.createSource(file))
                } else {
                    @Suppress("DEPRECATION")
                    BitmapDrawable(context.resources, file.absolutePath)
                }
            }.getOrNull()
        }
    }.value

    AndroidView(
        factory = { viewContext ->
            ImageView(viewContext).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                adjustViewBounds = true
            }
        },
        update = { view ->
            view.contentDescription = contentDescription
            if (view.drawable !== drawable) view.setImageDrawable(drawable)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                startAnimatedDrawable(drawable)
            }
        },
        modifier = modifier,
    )

    DisposableEffect(drawable) {
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                stopAnimatedDrawable(drawable)
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.P)
private fun startAnimatedDrawable(drawable: Drawable?) {
    (drawable as? AnimatedImageDrawable)?.start()
}

@RequiresApi(Build.VERSION_CODES.P)
private fun stopAnimatedDrawable(drawable: Drawable?) {
    (drawable as? AnimatedImageDrawable)?.stop()
}

/** Decodes a downsampled bitmap for an exercise photo off the main thread. */
@Composable
fun rememberExerciseImage(context: Context, imagePath: String?): State<ImageBitmap?> =
    produceState<ImageBitmap?>(initialValue = null, imagePath) {
        value = if (imagePath == null) null else withContext(Dispatchers.IO) {
            val file = exerciseImageFile(context, imagePath)
            if (file?.isFile != true) return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val maxSide = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
            val options = BitmapFactory.Options().apply {
                inSampleSize = Integer.highestOneBit((maxSide / 1024).coerceAtLeast(1))
            }
            BitmapFactory.decodeFile(file.absolutePath, options)?.asImageBitmap()
        }
    }
