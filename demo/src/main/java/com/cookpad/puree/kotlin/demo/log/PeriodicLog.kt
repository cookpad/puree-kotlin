package com.cookpad.puree.kotlin.demo.log

import com.cookpad.puree.kotlin.PureeLog
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PeriodicLog(@SerialName("sequence") val sequence: Int) : PureeLog
