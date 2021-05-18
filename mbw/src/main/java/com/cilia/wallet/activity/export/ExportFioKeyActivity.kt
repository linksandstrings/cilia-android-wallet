package com.cilia.wallet.activity.export

import android.os.Bundle
import android.view.View.*
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.google.common.base.Optional
import com.mrd.bitlib.crypto.BipDerivationType
import com.cilia.wallet.MbwManager
import com.cilia.wallet.R
import com.cilia.wallet.activity.export.viewmodel.ExportFioAsQrViewModel
import com.cilia.wallet.databinding.ActivityExportFioKeyBinding
import com.mycelium.wapi.wallet.ExportableAccount
import com.mycelium.wapi.wallet.btc.bip44.HDAccount
import com.mycelium.wapi.wallet.fio.FioKeyManager
import kotlinx.android.synthetic.main.export_as_qr_activity_qr.*
import kotlinx.android.synthetic.main.export_as_qr_activity_share.*

class ExportFioKeyActivity : AppCompatActivity() {
    private lateinit var viewModel: ExportAsQrViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        val mbwManager = MbwManager.getInstance(this)
        val fioKeyManager = FioKeyManager(mbwManager.masterSeedManager)
        val publicKey = fioKeyManager.getFioPublicKey((mbwManager.selectedAccount as HDAccount).accountIndex)
        val formatPubKey = fioKeyManager.formatPubKey(publicKey)


        val viewModelProvider = ViewModelProviders.of(this)
        viewModel = viewModelProvider.get(ExportFioAsQrViewModel::class.java)

        if (!viewModel.isInitialized()) {
            viewModel.init(ExportableAccount.Data(Optional.absent(), mapOf(BipDerivationType.BIP44 to formatPubKey)))
        }

        val binding = DataBindingUtil.setContentView<ActivityExportFioKeyBinding>(this, R.layout.activity_export_fio_key).also {
            it.viewModel = viewModel as ExportFioAsQrViewModel
            it.activity = this
        }
        binding.lifecycleOwner = this
        subscribeQR()
        tvWarning.visibility = GONE
        btShare.text = getString(R.string.share_fio_public_key)
    }

    // sets key as qr and as textView
    private fun subscribeQR() =
            viewModel.getAccountDataString().observe(this, Observer { accountData -> ivQrCode.qrCode = accountData })

}
