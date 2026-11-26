package com.esum.learnfarmore.data

import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings

@OptIn(ExperimentalSettingsImplementation::class)
class KeychainSecretStore : SecretStore {

    private val keychain = KeychainSettings(service = "com.esum.learnfarmore")

    override fun get(name: String): String? = keychain.getStringOrNull(name)

    override fun put(name: String, value: String) = keychain.putString(name, value)

    override fun remove(name: String) = keychain.remove(name)
}
