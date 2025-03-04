package com.cookpad.puree.kotlin.output

import com.cookpad.puree.kotlin.ManualClock
import com.cookpad.puree.kotlin.jsonOf
import com.cookpad.puree.kotlin.rule.LifecycleCoroutineRule
import com.cookpad.puree.kotlin.store.PureeLogStore
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Duration
import java.time.Instant
import kotlin.math.pow

@ExperimentalCoroutinesApi
class PureeBufferedOutputTest {
    @get:Rule
    val rule = LifecycleCoroutineRule()

    @MockK
    private lateinit var logStore: PureeLogStore
    private lateinit var clock: ManualClock
    private lateinit var output: TestPureeBufferedOutput

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxed = true)
        clock = ManualClock(Instant.now())
        output = TestPureeBufferedOutput("output")
        output.initialize(logStore, clock, rule.coroutineDispatcher)
    }

    @Test
    fun emit() {
        // given
        val log = JSONObject().apply {
            put("key", "value")
        }

        // when
        val time = Instant.now(clock)
        output.emit(log)
        clock.updateTime(Duration.ofSeconds(1))

        // then
        verify { logStore.add("output", log.toBufferedLog(time)) }
    }

    @Test
    fun suspend() = runTest(rule.coroutineDispatcher) {
        // given
        val output = spyk<PureeBufferedOutput>(output, recordPrivateCalls = true)

        // when
        output.suspend()
        clock.updateTime(output.flushInterval)
        advanceTimeBy(output.flushInterval.toMillis())

        // then
        verify(exactly = 0) { output["flush"]() }
    }

    @Test
    fun resume() = runTest(rule.coroutineDispatcher) {
        // given
        val output = spyk(output, recordPrivateCalls = true)

        // when
        output.suspend()
        clock.updateTime(output.flushInterval)
        advanceTimeBy(output.flushInterval.toMillis())
        output.resume()

        // then
        verify { output["flush"]() }

        output.suspend() // cancel all tasks
    }

    @Test
    fun flush() = runTest(rule.coroutineDispatcher) {
        // given
        val output = spyk(output)
        val logs = listOf(jsonOf("key" to "value"))
        val bufferedLogs = logs.map { it.toBufferedLog(Instant.now(clock)) }
        every { logStore.get("output", 100) } returns bufferedLogs

        // when
        clock.updateTime(output.flushInterval)
        advanceTimeBy(output.flushInterval.toMillis())
        output.resume()

        // then
        verify { output.emit(logs, any(), any()) }
        verify { logStore.remove("output", bufferedLogs) }

        output.suspend() // cancel all tasks
    }

    @Test
    fun flush_purge() = runTest(rule.coroutineDispatcher) {
        // given
        output.purgeableAge = Duration.ofSeconds(20)
        val output = spyk(output)
        val logs = listOf(jsonOf("key" to "value"))
        val bufferedLogs = logs.map { it.toBufferedLog(Instant.now(clock)) }
        every { logStore.get("output", 100) } returns bufferedLogs

        // when
        clock.updateTime(output.flushInterval)
        advanceTimeBy(output.flushInterval.toMillis())
        output.resume()

        // then
        verify { logStore.purgeLogsWithAge("output", clock.instant(), Duration.ofSeconds(20)) }
        verify { output.emit(logs, any(), any()) }
        verify { logStore.remove("output", bufferedLogs) }

        output.suspend() // cancel all tasks
    }

    @Test
    fun flush_empty() = runTest(rule.coroutineDispatcher) {
        // given
        val output = spyk(output)
        every { logStore.get("output", 100) } returns emptyList()

        // when
        clock.updateTime(output.flushInterval)
        advanceTimeBy(output.flushInterval.toMillis())
        output.resume()

        // then
        verify(exactly = 0) { output.emit(any(), any(), any()) }
        verify(exactly = 0) { logStore.remove("output", any()) }

        output.suspend() // cancel all tasks
    }

    @Test
    fun flush_maxFlushSizeInBytes() = runTest(rule.coroutineDispatcher) {
        // given
        val output = spyk(output)
        val log1 = jsonOf("key" to "value".repeat(100))
        val log2 = jsonOf("key" to "value")
        val bufferedLogs = listOf(log1, log2).map { it.toBufferedLog(Instant.now(clock)) }
        every { logStore.get("output", 100) } returns bufferedLogs
        output.maxFlushSizeInBytes = log1.toString().toByteArray().size.toLong()

        // when
        clock.updateTime(output.flushInterval)
        advanceTimeBy(output.flushInterval.toMillis())
        output.resume()

        // then
        verify { output.emit(listOf(log1), any(), any()) }
        verify { logStore.remove("output", bufferedLogs.take(1)) }

        output.suspend() // cancel all tasks
    }

    @Test
    fun flush_failed() = runTest(rule.coroutineDispatcher) {
        // given
        val output = spyk(output) {
            every { emit(any(), any(), any()) } answers { arg<(Throwable) -> Unit>(2).invoke(IOException()) }
        }
        val logs = listOf(jsonOf("key" to "value"))
        val bufferedLogs = logs.map { it.toBufferedLog(Instant.now(clock)) }
        every { logStore.get("output", 100) } returns bufferedLogs

        // when
        clock.updateTime(output.flushInterval)
        advanceTimeBy(output.flushInterval.toMillis())
        output.resume()

        // then
        verify { output.emit(logs, any(), any()) }
        verify(exactly = 0) { logStore.remove("output", bufferedLogs) }

        output.suspend() // cancel all tasks
    }

    @Test
    fun flush_failedThenRetry() = runTest(rule.coroutineDispatcher) {
        // given
        val output = spyk(output) {
            every { emit(any(), any(), any()) } answers {
                arg<(Throwable) -> Unit>(2).invoke(IOException())
            } andThenAnswer {
                arg<() -> Unit>(1).invoke()
            }
        }
        val logs = listOf(jsonOf("key" to "value"))
        val bufferedLogs = logs.map { it.toBufferedLog(Instant.now(clock)) }
        every { logStore.get("output", 100) } returns bufferedLogs

        // when
        clock.updateTime(output.flushInterval)
        advanceTimeBy(output.flushInterval.toMillis())
        output.resume()
        clock.updateTime(output.flushInterval)
        advanceTimeBy(output.exponentialBackoffBase.toMillis())

        // then
        verify(exactly = 2) { output.emit(logs, any(), any()) }
        verify(exactly = 1) { logStore.remove("output", bufferedLogs) }

        output.suspend() // cancel all tasks
    }

    @Test
    fun flush_failedMaxRetry() = runTest(rule.coroutineDispatcher) {
        // given
        output.flushInterval
        val output = spyk(output) {
            every { emit(any(), any(), any()) } answers { arg<(Throwable) -> Unit>(2).invoke(IOException()) }
        }
        val logs = listOf(jsonOf("key" to "value"))
        val bufferedLogs = logs.map { it.toBufferedLog(Instant.now(clock)) }
        every { logStore.get("output", 100) } returns bufferedLogs

        // when
        clock.updateTime(output.flushInterval)
        advanceTimeBy(output.flushInterval.toMillis())
        output.resume()
        repeat(output.maxRetryCount) {
            val step = output.exponentialBackoffBase.multipliedBy(2.0.pow((it).toDouble()).toLong())
            clock.updateTime(step)
            advanceTimeBy(step.toMillis())
        }
        clock.updateTime(output.flushInterval)
        advanceTimeBy(output.flushInterval.toMillis())

        // then
        // initial flush (1) + maxRetryCount (default 5) + regular flush (1)
        verify(exactly = output.maxRetryCount + 2) { output.emit(logs, any(), any()) }
        verify(exactly = 0) { logStore.remove("output", bufferedLogs) }

        output.suspend() // cancel all tasks
    }

    private fun JSONObject.toBufferedLog(createdAt: Instant): PureeBufferedLog = PureeBufferedLog(
        createdAt = createdAt,
        log = this
    )

    private class TestPureeBufferedOutput(uniqueId: String) : PureeBufferedOutput(uniqueId) {
        override var flushInterval: Duration = Duration.ofMinutes(20)

        override var purgeableAge: Duration? = null

        override var maxFlushSizeInBytes: Long = Long.MAX_VALUE

        override fun emit(logs: List<JSONObject>, onSuccess: () -> Unit, onFailed: (Throwable) -> Unit) {
            onSuccess()
        }
    }
}
