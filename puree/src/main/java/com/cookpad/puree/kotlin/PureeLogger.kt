package com.cookpad.puree.kotlin

import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.cookpad.puree.kotlin.output.PureeBufferedOutput
import com.cookpad.puree.kotlin.output.PureeOutput
import com.cookpad.puree.kotlin.serializer.PureeLogSerializer
import com.cookpad.puree.kotlin.store.PureeLogStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.time.Clock
import java.util.concurrent.Executors

/**
 *  Puree Log collector.
 *
 *  Ideally, only one instance of this class should exist and should be treated as singleton.
 *  To create an instance use [Builder]:
 *
 * ```
 *  PureeLogger.Builder(
 *      logSerializer = { log ->
 *          // Serialize log
 *      },
 *      logStore = DbPureeLogStore(context, "puree.db")
 *  )
 *      .filter(
 *          AddTimeFilter(),
 *          ClickLog::class.java, EventLog::class.java
 *      )
 *      .output(
 *          LogcatOutput(),
 *          ClickLog::class.java, EventLog::class.java
 *      )
 *      .build()
 *  ```
 *
 *  @see [Builder]
 */
class PureeLogger private constructor(
    lifecycle: Lifecycle,
    private val logSerializer: PureeLogSerializer,
    private val logStore: PureeLogStore,
    private val dispatcher: CoroutineDispatcher,
    private val clock: Clock,
    private val registeredLogs: Map<Class<out Any>, Configuration>,
    private val bufferedOutputs: List<PureeBufferedOutput>
) {
    private val scope: CoroutineScope = CoroutineScope(dispatcher + CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Exception thrown", throwable)
    })
    private var isResumed: Boolean = false

    init {
        bufferedOutputs.forEach { it.initialize(logStore, clock, dispatcher) }

        lifecycle.addObserver(
            object : LifecycleObserver {
                @Suppress("unused")
                @OnLifecycleEvent(Lifecycle.Event.ON_START)
                fun onStart() {
                    resume()
                }

                @Suppress("unused")
                @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
                fun onStop() {
                    suspend()
                }
            }
        )
    }

    /**
     * Sends a log through PureeLogger.
     *
     * @param log The type of the logs must be registered when creating [PureeLogger] through [Builder].
     *
     * @see Builder.filter
     * @see Builder.output
     */
    fun postLog(log: PureeLog) {
        val config = registeredLogs[log::class.java] ?: throw LogNotRegisteredException()

        scope.launch {
            val logJson = try {
                config.filters.fold(logSerializer.serialize(log)) { logJson, filter ->
                    filter.applyFilter(logJson) ?: throw SkippedLogException()
                }
            } catch (e: SkippedLogException) {
                return@launch
            }

            config.outputs.forEach {
                it.emit(logJson)
            }
        }
    }

    /**
     * Suspends the background process that periodically emits buffered logs if a [PureeBufferedOutput] is registered
     * through [Builder]. This is called when the [Lifecycle]'s state changes to [Lifecycle.Event.ON_STOP]
     *
     * @see PureeLogger.resume
     * @see PureeBufferedOutput
     * @see Builder.output
     */
    private fun suspend() {
        scope.launch {
            if (!isResumed) {
                return@launch
            }

            bufferedOutputs.forEach { it.suspend() }
            isResumed = false
        }
    }

    /**
     * Resumes the background process that periodically emits buffered logs if a [PureeBufferedOutput] is registered
     * through [Builder]. This is called when the [Lifecycle]'s state changes to [Lifecycle.Event.ON_START].
     *
     * @see PureeLogger.suspend
     * @see PureeBufferedOutput
     * @see Builder.output
     */
    private fun resume() {
        scope.launch {
            if (isResumed) {
                return@launch
            }

            bufferedOutputs.forEach { it.resume() }
            isResumed = true
        }
    }

    /**
     * @suppress
     */
    companion object {
        internal const val TAG = "PureeLogger"
    }

    class LogNotRegisteredException : Exception()

    private data class Configuration(
        val filters: List<PureeFilter>,
        val outputs: List<PureeOutput>
    )

    private class SkippedLogException : Exception()

    /**
     * The builder for [PureeLogger] class.
     *
     * @param lifecycle Registered [PureeBufferedOutput]'s background processes are automatically suspended
     * [PureeLogger.suspend] and resumed [PureeLogger.resume] based on the events and state of this Lifecycle.
     * @param logSerializer The serializer to be used when serializing log objects to JSONObject.
     * @param logStore The store that buffers logs.
     */
    class Builder(
        private val lifecycle: Lifecycle,
        private val logSerializer: PureeLogSerializer,
        private val logStore: PureeLogStore,
    ) {
        /**
         * Only use one thread to simplify thread-safety and avoid using locks.
         * Modifiable only for testing.
         */
        @VisibleForTesting
        internal var dispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor {
            Thread(it, TAG).apply {
                priority = Thread.MIN_PRIORITY
            }
        }.asCoroutineDispatcher()

        /**
         * Use default clock.
         * Modifiable only for testing.
         */
        @VisibleForTesting
        internal var clock: Clock = Clock.systemUTC()

        private val configuredLogs: MutableMap<Class<out Any>, Configuration> = mutableMapOf()
        private val outputIds: MutableSet<String> = mutableSetOf()
        private val bufferedOutputs: MutableList<PureeBufferedOutput> = mutableListOf()

        /**
         * Registers a [PureeFilter] and associate it with log types.
         *
         * @param filter The [PureeFilter] to be registered.
         * @param logTypes The log types of the objects on which the [PureeFilter] will be applied. If a type is included more
         * than once, the [PureeFilter] will be applied multiple times.
         */
        fun filter(filter: PureeFilter, vararg logTypes: Class<out Any>): Builder {
            logTypes.forEach {
                configuredLogs.getOrPut(it, { Configuration() }).filters.add(filter)
            }

            return this
        }

        /**
         * Registers a [PureeOutput] and associate it with log types.
         *
         * @param output The [PureeOutput] to be registered. To avoid issues when buffering logs, only one instance per
         * [PureeOutput] should be registered and duplicates are ignored.
         * @param logTypes The log types of the objects that will be sent to the [PureeOutput].
         */
        fun output(
            output: PureeOutput,
            vararg logTypes: Class<out Any>
        ): Builder {
            if (output is PureeBufferedOutput) {
                if (output.uniqueId in outputIds) {
                    throw IllegalArgumentException("Cannot register another PureeBufferedOutput with uniqueId: ${output.uniqueId}.")
                } else {
                    outputIds.add(output.uniqueId)
                    bufferedOutputs.add(output)
                }
            }

            logTypes.forEach {
                configuredLogs.getOrPut(it, { Configuration() }).outputs.add(output)
            }

            return this
        }

        /**
         * Builds a [PureeLogger] object.
         *
         * @return [PureeLogger] object
         */
        fun build(): PureeLogger {
            return PureeLogger(
                lifecycle,
                logSerializer,
                logStore,
                dispatcher,
                clock,
                configuredLogs.mapValues {
                    PureeLogger.Configuration(it.value.filters, it.value.outputs)
                },
                bufferedOutputs
            )
        }

        private data class Configuration(
            val filters: MutableList<PureeFilter> = mutableListOf(),
            val outputs: MutableList<PureeOutput> = mutableListOf()
        )
    }
}
