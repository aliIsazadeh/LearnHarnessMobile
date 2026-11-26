package com.esum.learnfarmore.data

import com.esum.learnfarmore.domain.KnowledgeGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class GraphRepository(private val store: JsonFileStore) {

    private val mutex = Mutex()
    private val _graph = MutableStateFlow(KnowledgeGraph())
    val graph: StateFlow<KnowledgeGraph> = _graph.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            _graph.value = runCatching { store.read(FILE, KnowledgeGraph.serializer()) }.getOrNull() ?: KnowledgeGraph()
        }
    }

    suspend fun save(graph: KnowledgeGraph) = withContext(Dispatchers.IO) {
        mutex.withLock {
            store.write(FILE, KnowledgeGraph.serializer(), graph)
            _graph.value = graph
        }
    }

    private companion object {
        const val FILE = "graph.json"
    }
}
