package com.mycelium.bequant.remote.repositories

import android.app.Activity
import com.google.gson.Gson
import com.mycelium.bequant.BequantConstants
import com.mycelium.bequant.BequantConstants.EXCLUDE_COIN_LIST
import com.mycelium.bequant.BequantConstants.LAST_SYMBOLS_UPDATE
import com.mycelium.bequant.BequantConstants.changeCoinToServer
import com.mycelium.bequant.remote.doRequest
import com.mycelium.bequant.remote.trading.api.PublicApi
import com.mycelium.bequant.remote.trading.model.*
import com.mycelium.bequant.remote.trading.model.Currency
import com.cilia.wallet.WalletApplication
import kotlinx.coroutines.CoroutineScope
import java.util.*
import java.util.concurrent.TimeUnit

class PublicApiRepository {
    private val api = PublicApi.create()
    private val preference by lazy { WalletApplication.getInstance().getSharedPreferences(BequantConstants.PUBLIC_REPOSITORY, Activity.MODE_PRIVATE) }

    fun publicCandlesGet(scope: CoroutineScope,
                         symbols: String, period: String, sort: String, from: String, till: String, limit: Int, offset: Int,
                         success: (Any?) -> Unit, error: (Int, String) -> Unit, finally: () -> Unit) {
        doRequest(scope, {
            api.publicCandlesGet(symbols, period, sort, from, till, limit, offset)
        }, successBlock = success, errorBlock = error, finallyBlock = finally)
    }


    fun publicCandlesSymbolGet(scope: CoroutineScope, symbol: String, period: String, sort: String, from: String, till: String, limit: Int, offset: Int,
                               success: (Array<Candle>?) -> Unit, error: (Int, String) -> Unit, finally: () -> Unit) {
        doRequest(scope, {
            api.publicCandlesSymbolGet(symbol, period, sort, from, till, limit, offset)
        }, successBlock = success, errorBlock = error, finallyBlock = finally)
    }

    fun publicCurrencyCurrencyGet(scope: CoroutineScope, currency: String,
                                  success: (Currency?) -> Unit, error: (Int, String) -> Unit, finally: () -> Unit) {
        doRequest(scope, {
            api.publicCurrencyCurrencyGet(changeCoinToServer(currency))
        }, successBlock = success, errorBlock = error, finallyBlock = finally,
                responseModifier = { result ->
                    if (result?.id == "USD") {
                        result.id = "USDT"
                    }
                    result
                })
    }

    // maybe need put it to sharedpreference
    private var publicCurrencies = arrayOf<Currency>()

    fun publicCurrencyGet(scope: CoroutineScope, currencies: String?,
                          success: (Array<Currency>?) -> Unit,
                          error: ((Int, String) -> Unit)? = null,
                          finally: (() -> Unit)? = null) {
        if (currencies == null && publicCurrencies.isNotEmpty()) {
            success(publicCurrencies)
            finally?.invoke()
        } else {
            doRequest(scope, {
                api.publicCurrencyGet(currencies)
            }, successBlock = success, errorBlock = error, finallyBlock = finally,
                    responseModifier = { result ->
                        result?.filterNot { EXCLUDE_COIN_LIST.contains(it.id) }?.apply {
                            firstOrNull { it.id == "USD" }?.id = "USDT"
                        }?.toTypedArray()?.apply {
                            if (currencies == null) {
                                publicCurrencies = this
                            }
                        }
                    })
        }
    }

    fun publicOrderbookGet(scope: CoroutineScope, symbols: String, limit: Int,
                           success: (Any?) -> Unit, error: (Int, String) -> Unit, finally: () -> Unit) {
        doRequest(scope, {
            api.publicOrderbookGet(symbols, limit)
        }, successBlock = success, errorBlock = error, finallyBlock = finally)
    }


    fun publicOrderbookSymbolGet(scope: CoroutineScope, symbol: String, limit: Int, volume: java.math.BigDecimal,
                                 success: (Orderbook?) -> Unit, error: (Int, String) -> Unit, finally: () -> Unit) {

        doRequest(scope, {
            api.publicOrderbookSymbolGet(symbol, limit, volume)
        }, successBlock = success, errorBlock = error, finallyBlock = finally)
    }

    private var publicSymbols: Array<Symbol>
        get() = Gson().fromJson(preference.getString("symbols", "[]"), Array<Symbol>::class.java)
        set(value) {
            preference.edit()
                    .putString("symbols", Gson().toJson(value))
                    .putLong(LAST_SYMBOLS_UPDATE, Date().time)
                    .apply()
        }

