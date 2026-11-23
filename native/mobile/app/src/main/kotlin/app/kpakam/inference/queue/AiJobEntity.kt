package app.kpakam.inference.queue

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** One pending AI job, stored on the phone. A job runs at most once per [idempotencyKey]. */
@Entity(tableName = "ai_jobs", indices = [Index(value = ["idempotencyKey"], unique = true), Index("state")])
data class AiJobEntity(
    @PrimaryKey val id: String,
    val idempotencyKey: String,
    val task: String,
    val state: String,
    val inputText: String?,
    val audioPath: String?,
    val audioMime: String?,
    val sizeBytes: Long,
    val approved: Boolean,
    val reason: String,
    val queueReason: String?,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val runs: Int,
    val resultJson: String?,
    val resultSource: String?,
    val resultReason: String?,
    val resultPromptVersion: String?,
    val resultConfidence: String?,
    val reviewed: Boolean,
)

@Dao
interface AiJobDao {
    /** Jobs the trader still needs to see: waiting, failed, or ready to check. */
    @Query("SELECT * FROM ai_jobs WHERE reviewed = 0 AND state != 'CANCELLED' ORDER BY createdAtMs")
    fun observeOpen(): Flow<List<AiJobEntity>>

    @Query("SELECT * FROM ai_jobs WHERE state = :state ORDER BY createdAtMs")
    suspend fun byState(state: String): List<AiJobEntity>

    @Query("SELECT * FROM ai_jobs WHERE id = :id")
    suspend fun get(id: String): AiJobEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(job: AiJobEntity): Long

    @Update
    suspend fun update(job: AiJobEntity)
}

/** The phone-only Room database. Journals live in Firestore; only the AI job queue lives here. */
@Database(entities = [AiJobEntity::class], version = 1, exportSchema = false)
abstract class KpakamDatabase : RoomDatabase() {
    abstract fun aiJobs(): AiJobDao
}
