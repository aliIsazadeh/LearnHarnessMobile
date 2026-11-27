package com.esum.learnfarmore

import androidx.compose.ui.window.ComposeUIViewController
import com.esum.learnfarmore.data.KeychainSecretStore
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
private val container: AppContainer by lazy {
    val documents = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first() as String
    val data = NSURL.fileURLWithPath("$documents/learnharness", isDirectory = true)
    NSFileManager.defaultManager.createDirectoryAtURL(data, withIntermediateDirectories = true, attributes = null, error = null)
    data.setResourceValue(NSNumber(bool = true), forKey = NSURLIsExcludedFromBackupKey, error = null)
    AppContainer(documents, KeychainSecretStore())
}

fun MainViewController() = ComposeUIViewController { App(container) }
