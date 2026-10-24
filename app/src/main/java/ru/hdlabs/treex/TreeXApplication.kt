package ru.hdlabs.treex

import android.app.Application
import ru.hdlabs.treex.di.AppContainer

class TreeXApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
