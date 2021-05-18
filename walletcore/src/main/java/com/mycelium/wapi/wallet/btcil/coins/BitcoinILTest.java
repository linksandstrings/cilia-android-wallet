package com.mycelium.wapi.wallet.btcil.coins;

import com.mrd.bitillib.model.BitcoinILAddress;
import com.mycelium.wapi.wallet.Address;
import com.mycelium.wapi.wallet.btcil.BtcILAddress;
import com.mycelium.wapi.wallet.coins.CryptoCurrency;

public class BitcoinILTest extends CryptoCurrency {
    private BitcoinILTest() {
        super("bitcoinil.test", "BitcoinIL Test", "tBTCIL", 8, 2, true);
    }

    private static BitcoinILTest instance = new BitcoinILTest();
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
            if (!address.getNetwork().isTestnet()) {
                return null;
            }
        } catch (IllegalStateException e) {
            return null;
        }
        return new BtcILAddress(this, address);
    }
}