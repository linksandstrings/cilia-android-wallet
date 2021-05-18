package com.megiontechnologies;

import java.math.BigDecimal;
import java.math.RoundingMode;


/**
 * a core BitcoinIL Cash Value representation, caputuring many domain specific aspects
 * of it. introduced to reduce the ambiguity when dealing with double,
 * BigInteger, long, or even worse, integer representations
 */
 public final class BitcoinILCash extends BitcoinILBase {
    private static final long serialVersionUID = 1L;

    public static final String BITCOINIL_CASH_SYMBOL = "BCH"; // BCH

    /**
     * @param bch double Value in full bitcoinils. must be an exact represenatation
     * @return bitcoinil cash value representation
     * @throws IllegalArgumentException if the given double value loses precision when converted to
     *                                  long
     */
    public static BitcoinILCash valueOf(double bch) {
        return valueOf(toLongExact(bch));
    }

    public static BitcoinILCash valueOf(String bch) {
        return BitcoinILCash.valueOf(new BigDecimal(bch).multiply(SATOSHIS_PER_BITCOINIL_BD).longValueExact());
    }

    public static BitcoinILCash nearestValue(double v) {
        return new BitcoinILCash(Math.round(v * SATOSHIS_PER_BITCOINIL));
    }

    public static BitcoinILCash nearestValue(BigDecimal bitcoinilAmount) {
        BigDecimal satoshis = bitcoinilAmount.multiply(SATOSHIS_PER_BITCOINIL_BD);
        long satoshisExact = satoshis.setScale(0, RoundingMode.HALF_UP).longValueExact();
        return new BitcoinILCash(satoshisExact);
    }

    public static BitcoinILCash valueOf(long satoshis) {
        return new BitcoinILCash(satoshis);
    }

    private BitcoinILCash(long satoshis) {
        if (satoshis < 0)
            throw new IllegalArgumentException(String.format("BitcoinIL Cash values must be debt-free and positive, but was %s",
                    satoshis));
        if (satoshis >= MAX_VALUE)
            throw new IllegalArgumentException(String.format(
                    "BitcoinIL values must be smaller than 21 Million BCH, but was %s", satoshis));
        this.satoshis = satoshis;
    }

    protected BitcoinILCash parse(String input) {
        return BitcoinILCash.valueOf(input);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        BitcoinILCash bchs = (BitcoinILCash) o;

        return satoshis == bchs.satoshis;
    }

    @Override
    public String toCurrencyString() {
        return BITCOINIL_CASH_SYMBOL + ' ' + toString();
    }

    @Override
    public String toCurrencyString(int decimals) {
        return BITCOINIL_CASH_SYMBOL + ' ' + toString(decimals);
    }
}