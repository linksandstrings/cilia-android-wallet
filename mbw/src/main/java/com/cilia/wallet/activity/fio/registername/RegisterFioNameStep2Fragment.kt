package com.cilia.wallet.activity.fio.registername

import android.os.AsyncTask
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.activity.addCallback
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.cilia.wallet.MbwManager
import com.cilia.wallet.R
import com.cilia.wallet.activity.fio.registername.viewmodel.RegisterFioNameViewModel
import com.cilia.wallet.activity.modern.Toaster
import com.cilia.wallet.activity.util.toStringWithUnit
import com.cilia.wallet.databinding.FragmentRegisterFioNameStep2BindingImpl
import com.mycelium.wapi.wallet.fio.*
import fiofoundation.io.fiosdk.errors.FIOError
import kotlinx.android.synthetic.main.fragment_register_fio_name_step2.*
import java.util.logging.Level
import java.util.logging.Logger


class RegisterFioNameStep2Fragment : Fragment() {
    private val viewModel: RegisterFioNameViewModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // without this the navigation through back button would finish the activity
        // but the desired behavior here is to return back to step 1
        requireActivity().onBackPressedDispatcher.addCallback(this) {
            findNavController().navigate(R.id.actionNext)
        }.apply { this.isEnabled = true }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
            DataBindingUtil.inflate<FragmentRegisterFioNameStep2BindingImpl>(inflater, R.layout.fragment_register_fio_name_step2, container, false)
                    .apply {
                        viewModel = this@RegisterFioNameStep2Fragment.viewModel.apply {
                            val fioAccounts = getFioAccountsToRegisterTo(this.domain.value!!)
                            spinnerFioAccounts?.adapter = ArrayAdapter<String>(requireContext(),
                                    R.layout.layout_fio_dropdown_medium_font, R.id.text, fioAccounts.map { it.label }).apply {
                                this.setDropDownViewResource(R.layout.layout_send_coin_transaction_replace_dropdown)
                            }
                            spinnerFioAccounts.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                                override fun onNothingSelected(p0: AdapterView<*>?) {}
                                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                                    viewModel!!.fioAccountToRegisterName.value = fioAccounts[p2]
                                    // temporary account to register on and to pay fee from are the same
                                    // until the ability of paying with other currencies is implemented
                                    // TODO remove next line when it's ready
                                    spinnerPayFromAccounts.setSelection((spinnerPayFromAccounts.adapter as ArrayAdapter<String>).getPosition(
                                            "${fioAccounts[p2].label} ${fioAccounts[p2].accountBalance.spendable.toStringWithUnit()}"))
                                }
                            }
                            spinnerPayFromAccounts?.adapter = ArrayAdapter<String>(requireContext(),
                                    R.layout.layout_fio_dropdown_medium_font, R.id.text,
                                    fioAccounts.map { "${it.label} ${it.accountBalance.spendable.toStringWithUnit()}" }).apply {
                                this.setDropDownViewResource(R.layout.layout_send_coin_transaction_replace_dropdown)
                            }
                            spinnerPayFromAccounts.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                                override fun onNothingSelected(p0: AdapterView<*>?) {}
                                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                                    viewModel!!.accountToPayFeeFrom.value = fioAccounts[p2]
                                    // temporary account to register on and to pay fee from are the same
                                    // until the ability of paying with other currencies is implemented
                                    // TODO remove next line when it's ready
                                    spinnerFioAccounts.setSelection((spinnerFioAccounts.adapter as ArrayAdapter<String>).getPosition(
                                            fioAccounts[p2].label))
                                }
                            }

                            // preselect account which context menu was used
                            val fioAccount = this.fioAccountToRegisterName.value
                            if (fioAccount != null) {
                                spinnerFioAccounts.setSelection((spinnerFioAccounts.adapter as ArrayAdapter<String>).getPosition(
                                        fioAccount.label))
                                // temporary account to register on and to pay fee from are the same
                                // until the ability of paying with other currencies is implemented
                                // TODO remove next line when it's ready
                                spinnerPayFromAccounts.setSelection((spinnerPayFromAccounts.adapter as ArrayAdapter<String>).getPosition(
                                        "${fioAccount.label} ${fioAccount.accountBalance.spendable.toStringWithUnit()}"))
                            }
                        }
                        lifecycleOwner = this@RegisterFioNameStep2Fragment
                    }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvNotEnoughFundsError.visibility = View.GONE
        btNextButton.setOnClickListener {
            val fioModule = MbwManager.getInstance(context).getWalletManager(false).getModuleById(FioModule.ID) as FioModule
            RegisterAddressTask(viewModel.fioAccountToRegisterName.value!!, viewModel.addressWithDomain.value!!, fioModule) { expiration ->
                if (expiration != null) {
                    requireActivity().supportFragmentManager
                            .beginTransaction()
                            .replace(R.id.container,
                                    RegisterFioNameCompletedFragment.newInstance(viewModel.addressWithDomain.value!!,
                                            viewModel.fioAccountToRegisterName.value!!.label, expiration))
                            .addToBackStack(null)
                            .commit()
                } else {
                    Toaster(this).toast("Something went wrong", true)
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
        }
        viewModel.registrationFee.observe(viewLifecycleOwner, Observer {
            tvFeeInfo.text = resources.getString(R.string.fio_annual_fee, it.toStringWithUnit())
        })
        viewModel.accountToPayFeeFrom.observe(viewLifecycleOwner, Observer {
            val isNotEnoughFunds = it.accountBalance.spendable < viewModel.registrationFee.value!!
            tvNotEnoughFundsError.visibility = if (isNotEnoughFunds) View.VISIBLE else View.GONE
            btNextButton.isEnabled = !isNotEnoughFunds
            (spinnerPayFromAccounts.getChildAt(0) as? TextView)?.setTextColor(
                    if (isNotEnoughFunds) resources.getColor(R.color.fio_red) else resources.getColor(R.color.white))
        })
        icEdit.setOnClickListener {
            findNavController().navigate(R.id.actionNext)
        }
    }

    private fun getFioAccountsToRegisterTo(fioDomain: FIODomain): List<FioAccount> {
        val walletManager = MbwManager.getInstance(context).getWalletManager(false)
        return if (fioDomain.isPublic) {
            walletManager.getActiveSpendableFioAccounts()
        } else {
            val uuid = (walletManager.getModuleById(FioModule.ID) as FioModule).getFioAccountByFioDomain(fioDomain.domain)
                    ?: throw IllegalStateException("Illegal domain ${fioDomain.domain} (Not owned by any of user's accounts)")
            listOf(walletManager.getAccount(uuid) as FioAccount)
        }
    }
}

class RegisterAddressTask(
        val account: FioAccount,
        private val fioAddress: String,
        private val fioModule: FioModule,
        val listener: ((String?) -> Unit)) : AsyncTask<Void, Void, String?>() {
    override fun doInBackground(vararg args: Void): String? {
        return try {
            account.registerFIOAddress(fioAddress)
        } catch (e: Exception) {
            Logger.getLogger(RegisterAddressTask::class.simpleName).log(Level.WARNING, "failed to register fio name: ${e.localizedMessage}")
            if (e is FIOError) {
                fioModule.addFioServerLog(e.toJson())
            }
            null
        }
    }

    override fun onPostExecute(result: String?) {
        listener(result)
    }
}