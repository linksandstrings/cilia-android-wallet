package com.mycelium.wapi.wallet.btcil.bip44

import com.mrd.bitillib.crypto.HdKeyNode
import com.mycelium.wapi.wallet.manager.Config


data class UnrelatedHDAccountConfigIL(val hdKeyNodes: List<HdKeyNode>) : Config

class AdditionalHDAccountConfigIL : Config

data class ExternalSignaturesAccountConfigIL(val hdKeyNodes: List<HdKeyNode>,
                                           val provider: ExternalSignatureProviderIL,
                                           val accountIndex: Int) : Config