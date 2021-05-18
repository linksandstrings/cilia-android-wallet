package com.cilia.wallet.activity.main.model.transactionhistory

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.cilia.wallet.MbwManager

/**
 * Model for [com.cilia.wallet.activity.main.TransactionHistoryFragment]
 */
class TransactionHistoryModel(application: Application) : AndroidViewModel(application) {
    val mbwManager = MbwManager.getInstance(application)
    val transactionHistory = TransactionHistoryLiveData(mbwManager)
    val addressBook = mbwManager.metadataStorage.allAddressLabels

    fun cacheAddressBook() {
        addressBook.clear()
        addressBook.putAll(mbwManager.metadataStorage.allAddressLabels)
    }
}