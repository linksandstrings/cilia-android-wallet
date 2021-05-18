package com.cilia.wallet.activity.send

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Point
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.databinding.BindingAdapter
import androidx.databinding.DataBindingUtil
import androidx.databinding.InverseBindingAdapter
import androidx.databinding.InverseBindingListener
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.observe
import com.google.common.base.Strings
import com.mrd.bitlib.crypto.HdKeyNode
import com.mrd.bitlib.util.HexUtils
import com.cilia.wallet.*
import com.cilia.wallet.activity.GetAmountActivity
import com.cilia.wallet.activity.ScanActivity
import com.cilia.wallet.activity.modern.GetFromAddressBookActivity
import com.cilia.wallet.activity.send.adapter.FeeLvlViewAdapter
import com.cilia.wallet.activity.send.adapter.FeeViewAdapter
import com.cilia.wallet.activity.send.event.AmountListener
import com.cilia.wallet.activity.send.event.BroadcastResultListener
import com.cilia.wallet.activity.send.model.*
import com.cilia.wallet.activity.util.AnimationUtils
import com.cilia.wallet.content.HandleConfigFactory
import com.cilia.wallet.databinding.*
import com.mycelium.wapi.content.AssetUri
import com.mycelium.wapi.content.WithCallback
import com.mycelium.wapi.content.btc.BitcoinUri
import com.mycelium.wapi.wallet.*
import com.mycelium.wapi.wallet.btc.bip44.HDAccount
import com.mycelium.wapi.wallet.btc.single.SingleAddressAccount
import com.mycelium.wapi.wallet.btcil.bip44.HDAccountIL
import com.mycelium.wapi.wallet.btcil.single.SingleAddressAccountIL
import com.mycelium.wapi.wallet.coins.Value
import com.mycelium.wapi.wallet.coins.Value.Companion.zeroValue
import com.mycelium.wapi.wallet.colu.ColuAccount
import com.mycelium.wapi.wallet.erc20.ERC20Account
import com.mycelium.wapi.wallet.eth.EthAccount
import com.mycelium.wapi.wallet.fio.FioAccount
import com.mycelium.wapi.wallet.fio.FioModule
import kotlinx.android.synthetic.main.fio_memo_input.*
import kotlinx.android.synthetic.main.send_coins_activity.*
import kotlinx.android.synthetic.main.send_coins_advanced_eth.*
import kotlinx.android.synthetic.main.send_coins_fee_selector.*
import kotlinx.android.synthetic.main.send_coins_sender_fio.*
import java.util.*
import java.util.concurrent.TimeUnit

class SendCoinsActivity : AppCompatActivity(), BroadcastResultListener, AmountListener {
    private lateinit var viewModel: SendCoinsViewModel
    private lateinit var mbwManager: MbwManager
    private lateinit var senderFioNamesMenu: PopupMenu

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mbwManager = MbwManager.getInstance(application)
        val accountId = checkNotNull(intent.getSerializableExtra(ACCOUNT) as UUID)
        val rawPaymentRequest = intent.getByteArrayExtra(RAW_PAYMENT_REQUEST)
        val crashHint = TextUtils.join(", ", intent.extras!!.keySet()) + " (account id was $accountId)"
        val isColdStorage = intent.getBooleanExtra(IS_COLD_STORAGE, false)
        val account = mbwManager.getWalletManager(isColdStorage).getAccount(accountId)
                ?: throw IllegalStateException(crashHint)

        val viewModelProvider = ViewModelProviders.of(this)

        viewModel = when (account) {
            is SingleAddressAccount, is HDAccount -> viewModelProvider.get(SendBtcViewModel::class.java)
            is SingleAddressAccountIL, is HDAccountIL -> viewModelProvider.get(SendBtcILViewModel::class.java)
            else -> throw NotImplementedError()
//            is ColuAccount -> viewModelProvider.get(SendColuViewModel::class.java)
//            is SingleAddressAccount, is HDAccount -> viewModelProvider.get(SendBtcViewModel::class.java)
//            is HDAccountIL -> viewModelProvider.get(SendBtcILViewModel::class.java)
//            is EthAccount, is ERC20Account -> viewModelProvider.get(SendEthViewModel::class.java)
//            is FioAccount -> viewModelProvider.get(SendFioViewModel::class.java)
//            else -> throw NotImplementedError()
        }
        viewModel.activity = this
        if (!viewModel.isInitialized()) {
            viewModel.init(account, intent)
        }

