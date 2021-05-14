package com.cookpad.puree.kotlin.demo.log

import android.util.Log
import com.cookpad.puree.kotlin.output.PureeOutput
import org.json.JSONObject

class LogcatOutput : PureeOutput {
    override fun emit(log: JSONObject) {
        Log.d("Puree", log.toString())
    }
}
