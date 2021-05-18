package com.cilia.wallet.activity.main.address

import android.app.Application
import androidx.lifecycle.MutableLiveData
import androidx.fragment.app.FragmentActivity
import asShortStringRes
import com.google.common.base.Optional
import com.mrd.bitlib.model.AddressType
import com.cilia.wallet.MbwManager
import com.cilia.wallet.R
import com.cilia.wallet.event.ReceivingAddressChanged
import com.mycelium.wapi.wallet.btc.AbstractBtcAccount

class AddressFragmentBtcModel(val app: Application) : AddressFragmentViewModel(app) {
    lateinit var currentType: AddressType
    var nextTypeLabel: MutableLiveData<String> = MutableLiveData()

    override fun init() {
        super.init()
        currentType = model.type.value!!
        setNextLabel()
    }

    override fun qrClickReaction(activity: FragmentActivity) {
        currentType = getNextType()
        setNextLabel()


        (model.account as AbstractBtcAccount).setDefaultAddressType(currentType)

        MbwManager.getEventBus().post(ReceivingAddressChanged(model.accountAddress.value!!))
        model.onAddressChange()
    }

    private fun setNextLabel() {
        val nextTypeShort = app.getString(getNextType().asShortStringRes())
        nextTypeLabel.value = app.getString(R.string.tap_next, nextTypeShort)
    }

    private fun getNextType(): AddressType {
        val addressTypes = (model.account as AbstractBtcAccount).availableAddressTypes
        val currentAddressTypeIndex = addressTypes.lastIndexOf(currentType)
        return addressTypes[(currentAddressTypeIndex + 1) % addressTypes.size]
    }
}
