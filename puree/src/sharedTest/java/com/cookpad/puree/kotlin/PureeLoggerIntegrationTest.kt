package com.cookpad.puree.kotlin

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.testing.TestLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cookpad.puree.kotlin.output.TestRecordedBufferedOutput
import com.cookpad.puree.kotlin.output.TestRecordedOutput
import com.cookpad.puree.kotlin.rule.InMemoryDbRule
import com.cookpad.puree.kotlin.rule.LifecycleCoroutineRule
import com.cookpad.puree.kotlin.store.DbPureeLogStore
import com.cookpad.puree.kotlin.store.PureeLogStore
import com.cookpad.puree.kotlin.store.internal.db.PureeDb
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineDispatcher
import org.json.JSONObject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Enclosed::class)
class PureeLoggerIntegrationTest {
    @RunWith(AndroidJUnit4::class)
    class FilterTests {
        @get:Rule
        val lifecycleCoroutineRule = LifecycleCoroutineRule()

        @get:Rule
        internal val dbRule = InMemoryDbRule(PureeDb::class.java)

        private lateinit var lifecycleOwner: LifecycleOwner
        private lateinit var coroutineDispatcher: TestCoroutineDispatcher
        private lateinit var logStore: PureeLogStore
        private lateinit var clock: ManualClock

        @Before
        fun setUp() {
            logStore = DbPureeLogStore(dbRule.db)
            lifecycleOwner = lifecycleCoroutineRule.lifecycleOwner
            coroutineDispatcher = lifecycleCoroutineRule.coroutineDispatcher
            clock = ManualClock(Instant.now())
        }

        @Test
        fun postLog_filter() {
            // given
            val output = TestRecordedOutput()
            val puree = PureeLogger.Builder(
                lifecycle = lifecycleOwner.lifecycle,
                logSerializer = SampleLogSerializer(),
                logStore = logStore
            ).apply {
                dispatcher = coroutineDispatcher
                clock = this@FilterTests.clock
                filter(
                    object : PureeFilter {
                        override fun applyFilter(log: JSONObject): JSONObject = log.apply {
                            put("filter1", "test_value")
                        }
                    },
                    SampleLog::class.java
                )
                output(
                    output,
                    SampleLog::class.java
                )
            }.build()

            // when
            puree.postLog(SampleLog(sequence = 1))

            // then
            assertThat(output.logs).comparingElementsUsing(JSON_TO_STRING)
                .containsExactly(
                    jsonStringOf(
                        "sequence" to 1,
                        "filter1" to "test_value"
                    )
                )
        }

        @Test
        fun postLog_filterSkip() {
            // given
            val output = TestRecordedOutput()
            var isLastFilterApplied = false
            val puree = PureeLogger.Builder(
                lifecycle = lifecycleOwner.lifecycle,
                logSerializer = SampleLogSerializer(),
                logStore = logStore
            ).apply {
                dispatcher = coroutineDispatcher
                clock = this@FilterTests.clock
                filter(
                    object : PureeFilter {
                        override fun applyFilter(log: JSONObject): JSONObject? = null
                    },
                    SampleLog::class.java
                )
                filter(
                    object : PureeFilter {
                        override fun applyFilter(log: JSONObject): JSONObject {
                            isLastFilterApplied = true
                            return log
                        }
                    }
                )
                output(
                    output,
                    SampleLog::class.java
                )
            }.build()

            // when
            puree.postLog(SampleLog(sequence = 1))

            // then
            assertThat(output.logs).isEmpty()
            assertThat(isLastFilterApplied).isFalse()
        }

        @Test
        fun postLog_filterMultiple() {
            // given
            val output = TestRecordedOutput()
            val puree = PureeLogger.Builder(
                lifecycle = lifecycleOwner.lifecycle,
                logSerializer = SampleLogSerializer(),
                logStore = logStore
            ).apply {
                dispatcher = coroutineDispatcher
                clock = this@FilterTests.clock
                filter(
                    object : PureeFilter {
                        override fun applyFilter(log: JSONObject): JSONObject = log.apply {
                            put("filter1", "test_value")
                        }
                    },
                    SampleLog::class.java
                )
                filter(
                    object : PureeFilter {
                        override fun applyFilter(log: JSONObject): JSONObject = log.apply {
                            put("filter2", "test_value")
                        }
                    },
                    SampleLog::class.java
                )
                output(
                    output,
                    SampleLog::class.java
                )
            }.build()

            // when
            puree.postLog(SampleLog(sequence = 1))

            // then
            assertThat(output.logs).comparingElementsUsing(JSON_TO_STRING)
                .containsExactly(
                    jsonStringOf(
                        "sequence" to 1,
                        "filter1" to "test_value",
                        "filter2" to "test_value"
                    )
                )
        }

