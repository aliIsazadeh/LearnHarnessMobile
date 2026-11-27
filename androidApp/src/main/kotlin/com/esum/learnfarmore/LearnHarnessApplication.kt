package com.esum.learnfarmore

import android.app.Application
import com.esum.learnfarmore.data.AndroidSecretStore

class LearnHarnessApplication : Application() {
    val container: AppContainer by lazy { AppContainer(filesDir.absolutePath, AndroidSecretStore(this)) }
}
