package com.mycelium.bequant.kyc.steps

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.MediaStore
import android.view.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.mycelium.bequant.BequantPreference
import com.mycelium.bequant.kyc.steps.adapter.*
import com.mycelium.bequant.kyc.steps.viewmodel.DocumentViewModel
import com.mycelium.bequant.kyc.steps.viewmodel.HeaderViewModel
import com.mycelium.bequant.remote.model.KYCDocument
import com.mycelium.bequant.remote.model.KYCRequest
import com.cilia.wallet.R
import com.cilia.wallet.activity.news.NewsImageActivity
import com.cilia.wallet.databinding.FragmentBequantSteps4Binding
import kotlinx.android.synthetic.main.fragment_bequant_steps_4.*
import kotlinx.android.synthetic.main.part_bequant_step_header.*
import kotlinx.android.synthetic.main.part_bequant_stepper_body.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*


class Step4Fragment : Fragment() {
    lateinit var viewModel: DocumentViewModel
    lateinit var headerViewModel: HeaderViewModel
    lateinit var kycRequest: KYCRequest

    val args: Step4FragmentArgs by navArgs()

    private val identityAdapter = DocumentAdapter()
    private val proofAddressAdapter = DocumentAdapter()
    private val selfieAdapter = DocumentAdapter()
    private var counter: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        kycRequest = args.kycRequest
        viewModel = ViewModelProviders.of(this).get(DocumentViewModel::class.java)
        headerViewModel = ViewModelProviders.of(this).get(HeaderViewModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
            DataBindingUtil.inflate<FragmentBequantSteps4Binding>(inflater, R.layout.fragment_bequant_steps_4, container, false)
                    .apply {
                        viewModel = this@Step4Fragment.viewModel
                        headerViewModel = this@Step4Fragment.headerViewModel
                        lifecycleOwner = this@Step4Fragment
                    }.root


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as AppCompatActivity?)?.supportActionBar?.run {
            title = getString(R.string.identity_auth)
            setHomeAsUpIndicator(resources.getDrawable(R.drawable.ic_bequant_arrow_back))
        }
        step.text = getString(R.string.step_n, 4)
        stepProgress.progress = 4
        val stepAdapter = StepAdapter()
        stepper.adapter = stepAdapter
        stepAdapter.submitList(listOf(
                ItemStep(1, getString(R.string.personal_info), StepState.COMPLETE)
                , ItemStep(2, getString(R.string.residential_address), StepState.COMPLETE)
                , ItemStep(3, getString(R.string.phone_number), StepState.COMPLETE)
                , ItemStep(4, getString(R.string.doc_selfie), StepState.CURRENT)))

