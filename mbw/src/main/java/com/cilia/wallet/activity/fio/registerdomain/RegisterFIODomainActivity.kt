package com.cilia.wallet.activity.fio.registerdomain

import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.cilia.wallet.MbwManager
import com.cilia.wallet.R
import com.cilia.wallet.Utils
import com.cilia.wallet.activity.fio.registerdomain.viewmodel.RegisterFioDomainViewModel
import com.cilia.wallet.activity.fio.registername.RegisterFioNameActivity
import com.mycelium.wapi.wallet.coins.Value
import com.mycelium.wapi.wallet.fio.FioAccount
import com.mycelium.wapi.wallet.fio.FioModule
import com.mycelium.wapi.wallet.fio.coins.isFioDomain
import fiofoundation.io.fiosdk.models.fionetworkprovider.FIOApiEndPoints
import java.util.*

class RegisterFIODomainActivity : AppCompatActivity(R.layout.activity_register_fio_domain) {
    private lateinit var viewModel: RegisterFioDomainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.run {
            setHomeAsUpIndicator(R.drawable.ic_back_arrow)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(true)
            title = "Register FIO Domain"
        }

        viewModel = ViewModelProviders.of(this).get(RegisterFioDomainViewModel::class.java)

        // set default fee at first, it will be updated in async task
        viewModel.registrationFee.value = Value.valueOf(Utils.getFIOCoinType(), DEFAULT_FEE)
        val fioModule = MbwManager.getInstance(this).getWalletManager(false).getModuleById(FioModule.ID) as FioModule
        viewModel.domain.observe(this, Observer { domain ->
            if (viewModel.domain.value!!.isNotEmpty()) {
                viewModel.isFioDomainValid.value = domain.isFioDomain().also { domainValid ->
                    if (domainValid) {
                        Log.i("asdaf", "asdaf checking avail. for $domain")
                        RegisterFioNameActivity.CheckAddressAvailabilityTask(MbwManager.getInstance(this).fioEndpoints, domain, fioModule) { isAvailable ->
                            if (isAvailable != null) {
                                viewModel.isFioDomainAvailable.value = isAvailable
                            } else {
                                viewModel.isFioServiceAvailable.value = false
                            }
                        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
                    }
                }
            }
        })
        (intent.getSerializableExtra(EXT_ACCOUNT) as? UUID)?.let {
            val walletManager = MbwManager.getInstance(this).getWalletManager(false)
            viewModel.fioAccountToRegisterName.value = walletManager.getAccount(it) as? FioAccount
        }
        (intent.getSerializableExtra(EXT_RENEW) as? Boolean)?.let {
            viewModel.isRenew.value = true
            (intent.getSerializableExtra(EXT_FIO_DOMAIN) as? String)?.let {
                viewModel.domain.value = it
            }
        }
        RegisterFioNameActivity.UpdateFeeTask(MbwManager.getInstance(this).fioEndpoints,
                FIOApiEndPoints.FeeEndPoint.RegisterFioDomain.endpoint, fioModule) { feeInSUF ->
            if (feeInSUF != null) {
                viewModel.registrationFee.value = Value.valueOf(Utils.getFIOCoinType(), feeInSUF)
                Log.i("asdaf", "asdaf updated fee: $feeInSUF, viewModel.registrationFee: ${viewModel.registrationFee.value}")
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)

        if (viewModel.isRenew.value!!) {
            supportFragmentManager.beginTransaction().add(R.id.container, RenewFioDomainFragment()).commit()
        } else {
            supportFragmentManager.beginTransaction().add(R.id.container, RegisterFioDomainFragment()).commit()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean =
            when (item?.itemId) {
                android.R.id.home -> {
                    onBackPressed()
                    true
                }
                else -> super.onOptionsItemSelected(item)
            }

    companion object {
        private const val EXT_ACCOUNT = "account"
        private const val EXT_FIO_DOMAIN = "fioDomain"
        private const val EXT_RENEW = "renew"
        const val DEFAULT_FEE = "200000000000" // 200 FIO

        fun start(context: Context) {
            context.startActivity(Intent(context, RegisterFIODomainActivity::class.java))
        }

        @JvmStatic
        fun start(context: Context, account: UUID) {
            context.startActivity(Intent(context, RegisterFIODomainActivity::class.java)
                    .putExtra(EXT_ACCOUNT, account))
        }

        fun startRenew(context: Context, account: UUID, fioDomain: String) {
            context.startActivity(Intent(context, RegisterFIODomainActivity::class.java)
                    .putExtra(EXT_ACCOUNT, account)
                    .putExtra(EXT_FIO_DOMAIN, fioDomain)
                    .putExtra(EXT_RENEW, true))
        }
    }
}
