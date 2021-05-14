package com.cookpad.puree.kotlin.demo.log

import com.cookpad.puree.kotlin.PureeFilter
import org.json.JSONObject

class AddTimeFilter : PureeFilter {
    override fun applyFilter(log: JSONObject): JSONObject {
        return log.apply {
            put("time", System.currentTimeMillis())
        }
    }
}
