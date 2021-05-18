package com.cilia.wallet.activity.receive

import android.app.Application
import com.cilia.wallet.R
import com.cilia.wallet.activity.util.toStringWithUnit
import com.mycelium.wapi.wallet.WalletAccount
import com.mycelium.wapi.wallet.coins.Value

open class ReceiveGenericCoinsViewModel(application: Application) : ReceiveCoinsViewModel(application) {
    private lateinit var accountLabel: String

    override fun init(account: WalletAccount<*>, hasPrivateKey: Boolean, showIncomingUtxo: Boolean) {
        super.init(account, hasPrivateKey, showIncomingUtxo)
        accountLabel = account.coinType.symbol
        model = ReceiveCoinsModel(getApplication(), account, accountLabel, showIncomingUtxo)
    }

    override fun getFormattedValue(sum: Value) = sum.toStringWithUnit()

    override fun getCurrencyName() = account.coinType.symbol

    override fun getTitle(): String {
        return if (Value.isNullOrZero(model.amount.value)) {
            context.getString(R.string.address_title, accountLabel)
        } else {
            context.getString(R.string.payment_request)
        }
    }
}