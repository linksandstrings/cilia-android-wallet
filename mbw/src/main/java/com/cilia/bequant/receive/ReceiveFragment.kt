package com.mycelium.bequant.receive

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.observe
import androidx.navigation.fragment.navArgs
import com.mycelium.bequant.common.loader
import com.mycelium.bequant.receive.adapter.ReceiveFragmentAdapter
import com.mycelium.bequant.receive.viewmodel.ReceiveCommonViewModel
import com.cilia.wallet.R
import kotlinx.android.synthetic.main.fragment_bequant_receive.*
import java.util.*


class ReceiveFragment : Fragment(R.layout.fragment_bequant_receive) {
    val args by navArgs<ReceiveFragmentArgs>()
    lateinit var viewModel: ReceiveCommonViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProviders.of(this).get(ReceiveCommonViewModel::class.java)
        viewModel.currency.value = args.currency

        fetchDepositAddress()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val supportedByMycelium = getSupportedByMycelium(args.currency)
        pager.adapter = ReceiveFragmentAdapter(this, viewModel, supportedByMycelium)
        tabs.setupWithViewPager(pager)
        viewModel.error.observe(viewLifecycleOwner) {
            //if no address just suppress this message, because it is not error
//            ErrorHandler(requireContext()).handle(it)
        }
        viewModel.currency.observe(viewLifecycleOwner, Observer {
            fetchDepositAddress()
        })
    }

    private fun getSupportedByMycelium(currency: String): Boolean {
        return currency.toLowerCase(Locale.getDefault()) in listOf("eth", "btc")
    }

    private fun fetchDepositAddress() {
        this.loader(true)
        viewModel.fetchDepositAddress {
            this.loader(false)
        }
    }
}