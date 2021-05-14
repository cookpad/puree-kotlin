package com.cookpad.puree.kotlin.rule

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineDispatcher
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class LifecycleCoroutineRule : TestWatcher() {
    lateinit var coroutineDispatcher: TestCoroutineDispatcher
        private set
    lateinit var lifecycleOwner: TestLifecycleOwner
        private set

    override fun starting(description: Description?) {
        super.starting(description)
        coroutineDispatcher = TestCoroutineDispatcher()
        lifecycleOwner = TestLifecycleOwner(Lifecycle.State.STARTED)
    }

    override fun finished(description: Description?) {
        super.finished(description)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        coroutineDispatcher.cleanupTestCoroutines()
    }
}
