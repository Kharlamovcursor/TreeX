package ru.hdlabs.treex

import android.app.Application
import kotlinx.coroutines.flow.MutableSharedFlow
import ru.hdlabs.treex.di.AppContainer
import ru.hdlabs.treex.diagnostic.AppEventLog

class TreeXApplication : Application() {
    val leaveHint = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        AppEventLog.info("app", "Запуск TreeX")
    }
}
