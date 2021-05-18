package com.mycelium.wapi.wallet.btcil.coins;

import com.mrd.bitillib.model.BitcoinILAddress;
import com.mycelium.wapi.wallet.Address;
import com.mycelium.wapi.wallet.btcil.BtcILAddress;
import com.mycelium.wapi.wallet.coins.CryptoCurrency;

public class BitcoinILMain extends CryptoCurrency {
    private BitcoinILMain() {
        super("bitcoinil.main", "BitcoinIL", "BTCIL", 8, 2, true);
    }

    private static BitcoinILMain instance = new BitcoinILMain();
    public static synchronized CryptoCurrency get() {
        return instance;
    }

    @Override
    public Address parseAddress(String addressString) {
        BitcoinILAddress address = BitcoinILAddress.fromString(addressString);
        if (address == null) {
            return null;
        }

        try {
            if (!address.getNetwork().isProdnet()) {
                return null;
            }
        } catch (IllegalStateException e) {
            return null;
        }
        return new BtcILAddress(this, address);
    }
}