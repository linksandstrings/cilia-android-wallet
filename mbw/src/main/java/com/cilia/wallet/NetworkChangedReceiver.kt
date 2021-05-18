package com.cilia.wallet

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.logging.Level
import java.util.logging.Logger

class NetworkChangedReceiver : BroadcastReceiver() {
    // We receive this event on wallet start, but this would start heavy init, which we don't want to.
    var wasInited: Boolean = false

    val logger = Logger.getLogger(NetworkChangedReceiver::class.java.simpleName)

    override fun onReceive(context: Context, intent: Intent) {
        if (!wasInited) {
            wasInited = true
            return
        }
        if (intent.action == "android.net.conn.CONNECTIVITY_CHANGE") {
            val mbwManager = MbwManager.getInstance(context)
            val connected = Utils.isConnected(context)
            logger.log(Level.INFO, "Connectivity status has been changed. Connected: $connected")
            mbwManager.getWalletManager(false).isNetworkConnected = connected
            mbwManager.wapi.setNetworkConnected(connected)
        }
    }
}
