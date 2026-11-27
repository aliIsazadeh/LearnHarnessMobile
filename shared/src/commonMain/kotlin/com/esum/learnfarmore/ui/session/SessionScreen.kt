package com.esum.learnfarmore.ui.session

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.esum.learnfarmore.AppContainer
import com.esum.learnfarmore.agent.Question
import com.esum.learnfarmore.agent.QuestionPurpose
import com.esum.learnfarmore.agent.SessionStatus
import com.esum.learnfarmore.agent.status
import com.esum.learnfarmore.domain.Confidence
import com.esum.learnfarmore.domain.LearnerAnswer
import com.esum.learnfarmore.domain.PlanStep
import com.esum.learnfarmore.domain.StepStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    sessionId: String,
    container: AppContainer,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel = viewModel(key = sessionId) { SessionViewModel(sessionId, container) }
    val state by viewModel.state.collectAsState()
    val session = state.session

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(session?.goal.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (session == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("This session no longer exists.")
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
                if (session.plan.isNotEmpty()) PlanStrip(session.plan)
                val items = remember(session.messages) { session.transcript(container.json) }
                TranscriptList(items, Modifier.weight(1f))
                HorizontalDivider()
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    val status = session.status()
                    val error = state.error
                    when {
                        state.running -> Thinking()
                        error != null -> ErrorPanel(error, state.missingKey, viewModel::retry, onOpenSettings)
                        status is SessionStatus.AwaitingAnswer -> AnswerDock(status.question, viewModel::answer)
                        status is SessionStatus.AwaitingLearner -> Composer(viewModel::send)
                        else -> Button(onClick = viewModel::retry, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanStrip(plan: List<PlanStep>) {
    val done = plan.count { it.status == StepStatus.DONE }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            "Plan: $done of ${plan.size} done",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = { done / plan.size.toFloat() },
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(plan, key = { it.id }) { step -> StepChip(step) }
        }
    }
}

@Composable
private fun StepChip(step: PlanStep) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(50),
        color = when (step.status) {
            StepStatus.ACTIVE -> colors.primaryContainer
            StepStatus.DONE -> colors.surfaceVariant
            StepStatus.PENDING -> colors.surface
        },
        border = if (step.status == StepStatus.PENDING) BorderStroke(1.dp, colors.outlineVariant) else null,
    ) {
        Text(
            text = if (step.status == StepStatus.DONE) "✓ ${step.title}" else step.title,
            modifier = Modifier.widthIn(max = 220.dp).padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (step.status == StepStatus.ACTIVE) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TranscriptList(items: List<TranscriptItem>, modifier: Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(items.size) {
        if (items.isNotEmpty()) listState.animateScrollToItem(items.lastIndex)
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.key }) { item ->
            when (item) {
                is TranscriptItem.Learner -> LearnerBubble(item.text)
                is TranscriptItem.Teacher -> Text(item.text, style = MaterialTheme.typography.bodyLarge)
                is TranscriptItem.Asked -> QuestionCard(item.question, item.answer)
                is TranscriptItem.Recorded -> Text(
                    "Added to your graph: ${item.statement}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
private fun LearnerBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Text(text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        }
    }
}

@Composable
private fun QuestionCard(question: Question, answer: LearnerAnswer?) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                purposeLabel(question.purpose),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(question.prompt, style = MaterialTheme.typography.bodyLarge)
            if (answer != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(answer.answer, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(confidenceLabel(answer.confidence), style = MaterialTheme.typography.labelSmall)
                if (answer.thinking.isNotBlank()) {
                    Text(answer.thinking, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
                }
            }
        }
    }
}

@Composable
private fun Thinking() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Text("Thinking…", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ErrorPanel(message: String, missingKey: Boolean, onRetry: () -> Unit, onOpenSettings: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (missingKey) OutlinedButton(onClick = onOpenSettings) { Text("Settings") }
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnswerDock(question: Question, onSubmit: (Question, LearnerAnswer) -> Unit) {
    var answer by rememberSaveable(question.callId) { mutableStateOf("") }
    var thinking by rememberSaveable(question.callId) { mutableStateOf("") }
    var confidence by rememberSaveable(question.callId) { mutableStateOf<Confidence?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (question.options.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                question.options.forEach { option ->
                    FilterChip(selected = answer == option, onClick = { answer = option }, label = { Text(option) })
                }
            }
        } else {
            OutlinedTextField(
                value = answer,
                onValueChange = { answer = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Your answer") },
                maxLines = 4,
            )
        }
        OutlinedTextField(
            value = thinking,
            onValueChange = { thinking = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Think out loud (optional)") },
            maxLines = 4,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Confidence.entries.forEach { value ->
                FilterChip(
                    selected = confidence == value,
                    onClick = { confidence = value },
                    label = { Text(confidenceLabel(value)) },
                )
            }
        }
        Button(
            onClick = {
                val chosen = confidence ?: return@Button
                onSubmit(question, LearnerAnswer(answer.trim(), thinking.trim(), chosen))
            },
            enabled = answer.isNotBlank() && confidence != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Submit") }
    }
}

@Composable
private fun Composer(onSend: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Reply or ask about anything unclear") },
            maxLines = 4,
        )
        IconButton(
            onClick = {
                onSend(text)
                text = ""
            },
            enabled = text.isNotBlank(),
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
        }
    }
}

private fun purposeLabel(purpose: QuestionPurpose) = when (purpose) {
    QuestionPurpose.PROBE -> "Where are you now?"
    QuestionPurpose.PREDICT -> "Predict"
    QuestionPurpose.CHECK -> "Check"
}

private fun confidenceLabel(confidence: Confidence) = when (confidence) {
    Confidence.GUESS -> "Guess"
    Confidence.UNSURE -> "Unsure"
    Confidence.CONFIDENT -> "Confident"
}
