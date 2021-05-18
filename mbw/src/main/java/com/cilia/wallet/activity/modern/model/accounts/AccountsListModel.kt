package com.cilia.wallet.activity.modern.model.accounts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.cilia.wallet.MbwManager

/**
 * Model for [com.cilia.wallet.activity.modern.adapter.AccountListAdapter]
 */
class AccountsListModel(application: Application) : AndroidViewModel(application) {
    val accountsData : AccountsViewLiveData
    init {
        val mbwManager = MbwManager.getInstance(application)
        accountsData = AccountsViewLiveData(mbwManager)
    }
}