package com.cookpad.puree.kotlin.store

import com.cookpad.puree.kotlin.output.PureeBufferedLog
import java.time.Duration
import java.time.Instant

/**
 * Stores the buffered logs for batched emissions.
 * Implementations of this interface are guaranteed to be thread-safe (called from a single thread).
 *
 * @see DbPureeLogStore
 */
interface PureeLogStore {
    /**
     * Adds a new [PureeBufferedLog] to the store.
     *
     * @param outputId The id of the [com.cookpad.puree.kotlin.output.PureeBufferedOutput] that owns this log.
     * @param bufferedLog The log to be stored
     */
    fun add(outputId: String, bufferedLog: PureeBufferedLog)

    /**
     * Retrieves the logs from the store.
     *
     * @param outputId The id of the [com.cookpad.puree.kotlin.output.PureeBufferedOutput] that owns the logs.
     * @param maxCount The maximum number of logs to retrieve.
     *
     * @return List of buffered logs.
     */
    fun get(outputId: String, maxCount: Int): List<PureeBufferedLog>

    /**
     * Deletes the logs from the store.
     *
     * @param outputId The id of the [com.cookpad.puree.kotlin.output.PureeBufferedOutput] that owns the logs.
     * @param bufferedLogs The logs to be deleted.
     */
    fun remove(outputId: String, bufferedLogs: List<PureeBufferedLog>)

    /**
     * Deletes all the logs according the age of the log.
     *
     * @param outputId The id of the [com.cookpad.puree.kotlin.output.PureeBufferedOutput] that owns the logs.
     * @param now The current date-time.
     * @param age The age of the of the logs to be deleted.
     */
    fun purgeLogsWithAge(outputId: String, now: Instant, age: Duration)
}

