package com.cookpad.puree.kotlin.rule

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.ExternalResource

@OptIn(ExperimentalCoroutinesApi::class)
class LifecycleCoroutineRule : ExternalResource() {
    lateinit var coroutineDispatcher: TestDispatcher
        private set
    lateinit var lifecycleOwner: TestLifecycleOwner
        private set

    override fun before() {
        coroutineDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(coroutineDispatcher)
        lifecycleOwner = TestLifecycleOwner(Lifecycle.State.STARTED)
    }

    override fun after() {
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        Dispatchers.resetMain()
    }
}
