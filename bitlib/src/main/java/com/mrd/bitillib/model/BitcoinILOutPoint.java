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

package com.mrd.bitillib.model;

import com.mrd.bitillib.util.ByteReader;
import com.mrd.bitillib.util.ByteWriter;
import com.mrd.bitillib.util.BitcoinILSha256Hash;
import com.mrd.bitillib.util.ByteReader.InsufficientBytesException;
import com.mrd.bitlib.util.HexUtils;

import java.io.Serializable;

/**
 * BitcoinILOutPoint is a reference to a particular 0-based output index of a given transaction identified by its txid.
 *
 * A hash of all 0s is spent from in Coinbase Transactions.
 */
public class BitcoinILOutPoint implements Serializable {
   private static final long serialVersionUID = 1L;

   // A coinbase transaction spends from the hash 00000... (not the txid(00000...) ). COINBASE_OUTPOINT is just that: 000000...:0
   // Or is it 0000...:Integer.MAX_VALUE?
   // So far, the index of the COINBASE_OUTPOINT isn't being used.
   public static final BitcoinILOutPoint COINBASE_OUTPOINT = new BitcoinILOutPoint(BitcoinILSha256Hash.ZERO_HASH, 0xFFFFFFFF);
   public BitcoinILSha256Hash txid;
   public int index;

   public BitcoinILOutPoint(BitcoinILSha256Hash txid, int index) {
      this.txid = txid;
      this.index = index;
   }

   public BitcoinILOutPoint(ByteReader reader) throws InsufficientBytesException {
      this.txid = reader.getBitcoinILSha256Hash();
      this.index = (int) reader.getCompactInt();
   }

   @Override
   public int hashCode() {
      return txid.hashCode() + index;
   }

   @Override
   public boolean equals(Object other) {
      if (!(other instanceof BitcoinILOutPoint)) {
         return false;
      }
      return txid.equals(((BitcoinILOutPoint) other).txid) && index == ((BitcoinILOutPoint) other).index;
   }

   @Override
   public String toString() {
      return String.valueOf(txid) + ':' + index;
   }

   public ByteWriter toByteWriter(ByteWriter writer) {
      writer.putBitcoinILSha256Hash(txid);
      writer.putCompactInt(index);
      return writer;
   }

   /**
    * Required for BIP143 tx digest.
    */
   void hashPrev(ByteWriter writer) {
      writer.putBitcoinILSha256Hash(txid, true);
      writer.putIntLE(index);
   }

   public static BitcoinILOutPoint fromString(String string) {
      try {
         if (string == null) {
            return null;
         }
         int colon = string.indexOf(':');
         if (colon == -1) {
            return null;
         }
         String txid = string.substring(0, colon);
         if (txid.length() != 64) {
            return null;
         }
         byte[] bytes = HexUtils.toBytes(txid);
         if (bytes == null) {
            return null;
         }
         String indexString = string.substring(colon + 1);
         int index = Integer.parseInt(indexString);
         return new BitcoinILOutPoint(new BitcoinILSha256Hash(bytes), index);
      } catch (Exception e) {
         return null;
      }
   }
}
