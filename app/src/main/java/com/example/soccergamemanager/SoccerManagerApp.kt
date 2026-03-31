package com.example.soccergamemanager

import android.app.Application
import com.example.soccergamemanager.data.AppDatabase
import com.example.soccergamemanager.data.SettingsStore
import com.example.soccergamemanager.data.SoccerRepository

class SoccerManagerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(application: Application) {
    private val database = AppDatabase.build(application)

    val repository = SoccerRepository(
        seasonDao = database.seasonDao(),
        playerDao = database.playerDao(),
        gameDao = database.gameDao(),
        availabilityDao = database.availabilityDao(),
        assignmentDao = database.assignmentDao(),
        goalDao = database.goalDao(),
    )
    val settingsStore = SettingsStore(application)
}