    fun publicSymbolGet(scope: CoroutineScope, symbols: String?,
                        success: (Array<Symbol>?) -> Unit, error: (Int, String) -> Unit, finally: (() -> Unit)? = null) {
        if (publicSymbols.isNotEmpty()
                && TimeUnit.MILLISECONDS.toDays(Date().time - preference.getLong(LAST_SYMBOLS_UPDATE, 0)) < 7) {
            if (symbols == null) {
                success(publicSymbols)
            } else {
                success(publicSymbols.filter { symbols.contains(it.id) }.toTypedArray())
            }
            finally?.invoke()
        } else {
            doRequest(scope, {
                api.publicSymbolGet(symbols)
            }, successBlock = success, errorBlock = error, finallyBlock = finally,
                    responseModifier = { result ->
                        result?.filterNot {
                            EXCLUDE_COIN_LIST.contains(it.baseCurrency) || EXCLUDE_COIN_LIST.contains(it.quoteCurrency)
                        }?.apply {
                            filter { it.baseCurrency == "USD" }.forEach { it.baseCurrency = "USDT" }
                            filter { it.quoteCurrency == "USD" }.forEach { it.quoteCurrency = "USDT" }
                        }?.toTypedArray()?.apply {
                            if (symbols == null) {
                                publicSymbols = this
                            }
                        }
                    })
        }
    }

    fun publicSymbolSymbolGet(scope: CoroutineScope, symbol: String,
                              success: (Symbol?) -> Unit, error: (Int, String) -> Unit, finally: () -> Unit) {
        if (publicSymbols.isNotEmpty()) {
            success(publicSymbols.find { it.id == symbol })
            finally()
        } else {
            doRequest(scope, {
                api.publicSymbolSymbolGet(symbol)
            }, successBlock = success, errorBlock = error, finallyBlock = finally)
        }
    }

    private var publicTickers = mutableMapOf<String, Ticker>()
    private val minTimeToUpdate = 5000

    fun publicTickerGet(scope: CoroutineScope,
                        symbols: String?,
                        success: (Array<Ticker>?) -> Unit, error: (Int, String) -> Unit, finally: (() -> Unit)? = null) {
        if (publicTickers.isNotEmpty() &&
                Date().time - (publicTickers[publicTickers.keys.first()]?.timestamp?.time
                        ?: 0) < minTimeToUpdate) {
            if (symbols == null) {
                success(publicTickers.values.toTypedArray())
            } else {
                success(publicTickers.filter { symbols.contains(it.key) }.values.toTypedArray())
            }
            finally?.invoke()
        } else {
            publicSymbolGet(scope, null, {
                doRequest(scope, {
                    api.publicTickerGet(symbols).apply {
                        body()?.forEach {
                            publicTickers[it.symbol] = it
                        }
                    }
                }, successBlock = success, errorBlock = error, finallyBlock = finally)
            }, { code, msg ->
                error(code, msg)
                finally?.invoke()
            }, finally)
        }
    }

    fun publicTickerSymbolGet(scope: CoroutineScope,
                              symbol: String,
                              success: (Ticker?) -> Unit, error: (Int, String) -> Unit, finally: (() -> Unit)? = null) {
        publicTickers[symbol]?.let { fromCache ->
            success(fromCache)
            finally?.invoke()
            if (Date().time - (fromCache.timestamp?.time ?: 0) > minTimeToUpdate) {
                publicTicker(scope, symbol, success, error)
            }
        }
                ?: publicTicker(scope, symbol, success, error, finally)

    }

    private fun publicTicker(scope: CoroutineScope, symbol: String,
                             success: (Ticker?) -> Unit, error: (Int, String) -> Unit, finally: (() -> Unit)? = null) {
        doRequest(scope, {
            api.publicTickerSymbolGet(symbol).apply {
                body()?.let {
                    publicTickers[it.symbol] = it
                }
            }
        }, successBlock = success, errorBlock = error, finallyBlock = finally)
    }


    fun publicTradesGet(scope: CoroutineScope, symbols: String, sort: String, from: String, till: String, limit: Int, offset: Int,
                        success: (Any?) -> Unit, error: (Int, String) -> Unit, finally: () -> Unit) {
        doRequest(scope, {
            api.publicTradesGet(symbols, sort, from, till, limit, offset)
        }, successBlock = success, errorBlock = error, finallyBlock = finally)
    }

    fun publicTradesSymbolGet(scope: CoroutineScope, symbol: String, sort: String, by: String, from: String, till: String, limit: Int, offset: Int,
                              success: (Array<PublicTrade>?) -> Unit, error: (Int, String) -> Unit, finally: () -> Unit) {
        doRequest(scope, {
            api.publicTradesSymbolGet(symbol, sort, by, from, till, limit, offset)
        }, successBlock = success, errorBlock = error, finallyBlock = finally)
    }

}