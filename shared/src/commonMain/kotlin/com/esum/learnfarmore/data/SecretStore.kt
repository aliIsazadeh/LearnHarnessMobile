package com.esum.learnfarmore.data

/** Platform-encrypted storage for API keys. Calls may block. */
interface SecretStore {
    fun get(name: String): String?
    fun put(name: String, value: String)
    fun remove(name: String)
}
