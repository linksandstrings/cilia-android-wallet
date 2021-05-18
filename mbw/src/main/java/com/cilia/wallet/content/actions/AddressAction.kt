package com.cilia.wallet.content.actions

import com.mrd.bitlib.model.NetworkParameters
import com.cilia.wallet.MbwManager
import com.cilia.wallet.activity.StringHandlerActivity
import com.cilia.wallet.content.Action


class AddressAction : Action {
    override fun handle(handlerActivity: StringHandlerActivity, content: String): Boolean {
        val address = MbwManager.getInstance(handlerActivity).getWalletManager(false).parseAddress(content)
        return if(address.isNotEmpty()) {
            handlerActivity.finishOk(address[0])
            true
        } else {
            false
        }
    }

    override fun canHandle(network: NetworkParameters, content: String): Boolean {
        return true
    }
}