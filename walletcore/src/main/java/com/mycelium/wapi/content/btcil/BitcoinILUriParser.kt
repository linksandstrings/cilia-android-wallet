package com.mycelium.wapi.content.btcil

import com.mrd.bitillib.model.NetworkParameters
import com.mycelium.wapi.content.AssetBitcoinILUriParser
import com.mycelium.wapi.content.AssetUri
import com.mycelium.wapi.wallet.btc.coins.BitcoinMain
import com.mycelium.wapi.wallet.btc.coins.BitcoinTest
import com.mycelium.wapi.wallet.btcil.coins.BitcoinILMain
import com.mycelium.wapi.wallet.btcil.coins.BitcoinILTest
import java.net.URI

class BitcoinILUriParser(override val network: NetworkParameters) : AssetBitcoinILUriParser(network) {
    override fun parse(content: String): AssetUri? {
        try {
            var uri = URI.create(content.trim { it <= ' ' })
            val scheme = uri.scheme
            if (!scheme!!.equals("bitcoinil", ignoreCase = true)) {
                // not a bitcoin URI
                return null
            }
            var schemeSpecific = uri.toString().substring("bitcoinil:".length)
            if (schemeSpecific.startsWith("//")) {
                // Fix for invalid bitcoin URI in the form "bitcoinil://"
                schemeSpecific = schemeSpecific.substring(2)
            }
            uri = URI.create("bitcoinil://$schemeSpecific")

            return parseParameters(uri, if (network.isProdnet) BitcoinILMain.get() else BitcoinILTest.get())
        } catch (e: Exception) {

        }
        return null
    }
}