package com.mycelium.wapi.wallet.btcil

import com.mycelium.wapi.wallet.Fee
import com.mycelium.wapi.wallet.coins.Value

open class FeePerKbFeeIL(val feePerKb: Value) : Fee()