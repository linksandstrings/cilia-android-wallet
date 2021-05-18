package com.mycelium.wapi.wallet.providers

import com.mycelium.wapi.api.Wapi
import com.mycelium.wapi.api.WapiException
import com.mycelium.wapi.wallet.FeeEstimationsGeneric
import com.mycelium.wapi.wallet.btcil.coins.BitcoinILMain
import com.mycelium.wapi.wallet.btcil.coins.BitcoinILTest
import com.mycelium.wapi.wallet.coins.Value
import com.mycelium.wapi.wallet.genericdb.FeeEstimationsBacking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

open class BtcILFeeProvider(testnet: Boolean, private val wapi: Wapi, private val feeBacking: FeeEstimationsBacking) : FeeProvider {
    final override val coinType = if (testnet) {
        BitcoinILTest.get()!!
    } else {
        BitcoinILMain.get()!!
    }

    override var estimation = feeBacking.getEstimationForCurrency(coinType)
            ?: FeeEstimationsGeneric(Value.valueOf(coinType, 1000),
                    Value.valueOf(coinType, 3000),
                    Value.valueOf(coinType, 6000),
                    Value.valueOf(coinType, 8000),
                    0)

    override suspend fun updateFeeEstimationsAsync() {
        // we try to get fee estimation from server
        estimation = withContext(Dispatchers.IO) {
            try {
                val response = wapi.bitcoinILMinerFeeEstimations
                val oldStyleFeeEstimation = response.result.feeEstimation
                fun convert(blocks: Int): Value {
                    val estimate = oldStyleFeeEstimation.getEstimation(blocks)
                    return Value.valueOf(coinType, estimate.longValue)
                }
                val newEstimation = FeeEstimationsGeneric(convert(20), convert(10), convert(3), convert(1),
                        System.currentTimeMillis()
                )
                //if all ok we return requested new fee estimation
                feeBacking.updateFeeEstimation(newEstimation)
                return@withContext newEstimation
            } catch (ex: WapiException) {
                return@withContext estimation
            }
        }
    }
}