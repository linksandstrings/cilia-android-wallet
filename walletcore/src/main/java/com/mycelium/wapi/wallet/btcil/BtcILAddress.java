package com.mycelium.wapi.wallet.btcil;

import com.mrd.bitillib.model.BitcoinILAddress;
import com.mrd.bitlib.model.AddressType;
import com.mycelium.wapi.wallet.Address;
import com.mycelium.wapi.wallet.coins.CryptoCurrency;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class BtcILAddress implements Address {
    private BitcoinILAddress address;
    private CryptoCurrency currencyType;

    public BtcILAddress(CryptoCurrency currencyType, BitcoinILAddress address) {
        this.address = address;
        this.currencyType = currencyType;
    }

    @Override
    public String toString() {
        return address.toString();
    }

    @NotNull
    public BitcoinILAddress getAddress() {
        return address;
    }

    @NotNull
    @Override
    public CryptoCurrency getCoinType() {
        return currencyType;
    }

    @NotNull
    @Override
    public byte[] getBytes() {
        return address.getAllAddressBytes();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BtcILAddress that = (BtcILAddress) o;
        return Objects.equals(address, that.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address);
    }

    @NotNull
    @Override
    public String getSubType() {
        return address.getType().name();
    }

    public AddressType getType()
    {
        return address.getType();
    }
}
