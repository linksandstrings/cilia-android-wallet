package com.mycelium.bequant.receive

import android.os.Bundle
import android.os.Parcelable
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.mycelium.bequant.receive.adapter.AccountAdapter
import com.mycelium.bequant.receive.adapter.AccountGroupItem
import com.mycelium.bequant.receive.adapter.AccountItem
import com.mycelium.bequant.receive.adapter.AccountListItem
import com.cilia.wallet.MbwManager
import com.cilia.wallet.R
import com.cilia.wallet.activity.util.getBTCSingleAddressAccounts
import com.cilia.wallet.exchange.ValueSum
import com.mycelium.wapi.wallet.Address
import com.mycelium.wapi.wallet.WalletAccount
import com.mycelium.wapi.wallet.btc.bip44.getBTCBip44Accounts
import com.mycelium.wapi.wallet.btcil.bip44.getBTCILBip44Accounts
import com.mycelium.wapi.wallet.eth.getEthAccounts
import kotlinx.android.parcel.Parcelize
import kotlinx.android.synthetic.main.fragment_bequant_select_account.*


class SelectAccountFragment : Fragment(R.layout.fragment_bequant_select_account) {
    val adapter = AccountAdapter()

    val args by navArgs<SelectAccountFragmentArgs>()
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        generateAccountList()
        list.adapter = adapter

    }

    private fun generateAccountList() {

        val accountsList = mutableListOf<AccountListItem>()
        val walletManager = MbwManager.getInstance(requireContext()).getWalletManager(false)

        val btcWallets = listOf(R.string.active_hd_accounts_name to walletManager.getBTCBip44Accounts(),
                R.string.active_bitcoin_sa_group_name to walletManager.getBTCSingleAddressAccounts())
        val btcilWallets = listOf(R.string.active_btcil_accounts_name to walletManager.getBTCILBip44Accounts())
        val ethWallets = listOf(R.string.eth_accounts_name to walletManager.getEthAccounts())
        val walletsAccounts = mutableListOf<Pair<Int,List<WalletAccount<*>>>>()
        if (args.currency?.toLowerCase() == "btcil") {
            walletsAccounts+=btcilWallets
        }
        if (args.currency?.toLowerCase() == "btc") {
            walletsAccounts+=btcWallets
        }
        if (args.currency?.toLowerCase() == "eth") {
            walletsAccounts+=ethWallets
        }
        if(args.currency.isNullOrEmpty()){
            walletsAccounts+= btcWallets + ethWallets
        }
        walletsAccounts.forEach {
            if (it.second.isNotEmpty()) {
                accountsList.add(AccountGroupItem(true, getString(it.first), getSpendableBalance(it.second)))
                accountsList.addAll(it.second.map { AccountItem(it.label, it.accountBalance.confirmed) })
            }
        }
//        accountsList.add(TotalItem(getSpendableBalance()))
        adapter.submitList(accountsList)

        adapter.accountClickListener = { accountItem ->
            val selectedAccount = walletsAccounts.map { it.second }.flatten().find { it.label == accountItem.label }
            val accountData = AccountData(selectedAccount?.label)
            findNavController().previousBackStackEntry?.savedStateHandle?.set(ACCOUNT_KEY, accountData)
            findNavController().popBackStack()
        }
    }

    @Parcelize
    data class AccountData(val label: String?) : Parcelable

    companion object {

        val ACCOUNT_KEY = "chooseAccount"

        private fun getSpendableBalance(walletAccountList: List<WalletAccount<out Address>>): ValueSum {
            val sum = ValueSum()
            for (account in walletAccountList) {
                if (account.isActive) {
                    sum.add(account.accountBalance.spendable)
                }
            }
            return sum
        }
    }
}