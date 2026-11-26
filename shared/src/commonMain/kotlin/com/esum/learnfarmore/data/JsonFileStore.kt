package com.esum.learnfarmore.data

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/** Blocking JSON file store; callers are responsible for running it off the main thread. */
class JsonFileStore(private val root: Path, private val json: Json) {

    fun <T> read(relative: String, serializer: KSerializer<T>): T? {
        val path = Path(root, relative)
        if (!SystemFileSystem.exists(path)) return null
        val text = SystemFileSystem.source(path).buffered().use { it.readString() }
        return json.decodeFromString(serializer, text)
    }

    fun <T> write(relative: String, serializer: KSerializer<T>, value: T) {
        val path = Path(root, relative)
        path.parent?.let { SystemFileSystem.createDirectories(it) }
        val temp = Path("$path.tmp")
        SystemFileSystem.sink(temp).buffered().use { it.writeString(json.encodeToString(serializer, value)) }
        SystemFileSystem.atomicMove(temp, path)
    }

    fun list(directory: String): List<String> {
        val path = Path(root, directory)
        if (!SystemFileSystem.exists(path)) return emptyList()
        return SystemFileSystem.list(path).map { it.name }
    }

    fun delete(relative: String) {
        SystemFileSystem.delete(Path(root, relative), mustExist = false)
    }
}
