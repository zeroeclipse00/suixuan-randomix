package com.suixuan.app.data

import androidx.room.withTransaction
import com.suixuan.app.domain.ChoiceItem
import com.suixuan.app.domain.DrawResult
import com.suixuan.app.domain.InputValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

data class DraftState(val revision: Long = 0, val items: List<ChoiceItem> = emptyList())

data class StoredDraw(
    val sequence: Long,
    val result: DrawResult,
    val committedAtMillis: Long = result.request.createdAtMillis,
)

class DraftConflictException : IllegalStateException("候选项已被更新，请重新载入后保存")
class DrawConflictException : IllegalStateException("同一抽取编号对应了不同内容，未覆盖已有结果")

/** UI depends on this interface, so storage errors can be injected in coordinator tests. */
interface DecisionRepository {
    fun observeDraft(): Flow<DraftState>
    fun observeHistory(): Flow<List<StoredDraw>>
    suspend fun saveDraft(expectedRevision: Long, items: List<ChoiceItem>): DraftState
    suspend fun commit(result: DrawResult): StoredDraw
    suspend fun find(drawId: String): StoredDraw?
    suspend fun clearHistory()
}

class SuixuanRepository(private val database: SuixuanDatabase) : DecisionRepository {
    private val dao = database.dao()

    override fun observeDraft(): Flow<DraftState> = dao.observeDraft()
        .map { it?.toState() ?: DraftState() }
        .flowOn(Dispatchers.IO)

    override fun observeHistory(): Flow<List<StoredDraw>> = dao.observeHistory()
        .map { records -> records.map { it.toStoredDraw() } }
        .flowOn(Dispatchers.IO)

    override suspend fun saveDraft(expectedRevision: Long, items: List<ChoiceItem>): DraftState {
        require(expectedRevision >= 0) { "候选项版本不能为负数" }
        val normalized = InputValidator.wheelItems(items, allowEmpty = true)
        val json = SnapshotCodec.encodeItems(normalized)
        return database.withTransaction {
            val currentRevision = dao.getDraft()?.revision ?: 0
            if (currentRevision != expectedRevision) throw DraftConflictException()
            val next = DraftEntity(revision = Math.addExact(currentRevision, 1), itemsJson = json)
            dao.writeDraft(next)
            next.toState()
        }
    }

    override suspend fun commit(result: DrawResult): StoredDraw {
        SnapshotCodec.validate(result)
        // Encode before suspending to isolate the operation from mutable caller lists.
        val snapshotJson = SnapshotCodec.encode(result)
        val frozen = SnapshotCodec.decode(snapshotJson)
        return database.withTransaction {
            val existing = dao.find(frozen.request.id)
            if (existing != null) {
                val stored = existing.toStoredDraw()
                if (stored.result != frozen) throw DrawConflictException()
                return@withTransaction stored
            }
            val pending = HistoryEntity(
                drawId = frozen.request.id,
                snapshotJson = snapshotJson,
                committedAtMillis = System.currentTimeMillis(),
            )
            val sequence = dao.insertHistory(pending)
            dao.trimHistory(HISTORY_LIMIT)
            pending.copy(sequence = sequence).toStoredDraw()
        }
    }

    override suspend fun find(drawId: String): StoredDraw? = dao.find(drawId)?.toStoredDraw()

    override suspend fun clearHistory() {
        database.withTransaction { dao.clearHistory() }
    }

    private fun DraftEntity.toState() = DraftState(revision, SnapshotCodec.decodeItems(itemsJson))

    private fun HistoryEntity.toStoredDraw() = StoredDraw(sequence, SnapshotCodec.decode(snapshotJson), committedAtMillis)

    companion object {
        const val HISTORY_LIMIT = 1000
    }
}
