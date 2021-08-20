package com.cookpad.puree.kotlin.demo

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.cookpad.puree.kotlin.PureeLogger
import com.cookpad.puree.kotlin.PureeLog
import com.cookpad.puree.kotlin.demo.log.AddTimeFilter
import com.cookpad.puree.kotlin.demo.log.LogcatDebugBufferedOutput
import com.cookpad.puree.kotlin.demo.log.PurgeableLogcatWarningBufferedOutput
import com.cookpad.puree.kotlin.demo.log.ClickLog
import com.cookpad.puree.kotlin.demo.log.LogcatOutput
import com.cookpad.puree.kotlin.demo.log.MenuLog
import com.cookpad.puree.kotlin.demo.log.PeriodicLog
import com.cookpad.puree.kotlin.serializer.PureeLogSerializer
import com.cookpad.puree.kotlin.store.DbPureeLogStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject

class DemoApp : Application() {
    lateinit var logger: PureeLogger

    override fun onCreate() {
        super.onCreate()

        logger = PureeLogger.Builder(
            lifecycle = ProcessLifecycleOwner.get().lifecycle,
            logSerializer = object : PureeLogSerializer {
                override fun serialize(log: PureeLog): JSONObject {
                    val json = when (log) {
                        is ClickLog -> Json.encodeToString(log)
                        is MenuLog -> Json.encodeToString(log)
                        is PeriodicLog -> Json.encodeToString(log)
                        else -> throw IllegalArgumentException("Unexpected log type: ${log::class.java.simpleName}")
                    }

                    return JSONObject(json)
                }
            },
            logStore = DbPureeLogStore(this, "demo_puree.db")
        )
            .filter(
                AddTimeFilter(),
                ClickLog::class.java, MenuLog::class.java
            )
            .output(
                LogcatOutput(),
                ClickLog::class.java, MenuLog::class.java
            )
            .output(
                LogcatDebugBufferedOutput("logcat_debug"),
                ClickLog::class.java, MenuLog::class.java
            )
            .logType(
                logType = PeriodicLog::class.java,
                filters = listOf(AddTimeFilter()),
                outputs = listOf(
                    LogcatOutput(),
                    PurgeableLogcatWarningBufferedOutput("logcat_warning")
                )
            )
            .build()
    }
}
