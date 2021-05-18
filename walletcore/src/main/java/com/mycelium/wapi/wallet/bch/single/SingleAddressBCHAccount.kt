package com.mycelium.wapi.wallet.bch.single

import com.mrd.bitillib.crypto.InMemoryPrivateKey
import com.mrd.bitlib.model.NetworkParameters
import com.mycelium.wapi.api.Wapi
import com.mycelium.wapi.wallet.KeyCipher
import com.mycelium.wapi.wallet.btc.Reference
import com.mycelium.wapi.wallet.SingleAddressBtcAccountBacking
import com.mycelium.wapi.wallet.btc.single.PublicPrivateKeyStore
import com.mycelium.wapi.wallet.btc.single.SingleAddressAccountContext
import com.mycelium.wapi.wallet.btc.single.SingleAddressAccount
import com.mycelium.wapi.wallet.btc.ChangeAddressMode

class SingleAddressBCHAccount(context: SingleAddressAccountContext,
                              keyStore: PublicPrivateKeyStore, network: NetworkParameters,
                              backing: SingleAddressBtcAccountBacking, wapi: Wapi)
    : SingleAddressAccount(context, keyStore, network, backing, wapi, Reference(ChangeAddressMode.NONE)) {
    override fun canSign(): Boolean = false
    override fun getBitcoinILPrivateKey(cipher: KeyCipher?): InMemoryPrivateKey {
        TODO("Not yet implemented")
    }
}