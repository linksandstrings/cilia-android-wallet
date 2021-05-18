package com.mycelium.wapi.wallet.genericdb

import com.mycelium.generated.wallet.database.FeeEstimation
import com.mycelium.generated.wallet.database.WalletDB
import com.mycelium.wapi.wallet.FeeEstimationsGeneric
import com.mycelium.wapi.wallet.coins.AssetInfo

class FeeEstimationsBacking(walletDB: WalletDB) {
    private val queries = walletDB.feeEstimationsQueries

    fun getEstimationForCurrency(currency: AssetInfo): FeeEstimationsGeneric? {
        var queryCurrency = queries.selectByCurrency(currency)
        val estimation = queryCurrency.executeAsOneOrNull()
                ?: return null
        return FeeEstimationsGeneric(estimation.low, estimation.economy, estimation.normal, estimation.high, estimation.lastCheck)
    }

    fun updateFeeEstimation(estimation: FeeEstimation) {
        queries.insertFullObject(estimation)
    }
}