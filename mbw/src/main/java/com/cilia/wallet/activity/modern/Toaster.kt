package com.cilia.wallet.activity.modern

import android.annotation.SuppressLint
import android.content.Context
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.cilia.wallet.R
import com.cilia.wallet.Utils

/**
 * Helper class that makes it easy to let a new toast replace another if they
 * come in rapid succession
 */
class Toaster(val context: Context) {

    private var fragment: Fragment? = null

    constructor(fragment: Fragment) : this(fragment.context!!) {
        this.fragment = fragment
    }


    fun toast(resourceId: Int, shortDuration: Boolean) {
        // Resolve the message from the resource id
        try {
            toast(context.resources.getString(resourceId), shortDuration)
        } catch (ignore: Exception) {
        }
    }

    fun toast(message: String, shortDuration: Boolean) {
        cancelCurrentToast()
        if (fragment != null && !fragment!!.isAdded) {
            return
        }
        currentToast = Toast.makeText(context, message, Toast.LENGTH_SHORT).apply {
            duration = if (shortDuration) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
            show()
        }
    }

    @SuppressLint("StringFormatInvalid")
    @JvmOverloads
    fun toastConnectionError(additionInfo: String? = "") {
        if (Utils.isConnected(context)) {
            toast(context.getString(R.string.no_server_connection, additionInfo), false)
        } else {
            toast(R.string.no_network_connection, true)
        }
    }

    companion object {
        private var currentToast: Toast? = null

        @JvmStatic
        fun onStop() {
            cancelCurrentToast()
        }

        private fun cancelCurrentToast() {
            currentToast?.cancel()
            currentToast = null
        }
    }
}