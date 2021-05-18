package com.mycelium.bequant.exchange

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mycelium.bequant.common.model.CoinListItem
import com.mycelium.bequant.exchange.SelectCoinFragment.Companion.SEND
import com.cilia.wallet.R
import com.mycelium.wapi.wallet.coins.AssetInfo
import kotlinx.android.synthetic.main.item_bequant_coin_expanded.view.*
import kotlinx.android.synthetic.main.item_bequant_search.view.*


class CoinAdapter(private val role: String, private val listener: ClickListener,
                  var youSendYouGetPair: MutableLiveData<Pair<AssetInfo, AssetInfo>>)
    : ListAdapter<CoinListItem, RecyclerView.ViewHolder>(DiffCallback()) {

    var searchChangeListener: ((String) -> Unit)? = null
    var searchClearListener: (() -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            when (viewType) {
                TYPE_SEARCH -> {
                    SearchHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_bequant_search, parent, false))
                }
                TYPE_ITEM -> {
                    ItemViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_bequant_coin_expanded, parent, false))
                }
                else -> {
                    SpaceHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_bequant_space, parent, false))
                }
            }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (item.type) {
            TYPE_SEARCH -> {
                holder.itemView.search.doOnTextChanged { text, _, _, _ ->
                    searchChangeListener?.invoke(text?.toString() ?: "")
                }
                holder.itemView.clear.setOnClickListener {
                    holder.itemView.search.text = null
                    searchClearListener?.invoke()
                }
            }
            TYPE_ITEM -> {
                holder.itemView.coinId.text = item.coin?.symbol
                holder.itemView.coinFullName.text = item.coin?.name
                when (item.coin?.symbol) {
                    youSendYouGetPair.value!!.first.symbol -> {
                        holder.itemView.grayArrow.visibility = View.VISIBLE
                        holder.itemView.yellowArrow.visibility = View.GONE
                    }
                    youSendYouGetPair.value!!.second.symbol -> {
                        holder.itemView.grayArrow.visibility = View.GONE
                        holder.itemView.yellowArrow.visibility = View.VISIBLE
                    }
                    else -> {
                        holder.itemView.grayArrow.visibility = View.GONE
                        holder.itemView.yellowArrow.visibility = View.GONE
                    }
                }
                holder.itemView.setOnClickListener {
                    youSendYouGetPair.value = if (role == SEND) {
                        if (item.coin?.symbol != youSendYouGetPair.value!!.second.symbol) {
                            Pair(item.coin!!, youSendYouGetPair.value!!.second)
                        } else {
                            Pair(youSendYouGetPair.value!!.second, youSendYouGetPair.value!!.first)
                        }
                    } else {
                        if (item.coin?.symbol != youSendYouGetPair.value!!.first.symbol) {
                            Pair(youSendYouGetPair.value!!.first, item.coin!!)
                        } else {
                            Pair(youSendYouGetPair.value!!.second, youSendYouGetPair.value!!.first)
                        }
                    }
                    listener.onClick()
                }
            }
        }
    }

    override fun getItemViewType(position: Int): Int = getItem(position).type

    class DiffCallback : DiffUtil.ItemCallback<CoinListItem>() {
        override fun areItemsTheSame(p0: CoinListItem, p1: CoinListItem): Boolean =
                p0.type == p1.type
                        && p0.coin == p1.coin

        override fun areContentsTheSame(p0: CoinListItem, p1: CoinListItem): Boolean =
                p0.coin?.symbol == p1.coin?.symbol
    }

    class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    class SearchHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    class SpaceHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    interface ClickListener {
        fun onClick()
    }

    companion object {
        const val TYPE_SEARCH = 0
        const val TYPE_SPACE = 1
        const val TYPE_ITEM = 2
    }
}