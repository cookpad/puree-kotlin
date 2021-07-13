package com.cookpad.puree.kotlin.output

import android.util.Log
import androidx.annotation.VisibleForTesting
import com.cookpad.puree.kotlin.PureeLogger
import com.cookpad.puree.kotlin.store.PureeLogStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.pow

/**
 * The output that emits posted logs in batches.
 */
abstract class PureeBufferedOutput(
    /**
     * Uniquely identifies this output.
     * This values is used to match the registered [PureeBufferedOutput] to the buffered logs when saving and retrieving
     * to and from the [PureeLogStore].
     * Changing this value will prevent the log from being processed correctly.
     */
    internal val uniqueId: String
) : PureeOutput {
    /**
     * The frequency interval when the buffer is flushed to be emitted in batches. Behavior is
     * undefined if set to a negative value.
     */
    open val flushInterval: Duration = Duration.ofMinutes(2)

    /**
     * The maximum number of logs in a batch.
     */
    open val logsPerFlush: Int = 100

    /**
     * The number of times to retry a failed batch.
     */
    open val maxRetryCount: Int = 5

    /**
     * The base duration to wait when retrying a failed batch. Negative duration is automatically converted to absolute value.
     */
    open val exponentialBackoffBase: Duration = Duration.ofSeconds(2)

    /**
     * If set to a non-null value, logs of this age will be not be processed and deleted. Behavior
     * is undefined if set to a negative value.
     */
    open val purgeableAge: Duration? = null

    /**
     * If set, maximum number of bytes to include in a single flush
     */
    open val maxFlushSizeInBytes: Long = Long.MAX_VALUE

    private lateinit var logStore: PureeLogStore
    private lateinit var clock: Clock
    private lateinit var nextFlush: Instant
    private lateinit var coroutineScope: CoroutineScope

    private var retryCount: Int = 0

    /**
     * Emits the logs.
     *
     * @param logs The logs in JSON format
     * @param onSuccess Should be invoked if successful.
     * @param onFailed Should be invoked if failed.
     */
    abstract fun emit(logs: List<JSONObject>, onSuccess: () -> Unit, onFailed: (Throwable) -> Unit)

    internal fun initialize(
        logStore: PureeLogStore,
        clock: Clock,
        coroutineContext: CoroutineContext
    ) {
        this.logStore = logStore
        this.clock = clock
        nextFlush = Instant.now(clock) + flushInterval
        retryCount = 0
        coroutineScope = CoroutineScope(coroutineContext + SupervisorJob())
    }

    /**
     * Stores the log instead of emitting immediately
     */
    final override fun emit(log: JSONObject) {
        // Guaranteed to be executed in the appropriate thread
        logStore.add(
            uniqueId,
            PureeBufferedLog(
                createdAt = Instant.now(clock),
                log = log
            )
        )
    }

    /**
     * Create and run a coroutine that periodically flushes and emit the buffered logs according to
     * [flushInterval]. Failed attempts will be retried according to [exponentialBackoffBase] and
     * [maxRetryCount].
     *
     * @see suspend
     */
    internal fun resume() {
        coroutineScope.launch {
            while (isActive) {
                delay(max(0, Duration.between(Instant.now(clock), nextFlush).toMillis()))
                runCatching { flush() }
                    .onSuccess {
                        retryCount = 0
                        nextFlush = Instant.now(clock) + flushInterval
                    }
                    .onFailure {
                        Log.e(PureeLogger.TAG, "Error emitting buffered logs", it)
                        retryCount++
                        if (retryCount > maxRetryCount) {
                            retryCount = 0
                            nextFlush = Instant.now(clock) + flushInterval
                        } else {
                            nextFlush = Instant.now(clock) + exponentialBackoffBase.abs().multipliedBy(
                                2.0.pow((retryCount - 1).toDouble()).toLong()
                            )
                        }
                    }
            }
        }
    }

    /**
     * Cancels the coroutine that periodically flushes and emit the buffered logs.
     *
     * @see resume
     */
    internal fun suspend() {
        coroutineScope.coroutineContext.cancelChildren()
    }

    /**
     * Flushes the buffered logs stored in [logStore]. If [purgeableAge] is set, old logs will be
     * deleted accordingly. The batch will contain at most [logsPerFlush] and if
     * [maxFlushSizeInBytes], the batch will be trimmed accordingly. If emitted successfully, the
     * logs will be deleted from the [logStore].
     *
     * @see resume
     */
    internal suspend fun flush() {
        purgeableAge?.let {
            logStore.purgeLogsWithAge(uniqueId, Instant.now(clock), it)
        }

        val bufferedLogs = logStore.get(uniqueId, logsPerFlush).let { bufferedLogs ->
            when (maxFlushSizeInBytes) {
                Long.MAX_VALUE -> bufferedLogs
                else -> {
                    var size = 0L
                    bufferedLogs.takeWhile {
                        size += it.log.toString().toByteArray().size
                        size <= maxFlushSizeInBytes
                    }
                }
            }
        }
        if (bufferedLogs.isEmpty()) {
            return
        }

        suspendCancellableCoroutine<Unit> { continuation ->
            emit(
                bufferedLogs.map { it.log },
                { continuation.resume(Unit) },
                { throwable -> continuation.resumeWithException(throwable) }
            )
        }

        logStore.remove(uniqueId, bufferedLogs)
    }
}
