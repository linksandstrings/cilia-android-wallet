package com.mycelium.wapi.wallet.btcil;

import com.megiontechnologies.BitcoinILs;
import com.mrd.bitillib.model.BitcoinILAddress;

import java.io.Serializable;

/**
 * Class representing a receiver of funds
 */
public class BtcILReceiver implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * The address to send funds to
     */
    public final BitcoinILAddress address;

    /**
     * The amount to send measured in satoshis
     */
    public final long amount;

    public BtcILReceiver(BitcoinILAddress address, long amount) {
        this.address = address;
        this.amount = amount;
    }

    public BtcILReceiver(BitcoinILAddress address, BitcoinILs amount) {
        this(address, amount.getLongValue());
    }
}
