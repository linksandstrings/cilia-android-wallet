package com.megiontechnologies;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * Base for {@link BitcoinILCash}, {@link BitcoinILs}
 * Introduced to reduce the ambiguity when dealing with double,
 * BigInteger, long, or even worse, integer representations when working with bitcoinils and bitcoinil cash
 */
abstract class BitcoinILBase implements Serializable {
    public static final long SATOSHIS_PER_BITCOINIL = 100000000L;
    protected static final BigDecimal SATOSHIS_PER_BITCOINIL_BD = BigDecimal.valueOf(SATOSHIS_PER_BITCOINIL);
    public static final long MAX_VALUE = 21000000 * SATOSHIS_PER_BITCOINIL;
    protected long satoshis;

    static long toLongExact(double origValue) {
        double satoshis = origValue * SATOSHIS_PER_BITCOINIL; // possible loss of
        // precision here
        return Math.round(satoshis);
    }

    public BigDecimal multiply(BigDecimal pricePerBtc) {
        return toBigDecimal().multiply(BigDecimal.valueOf(satoshis));
    }

    @Override
    public String toString() {
        // this could surely be implented faster without using BigDecimal. but it
        // is good enough for now.
        // this could be cached
        return toBigDecimal().toPlainString();
    }

    public String toString(int decimals) {
        // this could surely be implented faster without using BigDecimal. but it
        // is good enough for now.
        // this could be cached
        return toBigDecimal().setScale(decimals, RoundingMode.DOWN).toPlainString();
    }

    public BigDecimal toBigDecimal() {
        return BigDecimal.valueOf(satoshis).divide(SATOSHIS_PER_BITCOINIL_BD);
    }

    @Override
    public int hashCode() {
        return (int) (satoshis ^ (satoshis >>> 32));
    }

    public long getLongValue() {
        return satoshis;
    }

    public BigInteger toBigInteger() {
        return BigInteger.valueOf(satoshis);
    }

    abstract public String toCurrencyString();

    abstract public String toCurrencyString(int decimals);

    public BitcoinILCash roundToSignificantFigures(int n) {
        return BitcoinILCash.valueOf(roundToSignificantFigures(satoshis, n));
    }

    protected static long roundToSignificantFigures(long num, int n) {
        if (num == 0) {
            return 0;
        }
        // todo check if these are equal, take LongMath
        // int d = LongMath.log10(Math.abs(num), RoundingMode.CEILING);
        final double d = Math.ceil(Math.log10(num < 0 ? -num : num));
        final int power = n - (int) d;

        final double magnitude = Math.pow(10, power);
        final long shifted = Math.round(num * magnitude);
        return (long) (shifted / magnitude);
    }
}
