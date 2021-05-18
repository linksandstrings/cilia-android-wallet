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

package com.mycelium.wapi.wallet.btcil;


import com.google.common.base.Preconditions;
import com.mrd.bitillib.model.BitcoinILOutPoint;
import com.mrd.bitillib.util.BitcoinILSha256Hash;
import com.mrd.bitlib.util.HexUtils;
import com.mycelium.wapi.model.BitcoinILTransactionEx;
import com.mycelium.wapi.model.BitcoinILTransactionOutputEx;
import com.mycelium.wapi.wallet.SingleAddressBtcILAccountBacking;
import com.mycelium.wapi.wallet.btcil.bip44.HDAccountContextIL;
import com.mycelium.wapi.wallet.btcil.single.SingleAddressAccountContextIL;

import java.util.*;

/**
 * Backing for a wallet manager which is only kept temporarily in memory
 */
public class InMemoryBtcILWalletManagerBacking implements BtcILWalletManagerBacking<SingleAddressAccountContextIL> {
   private final Map<String, byte[]> _values = new HashMap<>();
   private final Map<UUID, InMemoryBtcILAccountBacking> _backings = new HashMap<>();
   private final Map<UUID, HDAccountContextIL> _bip44Contexts = new HashMap<>();
   private final Map<UUID, SingleAddressAccountContextIL> _singleAddressAccountContexts = new HashMap<>();
   private int maxSubId = 0;

   @Override
   public void beginTransaction() {
      // not supported
   }

   @Override
   public void setTransactionSuccessful() {
      // not supported
   }

   @Override
   public void endTransaction() {
      // not supported
   }

   @Override
   public List<HDAccountContextIL> loadBip44AccountContexts() {
      // Return a list containing copies
      List<HDAccountContextIL> list = new ArrayList<>();
      for (HDAccountContextIL c : _bip44Contexts.values()) {
         list.add(new HDAccountContextIL(c));
      }
      return list;
   }

   @Override
   public void createBip44AccountContext(HDAccountContextIL context) {
      _bip44Contexts.put(context.getId(), new HDAccountContextIL(context));
      _backings.put(context.getId(), new InMemoryBtcILAccountBacking());
   }

   @Override
   public List<SingleAddressAccountContextIL> loadSingleAddressAccountContexts() {
      // Return a list containing copies
      List<SingleAddressAccountContextIL> list = new ArrayList<>();
      for (SingleAddressAccountContextIL c : _singleAddressAccountContexts.values()) {
         list.add(new SingleAddressAccountContextIL(c));
      }
      return list;
   }

   @Override
   public void createSingleAddressAccountContext(SingleAddressAccountContextIL context) {
      _singleAddressAccountContexts.put(context.getId(), new SingleAddressAccountContextIL(context));
      _backings.put(context.getId(), new InMemoryBtcILAccountBacking());
   }

   @Override
   public void deleteSingleAddressAccountContext(UUID accountId) {
      _backings.remove(accountId);
      _singleAddressAccountContexts.remove(accountId);
   }

   @Override
   public List<SingleAddressAccountContextIL> loadAccountContexts() {
      return loadSingleAddressAccountContexts();
   }

   @Override
   public BtcILAccountBacking getAccountBacking(UUID accountId) {
      return getSingleAddressAccountBacking(accountId);
   }

   @Override
   public void createAccountContext(SingleAddressAccountContextIL singleAddressAccountContext) {

   }

   @Override
   public void updateAccountContext(SingleAddressAccountContextIL singleAddressAccountContext) {

   }

   @Override
   public void deleteAccountContext(UUID uuid) {
      deleteSingleAddressAccountContext(uuid);
   }


   @Override
   public void deleteBip44AccountContext(UUID accountId) {
      _backings.remove(accountId);
      _bip44Contexts.remove(accountId);
   }

   @Override
   public Bip44BtcILAccountBacking getBip44AccountBacking(UUID accountId) {
      InMemoryBtcILAccountBacking backing = _backings.get(accountId);
      Preconditions.checkNotNull(backing);
      return backing;
   }

   @Override
   public SingleAddressBtcILAccountBacking getSingleAddressAccountBacking(UUID accountId) {
      InMemoryBtcILAccountBacking backing = _backings.get(accountId);
      Preconditions.checkNotNull(backing);
      return backing;
   }

