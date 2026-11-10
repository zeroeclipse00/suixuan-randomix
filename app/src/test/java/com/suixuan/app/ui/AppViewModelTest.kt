package com.suixuan.app.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.suixuan.app.data.DecisionRepository
import com.suixuan.app.data.DraftConflictException
import com.suixuan.app.data.DraftState
import com.suixuan.app.data.StoredDraw
import com.suixuan.app.domain.ChoiceItem
import com.suixuan.app.domain.DrawEngine
import com.suixuan.app.domain.DrawRequest
import com.suixuan.app.domain.DrawResult
import com.suixuan.app.domain.RandomSource
import com.suixuan.app.domain.ToolType
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    private lateinit var dispatcher: TestDispatcher
    private lateinit var store: ViewModelStore

    @Before
    fun setUp() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        store = ViewModelStore()
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun rapidClicksAcquireBusyGateBeforeCommitCoroutineRuns() = runTest(dispatcher) {
        val random = CountingRandomSource()
        val repository = FakeRepository().apply { commitGate = CompletableDeferred() }
        val viewModel = createViewModel(repository, random)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.loading)

        viewModel.spin()
        viewModel.spin()
        viewModel.spin()

        // Generation is synchronous, while the commit coroutine has not started yet.
        assertEquals(1, random.calls)
        assertTrue(viewModel.state.value.busy)
        assertTrue(repository.attemptedResults.isEmpty())
        runCurrent()
        assertEquals(1, repository.attemptedResults.size)
        assertTrue(repository.history.value.isEmpty())

        requireNotNull(repository.commitGate).complete(Unit)
        advanceUntilIdle()
        assertEquals(1, random.calls)
        assertEquals(1, repository.history.value.size)
        assertEquals(repository.history.value.single().result, viewModel.state.value.result)
        assertFalse(viewModel.state.value.busy)
    }

    @Test
    fun lostCommitResponseRetriesLookupAndRestoresSameResultWithoutResampling() = runTest(dispatcher) {
        val random = CountingRandomSource()
        val repository = FakeRepository().apply { failAfterCommitOnce = true }
        val viewModel = createViewModel(repository, random)
        advanceUntilIdle()

        viewModel.spin()
        advanceUntilIdle()
        val committed = repository.history.value.single()
        assertTrue(viewModel.state.value.pendingSave)
        assertFalse(viewModel.state.value.busy)
        assertNull(viewModel.state.value.result)
        assertEquals(1, repository.attemptedResults.size)

        viewModel.retrySave()
        advanceUntilIdle()

        assertEquals(1, random.calls)
        assertEquals(1, repository.attemptedResults.size)
        assertEquals(listOf(committed), repository.history.value)
        assertEquals(committed.result, viewModel.state.value.result)
        assertFalse(viewModel.state.value.pendingSave)
        assertFalse(viewModel.state.value.busy)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun rolledBackCommitRetriesExactlySameEnvelopeInsteadOfGeneratingAgain() = runTest(dispatcher) {
        val random = CountingRandomSource()
        val repository = FakeRepository().apply { failBeforeCommitOnce = true }
        val viewModel = createViewModel(repository, random)
        advanceUntilIdle()

        viewModel.spin()
        advanceUntilIdle()
        val original = repository.attemptedResults.single()
        assertTrue(viewModel.state.value.pendingSave)
        assertTrue(repository.history.value.isEmpty())
        assertNull(viewModel.state.value.result)

        // A new spin cannot bypass the unresolved save and silently reroll.
        viewModel.spin()
        assertEquals(1, random.calls)
        viewModel.retrySave()
        advanceUntilIdle()

        assertEquals(1, random.calls)
        assertEquals(listOf(original, original), repository.attemptedResults)
        assertEquals(original, repository.history.value.single().result)
        assertEquals(original, viewModel.state.value.result)
        assertFalse(viewModel.state.value.pendingSave)
    }

    @Test
    fun clearingHistoryAndLateAnimationCallbacksCannotResurrectOrFinishAnotherDraw() = runTest(dispatcher) {
        val random = CountingRandomSource()
        val repository = FakeRepository()
        val viewModel = createViewModel(repository, random, reduceMotion = false)
        advanceUntilIdle()

        viewModel.spin()
        advanceUntilIdle()
        val oldId = requireNotNull(viewModel.state.value.result).request.id
        assertTrue(viewModel.state.value.animating)
        viewModel.finishAnimation(oldId)
        viewModel.clearHistory()
        advanceUntilIdle()
        assertEquals(1, repository.clearCalls)
        assertTrue(repository.history.value.isEmpty())

        viewModel.finishAnimation(oldId)
        advanceUntilIdle()
        assertTrue(repository.history.value.isEmpty())
        assertTrue(viewModel.state.value.history.isEmpty())
        assertEquals(1, repository.attemptedResults.size)

        viewModel.spin()
        advanceUntilIdle()
        val newId = requireNotNull(viewModel.state.value.result).request.id
        assertNotEquals(oldId, newId)
        viewModel.finishAnimation(oldId)
        assertTrue(viewModel.state.value.animating)
        assertTrue(viewModel.state.value.busy)
        assertEquals(listOf(newId), repository.history.value.map { it.result.request.id })
        assertEquals(2, random.calls)

        viewModel.finishAnimation(newId)
        assertFalse(viewModel.state.value.animating)
        assertFalse(viewModel.state.value.busy)
    }

    @Test
    fun delayedInitialRestoreCannotOverwriteDrawCompletedAfterLookupStarted() = runTest(dispatcher) {
        val random = CountingRandomSource()
        val repository = FakeRepository().apply {
            seed(wheel("previous-draw", selectedIndex = 1))
            gatedFindId = "previous-draw"
            findGate = CompletableDeferred()
        }
        val viewModel = createViewModel(
            repository, random,
            savedState = SavedStateHandle(mapOf("activeDrawId" to "previous-draw")),
        )
        runCurrent()
        assertFalse(viewModel.state.value.loading)
        assertEquals(1, repository.findCalls.count { it == "previous-draw" })

        viewModel.spin()
        advanceUntilIdle()
        val newResult = requireNotNull(viewModel.state.value.result)
        assertNotEquals("previous-draw", newResult.request.id)
        assertEquals("食堂", newResult.displayText)

        requireNotNull(repository.findGate).complete(Unit)
        advanceUntilIdle()

        assertEquals(newResult, viewModel.state.value.result)
        assertEquals(1, random.calls)
        assertEquals(2, viewModel.state.value.history.size)
        assertEquals(newResult.request.id, viewModel.state.value.history.first().result.request.id)
    }

    @Test
    fun failedInitialRestoreDoesNotTerminateSubsequentHistoryCollection() = runTest(dispatcher) {
        val repository = FakeRepository().apply {
            seed(wheel("previous-draw"))
            failFindIdOnce = "previous-draw"
        }
        val viewModel = createViewModel(
            repository, CountingRandomSource(),
            savedState = SavedStateHandle(mapOf("activeDrawId" to "previous-draw")),
        )
        advanceUntilIdle()
        assertEquals(1, repository.findCalls.count { it == "previous-draw" })

        // Simulate another repository writer after the one-off restoration lookup failed.
        val added = repository.commit(wheel("later-draw", selectedIndex = 1))
        advanceUntilIdle()

        assertEquals(listOf("later-draw", "previous-draw"),
            viewModel.state.value.history.map { it.result.request.id })
        assertEquals(added, viewModel.state.value.history.first())
    }

    private fun createViewModel(
        repository: DecisionRepository,
        random: RandomSource,
        reduceMotion: Boolean = true,
        savedState: SavedStateHandle = SavedStateHandle(),
    ): AppViewModel = AppViewModel(
        repository = repository,
        engine = DrawEngine(random),
        savedState = savedState,
        initialReduceMotion = reduceMotion,
    ).also { store.put("test-view-model", it) }

    private fun wheel(id: String, selectedIndex: Int = 0): DrawResult {
        val items = listOf(ChoiceItem("food", "食堂"), ChoiceItem("noodles", "面馆"))
        return DrawResult(
            request = DrawRequest(id, ToolType.WHEEL, items, createdAtMillis = 100),
            selectedItemId = items[selectedIndex].id,
            selectedIndex = selectedIndex,
            displayText = items[selectedIndex].label,
        )
    }

    private class CountingRandomSource : RandomSource {
        var calls: Int = 0
            private set

        override fun nextInt(bound: Int): Int {
            require(bound > 0)
            calls += 1
            return 0
        }
    }

    private class FakeRepository : DecisionRepository {
        private val draft = MutableStateFlow(DraftState(
            revision = 1,
            items = listOf(ChoiceItem("food", "食堂"), ChoiceItem("noodles", "面馆")),
        ))
        val history = MutableStateFlow<List<StoredDraw>>(emptyList())
        val attemptedResults = mutableListOf<DrawResult>()
        var commitGate: CompletableDeferred<Unit>? = null
        var failBeforeCommitOnce = false
        var failAfterCommitOnce = false
        var gatedFindId: String? = null
        var findGate: CompletableDeferred<Unit>? = null
        var failFindIdOnce: String? = null
        val findCalls = mutableListOf<String>()
        var clearCalls = 0
        private var sequence = 0L

        override fun observeDraft(): Flow<DraftState> = draft
        override fun observeHistory(): Flow<List<StoredDraw>> = history

        override suspend fun saveDraft(expectedRevision: Long, items: List<ChoiceItem>): DraftState {
            if (draft.value.revision != expectedRevision) throw DraftConflictException()
            return DraftState(expectedRevision + 1, items.toList()).also { draft.value = it }
        }

        override suspend fun commit(result: DrawResult): StoredDraw {
            attemptedResults += result
            commitGate?.await()
            if (failBeforeCommitOnce) {
                failBeforeCommitOnce = false
                throw IOException("Injected transaction rollback")
            }
            val stored = find(result.request.id) ?: seed(result)
            check(stored.result == result)
            if (failAfterCommitOnce) {
                failAfterCommitOnce = false
                throw IOException("Injected lost response after successful commit")
            }
            return stored
        }

        override suspend fun find(drawId: String): StoredDraw? {
            findCalls += drawId
            val found = history.value.firstOrNull { it.result.request.id == drawId }
            if (drawId == gatedFindId) findGate?.await()
            if (drawId == failFindIdOnce) {
                failFindIdOnce = null
                throw IOException("Injected initial restoration lookup failure")
            }
            return found
        }

        fun seed(result: DrawResult): StoredDraw = StoredDraw(++sequence, result).also {
            history.value = listOf(it) + history.value
        }

        override suspend fun clearHistory() {
            clearCalls += 1
            history.value = emptyList()
        }
    }
}