        if (savedInstanceState != null) {
            viewModel.loadInstance(savedInstanceState)
        }
        //if we do not have a stored receiving address, and got a keynode, we need to figure out the address
        if (viewModel.getReceivingAddress().value == null) {
            val hdKey = intent.getSerializableExtra(HD_KEY) as HdKeyNode?
            if (hdKey != null) {
                viewModel.setReceivingAddressFromKeynode(hdKey, this)
            }
        }

        if (!account.canSpend()) {
            chooseSpendingAccount(rawPaymentRequest)
            return
        }

        // lets see if we got a raw Payment request (probably by downloading a file with MIME application/bitcoin-paymentrequest)
        if (rawPaymentRequest != null && viewModel.hasPaymentRequestHandler()) {
            viewModel.verifyPaymentRequest(rawPaymentRequest, this)
        }

        // lets check whether we got a payment request uri and need to fetch payment data
        val genericUri = viewModel.getGenericUri().value
        if (genericUri is WithCallback && !Strings.isNullOrEmpty((genericUri as WithCallback).callbackURL)
                && !viewModel.hasPaymentRequestHandler()) {
            viewModel.verifyPaymentRequest(genericUri, this)
        }

        initDatabinding(account)

        initFeeView()
        initFeeLvlView()
        supportActionBar?.run {
            title = getString(R.string.send_cointype, viewModel.getAccount().coinType.symbol)
            setHomeAsUpIndicator(R.drawable.ic_back_arrow)
            setHomeButtonEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }
        createSenderFioNamesMenu()
        viewModel.payerFioName.observe(this) {
            updateMemoVisibility()
        }
        viewModel.payeeFioName.observe(this) {
            updateMemoVisibility()
        }
        updateMemoVisibility()
        et_fio_memo.setOnFocusChangeListener { view, b ->
            if(b) {
                root.postDelayed({ root.smoothScrollBy(0, root.maxScrollAmount) }, 500)
            }
        }
    }

    private fun updateMemoVisibility() {
        ll_fio_memo.visibility = if (viewModel.payeeFioName.value?.isNotEmpty() == true
                && viewModel.payerFioName.value?.isNotEmpty() == true)
            View.VISIBLE
        else
            View.GONE
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean =
            when (item?.itemId) {
                android.R.id.home -> {
                    onBackPressed()
                    true
                }
                else -> super.onOptionsItemSelected(item)
            }

    override fun onResume() {
        super.onResume()

        // If we don't have a fresh exchange rate, now is a good time to request one, as we will need it in a minute
        if (!mbwManager.currencySwitcher.isFiatExchangeRateAvailable(viewModel.getAccount().coinType)) {
            mbwManager.exchangeRateManager.requestRefresh()
        }

        Handler(Looper.getMainLooper()).postDelayed({ viewModel.updateClipboardUri() }, 300)
        viewModel.activityResultDialog?.show(supportFragmentManager, "ActivityResultDialog")
        viewModel.activityResultDialog = null
    }

    private fun chooseSpendingAccount(rawPaymentRequest: ByteArray?) {
        //we need the user to pick a spending account - the activity will then init sendmain correctly
        val uri: AssetUri = intent.getSerializableExtra(ASSET_URI) as AssetUri?
                ?: BitcoinUri.from(viewModel.getReceivingAddress().value, viewModel.getAmount().value,
                        viewModel.getTransactionLabel().value, null)

        if (rawPaymentRequest != null) {
            GetSpendingRecordActivity.callMeWithResult(this, rawPaymentRequest, REQUEST_PICK_ACCOUNT)
        } else {
            GetSpendingRecordActivity.callMeWithResult(this, uri, REQUEST_PICK_ACCOUNT)
        }
        //no matter whether the user did successfully send or tapped back - we do not want to stay here with a wrong account selected
        finish()
        return
    }

    override fun onPause() {
        mbwManager.versionManager.closeDialog()
        super.onPause()
    }

    private fun initDatabinding(account: WalletAccount<*>) {
        //Data binding, should be called after everything else
        val sendCoinsActivityBinding = when (account) {
            is HDAccount, is SingleAddressAccount -> {
                DataBindingUtil.setContentView<SendCoinsActivityBtcBinding>(this, R.layout.send_coins_activity_btc)
                        .also {
                            it.viewModel = viewModel as SendBtcViewModel
                            it.activity = this
                        }
            }
            is HDAccountIL, is SingleAddressAccountIL-> {
                DataBindingUtil.setContentView<SendCoinsActivityBtcilBinding>(this, R.layout.send_coins_activity_btcil)
                        .also {
                            it.viewModel = viewModel as SendBtcILViewModel
                            it.activity = this
                        }
            }
            is EthAccount, is ERC20Account -> {
                DataBindingUtil.setContentView<SendCoinsActivityEthBinding>(this, R.layout.send_coins_activity_eth)
                        .also {
                            it.viewModel = (viewModel as SendEthViewModel).apply {
                                spinner?.adapter = ArrayAdapter(context,
                                        R.layout.layout_send_coin_transaction_replace, R.id.text, getTxItems()).apply {
                                    this.setDropDownViewResource(R.layout.layout_send_coin_transaction_replace_dropdown)
                                }
                            }
                            it.activity = this
                        }
            }
            is FioAccount -> {
                DataBindingUtil.setContentView<SendCoinsActivityFioBinding>(this, R.layout.send_coins_activity_fio)
                        .also {
                            it.viewModel = viewModel as SendFioViewModel
                            it.activity = this
                        }
            }
            else -> getDefaultBinding()
        }
        sendCoinsActivityBinding.lifecycleOwner = this
    }

    private fun getDefaultBinding(): SendCoinsActivityBinding =
            DataBindingUtil.setContentView<SendCoinsActivityBinding>(this, R.layout.send_coins_activity)
                    .also {
                        it.viewModel = viewModel
                        it.activity = this
                    }

    private fun initFeeView() {
        feeValueList?.setHasFixedSize(true)

        val displaySize = Point()
        windowManager.defaultDisplay.getSize(displaySize)
        val feeFirstItemWidth = (displaySize.x - resources.getDimensionPixelSize(R.dimen.item_dob_width)) / 2

        val feeViewAdapter = FeeViewAdapter(feeFirstItemWidth)
        feeViewAdapter.setFormatter(viewModel.getFeeFormatter())

        feeValueList?.adapter = feeViewAdapter
        feeViewAdapter.setDataset(viewModel.getFeeDataset().value)
        viewModel.getFeeDataset().observe(this, Observer { feeItems ->
            feeViewAdapter.setDataset(feeItems)
            val selectedFee = viewModel.getSelectedFee().value!!
            if (feeViewAdapter.selectedItem >= feeViewAdapter.itemCount ||
                    feeViewAdapter.getItem(feeViewAdapter.selectedItem).feePerKb != selectedFee.valueAsLong) {
                feeValueList?.setSelectedItem(selectedFee)
            }
        })

        feeValueList?.setSelectListener { adapter, position ->
            val item = (adapter as FeeViewAdapter).getItem(position)
            viewModel.getSelectedFee().value = Value.valueOf(item.value.type, item.feePerKb)

            if (viewModel.isSendScrollDefault() && root.maxScrollAmount - root.scaleY > 0) {
                root.smoothScrollBy(0, root.maxScrollAmount)
                viewModel.setSendScrollDefault(false)
            }
        }
    }

    private fun initFeeLvlView() {
        feeLvlList?.setHasFixedSize(true)
        val feeLvlItems = viewModel.getFeeLvlItems()

        val displaySize = Point()
        windowManager.defaultDisplay.getSize(displaySize)
        val feeFirstItemWidth = (displaySize.x - resources.getDimensionPixelSize(R.dimen.item_dob_width)) / 2
        feeLvlList?.adapter = FeeLvlViewAdapter(feeLvlItems, feeFirstItemWidth)
        feeLvlList?.setSelectListener { adapter, position ->
            val item = (adapter as FeeLvlViewAdapter).getItem(position)
            viewModel.getFeeLvl().value = item.minerFee
            feeValueList?.setSelectedItem(viewModel.getSelectedFee().value)
        }
        feeLvlList?.setSelectedItem(viewModel.getFeeLvl().value)
    }

    fun onClickUnconfirmedWarning() {
        AlertDialog.Builder(this)
                .setTitle(R.string.spending_unconfirmed_title)
                .setMessage(R.string.spending_unconfirmed_description)
                .setPositiveButton(android.R.string.ok, null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show()
    }

    fun onColuTipClick() {
        AlertDialog.Builder(this)
                .setMessage(R.string.tips_rmc_check_address)
                .setPositiveButton(R.string.button_ok, null)
                .create()
                .show()
    }

    override fun onClickAmount() {
        val account = viewModel.getAccount()
        GetAmountActivity.callMeToSend(this, GET_AMOUNT_RESULT_CODE, account.id,
                viewModel.getAmount().value, viewModel.getSelectedFee().value,
                viewModel.isColdStorage(), viewModel.getReceivingAddress().value)
    }

    fun onClickScan() {
        val config = HandleConfigFactory.returnKeyOrAddressOrUriOrKeynode()
        ScanActivity.callMe(this, SCAN_RESULT_CODE, config)
    }

    fun onClickAddressBook() {
        val intent = Intent(this, GetFromAddressBookActivity::class.java)
        startActivityForResult(intent, ADDRESS_BOOK_RESULT_CODE)
    }

    fun onClickManualEntry() {
        val intent = Intent(this, ManualAddressEntry::class.java)
                .putExtra(ACCOUNT, viewModel.getAccount().id)
                .putExtra(IS_COLD_STORAGE, viewModel.isColdStorage())
        startActivityForResult(intent, MANUAL_ENTRY_RESULT_CODE)
    }

    fun onClickSenderFioNames() {
        senderFioNamesMenu.show()
    }

    private fun createSenderFioNamesMenu() {
        val fioModule = mbwManager.getWalletManager(false).getModuleById(FioModule.ID) as FioModule
        val now = Date()
        val fioNames = fioModule.getFIONames(viewModel.getAccount()).filter { it.expireDate.after(now) }
        if (fioNames.isEmpty()) {
            sender.visibility = View.GONE
        } else {
            senderFioNamesMenu = PopupMenu(this, iv_from_fio_name).apply {
                fioNames.forEach {
                    menu.add(it.name)
                }
                setOnMenuItemClickListener { item ->
                    // btcViewModel.setAddressType(AddressType.values()[item.itemId])
                    tv_from.text = item.title
                    getSharedPreferences(Constants.SETTINGS_NAME, MODE_PRIVATE)
                            .edit()
                            .putString(Constants.LAST_FIO_SENDER, "${item.title}")
                            .apply()
                    false
                }
                val fioSender = getSharedPreferences(Constants.SETTINGS_NAME, MODE_PRIVATE)
                        .getString(Constants.LAST_FIO_SENDER, fioNames.first().name)
                if (menu.children.any { it.title == fioSender }) {
                    viewModel.payerFioName.postValue(fioSender)
                }
            }
        }
    }

    fun onClickSend() {
        viewModel.fioMemo.value = et_fio_memo.text.toString()
        if (isPossibleDuplicateSending()) {
            AlertDialog.Builder(this)
                    .setTitle(R.string.possible_duplicate_warning_title)
                    .setMessage(R.string.possible_duplicate_warning_desc)
                    .setPositiveButton(android.R.string.yes) { _: DialogInterface?, _: Int -> viewModel.sendTransaction(this) }
                    .setNegativeButton(android.R.string.no) { _: DialogInterface?, _: Int -> finish() }
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show()
        } else {
            viewModel.sendTransaction(this)
        }
    }

    fun showInputDataInfo() {
        AlertDialog.Builder(this, R.style.CiliaModern_Dialog_BlueButtons)
                .setTitle(R.string.input_data_format)
                .setMessage(R.string.input_data_format_desc)
                .setPositiveButton(R.string.button_ok, null)
                .create()
                .show()
    }

    fun showGasLimitInfo() {
        AlertDialog.Builder(this, R.style.CiliaModern_Dialog_BlueButtons)
                .setTitle(R.string.gas_limit_info_title)
                .setMessage(R.string.gas_limit_info_desc)
                .setPositiveButton(R.string.button_ok, null)
                .create()
                .show()
    }

    fun showTxReplaceInfo() {
        AlertDialog.Builder(this, R.style.CiliaModern_Dialog_BlueButtons)
                .setTitle(R.string.tx_replace_info_title)
                .setMessage(R.string.tx_replacae_info_desc)
                .setPositiveButton(R.string.button_ok, null)
                .create()
                .show()
    }

    /**
     * Checks whether the last outgoing transaction that was sent recently (within 10 minutes)
     * has the same amount and receiving address to warn a user about possible duplicate sending.
     */
    private fun isPossibleDuplicateSending(): Boolean {
        // we could have used getTransactionsSince here instead of getTransactionSummaries
        // but for accounts with large number of transactions (>500) it would introduce quite delay
        // so we take last 25 transactions as a sort of heuristic
        val summaries: List<TransactionSummary> = viewModel.getAccount().getTransactionSummaries(0, 25)
        if (summaries.isEmpty()) {
            return false // user has no transactions
        }
        if (summaries[0].timestamp * 1000 < System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(10)) {
            return false // latest transaction is too old
        }
        // find latest outgoing transaction
        var outgoingTx: TransactionSummary? = null
        for (summary in summaries) {
            if (!summary.isIncoming) {
                outgoingTx = summary
                break
            }
        }
        if (outgoingTx == null) {
            return false // no outgoing transactions
        }
        // extract sent amount from the transaction
        var outgoingTxAmount = zeroValue(viewModel.getAccount().coinType)
        for (output in outgoingTx.outputs) {
            if (output.address == viewModel.getReceivingAddress().value) {
                outgoingTxAmount = output.value
            }
        }
        return outgoingTx.destinationAddresses.size > 0 && outgoingTx.destinationAddresses[0] == viewModel.getReceivingAddress().value &&
                outgoingTxAmount == viewModel.getAmount().value
    }

    override fun onSaveInstanceState(outState: Bundle) {
        viewModel.saveInstance(outState)
        super.onSaveInstanceState(outState)
    }

    override fun broadcastResult(broadcastResult: BroadcastResult) {
        val result = Intent()
        if (broadcastResult.resultType == BroadcastResultType.SUCCESS) {
            val signedTransaction = viewModel.getSignedTransaction()!!
            viewModel.getTransactionLabel().value?.run {
                mbwManager.metadataStorage.storeTransactionLabel(HexUtils.toHex(signedTransaction.id), this)
            }
            val hash = HexUtils.toHex(signedTransaction.id)
            val fiat = viewModel.getFiatValue()
            fiat?.run {
                getSharedPreferences(TRANSACTION_FIAT_VALUE, Context.MODE_PRIVATE).edit().putString(hash, fiat).apply()
            }
            result.putExtra(Constants.TRANSACTION_FIAT_VALUE_KEY, fiat)
                    .putExtra(Constants.TRANSACTION_ID_INTENT_KEY, hash)
        }
        val resultType = if (broadcastResult.resultType == BroadcastResultType.SUCCESS) {
            Activity.RESULT_OK
        } else {
            Activity.RESULT_CANCELED
        }
        setResult(resultType, result)
        finish()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        viewModel.processReceivedResults(requestCode, resultCode, data, this)
        super.onActivityResult(requestCode, resultCode, data)
    }

    companion object {
        const val GET_AMOUNT_RESULT_CODE = 1
        const val SCAN_RESULT_CODE = 2
        const val ADDRESS_BOOK_RESULT_CODE = 3
        const val MANUAL_ENTRY_RESULT_CODE = 4
        const val REQUEST_PICK_ACCOUNT = 5
        const val SIGN_TRANSACTION_REQUEST_CODE = 6
        const val REQUEST_PAYMENT_HANDLER = 8
        const val RAW_PAYMENT_REQUEST = "rawPaymentRequest"

        internal const val ACCOUNT = "account"
        internal const val IS_COLD_STORAGE = "isColdStorage"
        internal const val AMOUNT = "amount"
        internal const val RECEIVING_ADDRESS = "receivingAddress"
        internal const val HD_KEY = "hdKey"
        internal const val TRANSACTION_LABEL = "transactionLabel"
        internal const val ASSET_URI = "assetUri"
        const val SIGNED_TRANSACTION = "signedTransaction"
        const val TRANSACTION_FIAT_VALUE = "transaction_fiat_value"

        @JvmStatic
        fun getIntent(currentActivity: Activity, account: UUID, isColdStorage: Boolean): Intent =
                Intent(currentActivity, SendCoinsActivity::class.java)
                        .putExtra(ACCOUNT, account)
                        .putExtra(IS_COLD_STORAGE, isColdStorage)

        @JvmStatic
        fun getIntent(currentActivity: Activity, account: UUID,
                      amountToSend: Long, receivingAddress: Address, isColdStorage: Boolean): Intent =
                getIntent(currentActivity, account, isColdStorage)
                        .putExtra(AMOUNT, Value.valueOf(
                                Utils.getBtcCoinType(),
                                amountToSend))
                        .putExtra(RECEIVING_ADDRESS, receivingAddress)

        @JvmStatic
        fun getIntent(currentActivity: Activity, account: UUID, rawPaymentRequest: ByteArray,
                      isColdStorage: Boolean): Intent =
                getIntent(currentActivity, account, isColdStorage)
                        .putExtra(RAW_PAYMENT_REQUEST, rawPaymentRequest)

        @JvmStatic
        fun getIntent(currentActivity: Activity, account: UUID, uri: AssetUri, isColdStorage: Boolean): Intent =
                getIntent(currentActivity, account, isColdStorage)
                        .putExtra(AMOUNT, uri.value)
                        .putExtra(RECEIVING_ADDRESS, uri.address)
                        .putExtra(TRANSACTION_LABEL, uri.label)
                        .putExtra(ASSET_URI, uri)

        @JvmStatic
        fun getIntent(currentActivity: Activity, account: UUID, hdKey: HdKeyNode): Intent =
                getIntent(currentActivity, account, false)
                        .putExtra(HD_KEY, hdKey)
    }
}

@BindingAdapter("errorAnimatedText")
fun setVisibilityAnimated(target: TextView, error: CharSequence) {
    val newVisibility = if (error.isNotEmpty()) View.VISIBLE else View.GONE
    if (target.visibility == newVisibility) {
        target.text = error
        return
    }
    if (error.isNotEmpty()) {
        target.text = error
        target.visibility = newVisibility
        AnimationUtils.expand(target, null)
    } else {
        AnimationUtils.collapse(target) {
            target.visibility = newVisibility
            target.text = error
        }
    }
}

@BindingAdapter(value = ["animatedVisibility", "activity"], requireAll = false)
fun setVisibilityAnimated(target: View, visible: Boolean, activity: SendCoinsActivity?) {
    val newVisibility = if (visible) View.VISIBLE else View.GONE
    if (target.visibility == newVisibility) {
        return
    }
    if (visible) {
        target.visibility = newVisibility
        AnimationUtils.expand(target) { activity?.root?.smoothScrollTo(0, activity.root.measuredHeight) }
    } else {
        AnimationUtils.collapse(target) {
            target.visibility = newVisibility
        }
    }
}

@BindingAdapter("imageRotation")
fun setRotationAnimated(target: ImageView, isExpanded: Boolean) {
    target.rotation = (if (isExpanded) 180 else 0).toFloat()
}

@BindingAdapter(value = ["isRedColor", "activity"])
fun setRedTextColor(target: EditText, isRedColor: Boolean, activity: SendCoinsActivity) {
    if (isRedColor) {
        target.setTextColor(ContextCompat.getColor(activity, R.color.red))
    } else {
        target.setTextColor(ContextCompat.getColor(activity, android.R.color.white))
    }
}

@BindingAdapter(value = ["selectedItem", "selectedItemAttrChanged"], requireAll = false)
fun setSpinnerListener(spinner: Spinner, spinnerItem: SpinnerItem, listener: InverseBindingListener) {
    spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = listener.onChange()
        override fun onNothingSelected(adapterView: AdapterView<*>) = listener.onChange()
    }
}

@InverseBindingAdapter(attribute = "selectedItem")
fun getSelectedItem(spinner: Spinner): SpinnerItem {
    return spinner.selectedItem as SpinnerItem
}

interface SpinnerItem

class TransactionItem(val tx: TransactionSummary, private val dateString: String,
                      private val amountString: String) : SpinnerItem {
    override fun toString(): String {
        val idHex = HexUtils.toHex(tx.id)
        val idString = "${idHex.substring(0, 6)}…${idHex.substring(idHex.length - 2)}"
        return "$idString - $dateString, $amountString"
    }
}

class NoneItem : SpinnerItem {
    override fun toString(): String = WalletApplication.getInstance().getString(R.string.none)
    override fun equals(other: Any?) = this.toString() == other.toString()
}
