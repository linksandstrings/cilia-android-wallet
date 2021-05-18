package com.cilia.wallet.activity.fio.mapaccount.adapter.viewholder

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.cilia.wallet.R
import kotlinx.android.synthetic.main.item_fio_name.view.*


class FIODomainViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val title = itemView.fioName
    val expireDate = itemView.expireDate

    init {
        title.setCompoundDrawablesRelativeWithIntrinsicBounds(
                title.resources.getDrawable(R.drawable.ic_fio_domain),
                null, null, null)
    }
}