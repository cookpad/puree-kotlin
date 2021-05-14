package com.cookpad.puree.kotlin

import com.google.common.truth.Correspondence
import org.json.JSONObject

val JSON_TO_STRING = Correspondence.transforming<JSONObject, String>({ it.toString() }, "toString() is")

fun jsonOf(vararg keyValues: Pair<String, Any>): JSONObject = JSONObject(mapOf(*keyValues))

fun jsonStringOf(vararg keyValues: Pair<String, Any>): String = jsonOf(*keyValues).toString()
