package com.cilia.wallet.activity.util.accountstrategy;

import android.content.Context;

import com.cilia.wallet.MbwManager;
import com.cilia.wallet.R;
import com.mycelium.wapi.wallet.WalletAccount;
import com.mycelium.wapi.wallet.currency.CurrencyValue;

public class BCHAccountDisplayStrategy extends BTCAccountDisplayStrategy {
    private static final String ACCOUNT_LABEL = "bitcoincash";

    public BCHAccountDisplayStrategy(WalletAccount account, Context context, MbwManager mbwManager) {
        super(account, context, mbwManager);
    }

    @Override
    public String getLabel() {
        return ACCOUNT_LABEL;
    }

    @Override
    public String getCurrencyName() {
        return context.getString(R.string.bitcoin_cash_name);
    }

    @Override
    public String getHint() {
        return super.getHint().replace("BTC", "BCH");
    }
}
