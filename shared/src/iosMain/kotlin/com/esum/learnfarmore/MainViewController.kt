package com.esum.learnfarmore

import androidx.compose.ui.window.ComposeUIViewController
import com.esum.learnfarmore.data.KeychainSecretStore
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

private val container: AppContainer by lazy {
    val documents = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first() as String
    AppContainer(documents, KeychainSecretStore())
}

fun MainViewController() = ComposeUIViewController { App(container) }
