package com.akira.island

import android.app.Application
import com.akira.island.data.SettingsRepo

class IslandApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SettingsRepo.init(this)
    }
}
