package com.cilia.wallet.activity.util;

import com.mycelium.wapi.wallet.WalletAccount;
import com.mycelium.wapi.wallet.bch.bip44.Bip44BCHAccount;
import com.mycelium.wapi.wallet.bch.single.SingleAddressBCHAccount;
import com.mycelium.wapi.wallet.btc.bip44.HDAccount;
import com.mycelium.wapi.wallet.btc.single.SingleAddressAccount;
import com.mycelium.wapi.wallet.btcil.bip44.HDAccountIL;
import com.mycelium.wapi.wallet.colu.ColuAccount;

public enum AccountDisplayType {
    BTC_ACCOUNT("BTC"),
    BTCIL_ACCOUNT("BTCIL"),
    BCH_ACCOUNT("BCH"),
    DASH_ACCOUNT("DASH"),
    COLU_ACCOUNT("COLU"),
    UNKNOWN_ACCOUNT("UNKNOWN");

    private final String accountLabel;

    AccountDisplayType(String accountLabel) {
        this.accountLabel = accountLabel;
    }

    public static AccountDisplayType getAccountType(WalletAccount account) {
        if (account instanceof HDAccount || account instanceof SingleAddressAccount) {
            return BTC_ACCOUNT;
        }
        if (account instanceof HDAccountIL) {
            return BTCIL_ACCOUNT;
        }
        if (account instanceof Bip44BCHAccount || account instanceof SingleAddressBCHAccount) {
            return BCH_ACCOUNT;
        }
        if (account instanceof ColuAccount) {
            return COLU_ACCOUNT;
        }
        return UNKNOWN_ACCOUNT;
    }

    public String getAccountLabel() {
        return accountLabel;
    }
}
