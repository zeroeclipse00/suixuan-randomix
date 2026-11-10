package com.suixuan.app.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.suixuan.app.domain.ChoiceItem
import com.suixuan.app.domain.DrawRequest
import com.suixuan.app.domain.DrawResult
import com.suixuan.app.domain.ToolType
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SuixuanRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: SuixuanDatabase
    private lateinit var repository: SuixuanRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, SuixuanDatabase::class.java).build()
        repository = SuixuanRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun newDatabaseDoesNotInsertDemoData() = runBlocking {
        assertEquals(DraftState(), repository.observeDraft().first())
        assertTrue(repository.observeHistory().first().isEmpty())
    }

    @Test
    fun draftSaveNormalizesAndRejectsStaleVersionWithoutPartialWrite() = runBlocking {
        val saved = repository.saveDraft(0, listOf(ChoiceItem("one", "  食堂  ")))
        assertEquals(1L, saved.revision)
        assertEquals("食堂", saved.items.single().label)

        var conflicted = false
        try {
            repository.saveDraft(0, listOf(ChoiceItem("two", "面馆")))
        } catch (_: DraftConflictException) {
            conflicted = true
        }
        assertTrue(conflicted)
        assertEquals(saved, repository.observeDraft().first())
    }

    @Test
    fun sameOperationIsIdempotentAndDifferentContentIsRejected() = runBlocking {
        val first = repository.commit(wheel("draw-one"))
        assertEquals(first, repository.commit(wheel("draw-one")))

        var conflicted = false
        try {
            repository.commit(wheel("draw-one", selectedIndex = 1))
        } catch (_: DrawConflictException) {
            conflicted = true
        }
        assertTrue(conflicted)
        assertEquals(listOf(first), repository.observeHistory().first())
    }

    @Test
    fun storedSnapshotSurvivesCallerMutationAndDraftChanges() = runBlocking {
        val callerItems = mutableListOf(ChoiceItem("food", "食堂"), ChoiceItem("noodles", "面馆"))
        val result = DrawResult(
            request = DrawRequest("draw-snapshot", ToolType.WHEEL, callerItems, createdAtMillis = 100),
            selectedItemId = "food", selectedIndex = 0, displayText = "食堂",
        )
        repository.commit(result)
        callerItems.clear()
        repository.saveDraft(0, listOf(ChoiceItem("new", "盖饭")))

        val stored = requireNotNull(repository.find("draw-snapshot"))
        assertEquals(listOf("食堂", "面馆"), stored.result.request.items.map { it.label })
        assertEquals("食堂", stored.result.displayText)
    }

    @Test
    fun sequenceOrdersHistoryEvenWhenRequestClockMovesBackwards() = runBlocking {
        val first = repository.commit(wheel("earlier", time = 1000))
        val second = repository.commit(wheel("later", time = 10))
        assertTrue(second.sequence > first.sequence)
        assertEquals(listOf("later", "earlier"), repository.observeHistory().first().map { it.result.request.id })
    }

    @Test
    fun clearingHistoryPreservesDraftAndDoesNotReinsertResult() = runBlocking {
        val draft = repository.saveDraft(0, listOf(ChoiceItem("one", "食堂")))
        repository.commit(wheel("to-clear"))
        repository.clearHistory()

        assertTrue(repository.observeHistory().first().isEmpty())
        assertNull(repository.find("to-clear"))
        assertEquals(draft, repository.observeDraft().first())
    }

    @Test
    fun commitAndCapacityTrimmingPreserveNewestThousand() = runBlocking {
        database.withTransaction {
            repeat(SuixuanRepository.HISTORY_LIMIT) { index ->
                val result = wheel("seed-$index")
                database.dao().insertHistory(HistoryEntity(
                    drawId = result.request.id,
                    snapshotJson = SnapshotCodec.encode(result),
                    committedAtMillis = index.toLong(),
                ))
            }
        }
        val newest = repository.commit(wheel("newest"))
        val history = repository.observeHistory().first()
        assertEquals(SuixuanRepository.HISTORY_LIMIT, history.size)
        assertEquals(newest, history.first())
        assertNull(repository.find("seed-0"))
        assertNotEquals("seed-0", history.last().result.request.id)
    }

    @Test
    fun databaseReopenRestoresDraftAndFullResult() = runBlocking {
        val name = "suixuan-test-${UUID.randomUUID()}.db"
        var fileDatabase: SuixuanDatabase? = null
        try {
            fileDatabase = SuixuanDatabase.open(context, name)
            val before = SuixuanRepository(fileDatabase)
            val draft = before.saveDraft(0, listOf(ChoiceItem("one", "食堂")))
            val record = before.commit(wheel("persisted"))
            fileDatabase.close()

            fileDatabase = SuixuanDatabase.open(context, name)
            val after = SuixuanRepository(fileDatabase)
            assertEquals(draft, after.observeDraft().first())
            assertEquals(record, after.find("persisted"))
        } finally {
            fileDatabase?.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun inconsistentResultIsNotWritten() = runBlocking {
        var rejected = false
        try {
            repository.commit(wheel("invalid").copy(selectedItemId = "noodles"))
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
        assertTrue(repository.observeHistory().first().isEmpty())
    }

    private fun wheel(id: String, selectedIndex: Int = 0, time: Long = 100): DrawResult {
        val items = listOf(ChoiceItem("food", "食堂"), ChoiceItem("noodles", "面馆"))
        return DrawResult(
            request = DrawRequest(id, ToolType.WHEEL, items, createdAtMillis = time),
            selectedItemId = items[selectedIndex].id,
            selectedIndex = selectedIndex,
            displayText = items[selectedIndex].label,
        )
    }
}
