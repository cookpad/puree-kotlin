package com.cookpad.puree.kotlin

import com.cookpad.puree.kotlin.serializer.PureeLogSerializer
import org.json.JSONObject

data class SampleLog(val sequence: Int) : PureeLog

class SampleLogSerializer : PureeLogSerializer {
    override fun serialize(log: PureeLog): JSONObject = JSONObject().apply {
        when (log) {
            is SampleLog -> put("sequence", log.sequence)
        }
    }
}
