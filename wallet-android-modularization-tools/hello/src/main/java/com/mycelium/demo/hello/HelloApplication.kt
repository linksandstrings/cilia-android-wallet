package com.mycelium.demo.hello

import android.app.Application
import android.content.Intent
import android.util.Log
import com.mycelium.modularizationtools.CommunicationManager
import com.mycelium.modularizationtools.ModuleMessageReceiver

class HelloApplication : Application(), ModuleMessageReceiver {
    override fun getIcon(): Int = R.mipmap.ic_launcher

    override fun onCreate() {
        super.onCreate()
        CommunicationManager.init(this)
        try {
            CommunicationManager.getInstance().requestPair("com.mycelium.demo.world")
        } catch (se: SecurityException) {
            Log.w("HelloApplication", se.message!!)
        }
    }

    override fun onMessage(callingPackageName: String, intent: Intent) {
        when (callingPackageName) {
            "com.mycelium.demo.world" -> {
                val msg = intent.getStringExtra("message")
                Log.d("HelloApplication", "Message received: $msg")
                if (msg!!.contains("5")) {
                    startActivity(Intent(applicationContext, HelloActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            else -> Log.e("HelloApplication", "We don't know what to talk with $callingPackageName")
        }
    }
}