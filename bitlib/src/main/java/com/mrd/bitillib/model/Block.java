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
import com.mrd.bitillib.util.HashUtils;
import com.mrd.bitillib.util.BitcoinILSha256Hash;
import com.mrd.bitillib.model.BitcoinILTransaction.BitcoinILTransactionParsingException;
import com.mrd.bitillib.util.ByteReader.InsufficientBytesException;

public class Block {
   public static class BlockParsingException extends Exception {
      private static final long serialVersionUID = 1L;

      public BlockParsingException(String message) {
         super(message);
      }

      public BlockParsingException(String message, Exception e) {
         super(message, e);
      }
   }

   // The maximum size of a serialized block
   public static final int MAX_BLOCK_SIZE = 1000000;

   // Header
   public int version;
   public BitcoinILSha256Hash prevBlockHash;
   public BitcoinILSha256Hash merkleRoot;
   public int time;
   public int difficultyTarget;
   public int nonce;
   // Transactions
   public BitcoinILTransaction[] transactions;

   private BitcoinILSha256Hash _hash;

   public static Block fromBlockStore(ByteReader reader) throws BlockParsingException {
      try {
         // Parse header
         int version = reader.getIntLE();
         BitcoinILSha256Hash prevBlockHash = reader.getBitcoinILSha256Hash().reverse();
         BitcoinILSha256Hash merkleRoot = reader.getBitcoinILSha256Hash().reverse();
         int time = reader.getIntLE();
         int difficultyTarget = reader.getIntLE();
         int nonce = reader.getIntLE();
         // Parse transactions
         int numTransactions = (int) reader.getCompactInt();
         BitcoinILTransaction[] transactions = new BitcoinILTransaction[numTransactions];
         for (int i = 0; i < numTransactions; i++) {
            try {
               transactions[i] = BitcoinILTransaction.fromByteReader(reader);
            } catch (BitcoinILTransactionParsingException e) {
               throw new BlockParsingException("Unable to parse transaction at index " + i + ": " + e.getMessage());
            }
         }
         return new Block(version, prevBlockHash, merkleRoot, time, difficultyTarget, nonce, transactions);
      } catch (InsufficientBytesException e) {
         throw new BlockParsingException(e.getMessage());
      }
   }

   public Block(int version, BitcoinILSha256Hash prevBlockHash, BitcoinILSha256Hash merkleRoot, int time, int difficultyTargetm,
                int nonce, BitcoinILTransaction[] transactions) {
      this.version = version;
      this.prevBlockHash = prevBlockHash;
      this.merkleRoot = merkleRoot;
      this.time = time;
      this.difficultyTarget = difficultyTargetm;
      this.nonce = nonce;
      this.transactions = transactions;
   }

   public void toByteWriter(ByteWriter writer) {
      headerToByteWriter(writer);
      transactionsToByteWriter(writer);
   }

   public void headerToByteWriter(ByteWriter writer) {
      writer.putIntLE(version);
      writer.putBitcoinILSha256Hash(prevBlockHash, true);
      writer.putBitcoinILSha256Hash(merkleRoot, true);
      writer.putIntLE(time);
      writer.putIntLE(difficultyTarget);
      writer.putIntLE(nonce);
   }

   public void transactionsToByteWriter(ByteWriter writer) {
      writer.putCompactInt(transactions.length);
      for (BitcoinILTransaction t : transactions) {
         t.toByteWriter(writer);
      }
   }

   public BitcoinILSha256Hash getHash() {
      if (_hash == null) {
         if (version == 1){
            ByteWriter writer = new ByteWriter(2000);
            headerToByteWriter(writer);
            _hash = HashUtils.doubleSha256(writer.toBytes()).reverse();
         }else{
            ByteWriter writer = new ByteWriter(2000);
            headerToByteWriter(writer);
            _hash = HashUtils.x17Hash(writer.toBytes()).reverse();
         }

      }
      return _hash;
   }

   @Override
   public String toString() {
      return "Hash: " + getHash().toString() +
              " PrevHash: " + prevBlockHash.toString() +
              " #Tx: " + transactions.length;
   }

   @Override
   public int hashCode() {
      return getHash().hashCode();
   }

   @Override
   public boolean equals(Object other) {
      if (!(other instanceof Block)) {
         return false;
      }
      return getHash().equals(((Block) other).getHash());
   }
}
