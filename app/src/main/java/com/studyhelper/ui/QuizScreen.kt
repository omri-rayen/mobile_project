package com.studyhelper.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.studyhelper.viewmodel.StudyViewModel
import kotlinx.coroutines.flow.collectLatest

data class QuizQuestion(
    val question: String,
    val options: List<String>,
    val answer: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(viewModel: StudyViewModel, snackbarHostState: SnackbarHostState) {
    var topic by remember { mutableStateOf("") }
    val quizJson by viewModel.quizJson.collectAsState()
    val loading by viewModel.quizLoading.collectAsState()

    val questions = remember(quizJson) {
        if (quizJson.isBlank()) emptyList()
        else try {
            val type = object : TypeToken<List<QuizQuestion>>() {}.type
            val jsonStr = quizJson.let { raw ->
                val start = raw.indexOf('[')
                val end = raw.lastIndexOf(']')
                if (start >= 0 && end > start) raw.substring(start, end + 1) else raw
            }
            Gson().fromJson<List<QuizQuestion>>(jsonStr, type)
        } catch (_: Exception) {
            emptyList()
        }
    }

    val selectedAnswers = remember(questions) { mutableStateMapOf<Int, Int>() }
    var submitted by remember(questions) { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.error.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Generate Quiz") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = topic,
                onValueChange = { topic = it },
                label = { Text("Topic (e.g. Python, Computer Networks)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Button(
                onClick = {
                    submitted = false
                    selectedAnswers.clear()
                    viewModel.generateQuiz(topic.trim())
                },
                enabled = topic.isNotBlank() && !loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Generating\u2026")
                } else {
                    Icon(Icons.Default.Quiz, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Generate")
                }
            }

            if (questions.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(questions) { index, q ->
                        QuizQuestionCard(
                            index = index,
                            question = q,
                            selectedAnswer = selectedAnswers[index],
                            submitted = submitted,
                            onSelect = { if (!submitted) selectedAnswers[index] = it }
                        )
                    }
                    item {
                        Button(
                            onClick = { submitted = true },
                            enabled = !submitted && selectedAnswers.size == questions.size,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Submit Answers")
                        }
                        if (submitted) {
                            val correct = questions.indices.count {
                                selectedAnswers[it] == questions[it].answer
                            }
                            Text(
                                text = "Score: $correct / ${questions.size}",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            } else if (quizJson.isNotBlank() && !loading) {
                Text(
                    "Failed to parse quiz. Please try again.",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun QuizQuestionCard(
    index: Int,
    question: QuizQuestion,
    selectedAnswer: Int?,
    submitted: Boolean,
    onSelect: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Q${index + 1}: ${question.question}",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.height(8.dp))
            question.options.forEachIndexed { optIndex, option ->
                val isSelected = selectedAnswer == optIndex
                val isCorrect = question.answer == optIndex
                val bgColor = when {
                    !submitted -> MaterialTheme.colorScheme.surface
                    isSelected && isCorrect -> Color(0xFF4CAF50)
                    isSelected && !isCorrect -> Color(0xFFF44336)
                    isCorrect -> Color(0xFF4CAF50).copy(alpha = 0.3f)
                    else -> MaterialTheme.colorScheme.surface
                }
                Surface(
                    color = bgColor,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(4.dp)
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelect(optIndex) },
                            enabled = !submitted
                        )
                        Text(text = option)
                    }
                }
            }
        }
    }
}
