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

package com.mycelium.wapi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mrd.bitillib.model.BitcoinILTransaction;
import com.mrd.bitillib.model.BitcoinILTransaction.BitcoinILTransactionParsingException;
import com.mrd.bitillib.model.BitcoinILOutPoint;
import com.mrd.bitillib.model.TransactionOutput;
import com.mrd.bitillib.util.ByteReader;
import com.mrd.bitillib.util.BitcoinILSha256Hash;

import java.io.Serializable;
import java.util.Date;

public class BitcoinILTransactionEx implements Serializable, Comparable<BitcoinILTransactionEx> {
   private static final long serialVersionUID = 1L;

   @JsonProperty
   public final BitcoinILSha256Hash txid;
   @JsonProperty
   public final BitcoinILSha256Hash hash;
   @JsonProperty
   public final int height; // -1 means unconfirmed
   @JsonProperty
   public final int time;
   @JsonProperty
   public final byte[] binary;

   public BitcoinILTransactionEx(@JsonProperty("txid") BitcoinILSha256Hash txid, @JsonProperty("txid") BitcoinILSha256Hash hash, @JsonProperty("height") int height,
                                 @JsonProperty("time") int time, @JsonProperty("binary") byte[] binary) {
      this.txid = txid;
      this.hash = hash;
      this.height = height;
      this.time = time;
      this.binary = binary;
   }

   @Override
   public String toString() {
      return "txid:" + txid + " height:" + height + " byte-length: " + binary.length +
              " time:" + new Date(time * 1000L);
   }

   @Override
   public int hashCode() {
      return txid.hashCode();
   }

   @Override
   public boolean equals(Object obj) {
      if (obj == this) {
         return true;
      }
      if (!(obj instanceof BitcoinILTransactionEx)) {
         return false;
      }
      BitcoinILTransactionEx other = (BitcoinILTransactionEx) obj;
      return txid.equals(other.txid);
   }

   public static BitcoinILTransactionEx fromUnconfirmedTransaction(BitcoinILTransaction t) {
      int now = (int) (System.currentTimeMillis() / 1000);
      return new BitcoinILTransactionEx(t.getId(), t.getHash(), -1, now, t.toBytes());
   }

   public static BitcoinILTransaction toTransaction(BitcoinILTransactionEx tex) {
      if (tex == null) {
         return null;
      }
      try {
         return BitcoinILTransaction.fromByteReader(new ByteReader(tex.binary));
      } catch (BitcoinILTransactionParsingException e) {
         return null;
      }
   }

   public static BitcoinILTransactionOutputEx getTransactionOutput(BitcoinILTransactionEx tex, int index) {
      if (index < 0) {
         return null;
      }
      BitcoinILTransaction t = toTransaction(tex);
      if (t == null) {
         return null;
      }
      if (index >= t.outputs.length) {
         return null;
      }
      TransactionOutput output = t.outputs[index];
      return new BitcoinILTransactionOutputEx(new BitcoinILOutPoint(tex.txid, index), tex.height, output.value,
            output.script.getScriptBytes(), t.isCoinbase());
   }

   public int calculateConfirmations(int blockHeight) {
      if (height == -1) {
         return 0;
      } else {
         return Math.max(0, blockHeight - height + 1);
      }
   }

   @Override
   public int compareTo(BitcoinILTransactionEx other) {
      // Make pending transaction have maximum height
      int myHeight = height == -1 ? Integer.MAX_VALUE : height;
      int otherHeight = other.height == -1 ? Integer.MAX_VALUE : other.height;

      if (myHeight < otherHeight) {
         return 1;
      } else if (myHeight > otherHeight) {
         return -1;
      } else {
         // sort by time
         if (time < other.time) {
            return 1;
         } else if (time > other.time) {
            return -1;
         }
         return 0;
      }
   }
}
