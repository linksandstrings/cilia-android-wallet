package com.cilia.wallet.content.actions

import com.mrd.bitlib.crypto.InMemoryPrivateKey
import com.mrd.bitlib.model.NetworkParameters
import com.cilia.wallet.activity.StringHandlerActivity
import com.cilia.wallet.content.Action


class PrivateKeyAction : Action {
    override fun handle(handlerActivity: StringHandlerActivity, content: String): Boolean {
        val key = getPrivateKey(handlerActivity.network, content)
                ?: return false
        val btcil_key = getBtcilPrivateKey(handlerActivity.bitcoinILNetwork, content)
        handlerActivity.finishOk(key,btcil_key)
        return true
    }

    override fun canHandle(network: NetworkParameters, content: String): Boolean {
        return isPrivKey(network, content)
    }

    companion object {
        @JvmStatic
        fun getPrivateKey(network: NetworkParameters, content: String) =
                InMemoryPrivateKey.fromBase58String(content, network).orNull()
                        ?: InMemoryPrivateKey.fromBase58MiniFormat(content, network).orNull()

        private fun isPrivKey(network: NetworkParameters, content: String) =
                getPrivateKey(network, content) != null

        fun getBtcilPrivateKey(network: com.mrd.bitillib.model.NetworkParameters, content: String) =
                com.mrd.bitillib.crypto.InMemoryPrivateKey.fromBase58String(content, network).orNull()
                        ?: com.mrd.bitillib.crypto.InMemoryPrivateKey.fromBase58MiniFormat(content, network).orNull()
    }
}