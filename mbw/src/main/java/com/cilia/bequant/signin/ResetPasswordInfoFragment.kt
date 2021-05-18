package com.mycelium.bequant.signin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.mycelium.bequant.BequantConstants
import com.mycelium.bequant.signup.viewmodel.RegistrationInfoViewModel
import com.cilia.wallet.R
import com.cilia.wallet.databinding.FragmentBequantResetPasswordInfoBinding
import kotlinx.android.synthetic.main.part_bequant_not_receive_email.*


class ResetPasswordInfoFragment : Fragment() {

    lateinit var viewModel: RegistrationInfoViewModel

    val args by navArgs<ResetPasswordInfoFragmentArgs>()
    private val resetPasswordConfirmedReceiver = object : BroadcastReceiver() {
        override fun onReceive(p0: Context?, p1: Intent?) {
            findNavController().navigate(ResetPasswordInfoFragmentDirections.actionNext(viewModel.email.value!!,
                    p1?.getStringExtra("token") ?: ""))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        viewModel = ViewModelProviders.of(this).get(RegistrationInfoViewModel::class.java)
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
                resetPasswordConfirmedReceiver,
                IntentFilter(BequantConstants.ACTION_BEQUANT_RESET_PASSWORD_CONFIRMED))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
            DataBindingUtil.inflate<FragmentBequantResetPasswordInfoBinding>(
                    inflater, R.layout.fragment_bequant_reset_password_info, container, false)
                    .apply {
                        viewModel = this@ResetPasswordInfoFragment.viewModel
                        lifecycleOwner = this@ResetPasswordInfoFragment
                    }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as AppCompatActivity?)?.supportActionBar?.run {
            title = getString(R.string.bequant_page_title_reset_password)
            setHomeAsUpIndicator(resources.getDrawable(R.drawable.ic_bequant_clear))
        }
        viewModel.email.value = args.email
        supportTeam.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BequantConstants.LINK_SUPPORT_CENTER)))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(resetPasswordConfirmedReceiver)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
            when (item.itemId) {
                android.R.id.home -> {
                    findNavController().navigate(ResetPasswordInfoFragmentDirections.actionFinish())
                    true
                }
                else -> super.onOptionsItemSelected(item)
            }
}