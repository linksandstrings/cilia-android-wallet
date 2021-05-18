package com.mycelium.wapi.wallet.btcil.bip44

import com.mrd.bitillib.crypto.BipDerivationType
import com.mrd.bitillib.model.NetworkParameters
import com.mycelium.wapi.api.Wapi
import com.mycelium.wapi.wallet.btcil.Bip44BtcILAccountBacking
import com.mycelium.wapi.wallet.btcil.ReferenceIL
import com.mycelium.wapi.wallet.btcil.ChangeAddressModeIL


open class HDPubOnlyAccountIL(
        context: HDAccountContextIL,
        keyManagerMap: MutableMap<BipDerivationType, HDAccountKeyManagerIL>,
        network: NetworkParameters,
        backing: Bip44BtcILAccountBacking,
        wapi: Wapi
) : HDAccountIL(context, keyManagerMap, network, backing, wapi, ReferenceIL(ChangeAddressModeIL.NONE)) {
    override fun canSpend(): Boolean = false

    override fun canSign(): Boolean = false
}
