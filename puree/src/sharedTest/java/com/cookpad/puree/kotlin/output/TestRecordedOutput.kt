package com.cookpad.puree.kotlin.output

import org.json.JSONObject

class TestRecordedOutput : PureeOutput {
    private val _logs: MutableList<JSONObject> = mutableListOf()
    val logs: List<JSONObject> get() = _logs

    override fun emit(log: JSONObject) {
        _logs.add(log)
    }
}
