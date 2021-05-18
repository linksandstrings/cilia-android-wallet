package com.mycelium.bequant.signin

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.View.GONE
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.mycelium.bequant.common.ErrorHandler
import com.mycelium.bequant.common.loader
import com.mycelium.bequant.remote.client.models.AccountPasswordSetRequest
import com.mycelium.bequant.remote.repositories.Api
import com.mycelium.bequant.signup.viewmodel.SignUpViewModel
import com.cilia.wallet.R
import com.cilia.wallet.databinding.FragmentBequantChangePasswordBindingImpl
import kotlinx.android.synthetic.main.fragment_bequant_change_password.*
import kotlinx.android.synthetic.main.layout_password_registration.*


class ResetPasswordChangeFragment : Fragment() {

    lateinit var viewModel: SignUpViewModel

    val args by navArgs<ResetPasswordChangeFragmentArgs>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        viewModel = ViewModelProviders.of(this).get(SignUpViewModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
            DataBindingUtil.inflate<FragmentBequantChangePasswordBindingImpl>(inflater, R.layout.fragment_bequant_change_password, container, false)
                    .apply {
                        this.viewModel = this@ResetPasswordChangeFragment.viewModel
                        lifecycleOwner = this@ResetPasswordChangeFragment
                    }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as AppCompatActivity?)?.supportActionBar?.run {
            title = getString(R.string.bequant_page_title_reset_password)
            setHomeAsUpIndicator(resources.getDrawable(R.drawable.ic_bequant_arrow_back))
        }
        val mail = args.email
        val token = args.token
        viewModel.email.value = mail
        viewModel.password.observe(this, Observer { value ->
            passwordLayout.error = null
            viewModel.calculatePasswordLevel(value, passwordLevel, passwordLevelLabel)
        })
        viewModel.repeatPassword.observe(this, Observer { value ->
            repeatPasswordLayout.error = null
        })
        password.setOnFocusChangeListener { _, focus ->
            passwordNote.setTextColor(if (focus) Color.WHITE else Color.parseColor("#49505C"))
            if (focus) {
                viewModel.calculatePasswordLevel(viewModel.password.value
                        ?: "", passwordLevel, passwordLevelLabel)
            } else {
                viewModel.passwordLevelVisibility.value = GONE
            }
        }
        changePassword.setOnClickListener {
            val request = AccountPasswordSetRequest(viewModel.password.value!!, token)
            loader(true)
            Api.signRepository.resetPasswordSet(lifecycleScope, request, {
                findNavController().navigate(ResetPasswordChangeFragmentDirections.finish())
            }, error = { _, message ->
                ErrorHandler(requireContext()).handle(message)
            }, finally = {
                loader(false)
            })
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
            when (item.itemId) {
                android.R.id.home -> {
                    activity?.onBackPressed()
                    true
                }
                else -> super.onOptionsItemSelected(item)
            }
}