        @Test
        fun postLog_filterMultipleWithOverwrite() {
            // given
            val output = TestRecordedOutput()
            val puree = PureeLogger.Builder(
                lifecycle = lifecycleOwner.lifecycle,
                logSerializer = SampleLogSerializer(),
                logStore = logStore
            ).apply {
                dispatcher = coroutineDispatcher
                clock = this@FilterTests.clock
                filter(
                    object : PureeFilter {
                        override fun applyFilter(log: JSONObject): JSONObject = log.apply {
                            put("filter1", "test_value")
                        }
                    },
                    SampleLog::class.java
                )
                filter(
                    object : PureeFilter {
                        override fun applyFilter(log: JSONObject): JSONObject = JSONObject().apply {
                            put("filter2", "test_value")
                        }
                    },
                    SampleLog::class.java
                )
                output(
                    output,
                    SampleLog::class.java
                )
            }.build()

            // when
            puree.postLog(SampleLog(sequence = 1))

            // then
            assertThat(output.logs).comparingElementsUsing(JSON_TO_STRING)
                .containsExactly(
                    jsonStringOf(
                        "filter2" to "test_value"
                    )
                )
        }
    }

    @RunWith(AndroidJUnit4::class)
    class OutputTests {
        @get:Rule
        val lifecycleCoroutineRule = LifecycleCoroutineRule()

        @get:Rule
        internal val dbRule = InMemoryDbRule(PureeDb::class.java)

        private lateinit var lifecycleOwner: LifecycleOwner
        private lateinit var coroutineDispatcher: TestCoroutineDispatcher
        private lateinit var logStore: PureeLogStore
        private lateinit var clock: ManualClock

        @Before
        fun setUp() {
            lifecycleOwner = lifecycleCoroutineRule.lifecycleOwner
            coroutineDispatcher = lifecycleCoroutineRule.coroutineDispatcher
            logStore = DbPureeLogStore(dbRule.db)
            clock = ManualClock(Instant.now())
        }

        @Test
        fun postLog_outputMultiple() {
            // given
            val output1 = TestRecordedOutput()
            val output2 = TestRecordedOutput()
            val puree = PureeLogger.Builder(
                lifecycle = lifecycleOwner.lifecycle,
                logSerializer = SampleLogSerializer(),
                logStore = logStore,
            ).apply {
                dispatcher = coroutineDispatcher
                clock = this@OutputTests.clock
                output(
                    output1,
                    SampleLog::class.java
                )
                output(
                    output2,
                    SampleLog::class.java
                )
            }.build()

            // when
            puree.postLog(SampleLog(sequence = 1))

            // then
            assertThat(output1.logs).comparingElementsUsing(JSON_TO_STRING)
                .containsExactly(jsonStringOf("sequence" to 1))
            assertThat(output2.logs).comparingElementsUsing(JSON_TO_STRING)
                .containsExactly(jsonStringOf("sequence" to 1))
        }

        @Test
        fun postLog_outputBuffered() {
            // Given
            val output = TestRecordedBufferedOutput("output", Duration.ofSeconds(1))
            val puree = PureeLogger.Builder(
                lifecycle = lifecycleOwner.lifecycle,
                logSerializer = SampleLogSerializer(),
                logStore = logStore
            ).apply {
                dispatcher = coroutineDispatcher
                clock = this@OutputTests.clock
                output(
                    output,
                    SampleLog::class.java
                )
            }.build()

            // when / then
            puree.postLog(SampleLog(sequence = 1))
            assertThat(output.logs).isEmpty()

            coroutineDispatcher.advanceTimeBy(output.flushInterval.toMillis())
            assertThat(output.logs).comparingElementsUsing(JSON_TO_STRING)
                .containsExactly(jsonStringOf("sequence" to 1))
        }

