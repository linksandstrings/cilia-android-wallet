package com.cilia.wallet.activity.modern.helper

import android.app.Activity
import android.content.Intent
import com.cilia.wallet.activity.fio.mapaccount.AccountMappingActivity
import com.mycelium.wapi.wallet.Address
import com.mycelium.wapi.wallet.WalletAccount


object FioHelper {
    @JvmStatic
    fun chooseAccountToMap(context: Activity, account: WalletAccount<Address>) {
        context.startActivity(Intent(context, AccountMappingActivity::class.java)
                .putExtra("accountId", account.id)
//                .putExtra("fioName", names.first())
        )
    }
}