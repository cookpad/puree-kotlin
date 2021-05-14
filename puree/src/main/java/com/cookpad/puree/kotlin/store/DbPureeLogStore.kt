package com.cookpad.puree.kotlin.store

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.Room
import com.cookpad.puree.kotlin.output.PureeBufferedLog
import com.cookpad.puree.kotlin.store.internal.db.PureeDb
import com.cookpad.puree.kotlin.store.internal.db.PureeLogDao
import com.cookpad.puree.kotlin.store.internal.db.PureeLogEntity
import org.json.JSONObject
import java.time.Duration
import java.time.Instant

/**
 * Stores the logs in a SQLite database.
 */
class DbPureeLogStore @VisibleForTesting internal constructor(db: PureeDb) : PureeLogStore {
    private val logDao: PureeLogDao = db.pureeLogDao()

    /**
     * Creates a new DbPureeLogStore.
     *
     * @param context The context.
     * @param name The name of the database.
     */
    constructor(context: Context, name: String) : this(
        Room.databaseBuilder(context, PureeDb::class.java, name)
            .enableMultiInstanceInvalidation()
            .build()
    )

    override fun add(outputId: String, bufferedLog: PureeBufferedLog) {
        logDao.insert(
            PureeLogEntity(
                outputId = outputId,
                createdAt = bufferedLog.createdAt.toString(),
                log = bufferedLog.log.toString()
            )
        )
    }

    override fun get(outputId: String, maxCount: Int): List<PureeBufferedLog> = logDao
        .select(outputId, maxCount)
        .map {
            PureeBufferedLog(
                id = it.id,
                createdAt = Instant.parse(it.createdAt),
                log = JSONObject(it.log)
            )
        }

    override fun remove(outputId: String, bufferedLogs: List<PureeBufferedLog>) {
        logDao.delete(bufferedLogs.map { PureeLogEntity(id = it.id) })
    }

    override fun purgeLogsWithAge(outputId: String, now: Instant, age: Duration) {
        logDao.deleteCreatedUpTo(now.minus(age).toString())
    }
}
