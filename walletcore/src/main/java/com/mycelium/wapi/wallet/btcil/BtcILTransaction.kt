package com.mycelium.wapi.wallet.btcil

import com.mrd.bitillib.FeeEstimatorBuilder
import com.mrd.bitillib.UnsignedTransaction
import com.mrd.bitillib.model.BitcoinILTransaction
import com.mycelium.wapi.wallet.BitcoinILBasedTransaction
import com.mycelium.wapi.wallet.coins.CryptoCurrency
import com.mycelium.wapi.wallet.coins.Value

import java.io.Serializable

class BtcILTransaction constructor(type: CryptoCurrency, val destination: BtcILAddress?, val amount: Value?, val feePerKb: Value?)
    : BitcoinILBasedTransaction(type, feePerKb), Serializable {
    fun setTransaction(tx: BitcoinILTransaction) {
        this.tx = tx
        this.isSigned = true
    }

    constructor(coinType: CryptoCurrency, tx: BitcoinILTransaction): this (coinType, null, null, null) {
        setTransaction(tx)
    }

    constructor(coinType: CryptoCurrency, unsignedTx: UnsignedTransaction) : this(coinType, null, null, null){
        this.unsignedTx = unsignedTx
    }

    override fun getEstimatedTransactionSize(): Int {
        val estimatorBuilder = FeeEstimatorBuilder()
        val estimator = if (unsignedTx != null) {
            estimatorBuilder.setArrayOfInputs(unsignedTx!!.fundingOutputs)
                    .setArrayOfOutputs(unsignedTx!!.outputs)
                    .createFeeEstimator()
        } else {
            estimatorBuilder.setLegacyInputs(1)
                    .setLegacyOutputs(2)
                    .createFeeEstimator()
        }
        return estimator.estimateTransactionSize()
    }

    companion object {
        @JvmStatic
        fun to(destination: BtcILAddress, amount: Value, feePerkb: Value): BtcILTransaction {
            return BtcILTransaction(destination.coinType, destination, amount, feePerkb)
        }
    }
}
