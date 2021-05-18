package com.cilia.wallet.activity.send.event

import com.mycelium.wapi.wallet.BroadcastResult


interface BroadcastResultListener {
    fun broadcastResult(broadcastResult: BroadcastResult)
}