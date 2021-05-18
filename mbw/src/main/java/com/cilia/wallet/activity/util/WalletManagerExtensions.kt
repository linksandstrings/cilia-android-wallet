package com.cilia.wallet.activity.util

import com.cilia.wallet.Utils
import com.mycelium.wapi.wallet.WalletManager
import com.mycelium.wapi.wallet.btc.single.SingleAddressAccount
import com.mycelium.wapi.wallet.colu.getColuAccounts

/**
 * Get bitcoin single account list, excluding accounts linked with colu account
 *
 * @return list of accounts
 */
fun WalletManager.getBTCSingleAddressAccounts() = getAccounts().filter { it is SingleAddressAccount
        && !Utils.checkIsLinked(it, getColuAccounts()) && it.isVisible && !it.toRemove}

fun WalletManager.getActiveBTCSingleAddressAccounts() = getAccounts().filter { it is SingleAddressAccount
        && !Utils.checkIsLinked(it, getColuAccounts()) && it.isVisible && !it.toRemove && it.isActive }