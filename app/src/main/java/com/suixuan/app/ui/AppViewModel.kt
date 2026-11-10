package com.suixuan.app.ui

import androidx.lifecycle.SavedStateHandle
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.suixuan.app.AppContainer
import com.suixuan.app.data.DecisionRepository
import com.suixuan.app.data.DraftState
import com.suixuan.app.data.StoredDraw
import com.suixuan.app.domain.ChoiceItem
import com.suixuan.app.domain.DrawEngine
import com.suixuan.app.domain.DrawRequest
import com.suixuan.app.domain.DrawResult
import com.suixuan.app.domain.InputValidator
import com.suixuan.app.domain.ToolType
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Destination(val label: String) {
    WHEEL("转盘"), COIN("硬币"), DICE("骰子"), MAGIC("灵感球"), HISTORY("历史"), TEMPLATES("模板")
}

data class AppState(
    val loading: Boolean = true,
    val destination: Destination = Destination.WHEEL,
    val draft: DraftState = DraftState(),
    val history: List<StoredDraw> = emptyList(),
    val result: DrawResult? = null,
    val busy: Boolean = false,
    val animating: Boolean = false,
    val savingDraft: Boolean = false,
    val pendingSave: Boolean = false,
    val reduceMotion: Boolean = false,
    val error: String? = null,
) {
    val editable get() = !loading && !busy && !savingDraft && !pendingSave
}

/** App-scoped coordinator for the first vertical slice. New tools reuse the same repository. */
class AppViewModel(
    private val repository: DecisionRepository,
    private val engine: DrawEngine,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    initialReduceMotion: Boolean = false,
    private val saveMotionPreference: (Boolean) -> Unit = {},
) : ViewModel() {
    private val mutable = MutableStateFlow(AppState(reduceMotion = initialReduceMotion))
    val state = mutable.asStateFlow()
    private var pending: DrawResult? = null

    init {
        viewModelScope.launch {
            try {
                repository.observeDraft().collect { draft ->
                    mutable.update { it.copy(draft = draft, loading = false) }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutable.update { it.copy(loading = false, error = "读取选项失败，请重新打开应用。") }
            }
        }
        viewModelScope.launch {
            try {
                var first = true
                repository.observeHistory().collect { records ->
                    mutable.update { it.copy(history = records) }
                    if (first) {
                        first = false
                        val activeId = savedState.get<String>("activeDrawId")
                        try {
                            val restored = if (activeId != null) repository.find(activeId)
                                else records.firstOrNull { it.result.request.tool == ToolType.WHEEL }
                            if (savedState.get<String>("activeDrawId") == activeId &&
                                !mutable.value.busy && pending == null) {
                                mutable.update { it.copy(result = restored?.result) }
                            }
                        } catch (error: Exception) {
                            if (error is CancellationException) throw error
                            if (savedState.get<String>("activeDrawId") == activeId) {
                                mutable.update { it.copy(error = "历史已加载，上一条结果暂时无法恢复。") }
                            }
                        }
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutable.update { it.copy(error = "读取历史失败，暂时无法确认保存状态。") }
            }
        }
    }

    fun navigate(destination: Destination) {
        finishAnimation()
        mutable.update { it.copy(destination = destination) }
    }

    fun setReduceMotion(value: Boolean) {
        saveMotionPreference(value)
        mutable.update { it.copy(reduceMotion = value) }
        if (value) finishAnimation()
    }

    fun addCandidate(label: String): Boolean = updateDraft {
        it + ChoiceItem(UUID.randomUUID().toString(), label)
    }

    fun editCandidate(id: String, label: String): Boolean = updateDraft { items ->
        items.map { if (it.id == id) it.copy(label = label) else it }
    }

    fun removeCandidate(id: String) = updateDraft { items -> items.filterNot { it.id == id } }

    fun loadExample() {
        if (mutable.value.draft.items.isEmpty()) updateDraft {
            listOf("食堂", "面馆", "盖饭", "水饺").map { ChoiceItem(UUID.randomUUID().toString(), it) }
        }
    }

    private fun updateDraft(change: (List<ChoiceItem>) -> List<ChoiceItem>): Boolean {
        val current = mutable.value
        if (!current.editable) return false
        val next = try {
            InputValidator.wheelItems(change(current.draft.items), allowEmpty = true)
        } catch (error: IllegalArgumentException) {
            mutable.update { it.copy(error = error.message ?: "请检查选项内容。") }
            return false
        }
        mutable.update { it.copy(savingDraft = true, error = null) }
        viewModelScope.launch {
            try {
                val saved = repository.saveDraft(current.draft.revision, next)
                mutable.update { it.copy(draft = saved, savingDraft = false, result = null) }
                savedState.remove<String>("activeDrawId")
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutable.update { it.copy(savingDraft = false, error = "选项未保存，请重试。${error.message.orEmpty()}") }
            }
        }
        return true
    }

    fun spin() {
        val current = mutable.value
        if (!current.editable || current.draft.items.isEmpty()) return
        // Acquire synchronously, before launching, so rapid clicks cannot create another request.
        mutable.update { it.copy(busy = true, result = null, error = null) }
        try {
            val result = engine.generate(DrawRequest(
                id = UUID.randomUUID().toString(), tool = ToolType.WHEEL, items = current.draft.items,
            ))
            pending = result
            savedState["activeDrawId"] = result.request.id
            viewModelScope.launch { persistPending(result) }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            mutable.update { it.copy(busy = false, error = error.message ?: "暂时无法抽取。") }
        }
    }

    fun retrySave() {
        val result = pending ?: return
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch { persistPending(result) }
    }

    private suspend fun persistPending(result: DrawResult) {
        try {
            // A failed response may have committed. A failed lookup must not mean "not found".
            val existing = repository.find(result.request.id)
            val stored = if (existing != null) {
                check(existing.result == result) { "已保存结果与当前请求不一致。" }
                existing
            } else repository.commit(result)
            pending = null
            val animate = mutable.value.destination == Destination.WHEEL &&
                !mutable.value.reduceMotion && result.request.items.size > 1
            mutable.update {
                it.copy(result = stored.result, busy = animate, animating = animate, pendingSave = false, error = null)
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            mutable.update {
                it.copy(busy = false, animating = false, pendingSave = true,
                    error = "尚未确认保存成功。请重试保存，抽取结果不会重新生成。")
            }
        }
    }

    fun finishAnimation(drawId: String? = null) {
        val current = mutable.value
        if (current.animating && (drawId == null || drawId == current.result?.request?.id)) {
            mutable.update { it.copy(animating = false, busy = false) }
        }
    }

    fun clearHistory() {
        val current = mutable.value
        if (!current.editable) return
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                repository.clearHistory()
                savedState.remove<String>("activeDrawId")
                mutable.update { it.copy(busy = false) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutable.update { it.copy(busy = false, error = "历史未清空，请重试。") }
            }
        }
    }

    fun dismissError() { if (!mutable.value.pendingSave) mutable.update { it.copy(error = null) } }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                AppViewModel(container.repository, container.engine, createSavedStateHandle(),
                    container.preferences.getBoolean("reduce_motion", false)) { value ->
                    container.preferences.edit { putBoolean("reduce_motion", value) }
                }
            }
        }
    }
}
