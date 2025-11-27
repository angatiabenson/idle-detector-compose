package ke.co.banit.idle_detector_compose

import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import java.util.concurrent.atomic.AtomicLong
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
    private val workManager = WorkManager.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Thread-safe timestamp using AtomicLong
    private val _lastInteractionTimestamp = AtomicLong(0L)
    val lastInteractionTimestamp: Long
        get() = _lastInteractionTimestamp.get()

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
        IdleDetectorLogger.d("IdleDetector initialized")
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
    }

    private fun handleCreate() {
        IdleDetectorLogger.d("Resetting IdleDetector state")
        IdlePersistence.reset(context)
        registerInteraction()
        _isIdle = false
        _onIdleCalled = false
    }

    fun registerInteraction() {
        IdleDetectorLogger.d("Registering user interaction")
        val now = System.currentTimeMillis()
        _lastInteractionTimestamp.set(now)
        IdlePersistence.recordInteraction(context, now)
        _isIdle = false

        if (_onIdleCalled) {
            IdleDetectorLogger.d("Resetting onIdleCalled state due to new interaction")
            _onIdleCalled = false
        }

        cancelBackgroundTimeoutWorker()
    }

    fun checkIdleState(isFromBackground: Boolean = false) {
        IdleDetectorLogger.d("Checking idle state")
        val previousInteractionTimestamp = IdlePersistence.getLastInteractionTimestamp(context)
        if (previousInteractionTimestamp == 0L) return

        val now = System.currentTimeMillis()
        val elapsed = now - previousInteractionTimestamp
        val isCurrentlyIdle = elapsed >= idleTimeout.inWholeMilliseconds

        IdleDetectorLogger.d(
            "Idle state: $isCurrentlyIdle (elapsed: $elapsed ms, timeout: ${idleTimeout.inWholeMilliseconds} ms)"
        )

        when {
            isCurrentlyIdle && !_onIdleCalled -> {
                IdleDetectorLogger.d("Idle state detected, calling onIdleWithOrigin")
                onIdleWithOrigin(isFromBackground)
                _onIdleCalled = true
                _isIdle = true
            }

            !isCurrentlyIdle && _onIdleCalled -> {
                IdleDetectorLogger.d("User interaction detected, resetting onIdleCalled state")
                _onIdleCalled = false
                _isIdle = false
            }
        }
    }

    private fun startPollingTimer() {
        IdleDetectorLogger.d("Starting polling timer with interval: ${checkInterval.inWholeMilliseconds} ms")
        pollingTimerJob?.cancel()
        pollingTimerJob = scope.launch {
            IdleDetectorLogger.d("Polling timer started")
            var currentInterval = checkInterval.inWholeMilliseconds
            val maxInterval = (idleTimeout.inWholeMilliseconds / 4).coerceAtMost(5000L)

            while (isActive) {
                delay(currentInterval)
                checkIdleState()

                // Adaptive polling: poll faster as we approach timeout
                if (!_isIdle) {
                    val lastInteraction = _lastInteractionTimestamp.get()
                    if (lastInteraction > 0) {
                        val elapsed = System.currentTimeMillis() - lastInteraction
                        val remaining = idleTimeout.inWholeMilliseconds - elapsed

                        currentInterval = when {
                            remaining < 10000 -> checkInterval.inWholeMilliseconds // Last 10s: use default
                            remaining < 30000 -> (checkInterval.inWholeMilliseconds * 2).coerceAtMost(maxInterval) // Last 30s: 2x
                            else -> maxInterval // Otherwise: max interval
                        }
                    }
                }
            }
        }
    }

    private fun stopPollingTimer() {
        IdleDetectorLogger.d("Stopping polling timer")
        pollingTimerJob?.cancel()
        pollingTimerJob = null
    }

    private fun handleResume() {
        IdleDetectorLogger.d("Handling resume event")
        cancelBackgroundTimeoutWorker()
        val backgroundTimeoutTriggered = IdlePersistence.isBackgroundTimeoutTriggered(context)

        if (backgroundTimeoutTriggered) {
            IdleDetectorLogger.d("Background timeout triggered")
            IdlePersistence.setBackgroundTimeoutTriggered(context, false)
            checkIdleState(isFromBackground = true)
        } else {
            IdleDetectorLogger.d("No background timeout triggered, checking current idle state")
            checkIdleState()
        }

        startPollingTimer()
    }

    private fun handlePause() {
        IdleDetectorLogger.d("Handling pause event")
        stopPollingTimer()

        // Flush any pending writes before going to background
        IdlePersistence.flush(context)

        scheduleBackgroundTimeoutWorker()
    }

    private fun scheduleBackgroundTimeoutWorker() {
        IdleDetectorLogger.d("Scheduling background timeout worker")
        val lastInteraction = _lastInteractionTimestamp.get()
        if (lastInteraction == 0L) {
            IdleDetectorLogger.d("No interaction timestamp, skipping worker scheduling")
            return
        }

        val currentTime = System.currentTimeMillis()
        val elapsedMillis = currentTime - lastInteraction
        val timeoutMillis = idleTimeout.inWholeMilliseconds

        // Already idle, set flag immediately
        if (elapsedMillis >= timeoutMillis) {
            IdleDetectorLogger.d("Already idle (elapsed: ${elapsedMillis}ms >= timeout: ${timeoutMillis}ms), setting flag immediately")
            IdlePersistence.setBackgroundTimeoutTriggered(context, true)
            return
        }

        val remainingDelay = timeoutMillis - elapsedMillis

        // Too short to schedule worker, will catch on resume
        if (remainingDelay < 1000) {
            IdleDetectorLogger.d("Remaining delay too short (${remainingDelay}ms), skipping worker scheduling")
            return
        }

        IdleDetectorLogger.d("Scheduling worker with delay: ${remainingDelay}ms")

        val data = workDataOf(BackgroundTimeoutWorker.KEY_TIMEOUT_MILLIS to timeoutMillis)
        val workRequest = OneTimeWorkRequestBuilder<BackgroundTimeoutWorker>()
            .setInitialDelay(remainingDelay, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()

        workManager.enqueueUniqueWork(
            BackgroundTimeoutWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    private fun cancelBackgroundTimeoutWorker() {
        IdleDetectorLogger.d("Cancelling background timeout worker")
        workManager.cancelUniqueWork(BackgroundTimeoutWorker.WORK_NAME)
    }

    fun cleanUp() {
        IdleDetectorLogger.d("Cleaning up IdleDetector")

        // Flush any pending writes before cleanup
        IdlePersistence.flush(context)

        lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        scope.cancel()
        cancelBackgroundTimeoutWorker()
        IdlePersistence.reset(context = context)
    }
}