package com.cookpad.puree.kotlin.demo.log

import android.util.Log
import com.cookpad.puree.kotlin.output.PureeBufferedOutput
import org.json.JSONObject
import java.time.Duration

class PurgeableLogcatWarningBufferedOutput(uniqueId: String) : PureeBufferedOutput(uniqueId) {
    override val flushInterval: Duration = Duration.ofSeconds(10)

    override val purgeableAge: Duration? = Duration.ofSeconds(5)

    override fun emit(logs: List<JSONObject>, onSuccess: () -> Unit, onFailed: (Throwable) -> Unit) {
        Log.w(this::class.java.simpleName, "Logs: $logs")
        onSuccess()
    }
}
