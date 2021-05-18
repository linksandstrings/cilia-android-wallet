package com.cilia.wallet.content.actions

import android.net.Uri
import com.mrd.bitlib.model.NetworkParameters
import com.cilia.wallet.MbwManager
import com.cilia.wallet.R
import com.cilia.wallet.activity.StringHandlerActivity
import com.cilia.wallet.content.Action


class UriAction @JvmOverloads constructor(private val onlyWithAddress: Boolean = false) : Action {
    override fun handle(handlerActivity: StringHandlerActivity, content: String): Boolean {
        val manager = MbwManager.getInstance(handlerActivity)
        val uri = manager.contentResolver.resolveUri(content)
        if (uri != null) {
            if (onlyWithAddress && uri.address == null) {
                return false
            }
            handlerActivity.finishOk(uri)
            return true
        }
        return false
    }

    override fun canHandle(network: NetworkParameters, content: String): Boolean {
        return Uri.parse(content) != null
    }
}