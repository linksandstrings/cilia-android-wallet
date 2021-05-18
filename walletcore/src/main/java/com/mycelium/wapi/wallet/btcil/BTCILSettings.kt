package com.mycelium.wapi.wallet.btcil

import com.mrd.bitlib.model.AddressType
import com.mycelium.wapi.wallet.CurrencySettings

data class BTCSettingsIL(
        var defaultAddressType: AddressType,
        var changeAddressModeReference: ReferenceIL<ChangeAddressModeIL>
) : CurrencySettings {
    fun setChangeAddressMode(changeAddressMode: ChangeAddressModeIL) =
        changeAddressModeReference.set(changeAddressMode)
}


class ReferenceIL<T>(private var referent: T?) {
    fun set(newVal: T) {
        referent = newVal
    }

    fun get(): T? {
        return referent
    }
}