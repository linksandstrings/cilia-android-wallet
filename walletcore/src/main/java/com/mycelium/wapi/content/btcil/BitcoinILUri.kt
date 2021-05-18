package com.mycelium.wapi.content.btcil

import com.mycelium.wapi.content.AssetUri
import com.mycelium.wapi.content.WithCallback
import com.mycelium.wapi.wallet.Address
import com.mycelium.wapi.wallet.coins.Value
import java.lang.StringBuilder


class BitcoinILUri(address: Address?, value: Value?, label: String?, override val callbackURL: String? = null, scheme: String = "bitcoinil")
    : AssetUri(address, value, label, scheme), WithCallback {
    companion object {
        @JvmStatic
        fun from(address: Address?, value: Value?, label: String?, callbackURL: String? = null): BitcoinILUri {
            return BitcoinILUri(address, value, label, callbackURL)
        }
    }

    override fun equals(other: Any?): Boolean {
        val uri = other as BitcoinILUri
        if(this.address != uri.address || this.value != uri.value ||
                this.label != uri.label || this.callbackURL != uri.callbackURL){
            return false
        }
        return true
    }

    override fun toString(): String {
        val result = StringBuilder("bitcoinil:")

        // detect first parameter
        var isFirst = true
        address?.run { result.append(address.toString()) }
        if (value != null) {
            if (isFirst) {
                result.append("?")
            }
            result.append("amount=").append(value.value.toDouble() / 100000000)
            isFirst = false
        }
        label?.run {
            if (isFirst) {
                result.append("?")
                isFirst = false
            } else {
                result.append("&")
            }
            result.append("label=").append(label)
        }
        callbackURL?.run {
            if (isFirst) {
                result.append("?")
                isFirst = false
            } else {
                result.append("&")
            }
            result.append("r=").append(callbackURL)
        }
        return result.toString()
    }
}