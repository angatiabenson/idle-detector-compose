package ke.co.banit.idle_detector_compose

import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/*
 * ------------------------------------------------------------------------
 * Project: idle-detector-app
 * File Created by: Angatia Benson on Thu, Feb 13, 2025
 * ------------------------------------------------------------------------
 * © 2025 Angatia Benson. All rights reserved.
 * ------------------------------------------------------------------------
 */

internal class IdleDetector(
    private val context: android.content.Context,
    private val lifecycleOwner: LifecycleOwner,
    private val idleTimeout: Duration,
    private val checkInterval: Duration = 1.seconds,
    private val onIdleWithOrigin: (Boolean) -> Unit,
) {
    val TAG = "IdleDetector"
    private val workManager = WorkManager.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var _lastInteractionTimestamp by mutableLongStateOf(0L)
    val lastInteractionTimestamp: Long
        get() = _lastInteractionTimestamp

    private var _isIdle by mutableStateOf(false)
    val isIdle: State<Boolean>
        get() = derivedStateOf { _isIdle }

    private var _onIdleCalled by mutableStateOf(false)
    private var pollingTimerJob: Job? = null

    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_CREATE -> handleCreate()
            Lifecycle.Event.ON_RESUME -> handleResume()
            Lifecycle.Event.ON_PAUSE -> handlePause()
            Lifecycle.Event.ON_DESTROY -> cleanUp()
            else -> {}
        }
    }

    init {
        Log.d(TAG, "IdleDetector initialized")
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
    }

    private fun handleCreate() {
        Log.d(TAG, "Resetting IdleDetector state")
        IdlePersistence.reset(context)
        registerInteraction()
        _isIdle = false
        _onIdleCalled = false
    }

    fun registerInteraction() {
        Log.d(TAG, "Registering user interaction")
        val now = System.currentTimeMillis()
        _lastInteractionTimestamp = now
        IdlePersistence.recordInteraction(context, now)
        _isIdle = false

        if (_onIdleCalled) {
            Log.d(TAG, "Resetting onIdleCalled state due to new interaction")
            _onIdleCalled = false
        }

        cancelBackgroundTimeoutWorker()
    }

    fun checkIdleState(isFromBackground:Boolean = false) {
        Log.d(TAG, "Checking idle state")
        val previousInteractionTimestamp = IdlePersistence.getLastInteractionTimestamp(context)
        if (previousInteractionTimestamp == 0L) return

        val now = System.currentTimeMillis()
        val elapsed = now - previousInteractionTimestamp
        val isCurrentlyIdle = elapsed >= idleTimeout.inWholeMilliseconds

        Log.d(
            TAG,
            "Idle state: $isCurrentlyIdle (elapsed: $elapsed ms, timeout: ${idleTimeout.inWholeMilliseconds} ms)"
        )

        when {
            isCurrentlyIdle && !_onIdleCalled -> {
                Log.d(TAG, "Idle state detected, calling onIdleWithOrigin")
                onIdleWithOrigin(isFromBackground)
                _onIdleCalled = true
                _isIdle = true
            }

            !isCurrentlyIdle && _onIdleCalled -> {
                Log.d(TAG, "User interaction detected, resetting onIdleCalled state")
                _onIdleCalled = false
                _isIdle = false
            }
        }
    }

    private fun startPollingTimer() {
        Log.d(TAG, "Starting polling timer with interval: ${checkInterval.inWholeMilliseconds} ms")
        pollingTimerJob?.cancel()
        pollingTimerJob = scope.launch {
            Log.d(TAG, "Polling timer started")
            while (isActive) {
                delay(checkInterval.inWholeMilliseconds)
                checkIdleState()
            }
        }
    }

    private fun stopPollingTimer() {
        Log.d(TAG, "Stopping polling timer")
        pollingTimerJob?.cancel()
        pollingTimerJob = null
    }

    private fun handleResume() {
        Log.d(TAG, "Handling resume event")
        cancelBackgroundTimeoutWorker()
        val backgroundTimeoutTriggered = IdlePersistence.isBackgroundTimeoutTriggered(context)

        if (backgroundTimeoutTriggered) {
            Log.d(TAG, "Background timeout triggered")
            IdlePersistence.setBackgroundTimeoutTriggered(context, false)
            checkIdleState(isFromBackground = true)
        } else {
            Log.d(TAG, "No background timeout triggered, resetting last interaction timestamp")
            checkIdleState()
        }

        startPollingTimer()
    }

    private fun handlePause() {
        Log.d(TAG, "Handling pause event")
        stopPollingTimer()
        scheduleBackgroundTimeoutWorker()
    }

    private fun scheduleBackgroundTimeoutWorker() {
        Log.d(TAG, "Scheduling background timeout worker")
        if (_lastInteractionTimestamp == 0L) return

        val currentTime = System.currentTimeMillis()
        val elapsedMillis = currentTime - _lastInteractionTimestamp
        val timeoutMillis = idleTimeout.inWholeMilliseconds
        val remainingDelay = (timeoutMillis - elapsedMillis).coerceAtLeast(0)

        if (remainingDelay <= 0 && elapsedMillis >= timeoutMillis) return

        val data = workDataOf(BackgroundTimeoutWorker.KEY_TIMEOUT_MILLIS to timeoutMillis)
        val workRequest = OneTimeWorkRequestBuilder<BackgroundTimeoutWorker>()
            .setInitialDelay(remainingDelay.coerceAtLeast(50), TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()

        workManager.enqueueUniqueWork(
            BackgroundTimeoutWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    private fun cancelBackgroundTimeoutWorker() {
        Log.d(TAG, "Cancelling background timeout worker")
        workManager.cancelUniqueWork(BackgroundTimeoutWorker.WORK_NAME)
    }

    fun cleanUp() {
        Log.d(TAG, "Cleaning up IdleDetector")
        lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        scope.cancel()
        cancelBackgroundTimeoutWorker()
        IdlePersistence.reset(context = context)
    }
}