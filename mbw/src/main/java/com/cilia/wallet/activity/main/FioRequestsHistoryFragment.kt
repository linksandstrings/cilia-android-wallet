/*
 * Copyright 2013, 2014 Megion Research and Development GmbH
 *
 * Licensed under the Microsoft Reference Source License (MS-RSL)
 *
 * This license governs use of the accompanying software. If you use the software, you accept this license.
 * If you do not accept the license, do not use the software.
 *
 * 1. Definitions
 * The terms "reproduce," "reproduction," and "distribution" have the same meaning here as under U.S. copyright law.
 * "You" means the licensee of the software.
 * "Your company" means the company you worked for when you downloaded the software.
 * "Reference use" means use of the software within your company as a reference, in read only form, for the sole purposes
 * of debugging your products, maintaining your products, or enhancing the interoperability of your products with the
 * software, and specifically excludes the right to distribute the software outside of your company.
 * "Licensed patents" means any Licensor patent claims which read directly on the software as distributed by the Licensor
 * under this license.
 *
 * 2. Grant of Rights
 * (A) Copyright Grant- Subject to the terms of this license, the Licensor grants you a non-transferable, non-exclusive,
 * worldwide, royalty-free copyright license to reproduce the software for reference use.
 * (B) Patent Grant- Subject to the terms of this license, the Licensor grants you a non-transferable, non-exclusive,
 * worldwide, royalty-free patent license under licensed patents for reference use.
 *
 * 3. Limitations
 * (A) No Trademark License- This license does not grant you any rights to use the Licensor’s name, logo, or trademarks.
 * (B) If you begin patent litigation against the Licensor over patents that you think may apply to the software
 * (including a cross-claim or counterclaim in a lawsuit), your license to the software ends automatically.
 * (C) The software is licensed "as-is." You bear the risk of using it. The Licensor gives no express warranties,
 * guarantees or conditions. You may have additional consumer rights under your local laws which this license cannot
 * change. To the extent permitted under your local laws, the Licensor excludes the implied warranties of merchantability,
 * fitness for a particular purpose and non-infringement.
 */
package com.cilia.wallet.activity.main

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.appcompat.view.ActionMode
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.cilia.wallet.MbwManager
import com.cilia.wallet.R
import com.cilia.wallet.activity.fio.AboutFIOProtocolDialog
import com.cilia.wallet.activity.fio.requests.ApproveFioRequestActivity
import com.cilia.wallet.activity.fio.requests.SentFioRequestStatusActivity
import com.cilia.wallet.activity.main.adapter.FioRequestArrayAdapter
import com.cilia.wallet.activity.main.model.fiorequestshistory.FioRequestsHistoryViewModel
import com.cilia.wallet.event.*
import com.cilia.wallet.persistence.MetadataStorage
import com.mycelium.wapi.wallet.fio.FioGroup
import com.squareup.otto.Subscribe
import fiofoundation.io.fiosdk.models.fionetworkprovider.FIORequestContent
import kotlinx.android.synthetic.main.fio_request_history_view.*
import java.util.concurrent.atomic.AtomicBoolean

class FioRequestsHistoryFragment : Fragment(R.layout.fio_request_history_view) {
    private lateinit var _mbwManager: MbwManager
    private var _storage: MetadataStorage? = null
    private val currentActionMode: ActionMode? = null
    private val isLoadingPossible = AtomicBoolean(true)
    private lateinit var adapter: FioRequestArrayAdapter
    private lateinit var viewModel: FioRequestsHistoryViewModel

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        btAboutFioProtocol.setOnClickListener {
            AboutFIOProtocolDialog().show(parentFragmentManager, "modal")
        }

        adapter = FioRequestArrayAdapter(requireActivity(), viewModel.fioRequestHistory.value
                ?: emptyList(), _mbwManager)
        lvTransactionHistory.setOnChildClickListener { _, view, groupPosition, childPosition, l ->
            val item: FIORequestContent = adapter.getChild(groupPosition, childPosition) as FIORequestContent
            if (adapter.getGroup(groupPosition).status == FioGroup.Type.SENT) {
                SentFioRequestStatusActivity.start(requireActivity(), item)
            } else {
                ApproveFioRequestActivity.start(requireActivity(), item)
            }
            false
        }
        viewModel.fioRequestHistory.observe(this.viewLifecycleOwner, Observer {
            adapter.notifyDataSetChanged()
            showHistory(!viewModel.fioRequestHistory.value.isNullOrEmpty())

            //expand all lists
            it.forEachIndexed { index, _ ->
                lvTransactionHistory.expandGroup(index)
            }
            refreshList()
        })

        updateWrapper(adapter)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        viewModel = ViewModelProviders.of(this).get(FioRequestsHistoryViewModel::class.java)
        setHasOptionsMenu(true)
        super.onCreate(savedInstanceState)
        // cache the addressbook for faster lookup
        viewModel.cacheAddressBook()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        _mbwManager = MbwManager.getInstance(context)
        _storage = _mbwManager.metadataStorage
    }

    override fun onResume() {
        MbwManager.getEventBus().register(this)
        super.onResume()
    }

    override fun onPause() {
        MbwManager.getEventBus().unregister(this)
        super.onPause()
    }

    @Subscribe
    fun exchangeRateChanged(event: ExchangeRatesRefreshed?) {
        refreshList()
    }

    private fun refreshList() {
        lvTransactionHistory.invalidateViews()
    }

    @Subscribe
    fun fiatCurrencyChanged(event: SelectedCurrencyChanged?) {
        refreshList()
    }

    @Subscribe
    fun addressBookEntryChanged(event: AddressBookChanged?) {
        viewModel.cacheAddressBook()
        refreshList()
    }

    @Subscribe
    fun selectedAccountChanged(event: SelectedAccountChanged?) {
        isLoadingPossible.set(true)
        lvTransactionHistory.setSelection(0)
        //      updateWrapper(adapter);
    }

    @Subscribe
    fun syncStopped(event: SyncStopped?) {
        // It's possible that new transactions came. Adapter should allow to try to scroll
        isLoadingPossible.set(true)
    }

    private fun showHistory(hasHistory: Boolean) {
        llNoRecords.visibility = if (hasHistory) View.GONE else View.VISIBLE
        lvTransactionHistory.visibility = if (hasHistory) View.VISIBLE else View.GONE
    }

    private fun updateWrapper(adapter: FioRequestArrayAdapter) {
        this.adapter = adapter
        lvTransactionHistory.setAdapter(adapter)
    }

    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        super.setUserVisibleHint(isVisibleToUser)
        if (!isVisibleToUser) {
            finishActionMode()
        }
    }

    private fun finishActionMode() {
        currentActionMode?.finish()
    }
}