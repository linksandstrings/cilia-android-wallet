package com.mycelium.wapi.wallet

import com.mrd.bitillib.UnsignedTransaction
import com.mrd.bitillib.model.BitcoinILTransaction
import com.mycelium.wapi.wallet.coins.CryptoCurrency
import com.mycelium.wapi.wallet.coins.Value

abstract class BitcoinILBasedTransaction protected constructor(type: CryptoCurrency, feePerKb: Value?) : Transaction(type) {
    override fun txBytes(): ByteArray? {
        return tx?.toBytes()
    }

    override fun getId(): ByteArray? {
        return tx?.id!!.bytes
    }
    var tx: BitcoinILTransaction? = null
    var unsignedTx: UnsignedTransaction? = null

}
