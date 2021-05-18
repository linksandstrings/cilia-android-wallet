package com.mycelium.wapi.wallet.currency;


import com.megiontechnologies.BitcoinILCash;

import java.math.BigDecimal;

public class ExactBitcoinILCashValue extends ExactCurrencyValue {
    private final BitcoinILCash value;
    public static final ExactCurrencyValue ZERO = from(0L);
    public static final ExactCurrencyValue ONE = from(BigDecimal.ONE);

    public static ExactBitcoinILCashValue from(BigDecimal value) {
        return new ExactBitcoinILCashValue(value);
    }
    public static ExactBitcoinILCashValue from(Long value) {
        return new ExactBitcoinILCashValue(value);
    }
    public static ExactBitcoinILCashValue from(BitcoinILCash value) {
        return new ExactBitcoinILCashValue(value);
    }

    protected ExactBitcoinILCashValue(Long satoshis) {
        if (satoshis != null) {
            value = BitcoinILCash.valueOf(satoshis);
        } else {
            value = null;
        }
    }

    protected ExactBitcoinILCashValue(BitcoinILCash bitcoins) {
        value = bitcoins;
    }


    protected ExactBitcoinILCashValue(BigDecimal bitcoins) {
        if (bitcoins != null) {
            value = BitcoinILCash.nearestValue(bitcoins);
        } else {
            value = null;
        }
    }

    public long getLongValue() {
        return getAsBitcoinILCash().getLongValue();
    }

    public BitcoinILCash getAsBitcoinILCash() {
        return value;
    }

    @Override
    public String getCurrency() {
        return CurrencyValue.BCH;
    }

    @Override
    public BigDecimal getValue() {
        if (value != null) {
            return value.toBigDecimal();
        } else {
            return null;
        }
    }
}
