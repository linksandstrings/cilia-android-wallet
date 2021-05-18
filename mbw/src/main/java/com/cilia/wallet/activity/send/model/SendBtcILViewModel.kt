package com.cilia.wallet.activity.send.model

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.Intent
import android.graphics.Point
import android.widget.TextView
import androidx.databinding.BindingAdapter
import androidx.lifecycle.MutableLiveData
import com.mrd.bitlib.model.AddressType
import com.cilia.wallet.R
import com.cilia.wallet.activity.StringHandlerActivity
import com.cilia.wallet.activity.modern.Toaster
import com.cilia.wallet.activity.send.SendCoinsActivity
import com.cilia.wallet.activity.send.adapter.AddressViewAdapter
import com.cilia.wallet.activity.send.view.SelectableRecyclerView
import com.cilia.wallet.activity.util.BtcFeeFormatter
import com.cilia.wallet.activity.util.getAssetUri
import com.cilia.wallet.activity.util.getPrivateKey
import com.cilia.wallet.content.ResultType
import com.mycelium.wapi.content.btcil.BitcoinILUri
import com.mycelium.wapi.wallet.AddressUtils
import com.mycelium.wapi.wallet.Address
import com.mycelium.wapi.wallet.Transaction
import com.mycelium.wapi.wallet.WalletAccount
import com.mycelium.wapi.wallet.btc.BtcAddress
import com.mycelium.wapi.wallet.btc.bip44.HDAccountExternalSignature
import java.util.*
import java.util.regex.Pattern


open class SendBtcILViewModel(application: Application) : SendCoinsViewModel(application) {
    override val uriPattern =  Pattern.compile("[a-zA-Z0-9]+")!!

    override fun init(account: WalletAccount<*>, intent: Intent) {
        super.init(account, intent)
        model = SendBtcILModel(context, account, intent)
    }

    override fun sendTransaction(activity: Activity) {
        if (isColdStorage() || model.account is HDAccountExternalSignature) {
            // We do not ask for pin when the key is from cold storage or from a external device (trezor,...)
            model.signTransaction(activity)
            sendFioObtData()
        } else {
            mbwManager.runPinProtectedFunction(activity) {
                model.signTransaction(activity)
                sendFioObtData()
            }
        }
    }

    fun getReceivingAddresses() = (model as SendBtcILModel).receivingAddressesList

    fun getFeeDescription() = (model as SendBtcILModel).feeDescription

    fun isFeeExtended() = (model as SendBtcILModel).isFeeExtended

    override fun getFeeFormatter() = BtcFeeFormatter()

    override fun processReceivedResults(requestCode: Int, resultCode: Int, data: Intent?, activity: Activity) {
        if (requestCode == SendCoinsActivity.SCAN_RESULT_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                when (data?.getSerializableExtra(StringHandlerActivity.RESULT_TYPE_KEY) as ResultType) {
                    ResultType.PRIVATE_KEY -> {
                        val publicKey = data.getPrivateKey().publicKey
                        val addresses = publicKey.getAllSupportedAddresses(mbwManager.network).values
                                .map(AddressUtils::fromAddress)
                        val model = this.model as SendBtcILModel
                        model.receivingAddress.value = addresses[0]
                        if (addresses.size > 1) {
                            model.receivingAddressesList.value = addresses
                        }
                        return
                    }
                    ResultType.ASSET_URI -> {
                        val uri = data.getAssetUri()
                        if (uri is BitcoinILUri && uri.callbackURL != null) {
                            //we contact the merchant server instead of using the params
                            model.genericUri.value = uri
                            model.paymentFetched.value = false
                            verifyPaymentRequest(uri, activity)
                            return
                        }
                    }
                }
            }
        } else if (requestCode == SendCoinsActivity.SIGN_TRANSACTION_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            model.signedTransaction =
                    (data!!.getSerializableExtra(SendCoinsActivity.SIGNED_TRANSACTION)) as Transaction
            // if we have a payment request with a payment_url, handle the send differently:
            (model as SendBtcILModel).apply {
                if (hasPaymentRequestHandler() && hasPaymentCallbackUrl()) {

                    // check again if the payment request isn't expired, as signing might have taken some time
                    // (e.g. with external signature provider)
                    if (!paymentRequestExpired()) {
                        // first send signed tx directly to the Merchant, and broadcast
                        // it only if we get a ACK from him (in paymentRequestAck)
                        sendResponseToPR()
                    } else {
                        Toaster(activity).toast(R.string.payment_request_not_sent_expired, false)
                    }
                    return
                }
            }
        }
        super.processReceivedResults(requestCode, resultCode, data, activity)
    }
}

@BindingAdapter(
        "receivingAddressesList",
        "address",
        requireAll = false
)
fun updateBtcILReceiversView(view: SelectableRecyclerView, receivingAddressesList: MutableLiveData<List<Address>>,
                        receivingAddress: MutableLiveData<Address>) {
    if (receivingAddressesList.value!!.isEmpty() || view.adapter != null) {
        return
    }
    view.apply {
        setHasFixedSize(true)
        setItemWidth(resources.getDimensionPixelSize(R.dimen.item_addr_width))

        // these labels needed for readability
        val addressLabels = HashMap<AddressType, Array<String>>()
        addressLabels[AddressType.P2PKH] = arrayOf("Legacy", "P2PKH")
        addressLabels[AddressType.P2WPKH] = arrayOf("SegWit native", "Bech32")
        addressLabels[AddressType.P2SH_P2WPKH] = arrayOf("SegWit compat.", "P2SH")

        val addressesList = ArrayList<AddressItem>()
        for (address in receivingAddressesList.value!!) {
            val btcAddress = address as BtcAddress
            val btcAddressLabels = checkNotNull(addressLabels[btcAddress.type])
            addressesList.add(AddressItem(address,
                    btcAddressLabels[1],
                    btcAddressLabels[0],
                    SelectableRecyclerView.SRVAdapter.VIEW_TYPE_ITEM))
        }

        val displaySize = Point()
        display?.getSize(displaySize)
        val senderFinalWidth = displaySize.x
        val addressFirstItemWidth = (senderFinalWidth - view.resources.getDimensionPixelSize(R.dimen.item_addr_width)) / 2

        val adapter = AddressViewAdapter(addressesList, addressFirstItemWidth)
        this.adapter = adapter

        setSelectListener { rvAdapter, position ->
            val address = (rvAdapter as AddressViewAdapter).getItem(position)
            receivingAddress.value = address.address!!
        }
        selectedItem = adapter.itemCount - 1
    }
}

@BindingAdapter("feeExtDialogOnClick")
fun openBitcoinILFeeExtensionDialogOnWarningClick(target: TextView, shouldOpen: Boolean) {
    if (shouldOpen) {
        target.setOnClickListener {
            AlertDialog.Builder(target.context)
                    .setMessage(R.string.fee_change_description)
                    .setPositiveButton(R.string.button_ok, null).create()
                    .show()
        }
    } else {
        target.setOnClickListener(null)
    }
}