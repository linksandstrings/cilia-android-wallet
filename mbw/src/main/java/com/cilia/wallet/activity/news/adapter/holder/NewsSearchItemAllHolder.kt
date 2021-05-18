package com.cilia.wallet.activity.news.adapter.holder

import android.content.SharedPreferences
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cilia.wallet.activity.modern.adapter.holder.NewsV2ListHolder
import kotlinx.android.synthetic.main.item_all_news_search.view.*


class NewsSearchItemAllHolder(val preferences: SharedPreferences, itemView: View) : RecyclerView.ViewHolder(itemView) {

    val category = itemView.tvCategory as TextView
    val showAll = itemView.view_more
    val listHolder = NewsV2ListHolder(preferences, itemView.list)
}