package com.cilia.wallet.activity.receive

import android.app.Application
import android.os.Bundle
import android.text.Html
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import com.mrd.bitlib.model.AddressType
import com.cilia.wallet.R
import com.cilia.wallet.Utils
import com.cilia.wallet.activity.util.toString
import com.mycelium.wapi.wallet.WalletAccount
import com.mycelium.wapi.wallet.btcil.AbstractBtcILAccount
import com.mycelium.wapi.wallet.btcil.BtcILAddress
import com.mycelium.wapi.wallet.btcil.WalletBtcILAccount
import com.mycelium.wapi.wallet.btcil.bip44.HDAccountIL
import com.mycelium.wapi.wallet.btcil.single.SingleAddressAccountIL
import com.mycelium.wapi.wallet.coins.Value

class ReceiveBtcILViewModel(application: Application) : ReceiveCoinsViewModel(application) {
    val addressType: MutableLiveData<AddressType> = MutableLiveData()

    override fun init(account: WalletAccount<*>, hasPrivateKey: Boolean, showIncomingUtxo: Boolean) {
        super.init(account, hasPrivateKey, showIncomingUtxo)
        model = ReceiveCoinsModel(getApplication(), account, ACCOUNT_LABEL, showIncomingUtxo)
        addressType.value = (account as WalletBtcILAccount).receivingAddress.get().type
    }

    fun setAddressType(addressType: AddressType) {
        this.addressType.value = addressType
        model.receivingAddress.value = when (account) {
            is HDAccountIL -> BtcILAddress(Utils.getBtcILCoinType(), (account as HDAccountIL).getReceivingAddress(addressType)!!)
            is SingleAddressAccountIL -> BtcILAddress(Utils.getBtcILCoinType(), (account as SingleAddressAccountIL).getAddress(addressType))
            else -> throw IllegalStateException()
        }
        model.updateObservingAddress()
    }

    fun getAccountDefaultAddressType(): AddressType {
        return when (account) {
            is HDAccountIL -> (account as HDAccountIL).receivingAddress.get().type
            is SingleAddressAccountIL -> (account as SingleAddressAccountIL).address.type
            else -> throw IllegalStateException()
        }
    }

    fun setCurrentAddressTypeAsDefault() {
        (account as AbstractBtcILAccount).setDefaultAddressType(addressType.value)
        this.addressType.value = addressType.value // this is required to update UI
    }

    fun getAvailableAddressTypesCount() = (account as AbstractBtcILAccount).availableAddressTypes.size


    override fun getCurrencyName(): String = "BitcoinIL"

    override fun getFormattedValue(sum: Value) = sum.toString(mbwManager.getDenomination(account.coinType))

    fun showAddressTypesInfo(activity: AppCompatActivity) {
        // building message based on networking preferences
        val dialogMessage = if (mbwManager.network.isProdnet) {
            activity.getString(R.string.what_is_address_type_description, "1", "3", "bc1")
        } else {
            activity.getString(R.string.what_is_address_type_description, "m or n", "2", "tb1")
        }

        AlertDialog.Builder(activity, R.style.CiliaModern_Dialog_BlueButtons)
                .setTitle(activity.resources.getString(R.string.what_is_address_type))
                .setMessage(Html.fromHtml(dialogMessage))
                .setPositiveButton(R.string.button_ok, null)
                .create()
                .show()
    }

    override fun loadInstance(savedInstanceState: Bundle) {
        setAddressType(savedInstanceState.getSerializable(ADDRESS_TYPE) as AddressType)
        super.loadInstance(savedInstanceState)
    }

    override fun saveInstance(outState: Bundle) {
        outState.putSerializable(ADDRESS_TYPE, addressType.value)
        super.saveInstance(outState)
    }

    override fun getTitle(): String {
        return if (Value.isNullOrZero(model.amount.value)) {
            context.getString(R.string.address_title, "BitcoinIL")
        } else {
            context.getString(R.string.payment_request)
        }
    }

    companion object {
        private const val ACCOUNT_LABEL = "bitcoinil"
        private const val ADDRESS_TYPE = "addressType"
    }
}