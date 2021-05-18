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

package com.cilia.wallet.activity.util

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout

import com.google.common.base.Preconditions
import com.cilia.wallet.MbwManager
import com.cilia.wallet.R
import com.cilia.wallet.event.ExchangeRatesRefreshed
import com.cilia.wallet.event.SelectedCurrencyChanged
import com.cilia.wallet.exchange.ValueSum
import com.mycelium.wapi.wallet.coins.AssetInfo
import com.mycelium.wapi.wallet.coins.Value
import com.mycelium.wapi.wallet.eth.coins.EthCoin
import com.squareup.otto.Bus
import com.squareup.otto.Subscribe
import kotlinx.android.synthetic.main.toggleable_currency_display.view.*
import java.lang.Exception


open class ToggleableCurrencyDisplay : LinearLayout {
    protected val eventBus: Bus = MbwManager.getEventBus()
    protected val currencySwitcher by lazy { MbwManager.getInstance(context).currencySwitcher!! }

    protected var currentValue: Value? = null
    var fiatOnly = false
    protected var hideOnNoExchangeRate = false
    private var precision = -1

    private val valueToShow: Value?
        get() = currencySwitcher.getAsFiatValue(currentValue)

    private var isAddedToBus = false
    var coinType: AssetInfo? = null

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(context)
        parseXML(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        init(context)
        parseXML(context, attrs)
    }

    constructor(context: Context) : super(context) {
        init(context)
    }

    private fun parseXML(context: Context, attrs: AttributeSet) {
        val a = context.obtainStyledAttributes(attrs, R.styleable.ToggleableCurrencyButton)

        for (i in 0 until a.indexCount) {
            val attr = a.getIndex(i)
            when (attr) {
                R.styleable.ToggleableCurrencyButton_fiatOnly -> fiatOnly = a.getBoolean(attr, false)
                R.styleable.ToggleableCurrencyButton_textSize -> setTextSize(a.getDimensionPixelSize(attr, 12))
                R.styleable.ToggleableCurrencyButton_textColor -> setTextColor(a.getColor(attr, resources.getColor(R.color.lightgrey)))
                R.styleable.ToggleableCurrencyButton_hideOnNoExchangeRate -> {
                    hideOnNoExchangeRate = a.getBoolean(attr, false)
                    precision = a.getInteger(attr, -1)
                }
                R.styleable.ToggleableCurrencyButton_precision -> precision = a.getInteger(attr, -1)
            }
        }
        a.recycle()
    }

    protected fun init(context: Context) {
        val mInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

        mInflater.inflate(R.layout.toggleable_currency_display, this, true)
    }

    private fun setTextSize(size: Int) {
        tvCurrency.setTextSize(TypedValue.COMPLEX_UNIT_PX, size.toFloat())
        tvDisplayValue.setTextSize(TypedValue.COMPLEX_UNIT_PX, size.toFloat())
    }

    private fun setTextColor(color: Int) {
        tvCurrency.setTextColor(color)
        tvDisplayValue.setTextColor(color)
    }

    protected open fun updateUi() {
        Preconditions.checkNotNull(currencySwitcher)

        if (fiatOnly) {
            showFiat()
        } else {
            // Switch to cryptocurrency if no fiat fx rate is available
            if (!currencySwitcher.isFiatExchangeRateAvailable(coinType!!)
                    && currencySwitcher.isFiatCurrency(currencySwitcher.currentCurrencyMap[coinType!!])) {
//                    && !currencySwitcher.isFiatCurrency(currencySwitcher.defaultCurrency)) {
                currencySwitcher.setCurrency(coinType!!, coinType)
            }

            visibility = View.VISIBLE
            val displayValue = currentValue?.toString(currencySwitcher.getDenomination(coinType!!)!!)
            tvDisplayValue.text = if (currentValue?.type is EthCoin)
                makeNDigitsAfterComma(displayValue, 4)
            else
                displayValue
            val currentCurrency = currencySwitcher.getCurrentCurrencyIncludingDenomination(coinType!!)
            tvCurrency.text = currentCurrency
        }
    }

    private fun makeNDigitsAfterComma(displayValue: String?, n: Int): CharSequence? {
        displayValue ?: return null
        val commaPos = displayValue.indexOf(".")
        val digitsAfterComma = displayValue.length - (displayValue.indexOf(".") + 1)
        return if (commaPos == -1 || digitsAfterComma < (n + 1)) {
            displayValue
        } else {
            displayValue.substring(0, displayValue.length - (digitsAfterComma - n))
        }
    }

    private fun showFiat() {
        visibility = if (hideOnNoExchangeRate && !currencySwitcher.isFiatExchangeRateAvailable(MbwManager.getInstance(context).selectedAccount.coinType)) {
            // hide everything
            View.GONE
        } else {
            val value = currencySwitcher.getAsFiatValue(currentValue)
            if (coinType == null) { // then it's a total TCB
                tvCurrency.text = currencySwitcher.currentTotalCurrency!!.symbol
                tvDisplayValue.text = value?.toPlainString()
            } else {
                tvCurrency.text = currencySwitcher.currentFiatCurrencyMap[coinType!!]!!.symbol
                tvDisplayValue.text = value?.toString(currencySwitcher.getDenomination(coinType!!)!!)
            }
            View.VISIBLE
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isAddedToBus = true
        eventBus.register(this)
        updateUi()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        // unregister from the event bus
        if (isAddedToBus) {
            eventBus.unregister(this)
            isAddedToBus = false
        }
    }

    fun setValue(value: Value) {
        this.currentValue = value
        updateUi()
    }

    fun setValue(sum: ValueSum, totalBalance: Boolean) {
        try {
            val toCurrency: AssetInfo = if (totalBalance) {
                currencySwitcher.currentTotalCurrency!!
            } else {
                currencySwitcher.currentCurrencyMap[coinType]!!
            }
            this.currentValue = currencySwitcher.getValue(sum, toCurrency)
            updateUi()
        }catch (e:Exception){

        }
    }

    @Subscribe
    open fun onExchangeRateChange(event: ExchangeRatesRefreshed) = updateUi()

    @Subscribe
    open fun onSelectedCurrencyChange(event: SelectedCurrencyChanged) = updateUi()
}