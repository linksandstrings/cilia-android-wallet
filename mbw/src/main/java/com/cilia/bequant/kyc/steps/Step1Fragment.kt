package com.mycelium.bequant.kyc.steps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.view.View.GONE
import android.view.View.VISIBLE
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.viewModelScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.mycelium.bequant.BequantPreference
import com.mycelium.bequant.BequantConstants
import com.mycelium.bequant.common.BQDatePickerDialog
import com.mycelium.bequant.common.ErrorHandler
import com.mycelium.bequant.common.loader
import com.mycelium.bequant.kyc.inputPhone.coutrySelector.CountryModel
import com.mycelium.bequant.kyc.steps.adapter.ItemStep
import com.mycelium.bequant.kyc.steps.adapter.StepAdapter
import com.mycelium.bequant.kyc.steps.adapter.StepState
import com.mycelium.bequant.kyc.steps.viewmodel.HeaderViewModel
import com.mycelium.bequant.kyc.steps.viewmodel.Step1ViewModel
import com.mycelium.bequant.remote.model.KYCApplicant
import com.mycelium.bequant.remote.model.KYCRequest
import com.mycelium.bequant.remote.repositories.Api
import com.cilia.wallet.R
import com.cilia.wallet.databinding.FragmentBequantSteps1Binding
import kotlinx.android.synthetic.main.fragment_bequant_steps_1.*
import kotlinx.android.synthetic.main.part_bequant_step_header.*
import kotlinx.android.synthetic.main.part_bequant_stepper_body.*
import java.util.*


class Step1Fragment : Fragment() {
    lateinit var viewModel: Step1ViewModel
    lateinit var headerViewModel: HeaderViewModel
    lateinit var kycRequest: KYCRequest

    val args: Step1FragmentArgs by navArgs()

    val receiver = object : BroadcastReceiver() {
        override fun onReceive(p0: Context?, intent: Intent?) {
            intent?.getParcelableExtra<CountryModel>(BequantConstants.COUNTRY_MODEL_KEY)?.let {
                viewModel.nationality.value = it.nationality
                viewModel.nationalityAcronum.value = it.acronym3
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        kycRequest = args.kycRequest ?: KYCRequest()
        viewModel = ViewModelProviders.of(this).get(Step1ViewModel::class.java)
        viewModel.fromModel(kycRequest)
        headerViewModel = ViewModelProviders.of(this).get(HeaderViewModel::class.java)
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(receiver, IntentFilter(BequantConstants.ACTION_COUNTRY_SELECTED))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
            DataBindingUtil.inflate<FragmentBequantSteps1Binding>(inflater, R.layout.fragment_bequant_steps_1, container, false)
                    .apply {
                        viewModel = this@Step1Fragment.viewModel
                        headerViewModel = this@Step1Fragment.headerViewModel
                        lifecycleOwner = this@Step1Fragment
                    }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as AppCompatActivity?)?.supportActionBar?.run {
            title = getString(R.string.identity_auth)
            setHomeAsUpIndicator(resources.getDrawable(R.drawable.ic_bequant_arrow_back))
        }
        step.text = getString(R.string.step_n, 1)
        stepProgress.progress = 1
        val stepAdapter = StepAdapter()
        stepper.adapter = stepAdapter
        stepAdapter.submitList(listOf(
                ItemStep(1, getString(R.string.personal_info), StepState.CURRENT), ItemStep(2, getString(R.string.residential_address), StepState.FUTURE), ItemStep(3, getString(R.string.phone_number), StepState.FUTURE), ItemStep(4, getString(R.string.doc_selfie), StepState.FUTURE)))

        tvDateOfBirth.setOnClickListener {
            BQDatePickerDialog { year, month, day ->
                val calendar = Calendar.getInstance()
                calendar.set(year, month, day)
                viewModel.birthday.value = calendar.time
            }.show(childFragmentManager, "picker_dialog")
        }
        tvNationality.setOnClickListener {
            findNavController().navigate(Step1FragmentDirections.actionSelectCountry())
        }

        btNext.setOnClickListener {
            if (underFatca.isChecked) {
                findNavController().navigate(Step1FragmentDirections.actionFatca())
            } else {
                kycRequest.fatca = underFatca.isChecked
                viewModel.fillModel(kycRequest)
                BequantPreference.setKYCRequest(kycRequest)
                sendData()
            }
        }
        termsOfUse.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BequantConstants.LINK_TERMS_OF_USE)))
        }
        viewModel.firstName.observe(viewLifecycleOwner, Observer {
            viewModel.nextButton.value = viewModel.isValid()
        })
        viewModel.lastName.observe(viewLifecycleOwner, Observer {
            viewModel.nextButton.value = viewModel.isValid()
        })
        viewModel.birthday.observe(viewLifecycleOwner, Observer {
            viewModel.nextButton.value = viewModel.isValid()
        })
        viewModel.nationality.observe(viewLifecycleOwner, Observer {
            viewModel.nextButton.value = viewModel.isValid()
        })
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_bequant_kyc_step, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
            when (item.itemId) {
                android.R.id.home -> {
                    activity?.onBackPressed()
                    true
                }
                R.id.stepper -> {
                    item.icon = resources.getDrawable(if (stepperLayout.visibility == VISIBLE) R.drawable.ic_chevron_down else R.drawable.ic_chevron_up)
                    stepperLayout.visibility = if (stepperLayout.visibility == VISIBLE) GONE else VISIBLE
                    true
                }
                else -> super.onOptionsItemSelected(item)
            }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(receiver)
        super.onDestroy()
    }

    private fun sendData() {
        loader(true)
        Api.signRepository.accountOnceToken(viewModel.viewModelScope, {
            it?.token?.let { onceToken ->
                val applicant = KYCApplicant(BequantPreference.getEmail(), BequantPreference.getPhone())
                applicant.userId = onceToken
                BequantPreference.setKYCRequest(kycRequest)
                Api.kycRepository.create(viewModel.viewModelScope, kycRequest.toModel(applicant), {
                    nextPage()
                }, { _, msg ->
                    loader(false)
                    ErrorHandler(requireContext()).handle(msg)
                })
            }
        }, { _, msg ->
            loader(false)
            ErrorHandler(requireContext()).handle(msg)
        })
    }

    private fun nextPage() {
        when {
            BequantPreference.getKYCSectionStatus("residential_address") -> {
                findNavController().navigate(Step1FragmentDirections.actionEditStep2(BequantPreference.getKYCRequest()))
            }
            BequantPreference.getKYCSectionStatus("phone") -> {
                findNavController().navigate(Step1FragmentDirections.actionEditStep3(BequantPreference.getKYCRequest()))
            }
            BequantPreference.getKYCSectionStatus("documents") -> {
                findNavController().navigate(Step1FragmentDirections.actionEditStep4(BequantPreference.getKYCRequest()))
            }
            else -> {
                findNavController().navigate(Step1FragmentDirections.actionPending())
            }
        }
    }
}