        @Test
        fun postLog_outputBufferedMultiple() {
            // given
            val outputEvery1s = TestRecordedBufferedOutput(
                uniqueId = "output_every_1s",
                flushInterval = Duration.ofSeconds(1)
            )
            val outputEvery2s = TestRecordedBufferedOutput(
                uniqueId = "output_every_2s",
                flushInterval = Duration.ofSeconds(2)
            )
            val puree = PureeLogger.Builder(
                lifecycle = lifecycleOwner.lifecycle,
                logSerializer = SampleLogSerializer(),
                logStore = logStore
            ).apply {
                dispatcher = coroutineDispatcher
                clock = this@OutputTests.clock
                output(
                    outputEvery1s,
                    SampleLog::class.java
                )
                output(
                    outputEvery2s,
                    SampleLog::class.java
                )
            }.build()

            // when / then
            puree.postLog(SampleLog(sequence = 1))
            assertThat(outputEvery1s.logs).isEmpty()
            assertThat(outputEvery2s.logs).isEmpty()

            coroutineDispatcher.advanceTimeBy(1000)
            clock.updateTime(Duration.ofSeconds(1))
            puree.postLog(SampleLog(sequence = 2))
            assertThat(outputEvery1s.logs).comparingElementsUsing(JSON_TO_STRING)
                .containsExactly(
                    jsonStringOf("sequence" to 1)
                )
            assertThat(outputEvery2s.logs).isEmpty()

            coroutineDispatcher.advanceTimeBy(1000)
            assertThat(outputEvery1s.logs).comparingElementsUsing(JSON_TO_STRING)
                .containsExactly(
                    jsonStringOf("sequence" to 1),
                    jsonStringOf("sequence" to 2)
                )
            assertThat(outputEvery2s.logs).comparingElementsUsing(JSON_TO_STRING)
                .containsExactly(
                    jsonStringOf("sequence" to 1),
                    jsonStringOf("sequence" to 2)
                )
        }

        fun flush() {
            // given
            val outputEvery5s = TestRecordedBufferedOutput(
                uniqueId = "output_every_5s",
                flushInterval = Duration.ofSeconds(5)
            )
            val outputEvery10s = TestRecordedBufferedOutput(
                uniqueId = "output_every_10s",
                flushInterval = Duration.ofSeconds(10)
            )
            val puree = PureeLogger.Builder(
                lifecycle = lifecycleOwner.lifecycle,
                logSerializer = SampleLogSerializer(),
                logStore = logStore
            ).apply {
                dispatcher = coroutineDispatcher
                clock = this@OutputTests.clock
                output(
                    outputEvery5s,
                    SampleLog::class.java
                )
                output(
                    outputEvery10s,
                    SampleLog::class.java
                )
            }.build()

            // when
            puree.postLog(SampleLog(sequence = 1))
            assertThat(outputEvery5s.logs).isEmpty()
            assertThat(outputEvery10s.logs).isEmpty()
            puree.flush()

            // then
            assertThat(outputEvery5s.logs).comparingElementsUsing(JSON_TO_STRING)
                .containsExactly(
                    jsonStringOf("sequence" to 1),
                    jsonStringOf("sequence" to 2)
                )
            assertThat(outputEvery10s.logs).comparingElementsUsing(JSON_TO_STRING)
                .containsExactly(
                    jsonStringOf("sequence" to 1),
                    jsonStringOf("sequence" to 2)
                )
        }
    }

    @RunWith(AndroidJUnit4::class)
    class LifecycleTests {
        @get:Rule
        val lifecycleCoroutineRule = LifecycleCoroutineRule()

        @get:Rule
        internal val dbRule = InMemoryDbRule(PureeDb::class.java)

        private lateinit var lifecycleOwner: TestLifecycleOwner
        private lateinit var coroutineDispatcher: TestCoroutineDispatcher
        private lateinit var logStore: PureeLogStore
        private lateinit var clock: ManualClock

        @Before
        fun setUp() {
            lifecycleOwner = lifecycleCoroutineRule.lifecycleOwner
            coroutineDispatcher = lifecycleCoroutineRule.coroutineDispatcher
            logStore = DbPureeLogStore(dbRule.db)
            clock = ManualClock(Instant.now())
        }

