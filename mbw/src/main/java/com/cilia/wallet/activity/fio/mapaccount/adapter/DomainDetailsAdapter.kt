package com.cilia.wallet.activity.fio.mapaccount.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.cilia.wallet.R
import java.text.DateFormat
import java.text.SimpleDateFormat


//class DomainDetailsAdapter : ListAdapter<FIONameItem, FIONameViewHolder>(DiffCallback()) {
//
//    var clickListener: ((FIONameItem) -> Unit)? = null
//
//    override fun onCreateViewHolder(parent: ViewGroup, typeView: Int): FIONameViewHolder =
//            FIONameViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_fio_name, parent, false))
//
//
//    override fun onBindViewHolder(holder: FIONameViewHolder, position: Int) {
//        getItem(position).run {
//            holder.fioName.text = title
//            holder.expireDate.text = holder.expireDate.resources.getString(R.string.expiration_date) + " " +
//                    SimpleDateFormat.getDateInstance(DateFormat.LONG).format(expireDate)
//            holder.itemView.setOnClickListener {
//                clickListener?.invoke(this)
//            }
//        }
//    }
//
//    class DiffCallback : DiffUtil.ItemCallback<FIONameItem>() {
//        override fun areItemsTheSame(oldItem: FIONameItem, newItem: FIONameItem): Boolean =
//                oldItem.type == newItem.type && oldItem == newItem
//
//        override fun areContentsTheSame(oldItem: FIONameItem, newItem: FIONameItem): Boolean =
//                oldItem.title == newItem.title && oldItem.expireDate == newItem.expireDate
//    }
//}