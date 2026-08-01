/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 */
package de.tadris.fitness.ui.session

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import de.tadris.fitness.R

private enum class GuidedEndAction {
    FINISH,
    CANCEL,
}

/**
 * Stateful route boundary for the guided runner. Recorder navigation remains outside
 * the view model, while every workout transition is persisted by the repository.
 */
@Composable
fun GuidedWorkoutRoute(
    sessionId: Long,
    pauseOnOpen: Boolean = false,
    onBack: () -> Unit,
    onStartRecorder: (GuidedTrackerRequest) -> Unit,
    onFinished: () -> Unit = onBack,
) {
    val vm: GuidedWorkoutViewModel = viewModel(key = "guided-workout-$sessionId")
    val state by vm.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = LocalContext.current.findActivity()
    var pendingEndAction by remember { mutableStateOf<GuidedEndAction?>(null) }
    var exitInProgress by remember { mutableStateOf(false) }
    var navigatingToRecorder by remember { mutableStateOf(false) }

    LaunchedEffect(sessionId, pauseOnOpen) {
        vm.initialize(sessionId, pauseOnOpen)
    }

    // Going home, locking the screen or the OS stopping this route freezes the
    // deadline in Room. Recorder navigation is the one exception: that child
    // route owns the active set until it returns a result.
    DisposableEffect(lifecycleOwner, vm) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> navigatingToRecorder = false
                Lifecycle.Event.ON_STOP -> if (
                    GuidedSessionPersistencePolicy.shouldPauseForLifecycleStop(
                        navigatingToRecorder = navigatingToRecorder,
                        isChangingConfigurations = activity?.isChangingConfigurations == true,
                    )
                ) {
                    vm.pauseForBackground()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun pauseAndLeave() {
        if (exitInProgress) return
        exitInProgress = true
        vm.pauseAndExit(onBack)
    }

    fun requestBack() {
        if (state?.phase == GuidedWorkoutPhase.COMPLETE) {
            onBack()
        } else {
            pauseAndLeave()
        }
    }

    BackHandler(enabled = !exitInProgress, onBack = ::requestBack)

    val current = state
    if (current == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        GuidedWorkoutContent(
            state = current,
            onEvent = { event ->
                when (event) {
                    GuidedWorkoutEvent.Back,
                    GuidedWorkoutEvent.RequestExit -> requestBack()

                    GuidedWorkoutEvent.StartRecorder -> vm.trackerRequest()?.let { request ->
                        navigatingToRecorder = true
                        onStartRecorder(request)
                    }
                    GuidedWorkoutEvent.RequestFinishSession -> {
                        pendingEndAction = GuidedEndAction.FINISH
                    }
                    GuidedWorkoutEvent.RequestCancelSession -> {
                        pendingEndAction = GuidedEndAction.CANCEL
                    }
                    GuidedWorkoutEvent.FinishWorkout -> onFinished()
                    else -> vm.dispatch(event)
                }
            },
        )
    }

    pendingEndAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingEndAction = null },
            title = {
                Text(
                    stringResource(
                        if (action == GuidedEndAction.FINISH) {
                            R.string.guided_finish_early_title
                        } else {
                            R.string.guided_cancel_session_title
                        }
                    )
                )
            },
            text = {
                Text(
                    stringResource(
                        if (action == GuidedEndAction.FINISH) {
                            R.string.guided_finish_early_message
                        } else {
                            R.string.guided_cancel_session_message
                        }
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingEndAction = null
                        exitInProgress = true
                        if (action == GuidedEndAction.FINISH) {
                            vm.finishAndExit(onFinished)
                        } else {
                            vm.cancelAndExit(onBack)
                        }
                    },
                ) {
                    Text(
                        stringResource(
                            if (action == GuidedEndAction.FINISH) {
                                R.string.guided_finish_workout
                            } else {
                                R.string.guided_cancel_session
                            }
                        )
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingEndAction = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        val base = current.baseContext
        if (base === current) return null
        current = base
    }
    return current as? Activity
}
