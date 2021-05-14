package com.cookpad.puree.kotlin.output

import org.json.JSONObject
import java.time.Instant

/**
 * Log object that is buffered to a [com.cookpad.puree.kotlin.store.PureeLogStore].
 * Used by [com.cookpad.puree.kotlin.output.PureeBufferedOutput]
 *
 */
data class PureeBufferedLog constructor(
    /**
     * The unique identifier of this log.
     */
    val id: Long = 0,
    /**
     * The date and time when this log is posted.
     */
    val createdAt: Instant,
    /**
     * The log serialized in JSON format.
     */
    val log: JSONObject
)
