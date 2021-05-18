package com.mycelium.demo.world

import android.app.Application
import android.util.Log
import com.mycelium.modularizationtools.CommunicationManager

class WorldApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CommunicationManager.init(this)
        try {
            CommunicationManager.getInstance().requestPair("com.mycelium.demo.hello")
        } catch (se: SecurityException) {
            Log.w("WorldApplication", se.message!!)
        }
    }

    companion object {
        val sentMessages = mutableListOf<String>()
    }
}
