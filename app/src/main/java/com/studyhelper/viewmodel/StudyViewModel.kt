package com.studyhelper.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.studyhelper.data.HistoryDao
import com.studyhelper.data.HistoryEntry
import com.studyhelper.network.OpenAiService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StudyViewModel(private val dao: HistoryDao) : ViewModel() {

    // Ask AI
    private val _askResponse = MutableStateFlow("")
    val askResponse: StateFlow<String> = _askResponse

    private val _askLoading = MutableStateFlow(false)
    val askLoading: StateFlow<Boolean> = _askLoading

    // Summarize
    private val _summaryResponse = MutableStateFlow("")
    val summaryResponse: StateFlow<String> = _summaryResponse

    private val _summaryLoading = MutableStateFlow(false)
    val summaryLoading: StateFlow<Boolean> = _summaryLoading

    // Quiz
    private val _quizJson = MutableStateFlow("")
    val quizJson: StateFlow<String> = _quizJson

    private val _quizLoading = MutableStateFlow(false)
    val quizLoading: StateFlow<Boolean> = _quizLoading

    // Error
    private val _error = MutableSharedFlow<String>()
    val error: SharedFlow<String> = _error

    // History
    val historyEntries: StateFlow<List<HistoryEntry>> = dao.getAllEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun askAi(question: String) {
        viewModelScope.launch {
            _askLoading.value = true
            _askResponse.value = ""
            val result = OpenAiService.chat(question)
            _askLoading.value = false
            result.onSuccess { response ->
                _askResponse.value = response
                dao.insertEntry(
                    HistoryEntry(type = "ASK", title = question, response = response)
                )
            }.onFailure { e ->
                _error.emit(e.message ?: "Unknown error")
            }
        }
    }

    fun summarize(notes: String) {
        viewModelScope.launch {
            _summaryLoading.value = true
            _summaryResponse.value = ""
            val prompt = "Please summarize the following notes concisely:\n\n$notes"
            val result = OpenAiService.chat(prompt, maxTokens = 1024)
            _summaryLoading.value = false
            result.onSuccess { response ->
                _summaryResponse.value = response
                val title = if (notes.length > 80) notes.take(80) + "\u2026" else notes
                dao.insertEntry(
                    HistoryEntry(type = "SUMMARY", title = title, response = response)
                )
            }.onFailure { e ->
                _error.emit(e.message ?: "Unknown error")
            }
        }
    }

    fun generateQuiz(topic: String) {
        viewModelScope.launch {
            _quizLoading.value = true
            _quizJson.value = ""
            val prompt = """Generate exactly 5 multiple-choice questions about "$topic".
Return ONLY valid JSON — no explanation, no markdown.
Format: [{"question":"...","options":["A","B","C","D"],"answer":0}, ...]
where "answer" is the 0-based index of the correct option."""
            val result = OpenAiService.chat(prompt, maxTokens = 2048)
            _quizLoading.value = false
            result.onSuccess { response ->
                _quizJson.value = response
                dao.insertEntry(
                    HistoryEntry(type = "QUIZ", title = topic, response = response)
                )
            }.onFailure { e ->
                _error.emit(e.message ?: "Unknown error")
            }
        }
    }

    fun deleteEntry(entry: HistoryEntry) {
        viewModelScope.launch { dao.deleteEntry(entry) }
    }
}

class StudyViewModelFactory(private val dao: HistoryDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return StudyViewModel(dao) as T
    }
}
