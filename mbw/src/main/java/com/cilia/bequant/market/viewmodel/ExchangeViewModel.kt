package com.mycelium.bequant.market.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.mycelium.bequant.BequantPreference.getLastKnownBalance
import com.mycelium.bequant.remote.trading.model.Balance
import com.cilia.wallet.Utils
import com.mycelium.wapi.wallet.coins.Value


class ExchangeViewModel : ViewModel() {
    val available = MutableLiveData<Value>(getLastKnownBalance())
    val youSend = MutableLiveData(Value.valueOf(Utils.getBtcCoinType(), available.value!!.value))
    val youGet = MutableLiveData(Value.valueOf(Utils.getEthCoinType(), 0))
    val rate = MutableLiveData("")
    val accountBalances = MutableLiveData<Array<Balance>>()
    val tradingBalances = MutableLiveData<Array<Balance>>()
    val isEnoughFundsIncludingFees = MutableLiveData<Boolean>(true)
    val isExchangeEnabled = MutableLiveData<Boolean>(true)
    val youSendText = MutableLiveData<String>()
    val youGetText = MutableLiveData<String>()
}