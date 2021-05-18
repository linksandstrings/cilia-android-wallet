package com.mycelium.wapi.wallet.btc.bip44

import com.mrd.bitillib.crypto.InMemoryPrivateKey
import com.mrd.bitlib.crypto.BipDerivationType
import com.mrd.bitlib.model.NetworkParameters
import com.mycelium.wapi.api.Wapi
import com.mycelium.wapi.wallet.KeyCipher
import com.mycelium.wapi.wallet.btc.Bip44BtcAccountBacking
import com.mycelium.wapi.wallet.btc.Reference
import com.mycelium.wapi.wallet.btc.ChangeAddressMode


open class HDPubOnlyAccount(
        context: HDAccountContext,
        keyManagerMap: MutableMap<BipDerivationType, HDAccountKeyManager>,
        network: NetworkParameters,
        backing: Bip44BtcAccountBacking,
        wapi: Wapi
) : HDAccount(context, keyManagerMap, network, backing, wapi, Reference(ChangeAddressMode.NONE)) {
    override fun canSpend(): Boolean = false

    override fun canSign(): Boolean = false
    override fun getBitcoinILPrivateKey(cipher: KeyCipher?): InMemoryPrivateKey {
        TODO("Not yet implemented")
    }
}
