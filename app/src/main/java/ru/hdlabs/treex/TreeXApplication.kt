package ru.hdlabs.treex

import android.app.Application
import ru.hdlabs.treex.di.AppContainer
import ru.hdlabs.treex.diagnostic.AppEventLog

class TreeXApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        AppEventLog.info("app", "Запуск TreeX")
    }
}
