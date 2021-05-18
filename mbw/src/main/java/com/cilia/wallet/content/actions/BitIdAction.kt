package com.cilia.wallet.content.actions

import android.net.Uri
import com.mrd.bitlib.model.NetworkParameters
import com.cilia.wallet.R
import com.cilia.wallet.activity.StringHandlerActivity
import com.cilia.wallet.bitid.BitIDAuthenticationActivity
import com.cilia.wallet.bitid.BitIDSignRequest
import com.cilia.wallet.content.Action
import java.util.*


class BitIdAction : Action {
    override fun handle(handlerActivity: StringHandlerActivity, content: String): Boolean {
        if (!content.toLowerCase(Locale.US).startsWith("bitid:")) {
            return false
        }
        val request = BitIDSignRequest.parse(Uri.parse(content))
        if (!request.isPresent) {
            handlerActivity.finishError(R.string.unrecognized_format)
            //started with bitid, but unable to parse, so we handled it.
        } else {
            handlerActivity.finishOk(request.get())
        }
        return true
    }

    override fun canHandle(network: NetworkParameters, content: String): Boolean {
        return content.toLowerCase().startsWith("bitid:")
    }
}