package com.esum.learnfarmore.data

import com.esum.learnfarmore.domain.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SessionRepository(private val store: JsonFileStore) {

    private val mutex = Mutex()
    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions: StateFlow<List<Session>> = _sessions.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            _sessions.value = store.list(DIRECTORY)
                .filter { it.endsWith(EXTENSION) }
                .mapNotNull { runCatching { store.read("$DIRECTORY/$it", Session.serializer()) }.getOrNull() }
                .sortedByDescending { it.updatedAt }
        }
    }

    fun get(id: String): Session? = _sessions.value.firstOrNull { it.id == id }

    suspend fun save(session: Session) = withContext(Dispatchers.IO) {
        mutex.withLock {
            store.write(fileName(session.id), Session.serializer(), session)
            _sessions.update { current ->
                (current.filterNot { it.id == session.id } + session).sortedByDescending { it.updatedAt }
            }
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            store.delete(fileName(id))
            _sessions.update { current -> current.filterNot { it.id == id } }
        }
    }

    private fun fileName(id: String) = "$DIRECTORY/$id$EXTENSION"

    private companion object {
        const val DIRECTORY = "sessions"
        const val EXTENSION = ".json"
    }
}
