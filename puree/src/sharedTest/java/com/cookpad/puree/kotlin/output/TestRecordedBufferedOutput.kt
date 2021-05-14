package com.cookpad.puree.kotlin.output

import org.json.JSONObject
import java.time.Duration

class TestRecordedBufferedOutput(
    uniqueId: String,
    override val flushInterval: Duration,
    override val purgeableAge: Duration? = null
) : PureeBufferedOutput(uniqueId) {
    private val _logs: MutableList<JSONObject> = mutableListOf()
    val logs: List<JSONObject> get() = _logs
    var shouldSucceed: Boolean = true

    override fun emit(logs: List<JSONObject>, onSuccess: () -> Unit, onFailed: (Throwable) -> Unit) {
        if (shouldSucceed) {
            _logs.addAll(logs)
            onSuccess()
        } else {
            onFailed(Exception())
        }
    }
}
