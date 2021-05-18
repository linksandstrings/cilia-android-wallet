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

package com.mrd.bitillib.util;

import com.megiontechnologies.BitcoinILs;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashMap;

/**
 * Utility for turning an amount of satoshis into a user friendly string. 1
 * satoshi == 0.000000001 BTCILIL
 */
public class CoinUtil {
   private static final BigDecimal BTCIL_IN_SATOSHIS = new BigDecimal(100000000);
   private static final BigDecimal mBTCIL_IN_SATOSHIS = new BigDecimal(100000);
   private static final BigDecimal uBTCIL_IN_SATOSHIS = new BigDecimal(100);
   private static final BigDecimal BITS_IN_SATOSHIS = new BigDecimal(100);

   private static final BigDecimal BCH_IN_SATOSHIS = new BigDecimal(100000000);
   private static final BigDecimal mBCH_IN_SATOSHIS = new BigDecimal(100000);
   private static final BigDecimal uBCH_IN_SATOSHIS = new BigDecimal(100);

   private static final DecimalFormat COIN_FORMAT;

   static {
      COIN_FORMAT = new DecimalFormat();
      COIN_FORMAT.setGroupingSize(3);
      COIN_FORMAT.setGroupingUsed(true);
      COIN_FORMAT.setMaximumFractionDigits(8);
      DecimalFormatSymbols symbols = COIN_FORMAT.getDecimalFormatSymbols();
      symbols.setDecimalSeparator('.');
      symbols.setGroupingSeparator(' ');
      COIN_FORMAT.setDecimalFormatSymbols(symbols);
   }

   public enum Denomination {
      BTCIL(8, "BTCIL", "BTCIL",  BTCIL_IN_SATOSHIS),
      mBTCIL(5, "mBTCIL", "mBTCIL", mBTCIL_IN_SATOSHIS),
      uBTCIL(2, "uBTCIL", "\u00B5BTCIL", uBTCIL_IN_SATOSHIS),
      BCH(8, "BCH", "BCH", BCH_IN_SATOSHIS),
      mBCH(5, "mBCH", "mBCH", mBCH_IN_SATOSHIS),
      uBCH(2, "uBCH", "\u00B5BCH", uBCH_IN_SATOSHIS),
      BITS(2, "bits", "bits", BITS_IN_SATOSHIS);

      private final int _decimalPlaces;
      private final String _asciiString;
      private final String _unicodeString;
      private final BigDecimal _oneUnitInSatoshis;

      Denomination(int decimalPlaces, String asciiString, String unicodeString, BigDecimal oneUnitInSatoshis) {
         _decimalPlaces = decimalPlaces;
         _asciiString = asciiString;
         _unicodeString = unicodeString;
         _oneUnitInSatoshis = oneUnitInSatoshis;
      }

      public String getUnicodeString(String symbol) {
         if (this == BTCIL || this == BCH) {
            return symbol;
         } else if (this == mBTCIL || this == mBCH || this == uBTCIL || this == uBCH) {
            return this.getUnicodeName().substring(0, 1) + symbol;
         } else {
            return getUnicodeName();
         }
      }

      public int getDecimalPlaces() {
         return _decimalPlaces;
      }

      public String getAsciiName() {
         return _asciiString;
      }

      public String getUnicodeName() {
         return _unicodeString;
      }

      public BigDecimal getOneUnitInSatoshis() {
         return _oneUnitInSatoshis;
      }

      @Override
      public String toString() {
         return _asciiString;
      }

      public static Denomination fromString(String string) {
         if (string == null) {
            return BTCIL;
         }
         switch (string) {
            case "BTCIL":
               return BTCIL;
            case "mBTCIL":
               return mBTCIL;
            case "uBTCIL":
               return uBTCIL;
            case "BCH":
               return BCH;
            case "mBCH":
               return mBCH;
            case "uBCH":
               return uBCH;
            case "bits":
               return BITS;
            default:
               return BTCIL;
         }
      }
   }

   /**
    * Number of satoshis in a BitcoinIL.
    */
   public static final BigInteger BTCIL = new BigInteger("100000000", 10);

