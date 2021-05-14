package com.cookpad.puree.kotlin

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class ManualClock(initialTime: Instant) : Clock() {
    private var now: Instant = initialTime

    override fun getZone(): ZoneId = ZoneId.systemDefault()

    override fun withZone(zone: ZoneId?): Clock = this

    override fun instant(): Instant = now

    fun updateTime(duration: Duration) {
        now += duration
    }
}
