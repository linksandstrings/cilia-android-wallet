package com.cilia.wallet.content.actions

import com.mrd.bitlib.model.NetworkParameters
import com.cilia.wallet.MbwManager
import com.cilia.wallet.activity.StringHandlerActivity
import com.cilia.wallet.content.Action

class AddressStringAction : Action {
    override fun handle(handlerActivity: StringHandlerActivity, content: String): Boolean {
        handlerActivity.finishOk(content)
        return true
    }

    override fun canHandle(network: NetworkParameters, content: String): Boolean {
        return true
    }
}