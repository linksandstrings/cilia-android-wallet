package com.cilia.wallet.activity.send

import android.app.AlertDialog
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.mrd.bitlib.util.HexUtils
import com.cilia.wallet.MbwManager
import com.cilia.wallet.R
import com.cilia.wallet.Utils
import com.cilia.wallet.activity.modern.Toaster
import com.cilia.wallet.activity.send.event.BroadcastResultListener
import com.cilia.wallet.event.TransactionBroadcasted
import com.mycelium.wapi.wallet.*
import com.mycelium.wapi.wallet.erc20.ERC20Account
import com.mycelium.wapi.wallet.eth.EthAccount
import com.mycelium.wapi.wallet.exceptions.TransactionBroadcastException
import com.squareup.otto.Bus
import java.util.*


class BroadcastDialog : DialogFragment() {
    companion object {
        const val accountId = "account_id"
        const val coldStorage = "isColdStorage"
        const val tx = "transaction"

        @JvmOverloads
        @JvmStatic
        fun create(account: WalletAccount<*>, isColdStorage: Boolean = false
                   , transactionSummary: TransactionSummary): BroadcastDialog? {
            val transaction = account.getTx(transactionSummary.id)
            return create(account, isColdStorage, transaction)
        }

        @JvmOverloads
        @JvmStatic
        fun create(account: WalletAccount<*>, isColdStorage: Boolean = false
                              , transaction: Transaction): BroadcastDialog? {
            val dialog = BroadcastDialog()
            val bundle = Bundle()
            bundle.putSerializable(accountId, account.id)
            bundle.putBoolean(coldStorage, isColdStorage)
            bundle.putSerializable(tx, transaction)
            dialog.arguments = bundle
            return dialog
        }
    }

    lateinit var account: WalletAccount<*>
    lateinit var transaction: Transaction
    var isCold: Boolean = false
    private var task: BroadcastTask? = null
    private val bus: Bus = MbwManager.getEventBus()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            isCold = it.getBoolean(coldStorage, false)
            val manager = MbwManager.getInstance(activity)
            account = manager.getWalletManager(isCold).getAccount(it[accountId] as UUID) as WalletAccount<*>
            transaction = it[tx] as Transaction
        }
        startBroadcastingTask()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_broadcast_transaction, container, false)
    }

    override fun onDestroy() {
        task?.cancel(true)
        super.onDestroy()
    }

    private fun startBroadcastingTask() {
        // Broadcast the transaction in the background
        if (activity != null) {
            task = BroadcastTask(account, transaction) {
                dismissAllowingStateLoss()
                handleResult(it)
            }
            if (Utils.isConnected(context)) {
                task?.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
            } else {
                task?.listener?.invoke(BroadcastResult(BroadcastResultType.NO_SERVER_CONNECTION))
            }
        } else {
            dismissAllowingStateLoss()
        }
    }

    private fun returnResult(it: BroadcastResult) {
        bus.post(TransactionBroadcasted(HexUtils.toHex(transaction.id)))
        if (targetFragment is BroadcastResultListener) {
            (targetFragment as BroadcastResultListener).broadcastResult(it)
        } else if (activity is BroadcastResultListener) {
            (activity as BroadcastResultListener).broadcastResult(it)
        }
    }

    class BroadcastTask(
            val account: WalletAccount<*>,
            val transaction: Transaction,
            val listener: ((BroadcastResult) -> Unit)) : AsyncTask<Void, Int, BroadcastResult>() {
        override fun doInBackground(vararg args: Void): BroadcastResult {
            return try {
                account.broadcastTx(transaction)
            } catch (e: TransactionBroadcastException) {
                Log.e("BroadcastDialog", "", e)
                BroadcastResult(BroadcastResultType.REJECTED)
            }
        }

        override fun onPostExecute(result: BroadcastResult) {
            listener.invoke(result)
        }
    }

    private fun handleResult(broadcastResult: BroadcastResult) {
        when (broadcastResult.resultType) {
            BroadcastResultType.REJECT_DUPLICATE -> // Transaction rejected, display message and exit
                Utils.showSimpleMessageDialog(activity, R.string.transaction_rejected_double_spending_message) {
                    returnResult(broadcastResult)
                }
            BroadcastResultType.REJECTED -> // Transaction rejected, display message and exit
                Utils.showSimpleMessageDialog(activity, R.string.transaction_rejected_message) {
                    returnResult(broadcastResult)
                }
            BroadcastResultType.NO_SERVER_CONNECTION -> if (isCold || account is EthAccount || account is ERC20Account) {
                // When doing cold storage spending we do not offer to queue the transaction
                // We also do not offer to queue the transaction for eth accounts just yet
                Utils.showSimpleMessageDialog(activity, R.string.transaction_not_sent) {
                    returnResult(broadcastResult)
                }
            } else {
                // Offer the user to queue the transaction
                AlertDialog.Builder(activity)
                        .setTitle(activity!!.getString(R.string.no_server_connection, ""))
                        .setMessage(R.string.queue_transaction_message)
                        .setPositiveButton(R.string.yes) { textId, listener ->
                            account.queueTransaction(transaction)
                            returnResult(broadcastResult)
                        }
                        .setNegativeButton(R.string.no) { textId, listener -> returnResult(broadcastResult) }
                        .show()

            }
            BroadcastResultType.REJECT_MALFORMED -> {
                // Transaction rejected, display message and exit
                Utils.setClipboardString(HexUtils.toHex(transaction.txBytes()), context)
                Utils.showSimpleMessageDialog(activity, R.string.transaction_rejected_malformed) {
                    returnResult(broadcastResult)
                }
            }
            BroadcastResultType.REJECT_NONSTANDARD -> {
                // Transaction rejected, display message and exit
                val message = String.format(getString(R.string.transaction_not_sent_nonstandard), broadcastResult.errorMessage)
                Utils.setClipboardString(HexUtils.toHex(transaction.txBytes()), context)
                Utils.showSimpleMessageDialog(activity, message) {
                    returnResult(broadcastResult)
                }
            }
            BroadcastResultType.REJECT_INSUFFICIENT_FEE -> // Transaction rejected, display message and exit
                Utils.showSimpleMessageDialog(activity, R.string.transaction_not_sent_small_fee) {
                    returnResult(broadcastResult)
                }
            BroadcastResultType.REJECT_INVALID_TX_PARAMS -> // Transaction rejected, display message and exit
                Utils.showSimpleMessageDialog(activity, getString(R.string.transaction_rejected_invalid_tx_params, broadcastResult.errorMessage)) {
                    returnResult(broadcastResult)
                }
            BroadcastResultType.SUCCESS -> {
                // Toast success and finish
                activity?.let { Toaster(it).toast(R.string.transaction_sent, false) }
                returnResult(broadcastResult)
            }
            else -> throw RuntimeException("Unknown broadcast result type ${broadcastResult.resultType}")
        }
    }
}
