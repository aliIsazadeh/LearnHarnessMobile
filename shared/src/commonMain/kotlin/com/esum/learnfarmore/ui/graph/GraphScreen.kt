package com.esum.learnfarmore.ui.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.esum.learnfarmore.AppContainer
import com.esum.learnfarmore.domain.KnowledgeGraph
import com.esum.learnfarmore.domain.Proposition
import com.esum.learnfarmore.domain.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphScreen(container: AppContainer, onBack: () -> Unit, onOpenSession: (String) -> Unit) {
    val graph by container.graph.graph.collectAsState()
    val sessions by container.sessions.sessions.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Knowledge graph") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (graph.propositions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Ideas you come to hold in your sessions appear here, linked to the ideas they follow from.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                PrimaryTabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Map") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("List") })
                }
                if (tab == 0) {
                    GraphCanvas(graph, selectedId, { selectedId = it }, Modifier.weight(1f).fillMaxWidth())
                    PropositionDetail(graph, selectedId, sessions, onSelect = { selectedId = it }, onOpenSession)
                } else {
                    PropositionList(graph, Modifier.weight(1f)) { id ->
                        selectedId = id
                        tab = 0
                    }
                }
            }
        }
    }
}

@Composable
private fun GraphCanvas(
    graph: KnowledgeGraph,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier,
) {
    val positions by produceState(emptyMap<String, Offset>(), graph) {
        value = withContext(Dispatchers.Default) { layoutGraph(graph) }
    }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val textMeasurer = rememberTextMeasurer()
    val colors = MaterialTheme.colorScheme
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurface)
    val nodeRadius = with(LocalDensity.current) { 9.dp.toPx() }
    val labelWidth = with(LocalDensity.current) { 140.dp.roundToPx() }

    Canvas(
        modifier = modifier
            .clipToBounds()
            .pointerInput(Unit) {
                detectTransformGestures { _, panChange, zoomChange, _ ->
                    zoom = (zoom * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
                    pan += panChange
                }
            }
            .pointerInput(positions) {
                detectTapGestures { tap ->
                    val transform = ViewTransform(Size(size.width.toFloat(), size.height.toFloat()), zoom, pan)
                    val hit = positions.entries.minByOrNull { (transform.toScreen(it.value) - tap).getDistance() }
                    val close = hit != null && (transform.toScreen(hit.value) - tap).getDistance() <= nodeRadius * 3
                    onSelect(if (close) hit.key else null)
                }
            },
    ) {
        val transform = ViewTransform(size, zoom, pan)
        graph.propositions.forEach { node ->
            val to = positions[node.id] ?: return@forEach
            node.premiseIds.forEach { premiseId ->
                val from = positions[premiseId] ?: return@forEach
                val highlighted = selectedId == node.id || selectedId == premiseId
                drawArrow(
                    from = transform.toScreen(from),
                    to = transform.toScreen(to),
                    color = if (highlighted) colors.primary else colors.outlineVariant,
                    inset = nodeRadius,
                )
            }
        }
        val showLabels = positions.size <= LABEL_NODE_LIMIT || zoom >= LABEL_ZOOM
        graph.propositions.forEach { node ->
            val position = positions[node.id] ?: return@forEach
            val center = transform.toScreen(position)
            val selected = node.id == selectedId
            drawCircle(
                color = if (selected) colors.primary else colors.secondary,
                radius = if (selected) nodeRadius * 1.4f else nodeRadius,
                center = center,
            )
            if (showLabels || selected) {
                val layout = textMeasurer.measure(
                    text = node.statement,
                    style = labelStyle,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    constraints = Constraints(maxWidth = labelWidth),
                )
                drawText(layout, topLeft = Offset(center.x - layout.size.width / 2f, center.y + nodeRadius * 1.6f))
            }
        }
    }
}

private class ViewTransform(size: Size, zoom: Float, pan: Offset) {
    private val scale = min(size.width, size.height) / 2f * FIT * zoom
    private val origin = Offset(size.width / 2f, size.height / 2f) + pan

    fun toScreen(position: Offset): Offset = origin + position * scale
}

private fun DrawScope.drawArrow(from: Offset, to: Offset, color: Color, inset: Float) {
    val angle = atan2(to.y - from.y, to.x - from.x)
    val tip = Offset(to.x - cos(angle) * inset, to.y - sin(angle) * inset)
    val stroke = 1.5.dp.toPx()
    val head = 7.dp.toPx()
    drawLine(color, from, tip, strokeWidth = stroke)
    drawLine(color, tip, Offset(tip.x - cos(angle - ARROW_ANGLE) * head, tip.y - sin(angle - ARROW_ANGLE) * head), strokeWidth = stroke)
    drawLine(color, tip, Offset(tip.x - cos(angle + ARROW_ANGLE) * head, tip.y - sin(angle + ARROW_ANGLE) * head), strokeWidth = stroke)
}

@Composable
private fun PropositionDetail(
    graph: KnowledgeGraph,
    selectedId: String?,
    sessions: List<Session>,
    onSelect: (String) -> Unit,
    onOpenSession: (String) -> Unit,
) {
    val proposition = selectedId?.let(graph::byId)
    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (proposition == null) {
                Text(
                    "${graph.propositions.size} ideas. Tap one to see what it rests on and where it came from. Pinch to zoom.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(proposition.statement, style = MaterialTheme.typography.titleMedium)
                LinkedIdeas("Follows from", proposition.premiseIds.mapNotNull(graph::byId), onSelect)
                LinkedIdeas("Leads to", graph.conclusionsOf(proposition.id), onSelect)
                val session = sessions.firstOrNull { it.id == proposition.sessionId }
                if (session != null) {
                    TextButton(onClick = { onOpenSession(session.id) }) {
                        Text("From: ${session.goal}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkedIdeas(label: String, ideas: List<Proposition>, onSelect: (String) -> Unit) {
    if (ideas.isEmpty()) return
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    ideas.forEach { idea ->
        Text(
            "• ${idea.statement}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().clickable { onSelect(idea.id) }.padding(vertical = 2.dp),
        )
    }
}

@Composable
private fun PropositionList(graph: KnowledgeGraph, modifier: Modifier, onSelect: (String) -> Unit) {
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(graph.propositions.asReversed(), key = { it.id }) { proposition ->
            val premises = proposition.premiseIds.size
            ListItem(
                modifier = Modifier.clickable { onSelect(proposition.id) },
                headlineContent = { Text(proposition.statement) },
                supportingContent = {
                    Text(if (premises == 0) "Foundation" else "Follows from $premises ${if (premises == 1) "idea" else "ideas"}")
                },
            )
        }
    }
}

private const val MIN_ZOOM = 0.5f
private const val MAX_ZOOM = 4f
private const val FIT = 0.85f
private const val LABEL_NODE_LIMIT = 25
private const val LABEL_ZOOM = 1.6f
private const val ARROW_ANGLE = 0.45f
