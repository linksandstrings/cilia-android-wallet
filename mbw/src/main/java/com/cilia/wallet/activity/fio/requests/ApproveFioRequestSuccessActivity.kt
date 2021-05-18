package com.cilia.wallet.activity.fio.requests

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.cilia.wallet.MbwManager
import com.cilia.wallet.R
import com.cilia.wallet.activity.TransactionDetailsActivity
import com.cilia.wallet.activity.fio.requests.ApproveFioRequestActivity.Companion.ACCOUNT
import com.cilia.wallet.activity.fio.requests.ApproveFioRequestActivity.Companion.AMOUNT
import com.cilia.wallet.activity.fio.requests.ApproveFioRequestActivity.Companion.CONVERTED_AMOUNT
import com.cilia.wallet.activity.fio.requests.ApproveFioRequestActivity.Companion.DATE
import com.cilia.wallet.activity.fio.requests.ApproveFioRequestActivity.Companion.FEE
import com.cilia.wallet.activity.fio.requests.ApproveFioRequestActivity.Companion.FROM
import com.cilia.wallet.activity.fio.requests.ApproveFioRequestActivity.Companion.MEMO
import com.cilia.wallet.activity.fio.requests.ApproveFioRequestActivity.Companion.TO
import com.cilia.wallet.activity.fio.requests.ApproveFioRequestActivity.Companion.TXID
import com.cilia.wallet.activity.util.toStringWithUnit
import com.mycelium.wapi.wallet.WalletManager
import com.mycelium.wapi.wallet.coins.Value
import kotlinx.android.synthetic.main.fio_send_request_info.tvAmount
import kotlinx.android.synthetic.main.fio_send_request_status_activity.*
import java.text.DateFormat
import java.util.*


class ApproveFioRequestSuccessActivity : AppCompatActivity() {
    private lateinit var walletManager: WalletManager

    companion object {
        fun start(activity: Activity, amount: Value,
                  convertedAmount: String,
                  fee: Value,
                  date: Long,
                  from: String,
                  to: String, memo: String,
                  txid: ByteArray,
                  accountId: UUID) {
            with(Intent(activity, ApproveFioRequestSuccessActivity::class.java)) {
                putExtra(AMOUNT, amount)
                putExtra(CONVERTED_AMOUNT, convertedAmount)
                putExtra(FEE, fee)
                putExtra(DATE, date)
                putExtra(FROM, from)
                putExtra(TO, to)
                putExtra(MEMO, memo)
                putExtra(TXID, txid)
                putExtra(ACCOUNT, accountId)
                activity.startActivity(this)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fio_send_request_status_activity)

        supportActionBar?.run {
            title = "Success!"
        }
        walletManager = MbwManager.getInstance(this.application).getWalletManager(false)
        tvAmount.text = (intent.getSerializableExtra(AMOUNT) as Value).toStringWithUnit()
        tvConvertedAmount.text = " ~ ${intent.getStringExtra(CONVERTED_AMOUNT)}"
        tvMinerFee.text = (intent.getSerializableExtra(FEE) as Value).toStringWithUnit()
        tvFrom.text = intent.getStringExtra(FROM)
        val date = intent.getLongExtra(DATE, -1)
        tvTo.text = intent.getStringExtra(TO)
        tvMemo.text = intent.getStringExtra(MEMO)
        val accountId = intent.getSerializableExtra(ACCOUNT) as UUID
        val account = walletManager.getAccount(accountId)
        val txid = intent.getByteArrayExtra(TXID)
        btNextButton.setOnClickListener { finish() }
        try {
            if (txid.isEmpty()) {
                if (date != -1L) {
                    tvDate.text = getDateString(date)
                }
                tvTxDetailsLink.isVisible = false
            } else {
                val txTimestamp = account!!.getTxSummary(txid).timestamp
                tvDate.text = getDateString(txTimestamp)
                tvTxDetailsLink.setOnClickListener {
                    val intent: Intent = Intent(this, TransactionDetailsActivity::class.java)
                            .putExtra(TransactionDetailsActivity.EXTRA_TXID, txid)
                            .putExtra(TransactionDetailsActivity.ACCOUNT_ID, accountId)
                    startActivity(intent)
                    finish()
                }
            }
        } catch (ex: Exception) {
            //error read transaction
        }
    }

    private fun getDateString(timestamp: Long): String {
        val date = Date(timestamp * 1000L)
        val locale = resources.configuration.locale

        val dayFormat = DateFormat.getDateInstance(DateFormat.LONG, locale)
        val dateString = dayFormat.format(date)

        val hourFormat = DateFormat.getTimeInstance(DateFormat.LONG, locale)
        val timeString = hourFormat.format(date)

        return "$dateString $timeString"
    }
}