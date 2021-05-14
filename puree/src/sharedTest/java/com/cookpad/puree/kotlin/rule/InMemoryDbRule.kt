package com.cookpad.puree.kotlin.rule

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class InMemoryDbRule<T : RoomDatabase>(private val dbType: Class<out T>) : TestWatcher() {
    lateinit var db: T

    override fun starting(description: Description?) {
        super.starting(description)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), dbType)
            .allowMainThreadQueries()
            .build()
    }

    override fun finished(description: Description?) {
        super.finished(description)
        db.close()
    }
}
