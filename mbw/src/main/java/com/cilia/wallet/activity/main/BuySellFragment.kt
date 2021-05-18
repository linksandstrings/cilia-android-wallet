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

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.mycelium.view.ItemCentralizer
import com.cilia.wallet.MbwManager
import com.cilia.wallet.R
import com.cilia.wallet.activity.main.adapter.ButtonAdapter
import com.cilia.wallet.activity.main.adapter.ButtonClickListener
import com.cilia.wallet.activity.main.model.ActionButton
import com.cilia.wallet.activity.settings.SettingsPreference.fioEnabled
import com.cilia.wallet.activity.settings.SettingsPreference.getBalanceContent
import com.cilia.wallet.activity.settings.SettingsPreference.isContentEnabled
import com.cilia.wallet.event.PageSelectedEvent
import com.cilia.wallet.event.SelectedAccountChanged
import com.cilia.wallet.external.Ads.openFio
import com.cilia.wallet.external.BuySellSelectActivity
import com.cilia.wallet.external.changelly.ChangellyActivity
import com.cilia.wallet.external.changelly.bch.ExchangeActivity
import com.cilia.wallet.external.partner.model.BuySellButton
import com.mycelium.wapi.wallet.bch.bip44.Bip44BCHAccount
import com.mycelium.wapi.wallet.bch.single.SingleAddressBCHAccount
import com.mycelium.wapi.wallet.eth.EthAccount
import com.squareup.otto.Subscribe
import kotlinx.android.synthetic.main.main_buy_sell_fragment.*

class BuySellFragment : Fragment(R.layout.main_buy_sell_fragment), ButtonClickListener {
    enum class ACTION {
        BCH, ALT_COIN, BTC, FIO, ETH, ADS
    }

    private lateinit var mbwManager: MbwManager
    private val buttonAdapter = ButtonAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        setHasOptionsMenu(false)
        super.onCreate(savedInstanceState)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mbwManager = MbwManager.getInstance(context)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        button_list.adapter = buttonAdapter
        button_list.addOnScrollListener(ItemCentralizer())
        buttonAdapter.setClickListener(this)
        recreateActions()
    }

    private fun recreateActions() {
        val actions = mutableListOf<ActionButton>()
        if (mbwManager.selectedAccount is EthAccount) {
            actions.add(ActionButton(ACTION.ALT_COIN, getString(R.string.exchange_altcoins_to_btc)))
            actions.add(ActionButton(ACTION.ETH, getString(R.string.buy_ethereum)))
            addAdsContent(actions)
        } else {
            val showButton = mbwManager.environmentSettings.buySellServices.any { input -> input!!.isEnabled(mbwManager) }
            if (mbwManager.selectedAccount is Bip44BCHAccount ||
                    mbwManager.selectedAccount is SingleAddressBCHAccount) {
                actions.add(ActionButton(ACTION.BCH, getString(R.string.exchange_bch_to_btc)))
            } else {
                actions.add(ActionButton(ACTION.ALT_COIN, getString(R.string.exchange_altcoins_to_btc)))
                if (showButton) {
                    actions.add(ActionButton(ACTION.BTC, getString(R.string.gd_buy_sell_button)))
                }
                addAdsContent(actions)
                addFio(actions)
            }
        }
        buttonAdapter.setButtons(actions)
        button_list.postDelayed(ScrollToRunner(1), 500)
    }

    private fun addAdsContent(actions: MutableList<ActionButton>) {
        val balanceContent = getBalanceContent()
                ?: return
        val indexs = mutableSetOf<Int>()
        for (button in balanceContent.buttons) {
            if (button.isActive() && isContentEnabled(button.parentId ?: "")) {
                val args = Bundle()
                args.putSerializable("data", button)
                button.index?.let {
                    indexs.add(it)
                    args.putInt("index", it)
                }
                if (ACTION.values().map { it.toString() }.contains(button.name)) {
                    actions.find { it.id == ACTION.valueOf(button.name ?: "") }?.let { item ->
                        item.args = args
                    }
                } else {
                    actions.add(ActionButton(ACTION.ADS, button.name
                            ?: "", 0, button.iconUrl, args))
                }
            }
        }
        var index = 0
        actions.forEach {
            if (!it.args.containsKey("index")) {
                while (indexs.contains(index)) {
                    index++
                }
                it.args.putInt("index", index)
            }
        }
        actions.sortBy { it.args.getInt("index") }
    }

    private fun addFio(actions: MutableList<ActionButton>) {
        if (fioEnabled) {
            actions.add(ActionButton(ACTION.FIO, getString(R.string.partner_fiopresale), R.drawable.ic_fiopresale_icon_small))
        }
    }

    override fun onClick(actionButton: ActionButton) {
        when (actionButton.id) {
            ACTION.BCH -> startActivity(Intent(activity, ExchangeActivity::class.java))
            ACTION.ALT_COIN -> startActivity(Intent(activity, ChangellyActivity::class.java))
            ACTION.ETH, ACTION.BTC -> startActivity(Intent(activity, BuySellSelectActivity::class.java))
            ACTION.FIO -> openFio(requireContext())
            ACTION.ADS -> if (actionButton.args.containsKey("data")) {
                (actionButton.args.getSerializable("data") as BuySellButton?)?.let { data ->
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(data.link)))
                    } catch (ignored: ActivityNotFoundException) {
                    }
                }
            }
        }
    }

    internal inner class ScrollToRunner(var scrollTo: Int) : Runnable {
        override fun run() {
            button_list?.smoothScrollToPosition(scrollTo)
        }
    }


    override fun onStart() {
        MbwManager.getEventBus().register(this)
        recreateActions()
        super.onStart()
    }

    override fun onStop() {
        MbwManager.getEventBus().unregister(this)
        super.onStop()
    }

    @Subscribe
    fun selectedAccountChanged(event: SelectedAccountChanged?) {
        recreateActions()
    }

    @Subscribe
    fun pageSelectedEvent(event: PageSelectedEvent) {
        if (event.position == 1) {
            recreateActions()
        }
    }
}