        @Test
        fun suspendResume_flushIntervalHasElapsed() {
            // given
            val output = TestRecordedBufferedOutput("output", Duration.ofSeconds(1))
            val puree = PureeLogger.Builder(
                lifecycle = lifecycleOwner.lifecycle,
                logSerializer = SampleLogSerializer(),
                logStore = logStore
            ).apply {
                dispatcher = coroutineDispatcher
                clock = this@LifecycleTests.clock
                output(
                    output,
                    SampleLog::class.java
                )
            }.build()

            // when / then
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            puree.postLog(SampleLog(sequence = 1))
            assertThat(output.logs).isEmpty()

            clock.updateTime(output.flushInterval)
            coroutineDispatcher.advanceTimeBy(output.flushInterval.toMillis())
            puree.postLog(SampleLog(sequence = 2))
            assertThat(output.logs).isEmpty()

            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            assertThat(output.logs).comparingElementsUsing(JSON_TO_STRING)
                .containsExactly(
                    jsonStringOf("sequence" to 1),
                    jsonStringOf("sequence" to 2)
                )
        }

        @Test
        fun suspendResume_flushIntervalHasNotElapsed() {
            // given
            val output = TestRecordedBufferedOutput("output", Duration.ofSeconds(5))
            val puree = PureeLogger.Builder(
                lifecycle = lifecycleOwner.lifecycle,
                logSerializer = SampleLogSerializer(),
                logStore = logStore
            ).apply {
                dispatcher = coroutineDispatcher
                clock = this@LifecycleTests.clock
                output(
                    output,
                    SampleLog::class.java
                )
            }.build()

            // when / then
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            puree.postLog(SampleLog(sequence = 1))
            assertThat(output.logs).isEmpty()

            clock.updateTime(Duration.ofSeconds(1))
            coroutineDispatcher.advanceTimeBy(1000)
            puree.postLog(SampleLog(sequence = 2))
            assertThat(output.logs).isEmpty()

            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            coroutineDispatcher.advanceTimeBy(1000)
            assertThat(output.logs).isEmpty()

            coroutineDispatcher.advanceTimeBy(3000)
            assertThat(output.logs).comparingElementsUsing(JSON_TO_STRING)
                .containsExactly(
                    jsonStringOf("sequence" to 1),
                    jsonStringOf("sequence" to 2)
                )
        }
    }

    @RunWith(AndroidJUnit4::class)
    class PurgeTests {
        @get:Rule
        val lifecycleCoroutineRule = LifecycleCoroutineRule()

        @get:Rule
        internal val dbRule = InMemoryDbRule(PureeDb::class.java)

        private lateinit var lifecycleOwner: TestLifecycleOwner
        private lateinit var coroutineDispatcher: TestCoroutineDispatcher
        private lateinit var logStore: PureeLogStore
        private lateinit var clock: ManualClock

        @Before
        fun setUp() {
            lifecycleOwner = lifecycleCoroutineRule.lifecycleOwner
            coroutineDispatcher = lifecycleCoroutineRule.coroutineDispatcher
            logStore = DbPureeLogStore(dbRule.db)
            clock = ManualClock(Instant.now())
        }

        @Test
        fun purged() {
            // given
            val output = TestRecordedBufferedOutput(
                "output",
                Duration.ofSeconds(1),
                Duration.ofSeconds(10)
            )
            val puree = PureeLogger.Builder(
                lifecycle = lifecycleOwner.lifecycle,
                logSerializer = SampleLogSerializer(),
                logStore = logStore
            ).apply {
                dispatcher = coroutineDispatcher
                clock = this@PurgeTests.clock
                output(
                    output,
                    SampleLog::class.java
                )
            }.build()

            // when / then
            puree.postLog(SampleLog(sequence = 1))

            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            clock.updateTime(Duration.ofSeconds(10))
            coroutineDispatcher.advanceTimeBy(10000)

            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
            assertThat(output.logs).isEmpty()
        }

        @Test
        fun notPurged() {
            // given
            val output = TestRecordedBufferedOutput(
                "output",
                Duration.ofSeconds(1),
                Duration.ofSeconds(10)
            )
            val puree = PureeLogger.Builder(
                lifecycle = lifecycleOwner.lifecycle,
                logSerializer = SampleLogSerializer(),
                logStore = logStore
            ).apply {
                dispatcher = coroutineDispatcher
                clock = this@PurgeTests.clock
                output(
                    output,
                    SampleLog::class.java
                )
            }.build()

            // when / then
            puree.postLog(SampleLog(sequence = 1))

            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            clock.updateTime(Duration.ofSeconds(5))
            coroutineDispatcher.advanceTimeBy(5000)

            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            assertThat(output.logs).comparingElementsUsing(JSON_TO_STRING)
                .containsExactly(
                    jsonStringOf("sequence" to 1)
                )
        }
    }
}