        identityList.adapter = identityAdapter
        identityAdapter.listChangeListener = {
            viewModel.identityCount.value = it.size
            viewModel.nextButton.value = viewModel.isValid()
        }
        identityAdapter.submitList(kycRequest.identityList.map {
            Document(BitmapFactory.decodeFile(it, bitmapOptions), "Doc ${++counter}", KYCDocument.PASSPORT, it,
                    progress = 100, loadStatus = LoadStatus.LOADED)
        })
        identityAdapter.removeListner = {
            removeDialog { kycRequest.identityList.remove(it.name) }
        }
        identityAdapter.viewListener = {
            startActivity(Intent(requireContext(), NewsImageActivity::class.java)
                    .putExtra("url", it.url))
        }
        proofAddressList.adapter = proofAddressAdapter
        proofAddressAdapter.listChangeListener = {
            viewModel.poaCount.value = it.size
            viewModel.nextButton.value = viewModel.isValid()
        }
        proofAddressAdapter.submitList(kycRequest.poaList.map {
            Document(BitmapFactory.decodeFile(it, bitmapOptions), "Doc ${++counter}", KYCDocument.POA, it)
        })
        proofAddressAdapter.removeListner = {
            removeDialog { kycRequest.poaList.remove(it.name) }
        }
        proofAddressAdapter.viewListener = {
            startActivity(Intent(requireContext(), NewsImageActivity::class.java)
                    .putExtra("url", it.url))
        }
        selfieList.adapter = selfieAdapter
        selfieAdapter.listChangeListener = {
            viewModel.selfieCount.value = it.size
            viewModel.nextButton.value = viewModel.isValid()
        }
        selfieAdapter.submitList(kycRequest.selfieList.map {
            Document(BitmapFactory.decodeFile(it, bitmapOptions), "Doc ${++counter}", KYCDocument.SELFIE, it)
        })
        selfieAdapter.removeListner = {
            removeDialog { kycRequest.selfieList.remove(it.name) }
        }
        selfieAdapter.viewListener = {
            startActivity(Intent(requireContext(), NewsImageActivity::class.java)
                    .putExtra("url", it.url))
        }
        fun uploadClick(requestCode: Int) = { v: View ->
            DocumentAttachDialog().apply {
                setTargetFragment(this@Step4Fragment, requestCode)
            }.show(parentFragmentManager, "upload_document")
        }
        val identityClick = uploadClick(REQUEST_CODE_IDENTITY)
        uploadIdentity.setOnClickListener(identityClick)
        addIdentity.setOnClickListener(identityClick)
        val poaClick = uploadClick(REQUEST_CODE_PROOF_ADDRESS)
        uploadProofAddress.setOnClickListener(poaClick)
        addProofAddress.setOnClickListener(poaClick)
        val selfieClick = uploadClick(REQUEST_CODE_SELFIE)
        uploadSelfie.setOnClickListener(selfieClick)
        addSelfie.setOnClickListener(selfieClick)

        btFinish.setOnClickListener {
            BequantPreference.setKYCRequest(kycRequest)
            findNavController().navigate(Step4FragmentDirections.actionNext(kycRequest))
        }
    }

    private fun removeDialog(remove: () -> Unit) {
        AlertDialog.Builder(requireContext())
                .setMessage(getString(R.string.delete_document))
                .setPositiveButton(R.string.yes) { _, _ ->
                    remove.invoke()
                }.setNegativeButton(R.string.cancel, null)
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
                    item.icon = resources.getDrawable(if (stepperLayout.visibility == View.VISIBLE) R.drawable.ic_chevron_down else R.drawable.ic_chevron_up)
                    stepperLayout.visibility = if (stepperLayout.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                    true
                }
                else -> super.onOptionsItemSelected(item)
            }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                REQUEST_CODE_IDENTITY -> uploadImage(data, identityAdapter, KYCDocument.PASSPORT, kycRequest.identityList)
                REQUEST_CODE_PROOF_ADDRESS -> uploadImage(data, proofAddressAdapter, KYCDocument.POA, kycRequest.poaList)
                REQUEST_CODE_SELFIE -> uploadImage(data, selfieAdapter, KYCDocument.SELFIE, kycRequest.selfieList)
            }
        }
    }

    private fun uploadImage(data: Intent?, adapter: DocumentAdapter, docType: KYCDocument, requestList: MutableList<String>) {
        (if (data?.data != null) getFileFromGallery(data) else DocumentAttachDialog.currentPhotoFile)?.run {
            val item = Document(BitmapFactory.decodeFile(absolutePath, bitmapOptions), "Doc ${++counter}", docType, absolutePath)
            adapter.submitList(adapter.currentList + item)
            requestList.add(File(item.url!!).absolutePath)
        }
    }

    private fun getFileFromGallery(data: Intent): File =
            File.createTempFile(SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()),
                    ".jpg", requireContext().cacheDir).apply {
                MediaStore.Images.Media.getBitmap(requireActivity().contentResolver, data.data)
                        .compress(Bitmap.CompressFormat.JPEG, 100, this.outputStream())
            }

    companion object {
        val bitmapOptions = BitmapFactory.Options().apply { inSampleSize = 3; }
        const val REQUEST_CODE_IDENTITY = 1001
        const val REQUEST_CODE_PROOF_ADDRESS = 1002
        const val REQUEST_CODE_SELFIE = 1003
    }
}