   /**
    * Get the given value in satoshis as a string on the form "10.12345"
    * denominated in BTCIL.
    * <p>
    * This method only returns necessary decimal points to tell the exact value.
    * If you wish to display all digits use
    * {@link CoinUtil#fullValueString(long)}
    * 
    * @param value
    *           The number of satoshis
    * @param withThousandSeparator
    *           Use ' ' as the 1000 grouping separator in the output 
    * @return The given value in satoshis as a string on the form "10.12345".
    */
   public static String valueString(long value, boolean withThousandSeparator) {
      return valueString(value, Denomination.BTCIL, withThousandSeparator);
   }

   /**
    * Get the given value in satoshis as a string on the form "10.12345" using
    * the specified denomination.
    * <p>
    * This method only returns necessary decimal points to tell the exact value.
    * If you wish to display all digits use
    * {@link CoinUtil#fullValueString(long, Denomination)}
    * 
    * @param value
    *           The number of satoshis
    * @param denomination
    *           The denomination to use
    * @param withThousandSeparator
    *           Use ' ' as the 1000 grouping separator in the output 
    * @return The given value in satoshis as a string on the form "10.12345".
    */
   public static String valueString(long value, Denomination denomination, boolean withThousandSeparator) {
      BigDecimal d = BigDecimal.valueOf(value);
      d = d.divide(denomination.getOneUnitInSatoshis());
      if (!withThousandSeparator) {
         return d.toPlainString();
      } else {
         return COIN_FORMAT.format(d);
      }
   }

   /**
    * Get the given value as a string on the form "10.12345" using
    * the specified denomination.
    * <p>
    * This method only returns necessary decimal points to tell the exact value.
    * If you wish to display all digits use
    * {@link CoinUtil#fullValueString(long, Denomination)}
    *
    * @param value
    *           The number of bitcoinils
    * @param denomination
    *           The denomination to use
    * @param withThousandSeparator
    *           Use ' ' as the 1000 grouping separator in the output
    * @return The given value as a string on the form "10.12345".
    */
   public static String valueString(BigDecimal value, Denomination denomination, boolean withThousandSeparator) {
      Long satoshis = BitcoinILs.nearestValue(value).getLongValue();
      return valueString(satoshis, denomination, withThousandSeparator);
   }

   private static HashMap<Integer, DecimalFormat> formatCache = new HashMap<Integer, DecimalFormat>(2);

   /**
    * Get the given value in satoshis as a string on the form "10.12345" using
    * the specified denomination.
    * <p>
    * This method only returns necessary decimal points to tell the exact value.
    * If you wish to display all digits use
    * {@link CoinUtil#fullValueString(long, Denomination)}
    *
    * @param value
    *           The number of satoshis
    * @param denomination
    *           The denomination to use
    * @param precision
    *           max number of digits after the comma
    * @return The given value in satoshis as a string on the form "10.12345".
    */
   public static String valueString(long value, Denomination denomination, int precision) {
      BigDecimal d = BigDecimal.valueOf(value);
      d = d.divide(denomination.getOneUnitInSatoshis());

      if (!formatCache.containsKey(precision)){
         DecimalFormat coinFormat = (DecimalFormat) COIN_FORMAT.clone();
         coinFormat.setMaximumFractionDigits(precision);
         formatCache.put(precision, coinFormat);
      }
      return formatCache.get(precision).format(d);
   }

   /**
    * Get the given value in satoshis as a string on the form "10.12345000"
    * denominated in BTCIL.
    * <p>
    * This method always returns a string with 8 decimal points. If you only
    * wish to have the necessary digits use {@link CoinUtil#valueString(long, boolean)}
    * 
    * @param value
    *           The number of satoshis
    * @return The given value in satoshis as a string on the form "10.12345000".
    */
   public static String fullValueString(long value) {
      return fullValueString(value, Denomination.BTCIL);
   }

   /**
    * Get the given value in satoshis as a string on the form "10.12345000"
    * using the specified denomination.
    * <p>
    * This method always returns a string with all decimal points. If you only
    * wish to have the necessary digits use
    * {@link CoinUtil#valueString(long, Denomination, boolean)}
    * 
    * @param value
    *           The number of satoshis
    * @param denomination
    *           The denomination to use
    * @return The given value in satoshis as a string on the form "10.12345000".
    */
   public static String fullValueString(long value, Denomination denomination) {
      BigDecimal d = BigDecimal.valueOf(value);
      d = d.movePointLeft(denomination.getDecimalPlaces());
      return d.toPlainString();
   }

}
