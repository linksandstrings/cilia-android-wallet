package com.cilia.wallet.activity.modern.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mycelium.bequant.BequantPreference
import com.mycelium.bequant.remote.repositories.Api
import com.cilia.wallet.MbwManager
import com.cilia.wallet.R
import com.cilia.wallet.activity.modern.RecordRowBuilder
import com.cilia.wallet.activity.modern.adapter.holder.*
import com.cilia.wallet.activity.modern.model.ViewAccountModel
import com.cilia.wallet.activity.modern.model.accounts.*
import com.cilia.wallet.activity.modern.model.accounts.AccountListItem.Type.*
import com.cilia.wallet.exchange.ValueSum
import com.mycelium.wapi.wallet.Address
import com.mycelium.wapi.wallet.WalletAccount
import java.util.*
import kotlin.collections.ArrayList

class AccountListAdapter(fragment: Fragment, private val mbwManager: MbwManager)
    : ListAdapter<AccountListItem, RecyclerView.ViewHolder>(ItemListDiffCallback(fragment.requireContext())) {
    private val context = fragment.requireContext()

    private var focusedAccountId: UUID? = null
    private var selectedAccountId: UUID? = mbwManager.selectedAccount.id

    private var itemClickListener: ItemClickListener? = null
    var investmentAccountClickListener: ItemClickListener? = null
    private val layoutInflater: LayoutInflater
    private val pagePrefs = context.getSharedPreferences("account_list", Context.MODE_PRIVATE)
    private val listModel: AccountsListModel = ViewModelProviders.of(fragment).get(AccountsListModel::class.java)
    private val walletManager = mbwManager.getWalletManager(false)

    val focusedAccount: WalletAccount<out Address>?
        get() = focusedAccountId?.let { walletManager.getAccount(it) }

    init {
        layoutInflater = LayoutInflater.from(context)
        listModel.accountsData.observe(fragment, Observer { accountsGroupModels ->
            accountsGroupModels!!
            val selectedAccountExists = accountsGroupModels.any { it.accountsList.any { it is AccountViewModel && it.accountId == selectedAccountId } }
            if (!selectedAccountExists) {
                setFocusedAccountId(null)
            }
            refreshList(accountsGroupModels)
        })
        val accountsGroupsList = listModel.accountsData.value!!
        refreshList(accountsGroupsList)

        Api.accountRepository.accountBalanceGet(fragment.lifecycleScope, {
            val find = it?.find { it.currency?.toLowerCase() == "btc" }
        }, { _, _ ->

        }, {

        })
    }

    private fun refreshList(accountsGroupModels: List<AccountsGroupModel>) {
        submitList(generateListView(accountsGroupModels))
    }

    private fun generateListView(accountsGroupsList: List<AccountsGroupModel>): List<AccountListItem> {
        val itemList = ArrayList<AccountListItem>()
        var totalAdded = false

        for (accountsGroup in accountsGroupsList) {
            if (accountsGroup.getType() == GROUP_ARCHIVED_TITLE_TYPE) {
                itemList.add(TotalViewModel(getSpendableBalance(itemList)))
                totalAdded = true
            }
            val groupModel = AccountsGroupModel(accountsGroup)
            itemList.add(groupModel)
            groupModel.isCollapsed = !pagePrefs.getBoolean(accountsGroup.getTitle(context), true)
            if (!groupModel.isCollapsed) {
                itemList.addAll(accountsGroup.accountsList)
            }
        }
        if (itemList.isNotEmpty() && !totalAdded) {
            itemList.add(TotalViewModel(getSpendableBalance(itemList)))
        }

        return itemList
    }

    fun setItemClickListener(itemClickListener: ItemClickListener) {
        this.itemClickListener = itemClickListener
    }

    fun setFocusedAccountId(focusedAccountId: UUID?) {
        if (this.focusedAccountId == null) {
            this.focusedAccountId = mbwManager.selectedAccount.id
        }
        val oldFocusedPosition = findPosition(this.focusedAccountId)
        // If old account was removed we don't want to notify removed element. It would be updated itself.
        val updateOld = walletManager.getAccount(this.focusedAccountId!!) != null
        val oldSelectedPosition = findPosition(this.selectedAccountId)
        this.focusedAccountId = focusedAccountId
        if (focusedAccountId != null && walletManager.getAccount(focusedAccountId)?.isActive == true) {
            this.selectedAccountId = focusedAccountId
            notifyItemChanged(oldSelectedPosition)
        }
        if (updateOld) {
            notifyItemChanged(oldFocusedPosition)
        }
        notifyItemChanged(findPosition(this.focusedAccountId))
    }

    private fun findPosition(account: UUID?): Int {
        var position = -1
        for (i in 0 until itemCount) {
            if (getItem(i).getType() == ACCOUNT_TYPE) {
                val item = getItem(i) as AccountViewModel
                if (item.accountId == account) {
                    position = i
                    break
                }
            }
        }
        return position
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (AccountListItem.Type.fromId(viewType)) {
            GROUP_TITLE_TYPE -> createGroupViewHolder(parent)
            GROUP_ARCHIVED_TITLE_TYPE -> createArchivedTitleViewHolder(parent)
            ACCOUNT_TYPE -> createAccountViewHolder(parent)
            TOTAL_BALANCE_TYPE -> createTotalBalanceViewHolder(parent)
            INVESTMENT_TYPE -> createInvestmentAccountViewHolder(parent)
            else -> throw IllegalArgumentException("Unknown account type")
        }
    }

    private fun createGroupViewHolder(parent: ViewGroup): GroupTitleViewHolder {
        val view = layoutInflater.inflate(R.layout.accounts_title_view, parent, false)
        return GroupTitleViewHolder(view)
    }

    private fun createArchivedTitleViewHolder(parent: ViewGroup): ArchivedGroupTitleViewHolder {
        val view = layoutInflater.inflate(R.layout.archived_accounts_title_view, parent, false)
        return ArchivedGroupTitleViewHolder(view)
    }

    private fun createAccountViewHolder(parent: ViewGroup): AccountViewHolder {
        val view = layoutInflater.inflate(R.layout.record_row, parent, false)
        return AccountViewHolder(view)
    }

    private fun createTotalBalanceViewHolder(parent: ViewGroup): TotalViewHolder {
        val view = layoutInflater.inflate(R.layout.record_row_total, parent, false)
        view.setVisibility(View.GONE)
        return TotalViewHolder(view)
    }

    private fun createInvestmentAccountViewHolder(parent: ViewGroup): InvestmentViewHolder =
            InvestmentViewHolder(layoutInflater.inflate(R.layout.record_row_investment, parent, false))

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        val viewType = getItemViewType(position)
        when (AccountListItem.Type.fromId(viewType)) {
            ACCOUNT_TYPE -> {
                val accountHolder = holder as AccountViewHolder
                val account = item as AccountViewModel
                val viewModel = ViewAccountModel(account, context)
                val builder = RecordRowBuilder(mbwManager, context.resources)
                builder.buildRecordView(accountHolder, viewModel, mbwManager.selectedAccount.id == account.accountId,
                        focusedAccountId == account.accountId)
                accountHolder.llAddress.setOnClickListener {
                    setFocusedAccountId(account.accountId)
                    walletManager.getAccount(account.accountId)?.run { itemClickListener?.onItemClick(this) }
                }
            }
            GROUP_TITLE_TYPE -> {
                val groupHolder = holder as GroupTitleViewHolder
                val group = item as AccountsGroupModel
                buildGroupBase(group, groupHolder)
                groupHolder.tvBalance.coinType = group.coinType
                groupHolder.tvBalance.setValue(group.sum!!, false)
                groupHolder.tvBalance.visibility = View.VISIBLE
            }
            GROUP_ARCHIVED_TITLE_TYPE -> {
                val groupHolder = holder as ArchivedGroupTitleViewHolder
                val group = item as AccountsGroupModel
                buildGroupBase(group, groupHolder)
            }
            TOTAL_BALANCE_TYPE -> {
                val totalHolder = holder as TotalViewHolder
                val sum = (item as TotalViewModel).balance
                totalHolder.tcdBalance.setValue(sum, totalBalance = true)
            }
            INVESTMENT_TYPE -> {
                val investHolder = holder as InvestmentViewHolder
                val investItem = item as AccountInvestmentViewModel
                investHolder.balance.text = investItem.balance
                investHolder.itemView.setOnClickListener {
                    investmentAccountClickListener?.onItemClick(item.account)
                }
                if (BequantPreference.isLogged()) {
                    investHolder.balance.visibility = View.VISIBLE
                    investHolder.activateLink.visibility = View.GONE
                } else {
                    investHolder.balance.visibility = View.GONE
                    investHolder.activateLink.visibility = View.VISIBLE
                }
            }
            UNKNOWN -> throw IllegalArgumentException("Unknown view type")
        }
    }

    @SuppressLint("SetTextI18n")
    private fun buildGroupBase(group: AccountsGroupModel, groupHolder: GroupTitleViewHolder) {
        val title = group.getTitle(context)
        groupHolder.tvTitle.text = Html.fromHtml(title)
        val count = group.accountsList.size
        groupHolder.tvAccountsCount.visibility = if (count > 0 && !group.isInvestmentAccount) View.VISIBLE else View.GONE
        groupHolder.tvAccountsCount.text = "($count)"
        groupHolder.itemView.setOnClickListener {
            //Should be here as initial state in model is wrong
            val isGroupVisible = !pagePrefs.getBoolean(title, true)
            pagePrefs.edit().putBoolean(title, isGroupVisible).apply()
            refreshList(listModel.accountsData.value!!)
        }
        groupHolder.expandIcon.rotation = (if (!group.isCollapsed) 180 else 0).toFloat()
    }

    private fun getSpendableBalance(walletAccountList: List<AccountListItem>): ValueSum {
        val sum = ValueSum()
        for (item in walletAccountList) {
            if (item.getType() == GROUP_TITLE_TYPE) {
                for (account in (item as AccountsGroupModel).accountsList) {
                    if (account.getType() == ACCOUNT_TYPE && (account as AccountViewModel).isActive) {
                        sum.add(account.balance!!.spendable)
                    }
                    if (account.getType() == INVESTMENT_TYPE) {
                        val account = account as AccountInvestmentViewModel
                        sum.add(account.account.accountBalance.spendable)
                    }
                }
            }
        }
        return sum
    }

    override fun getItemViewType(position: Int) = getItem(position).getType().typeId

    interface ItemClickListener {
        fun onItemClick(account: WalletAccount<out Address>)
    }

    class ItemListDiffCallback(val context: Context) : DiffUtil.ItemCallback<AccountListItem>() {
        override fun areItemsTheSame(oldItem: AccountListItem, newItem: AccountListItem): Boolean {
            return when {
                oldItem.getType() != newItem.getType() -> false
                listOf(GROUP_TITLE_TYPE, GROUP_ARCHIVED_TITLE_TYPE).any { it == oldItem.getType() } -> {
                    (oldItem as AccountsGroupModel).titleId == (newItem as AccountsGroupModel).titleId
                }
                oldItem.getType() == ACCOUNT_TYPE -> {
                    (oldItem as AccountViewModel).accountId == (newItem as AccountViewModel).accountId
                }
                oldItem.getType() == INVESTMENT_TYPE -> {
                    (oldItem as AccountInvestmentViewModel).accountId == (newItem as AccountInvestmentViewModel).accountId
                }
                else -> true
            }
        }

        override fun areContentsTheSame(oldItem: AccountListItem, newItem: AccountListItem): Boolean =
                when (oldItem.getType()) {
                    GROUP_TITLE_TYPE, GROUP_ARCHIVED_TITLE_TYPE -> {
                        newItem as AccountsGroupModel
                        oldItem as AccountsGroupModel
                        newItem.isCollapsed == oldItem.isCollapsed
                                && newItem.coinType == oldItem.coinType
                                && newItem.accountsList.size == oldItem.accountsList.size
                                && newItem.sum == oldItem.sum
                    }
                    ACCOUNT_TYPE -> {
                        newItem as AccountViewModel
                        oldItem as AccountViewModel
                        newItem.displayAddress == oldItem.displayAddress
                                && newItem.isActive == oldItem.isActive
                                && newItem.canSpend == oldItem.canSpend
                                && newItem.externalAccountType == oldItem.externalAccountType
                                && newItem.isRMCLinkedAccount == oldItem.isRMCLinkedAccount
                                && newItem.label == oldItem.label
                                && newItem.showBackupMissingWarning == oldItem.showBackupMissingWarning
                                && newItem.syncTotalRetrievedTransactions == oldItem.syncTotalRetrievedTransactions
                                && newItem.isSyncing == oldItem.isSyncing
                                && newItem.privateKeyCount == oldItem.privateKeyCount
                                && newItem.balance?.spendable == oldItem.balance?.spendable
                    }
                    TOTAL_BALANCE_TYPE -> {
                        newItem as TotalViewModel
                        oldItem as TotalViewModel
                        newItem.balance.values == oldItem.balance.values
                    }
                    INVESTMENT_TYPE -> {
                        newItem as AccountInvestmentViewModel
                        oldItem as AccountInvestmentViewModel
                        newItem.accountId == oldItem.accountId
                                && newItem.balance == oldItem.balance
                    }
                    else -> oldItem == newItem
                }
    }
}
