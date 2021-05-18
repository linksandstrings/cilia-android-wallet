package com.mycelium.wapi.wallet

import com.mycelium.wapi.wallet.coins.CryptoCurrency

import java.io.Serializable

interface Address : Serializable {
    val coinType: CryptoCurrency

    // An address for the particular asset could have some subtypes.
    // For example, for BTC we have
    // To have an ability to detect and compare types in generic way
    // the subType is stored as string
    fun getSubType(): String

    fun getBytes(): ByteArray
}
