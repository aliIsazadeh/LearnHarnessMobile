package com.esum.learnfarmore.ui.graph

import androidx.compose.ui.geometry.Offset
import com.esum.learnfarmore.domain.KnowledgeGraph
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Deterministic force-directed (Fruchterman–Reingold) layout. Positions are normalized so the
 * graph fits in [-1, 1] on both axes with its aspect ratio preserved. O(n² · iterations).
 */
fun layoutGraph(graph: KnowledgeGraph, iterations: Int = 300): Map<String, Offset> {
    val nodes = graph.propositions
    if (nodes.isEmpty()) return emptyMap()
    if (nodes.size == 1) return mapOf(nodes.single().id to Offset.Zero)

    val n = nodes.size
    val index = nodes.withIndex().associate { (i, node) -> node.id to i }
    val edges = nodes.flatMap { node ->
        val to = index.getValue(node.id)
        node.premiseIds.mapNotNull { index[it] }.map { from -> from to to }
    }
    val xs = FloatArray(n) { cos(2 * PI * it / n).toFloat() }
    val ys = FloatArray(n) { sin(2 * PI * it / n).toFloat() }
    val k = 1f / sqrt(n.toFloat())
    val dx = FloatArray(n)
    val dy = FloatArray(n)

    repeat(iterations) { iteration ->
        dx.fill(0f)
        dy.fill(0f)
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val ox = xs[i] - xs[j]
                val oy = ys[i] - ys[j]
                val dist = max(sqrt(ox * ox + oy * oy), MIN_DISTANCE)
                val force = k * k / dist
                dx[i] += ox / dist * force
                dy[i] += oy / dist * force
                dx[j] -= ox / dist * force
                dy[j] -= oy / dist * force
            }
        }
        edges.forEach { (a, b) ->
            val ox = xs[a] - xs[b]
            val oy = ys[a] - ys[b]
            val dist = max(sqrt(ox * ox + oy * oy), MIN_DISTANCE)
            val force = dist * dist / k
            dx[a] -= ox / dist * force
            dy[a] -= oy / dist * force
            dx[b] += ox / dist * force
            dy[b] += oy / dist * force
        }
        val temperature = START_TEMPERATURE * (1f - iteration / iterations.toFloat()) + MIN_TEMPERATURE
        for (i in 0 until n) {
            dx[i] -= xs[i] * GRAVITY
            dy[i] -= ys[i] * GRAVITY
            val length = max(sqrt(dx[i] * dx[i] + dy[i] * dy[i]), 1e-6f)
            val step = min(length, temperature)
            xs[i] += dx[i] / length * step
            ys[i] += dy[i] / length * step
        }
    }

    val centerX = (xs.min() + xs.max()) / 2
    val centerY = (ys.min() + ys.max()) / 2
    val half = max(max(xs.max() - xs.min(), ys.max() - ys.min()) / 2, 1e-3f)
    return nodes.withIndex().associate { (i, node) ->
        node.id to Offset((xs[i] - centerX) / half, (ys[i] - centerY) / half)
    }
}

private const val MIN_DISTANCE = 0.01f
private const val START_TEMPERATURE = 0.1f
private const val MIN_TEMPERATURE = 0.001f
private const val GRAVITY = 0.05f
