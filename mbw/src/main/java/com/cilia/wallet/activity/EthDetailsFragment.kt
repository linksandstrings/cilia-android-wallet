package com.cilia.wallet.activity

import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.cilia.wallet.R
import com.cilia.wallet.activity.util.AddressLabel
import com.cilia.wallet.activity.util.EthFeeFormatter
import com.mycelium.wapi.wallet.EthTransactionSummary
import com.mycelium.wapi.wallet.TransactionSummary
import kotlinx.android.synthetic.main.transaction_details_eth.*
import java.math.BigDecimal
import java.math.RoundingMode

class EthDetailsFragment : DetailsFragment() {
    private val tx: EthTransactionSummary by lazy {
        arguments!!.getSerializable("tx") as EthTransactionSummary
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
            inflater.inflate(R.layout.transaction_details_eth, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateUi()
    }

    private fun updateUi() {
        if (specific_table == null) {
            return
        }
        alignTables(specific_table)

        val fromAddress = AddressLabel(requireContext())
        fromAddress.address = tx.sender
        llFrom.addView(fromAddress)

        val toAddress = AddressLabel(requireContext())
        toAddress.address = tx.receiver
        llTo.addView(toAddress)

        llValue.addView(getValue(tx.value, null))
        if (tx.internalValue?.isZero() == false) {
            llValue.addView(TextView(requireContext()).apply {
                layoutParams = TransactionDetailsActivity.WCWC
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                text = getString(R.string.eth_internal_transfer, tx.internalValue)
            })
        }
        llFee.addView(getValue(tx.fee!!, null))

        tvGasLimit.text = tx.gasLimit.toString()
        val percent = BigDecimal(tx.gasUsed.toDouble() / tx.gasLimit.toDouble() * 100).setScale(2, RoundingMode.UP).toDouble()
        val percentString = if (isWholeNumber(percent)) "%.0f".format(percent) else percent.toString()
        tvGasUsed.text = "${tx.gasUsed} ($percentString%)"
        val txFeePerUnit = tx.fee!!.value / tx.gasUsed
        tvGasPrice.text = EthFeeFormatter().getFeePerUnit(txFeePerUnit.toLong())
        tvNonce.text = tx.nonce.toString()
    }

    companion object {
        @JvmStatic
        fun newInstance(tx: TransactionSummary): EthDetailsFragment {
            val f = EthDetailsFragment()
            val args = Bundle()

            args.putSerializable("tx", tx)
            f.arguments = args
            return f
        }
    }

    private fun isWholeNumber(d: Double) = d % 1.0 == 0.0
}