   @Override
   public byte[] getValue(byte[] id) {
      return _values.get(idToString(id));
   }

   @Override
   public byte[] getValue(byte[] id, int subId) {
      if (subId > maxSubId) {
         throw new RuntimeException("subId does not exist");
      }
      return _values.get(idToString(id, subId));
   }

   @Override
   public void setValue(byte[] id, byte[] plaintextValue) {
      _values.put(idToString(id), plaintextValue);
   }

   @Override
   public void setValue(byte[] key, int subId, byte[] plaintextValue) {
      if (subId > maxSubId){
         maxSubId = subId;
      }
      _values.put(idToString(key, subId), plaintextValue);
   }

   @Override
   public int getMaxSubId() {
      return  maxSubId;
   }

   @Override
   public void deleteValue(byte[] id) {
      _values.remove(idToString(id));
   }

   @Override
   public void deleteSubStorageId(int subId) {
      throw new UnsupportedOperationException();
   }

   private String idToString(byte[] id) {
      return HexUtils.toHex(id);
   }

   private String idToString(byte[] id, int subId) {
      return "sub" + subId + "." + HexUtils.toHex(id);
   }

   private class InMemoryBtcILAccountBacking implements Bip44BtcILAccountBacking, SingleAddressBtcILAccountBacking {
      private final Map<BitcoinILOutPoint, BitcoinILTransactionOutputEx> _unspentOuputs = new HashMap<>();
      private final Map<BitcoinILSha256Hash, BitcoinILTransactionEx> _transactions = new HashMap<>();
      private final Map<BitcoinILOutPoint, BitcoinILTransactionOutputEx> _parentOutputs = new HashMap<>();
      private final Map<BitcoinILSha256Hash, byte[]> _outgoingTransactions = new HashMap<>();
      private final HashMap<BitcoinILSha256Hash, BitcoinILOutPoint> _txRefersParentTxOpus = new HashMap<>();

      @Override
      public void updateAccountContext(HDAccountContextIL context) {
         // Since this is in-memory we don't try to optimize and just update all values
         _bip44Contexts.put(context.getId(), new HDAccountContextIL(context));
      }

      @Override
      public void updateAccountContext(SingleAddressAccountContextIL context) {
         // Since this is in-memory we don't try to optimize and just update all values
         _singleAddressAccountContexts.put(context.getId(), new SingleAddressAccountContextIL(context));
      }

      @Override
      public void beginTransaction() {
         InMemoryBtcILWalletManagerBacking.this.beginTransaction();
      }

      @Override
      public void setTransactionSuccessful() {
         InMemoryBtcILWalletManagerBacking.this.setTransactionSuccessful();
      }

      @Override
      public void endTransaction() {
         InMemoryBtcILWalletManagerBacking.this.endTransaction();
      }

      @Override
      public void clear() {
         _unspentOuputs.clear();
         _transactions.clear();
         _parentOutputs.clear();
         _outgoingTransactions.clear();
      }

      @Override
      public Collection<BitcoinILTransactionOutputEx> getAllUnspentOutputs() {
         return new LinkedList<>(_unspentOuputs.values());
      }

      @Override
      public BitcoinILTransactionOutputEx getUnspentOutput(BitcoinILOutPoint outPoint) {
         return _unspentOuputs.get(outPoint);
      }

      @Override
      public void deleteUnspentOutput(BitcoinILOutPoint outPoint) {
         _unspentOuputs.remove(outPoint);
      }

      @Override
      public void putUnspentOutput(BitcoinILTransactionOutputEx output) {
         _unspentOuputs.put(output.outPoint, output);
      }

      @Override
      public void putParentTransactionOuputs(List<BitcoinILTransactionOutputEx> outputsList) {
         for (BitcoinILTransactionOutputEx outputEx : outputsList) {
            putParentTransactionOutput(outputEx);
         }
      }

      @Override
      public void putParentTransactionOutput(BitcoinILTransactionOutputEx output) {
         _parentOutputs.put(output.outPoint, output);
      }

      @Override
      public BitcoinILTransactionOutputEx getParentTransactionOutput(BitcoinILOutPoint outPoint) {
         return _parentOutputs.get(outPoint);
      }

