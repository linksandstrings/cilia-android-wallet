/*
 * Copyright 2013, 2014 Megion Research & Development GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.megiontechnologies;


import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * a core BitcoinIL Value representation, caputuring many domain specific aspects
 * of it. introduced to reduce the ambiguity when dealing with double,
 * BigInteger, long, or even worse, integer representations
 * 
 * @author apetersson
 */
public final class BitcoinILs extends BitcoinILBase {
   private static final long serialVersionUID = 1L;

   // public static final String BITCOINIL_SYMBOL = "\u0243"; // Ƀ
   // public static final String BITCOINIL_SYMBOL = "\u0E3F"; // ฿
   public static final String BITCOINIL_SYMBOL = "BTCIL"; // BTC

   /**
    * @param btc
    *           double Value in full bitcoinils. must be an exact represenatation
    * @return bitcoinil value representation
    * @throws IllegalArgumentException
    *            if the given double value loses precision when converted to
    *            long
    */
   public static BitcoinILs valueOf(double btc) {
      return valueOf(toLongExact(btc));
   }

    public static BitcoinILs valueOf(String btc) {
        return BitcoinILs.valueOf(new BigDecimal(btc).multiply(SATOSHIS_PER_BITCOINIL_BD).longValueExact());
    }

    public static BitcoinILs nearestValue(double v) {
      return new BitcoinILs(Math.round(v * SATOSHIS_PER_BITCOINIL));
   }

   public static BitcoinILs nearestValue(BigDecimal bitcoinilAmount) {
      BigDecimal satoshis = bitcoinilAmount.multiply(SATOSHIS_PER_BITCOINIL_BD);
      long satoshisExact = satoshis.setScale(0, RoundingMode.HALF_UP).longValueExact();
      return new BitcoinILs(satoshisExact);
   }

   public static BitcoinILs valueOf(long satoshis) {
      return new BitcoinILs(satoshis);
   }

   /**
    * XXX Jan: Commented out the below as this gives unnecessary runtime faults.
    * There may be rounding errors on the last decimals, and that is how life
    * is. The above simple conversion ois used instead.
    */

   // private static long toLongExact(double origValue) {
   // double satoshis = origValue * SATOSHIS_PER_BITCOINIL; // possible loss of
   // // precision here?
   // long longSatoshis = Math.round(satoshis);
   // if (satoshis != (double) longSatoshis) {
   // double error = longSatoshis - satoshis;
   // throw new IllegalArgumentException("the given double value " + origValue
   // + " was not convertable to a precise value." + " error: " + error +
   // " satoshis");
   // }
   // return longSatoshis;
   // }

   private BitcoinILs(long satoshis) {
      if (satoshis < 0)
         throw new IllegalArgumentException(String.format("BitcoinIL values must be debt-free and positive, but was %s",
               satoshis));
      if (satoshis >= MAX_VALUE)
         throw new IllegalArgumentException(String.format(
               "BitcoinIL values must be smaller than 21 Million BTCIL, but was %s", satoshis));
      this.satoshis = satoshis;
   }

   protected BitcoinILs parse(String input) {
      return BitcoinILs.valueOf(input);
   }

   @Override
   public boolean equals(Object o) {
      if (this == o)
         return true;
      if (o == null || getClass() != o.getClass())
         return false;

      BitcoinILs bitcoinils = (BitcoinILs) o;

      return satoshis == bitcoinils.satoshis;
   }

   @Override
   public String toCurrencyString() {
      return BITCOINIL_SYMBOL + ' ' + toString();
   }

   @Override
   public String toCurrencyString(int decimals) {
      return BITCOINIL_SYMBOL + ' ' + toString(decimals);
   }
}