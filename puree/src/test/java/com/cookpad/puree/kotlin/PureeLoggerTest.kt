package com.cookpad.puree.kotlin

import androidx.lifecycle.Lifecycle
import com.cookpad.puree.kotlin.output.PureeBufferedOutput
import com.cookpad.puree.kotlin.output.PureeOutput
import com.cookpad.puree.kotlin.rule.LifecycleCoroutineRule
import com.cookpad.puree.kotlin.store.PureeLogStore
import io.mockk.MockKAnnotations
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.excludeRecords
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.json.JSONObject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PureeLoggerTest {
    @get:Rule
    val rule: LifecycleCoroutineRule = LifecycleCoroutineRule()

    @MockK
    private lateinit var logStore: PureeLogStore
    @MockK
    private lateinit var output: PureeOutput
    @MockK
    private lateinit var bufferedOutput: PureeBufferedOutput

    private lateinit var clock: ManualClock

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxed = true)
        every { bufferedOutput.uniqueId } returns "buffered_output"

        clock = ManualClock(Instant.now())
    }

    @Test
    fun postLog() {
        // given
        val logSlot = slot<JSONObject>()
        val filter = mockk<PureeFilter> {
            every { applyFilter(capture(logSlot)) } answers { logSlot.captured }
        }
        val puree = createPureeBuilder()
            .filter(
                filter,
                SampleLog::class.java
            )
            .output(
                output,
                SampleLog::class.java
            )
            .output(
                bufferedOutput,
                SampleLog::class.java
            )
            .build()

        // when
        puree.postLog(SampleLog(sequence = 1))

        // then
        verify { filter.applyFilter(any()) }
        verify {
            output.emit(logSlot.captured)
            bufferedOutput.emit(logSlot.captured)
        }
    }

    @Test
    fun postLog_skipped() {
        // given
        val filterSkip = mockk<PureeFilter> {
            every { applyFilter(any()) } returns null
        }
        val filter = mockk<PureeFilter> {
            every { applyFilter(any()) } returnsArgument 0
        }
        val puree = createPureeBuilder()
            .filter(
                filterSkip,
                SampleLog::class.java
            )
            .filter(
                filter,
                SampleLog::class.java
            )
            .output(
                output,
                SampleLog::class.java
            )
            .build()

        // when
        puree.postLog(SampleLog(sequence = 1))

        // then
        verify(exactly = 0) { filter.applyFilter(any()) }
        verify(exactly = 0) { output.emit(any()) }
    }

    @Test
    fun lifecycle() {
        // given
        excludeRecords { bufferedOutput.uniqueId }
        excludeRecords { bufferedOutput.initialize(any(), any(), any()) }
        createPureeBuilder()
            .output(bufferedOutput, SampleLog::class.java)
            .build()

        // when
        rule.lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        rule.lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        rule.lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        rule.lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)

        // then
        verifyOrder {
            bufferedOutput.resume()
            bufferedOutput.suspend()
            bufferedOutput.resume()
        }
    }

    @Test
    fun flush() {
        // given
        val outputs = (1..5).toList().map { index ->
            mockk<PureeBufferedOutput>(relaxed = true) {
                every { uniqueId } returns "buffered_output_$index"
            }
        }
        val puree = createPureeBuilder().apply {
            outputs.forEach {
                output(it, SampleLog::class.java)
            }
        }.build()

        // when
        puree.flush()

        // then
        coVerifyOrder {
            outputs.forEach {
                it.flush()
            }
        }
    }

    private fun createPureeBuilder(): PureeLogger.Builder = PureeLogger.Builder(
        rule.lifecycleOwner.lifecycle,
        SampleLogSerializer(),
        logStore
    ).apply {
        dispatcher = rule.coroutineDispatcher
        clock = this@PureeLoggerTest.clock
    }
}
