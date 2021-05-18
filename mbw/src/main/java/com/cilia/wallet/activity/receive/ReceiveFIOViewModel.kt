package com.cilia.wallet.activity.receive

import android.app.Application
import com.cilia.wallet.R


class ReceiveFIOViewModel(application: Application) : ReceiveGenericCoinsViewModel(application) {
    override fun getTitle(): String = context.getString(R.string.receive_title_fio)
}