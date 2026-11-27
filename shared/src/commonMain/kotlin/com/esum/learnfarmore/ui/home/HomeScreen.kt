package com.esum.learnfarmore.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.esum.learnfarmore.AppContainer
import com.esum.learnfarmore.agent.newSession
import com.esum.learnfarmore.data.ProviderKind
import com.esum.learnfarmore.domain.Session
import com.esum.learnfarmore.domain.StepStatus
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    container: AppContainer,
    onOpenSession: (String) -> Unit,
    onOpenGraph: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val sessions by container.sessions.sessions.collectAsState()
    val settings by container.settings.settings.collectAsState()
    val hasKey by produceState<Boolean?>(null, settings) {
        value = !container.settings.apiKey(settings.provider).isNullOrBlank()
    }
    val needsSetup = hasKey == false && (settings.provider == ProviderKind.ANTHROPIC || settings.baseUrl.isBlank())
    val scope = rememberCoroutineScope()
    var showNewSession by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Session?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LearnHarness") },
                actions = {
                    TextButton(onClick = onOpenGraph) { Text("Graph") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNewSession = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New session") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (needsSetup) item { SetupCard(onOpenSettings) }
            if (sessions.isEmpty()) item { EmptyState() }
            items(sessions, key = { it.id }) { session ->
                SessionRow(
                    session = session,
                    onClick = { onOpenSession(session.id) },
                    onDelete = { pendingDelete = session },
                )
            }
        }
    }

    if (showNewSession) {
        NewSessionDialog(
            onDismiss = { showNewSession = false },
            onStart = { goal ->
                showNewSession = false
                scope.launch {
                    val session = newSession(Uuid.random().toString(), goal, container.clock())
                    container.sessions.save(session)
                    onOpenSession(session.id)
                }
            },
        )
    }

    pendingDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete session?") },
            text = { Text("The conversation is removed. Ideas it added stay in your knowledge graph.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch { container.sessions.delete(session.id) }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SetupCard(onOpenSettings: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Connect a model", style = MaterialTheme.typography.titleMedium)
            Text(
                "LearnHarness uses your own API key. It's stored encrypted on this device and sent only to your provider.",
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onOpenSettings) { Text("Open settings") }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("What do you want to understand?", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Start a session with a goal. The teacher first finds where your understanding ends, plans the shortest " +
                "route, then teaches one reasoning step at a time and checks each one. What you learn is kept in your knowledge graph.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SessionRow(session: Session, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            headlineContent = { Text(session.goal, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            supportingContent = { Text(progressLabel(session)) },
            trailingContent = {
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete session") }
            },
        )
    }
}

private fun progressLabel(session: Session): String = when {
    session.plan.isNotEmpty() -> "${session.plan.count { it.status == StepStatus.DONE }} of ${session.plan.size} steps done"
    session.messages.size <= 1 -> "Not started"
    else -> "Finding your starting point"
}

@Composable
private fun NewSessionDialog(onDismiss: () -> Unit, onStart: (String) -> Unit) {
    var goal by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New session") },
        text = {
            OutlinedTextField(
                value = goal,
                onValueChange = { goal = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("What do you want to understand?") },
                placeholder = { Text("e.g. Why does ice float?") },
                minLines = 2,
            )
        },
        confirmButton = {
            TextButton(onClick = { onStart(goal) }, enabled = goal.isNotBlank()) { Text("Start") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
