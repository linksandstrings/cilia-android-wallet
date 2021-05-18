package com.cilia.wallet.activity.main.address

import android.app.Application
import androidx.fragment.app.FragmentActivity
import com.cilia.wallet.activity.receive.ReceiveCoinsActivity

class AddressFragmentCoinsModel(app: Application) : AddressFragmentViewModel(app) {
    override fun qrClickReaction(activity: FragmentActivity) {
        if (model.account.receiveAddress != null) {
            ReceiveCoinsActivity.callMe(activity, model.account, model.account.canSpend())
        }
    }
}
