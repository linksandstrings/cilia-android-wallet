package com.cilia.wallet.activity.main.model.fiorequestshistory

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.cilia.wallet.MbwManager

/**
 * Model for [com.cilia.wallet.activity.main.TransactionHistoryFragment]
 */
class FioRequestsHistoryViewModel(application: Application) : AndroidViewModel(application) {
    val mbwManager = MbwManager.getInstance(application)
    val fioRequestHistory = FioRequestsLiveData(mbwManager)
    val addressBook = mbwManager.metadataStorage.allAddressLabels

    fun cacheAddressBook() {
        addressBook.clear()
        addressBook.putAll(mbwManager.metadataStorage.allAddressLabels)
    }
}