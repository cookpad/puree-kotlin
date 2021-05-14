package com.cookpad.puree.kotlin.output

import org.json.JSONObject

/**
 * The output that emits posted logs.
 */
interface PureeOutput {
    /**
     * Emits the log.
     *
     * @param log The log serialized in JSON format
     */
    fun emit(log: JSONObject)
}
