package com.cilia.wallet.activity.util.accountstrategy;

import android.content.Context;

import com.cilia.wallet.MbwManager;
import com.cilia.wallet.R;
import com.cilia.wallet.Utils;
import com.mycelium.wapi.wallet.WalletAccount;
import com.mycelium.wapi.wallet.currency.CurrencyValue;

public class BTCAccountDisplayStrategy implements AccountDisplayStrategy {
    private static final String ACCOUNT_LABEL = "bitcoin";
    protected final WalletAccount account;
    protected final Context context;
    protected final MbwManager mbwManager;

    public BTCAccountDisplayStrategy(WalletAccount account, Context context, MbwManager mbwManager) {
        this.account = account;
        this.context = context;
        this.mbwManager = mbwManager;
    }

    @Override
    public String getLabel() {
        return ACCOUNT_LABEL;
    }

    @Override
    public String getCurrencyName() {
        return context.getString(R.string.bitcoin_name);
    }

    @Override
    public String getHint() {
        return context.getString(R.string.amount_hint_denomination,
                mbwManager.getDenomination(Utils.getBtcCoinType()).toString());
    }
}
