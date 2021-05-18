package com.cilia.wallet.activity.fio.registerdomain.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.mycelium.wapi.wallet.WalletAccount
import com.mycelium.wapi.wallet.coins.Value
import com.mycelium.wapi.wallet.fio.FioAccount

class RegisterFioDomainViewModel : ViewModel() {
    val fioAccountToRegisterName = MutableLiveData<FioAccount>()
    val accountToPayFeeFrom = MutableLiveData<WalletAccount<*>>()
    val registrationFee = MutableLiveData<Value>()
    val domain = MutableLiveData<String>("")
    val isFioDomainAvailable = MutableLiveData<Boolean>(true)
    val isFioDomainValid = MutableLiveData<Boolean>(true)
    val isFioServiceAvailable = MutableLiveData<Boolean>(true)
    val isRenew = MutableLiveData(false)
}