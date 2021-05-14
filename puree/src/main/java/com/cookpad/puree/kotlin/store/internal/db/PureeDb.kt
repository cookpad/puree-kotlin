package com.cookpad.puree.kotlin.store.internal.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [PureeLogEntity::class], version = 1)
internal abstract class PureeDb : RoomDatabase() {
    abstract fun pureeLogDao(): PureeLogDao
}