      @Override
      public boolean hasParentTransactionOutput(BitcoinILOutPoint outPoint) {
         return _parentOutputs.containsKey(outPoint);
      }

      @Override
      public void putTransactions(Collection<? extends BitcoinILTransactionEx> transactions) {
         for (BitcoinILTransactionEx transaction : transactions) {
            putTransaction(transaction);
         }
      }

      @Override
      public void putTransaction(BitcoinILTransactionEx transaction) {
         _transactions.put(transaction.txid, transaction);
      }

      @Override
      public BitcoinILTransactionEx getTransaction(BitcoinILSha256Hash hash) {
         return _transactions.get(hash);
      }

      @Override
      public void deleteTransaction(BitcoinILSha256Hash hash) {
         _transactions.remove(hash);
      }

      @Override
      public List<BitcoinILTransactionEx> getTransactionHistory(int offset, int limit) {
         List<BitcoinILTransactionEx> list = new ArrayList<>(_transactions.values());
         Collections.sort(list);
         if (offset >= list.size()) {
            return Collections.emptyList();
         }
         int endIndex = Math.min(offset + limit, list.size());
         return Collections.unmodifiableList(list.subList(offset, endIndex));
      }

      @Override
      public List<BitcoinILTransactionEx> getTransactionsSince(long since) {
         List<BitcoinILTransactionEx> list = new ArrayList<>(_transactions.values());
         Collections.sort(list);
         final ArrayList<BitcoinILTransactionEx> result = new ArrayList<>();
         for (BitcoinILTransactionEx entry : list) {
            if (entry.time < since) {
               break;
            }
            result.add(entry);
         }
         return result;
      }

      @Override
      public Collection<BitcoinILTransactionEx> getUnconfirmedTransactions() {
         List<BitcoinILTransactionEx> unconfirmed = new LinkedList<>();
         for (BitcoinILTransactionEx tex : _transactions.values()) {
            if (tex.height == -1) {
               unconfirmed.add(tex);
            }
         }
         return unconfirmed;
      }

      @Override
      public Collection<BitcoinILTransactionEx> getYoungTransactions(int maxConfirmations, int blockChainHeight) {
         List<BitcoinILTransactionEx> young = new LinkedList<>();
         for (BitcoinILTransactionEx tex : _transactions.values()) {
            int confirmations = tex.calculateConfirmations(blockChainHeight);
            if (confirmations <= maxConfirmations) {
               young.add(tex);
            }
         }
         return young;
      }

      @Override
      public boolean hasTransaction(BitcoinILSha256Hash txid) {
         return _transactions.containsKey(txid);
      }

      @Override
      public void putOutgoingTransaction(BitcoinILSha256Hash txid, byte[] rawTransaction) {
         _outgoingTransactions.put(txid, rawTransaction);
      }

      @Override
      public Map<BitcoinILSha256Hash, byte[]> getOutgoingTransactions() {
         return new HashMap<>(_outgoingTransactions);
      }

      @Override
      public boolean isOutgoingTransaction(BitcoinILSha256Hash txid) {
         return _outgoingTransactions.containsKey(txid);
      }

      @Override
      public void removeOutgoingTransaction(BitcoinILSha256Hash txid) {
         _outgoingTransactions.remove(txid);
      }

      @Override
      public void putTxRefersParentTransaction(BitcoinILSha256Hash txId, List<BitcoinILOutPoint> refersOutputs) {
         for (BitcoinILOutPoint outpoint : refersOutputs) {
            _txRefersParentTxOpus.put(txId, outpoint);
         }
      }

      @Override
      public void deleteTxRefersParentTransaction(BitcoinILSha256Hash txId) {
         _txRefersParentTxOpus.remove(txId);
      }

      @Override
      public Collection<BitcoinILSha256Hash> getTransactionsReferencingOutPoint(BitcoinILOutPoint outPoint) {
         ArrayList<BitcoinILSha256Hash> ret = new ArrayList<>();
         for (Map.Entry<BitcoinILSha256Hash, BitcoinILOutPoint> entry : _txRefersParentTxOpus.entrySet()) {
            if (entry.getValue().equals(outPoint)) {
               ret.add(entry.getKey());
            }
         }
         return ret;
      }
   }
}
