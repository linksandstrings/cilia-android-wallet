package com.cilia.wallet.activity.modern.adapter.holder;

import android.view.View;

import com.cilia.wallet.R;


public class ArchivedGroupTitleViewHolder extends GroupTitleViewHolder {
    public ArchivedGroupTitleViewHolder(View itemView) {
        super(itemView);
        tvTitle = itemView.findViewById(R.id.tvTitle);
        tvAccountsCount = itemView.findViewById(R.id.tvAccountsCount);
        expandIcon = itemView.findViewById(R.id.expand);
    }
}