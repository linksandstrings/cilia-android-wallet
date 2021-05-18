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

package com.mycelium.wapi.wallet.btc;


import com.google.common.base.Preconditions;
import com.mrd.bitillib.model.BitcoinILOutPoint;
import com.mrd.bitillib.util.BitcoinILSha256Hash;
import com.mrd.bitlib.model.OutPoint;
import com.mrd.bitlib.util.HexUtils;
import com.mrd.bitlib.util.Sha256Hash;
import com.mycelium.wapi.model.BitcoinILTransactionEx;
import com.mycelium.wapi.model.BitcoinILTransactionOutputEx;
import com.mycelium.wapi.model.TransactionEx;
import com.mycelium.wapi.model.TransactionOutputEx;
import com.mycelium.wapi.wallet.FeeEstimationsGeneric;
import com.mycelium.wapi.wallet.SingleAddressBtcAccountBacking;
import com.mycelium.wapi.wallet.SingleAddressBtcILAccountBacking;
import com.mycelium.wapi.wallet.btc.bip44.HDAccountContext;
import com.mycelium.wapi.wallet.btc.single.SingleAddressAccountContext;
import com.mycelium.wapi.wallet.btcil.Bip44BtcILAccountBacking;
import com.mycelium.wapi.wallet.btcil.bip44.HDAccountContextIL;
import com.mycelium.wapi.wallet.btcil.single.SingleAddressAccountContextIL;
import com.mycelium.wapi.wallet.coins.AssetInfo;

import java.util.*;

/**
 * Backing for a wallet manager which is only kept temporarily in memory
 */
public class InMemoryBtcWalletManagerBacking implements BtcWalletManagerBacking<SingleAddressAccountContext> {
   private final Map<String, byte[]> _values = new HashMap<>();
   private final Map<UUID, InMemoryBtcAccountBacking> _backings = new HashMap<>();
   private final Map<UUID, InMemoryBtcILAccountBacking> _btcilbackings = new HashMap<>();
   private final Map<UUID, HDAccountContext> _bip44Contexts = new HashMap<>();
   private final Map<UUID, HDAccountContextIL> _bip44ContextILs = new HashMap<>();
   private final Map<UUID, SingleAddressAccountContext> _singleAddressAccountContexts = new HashMap<>();
   private final Map<UUID, SingleAddressAccountContextIL> _singleAddressAccountILContexts = new HashMap<>();
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
   public List<HDAccountContext> loadBip44AccountContexts() {
      // Return a list containing copies
      List<HDAccountContext> list = new ArrayList<>();
      for (HDAccountContext c : _bip44Contexts.values()) {
         list.add(new HDAccountContext(c));
      }
      return list;
   }

   @Override
   public List<HDAccountContextIL> loadBip44AccountILContexts() {
      return null;
   }

   @Override
   public void createBip44AccountContext(HDAccountContext context) {
      _bip44Contexts.put(context.getId(), new HDAccountContext(context));
      _backings.put(context.getId(), new InMemoryBtcAccountBacking());
   }

   @Override
   public void createBip44AccountILContext(HDAccountContextIL context) {
      _bip44ContextILs.put(context.getId(), new HDAccountContextIL(context));
      _btcilbackings.put(context.getId(), new InMemoryBtcILAccountBacking());
   }

   @Override
   public List<SingleAddressAccountContext> loadSingleAddressAccountContexts() {
      // Return a list containing copies
      List<SingleAddressAccountContext> list = new ArrayList<>();
      for (SingleAddressAccountContext c : _singleAddressAccountContexts.values()) {
         list.add(new SingleAddressAccountContext(c));
      }
      return list;
   }

   @Override
   public List<SingleAddressAccountContextIL> loadSingleAddressAccountILContexts() {
      List<SingleAddressAccountContextIL> list = new ArrayList<>();
      for (SingleAddressAccountContextIL c : _singleAddressAccountILContexts.values()) {
         list.add(new SingleAddressAccountContextIL(c));
      }
      return list;
   }

   @Override
   public void createSingleAddressAccountContext(SingleAddressAccountContext context) {
      _singleAddressAccountContexts.put(context.getId(), new SingleAddressAccountContext(context));
      _backings.put(context.getId(), new InMemoryBtcAccountBacking());
   }

   @Override
   public void createSingleAddressAccountContextIL(SingleAddressAccountContextIL context) {
      _singleAddressAccountILContexts.put(context.getId(), new SingleAddressAccountContextIL(context));
      _btcilbackings.put(context.getId(), new InMemoryBtcILAccountBacking());
   }

   @Override
   public void deleteSingleAddressAccountContext(UUID accountId) {
      _backings.remove(accountId);
      _singleAddressAccountContexts.remove(accountId);
   }

   @Override
   public List<SingleAddressAccountContext> loadAccountContexts() {
      return loadSingleAddressAccountContexts();
   }

   @Override
   public BtcAccountBacking getAccountBacking(UUID accountId) {
      return getSingleAddressAccountBacking(accountId);
   }

   @Override
   public void createAccountContext(SingleAddressAccountContext singleAddressAccountContext) {

   }

   @Override
   public void updateAccountContext(SingleAddressAccountContext singleAddressAccountContext) {

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
   public Bip44BtcAccountBacking getBip44AccountBacking(UUID accountId) {
      InMemoryBtcAccountBacking backing = _backings.get(accountId);
      Preconditions.checkNotNull(backing);
      return backing;
   }

   @Override
   public Bip44BtcILAccountBacking getBip44AccountILBacking(UUID accountId) {
      return null;
   }

   @Override
   public SingleAddressBtcAccountBacking getSingleAddressAccountBacking(UUID accountId) {
      InMemoryBtcAccountBacking backing = _backings.get(accountId);
      Preconditions.checkNotNull(backing);
      return backing;
   }

   @Override
   public SingleAddressBtcILAccountBacking getSingleAddressAccountILBacking(UUID accountId) {
      InMemoryBtcILAccountBacking backing = _btcilbackings.get(accountId);
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

   private class InMemoryBtcAccountBacking implements Bip44BtcAccountBacking, SingleAddressBtcAccountBacking {
      private final Map<OutPoint, TransactionOutputEx> _unspentOuputs = new HashMap<>();
      private final Map<Sha256Hash, TransactionEx> _transactions = new HashMap<>();
      private final Map<OutPoint, TransactionOutputEx> _parentOutputs = new HashMap<>();
      private final Map<Sha256Hash, byte[]> _outgoingTransactions = new HashMap<>();
      private final HashMap<Sha256Hash, OutPoint> _txRefersParentTxOpus = new HashMap<>();

      @Override
      public void updateAccountContext(HDAccountContext context) {
         // Since this is in-memory we don't try to optimize and just update all values
         _bip44Contexts.put(context.getId(), new HDAccountContext(context));
      }

      @Override
      public void updateAccountContext(SingleAddressAccountContext context) {
         // Since this is in-memory we don't try to optimize and just update all values
         _singleAddressAccountContexts.put(context.getId(), new SingleAddressAccountContext(context));
      }

      @Override
      public void beginTransaction() {
         InMemoryBtcWalletManagerBacking.this.beginTransaction();
      }

      @Override
      public void setTransactionSuccessful() {
         InMemoryBtcWalletManagerBacking.this.setTransactionSuccessful();
      }

      @Override
      public void endTransaction() {
         InMemoryBtcWalletManagerBacking.this.endTransaction();
      }

      @Override
      public void clear() {
         _unspentOuputs.clear();
         _transactions.clear();
         _parentOutputs.clear();
         _outgoingTransactions.clear();
      }

      @Override
      public Collection<TransactionOutputEx> getAllUnspentOutputs() {
         return new LinkedList<>(_unspentOuputs.values());
      }

      @Override
      public TransactionOutputEx getUnspentOutput(OutPoint outPoint) {
         return _unspentOuputs.get(outPoint);
      }

      @Override
      public void deleteUnspentOutput(OutPoint outPoint) {
         _unspentOuputs.remove(outPoint);
      }

      @Override
      public void putUnspentOutput(TransactionOutputEx output) {
         _unspentOuputs.put(output.outPoint, output);
      }

      @Override
      public void putParentTransactionOuputs(List<TransactionOutputEx> outputsList) {
         for (TransactionOutputEx outputEx : outputsList) {
            putParentTransactionOutput(outputEx);
         }
      }

      @Override
      public void putParentTransactionOutput(TransactionOutputEx output) {
         _parentOutputs.put(output.outPoint, output);
      }

      @Override
      public TransactionOutputEx getParentTransactionOutput(OutPoint outPoint) {
         return _parentOutputs.get(outPoint);
      }

      @Override
      public boolean hasParentTransactionOutput(OutPoint outPoint) {
         return _parentOutputs.containsKey(outPoint);
      }

      @Override
      public void putTransactions(Collection<? extends TransactionEx> transactions) {
         for (TransactionEx transaction : transactions) {
            putTransaction(transaction);
         }
      }

      @Override
      public void putTransaction(TransactionEx transaction) {
         _transactions.put(transaction.txid, transaction);
      }

      @Override
      public TransactionEx getTransaction(Sha256Hash hash) {
         return _transactions.get(hash);
      }

      @Override
      public void deleteTransaction(Sha256Hash hash) {
         _transactions.remove(hash);
      }

      @Override
      public List<TransactionEx> getTransactionHistory(int offset, int limit) {
         List<TransactionEx> list = new ArrayList<>(_transactions.values());
         Collections.sort(list);
         if (offset >= list.size()) {
            return Collections.emptyList();
         }
         int endIndex = Math.min(offset + limit, list.size());
         return Collections.unmodifiableList(list.subList(offset, endIndex));
      }

      @Override
      public List<TransactionEx> getTransactionsSince(long since) {
         List<TransactionEx> list = new ArrayList<>(_transactions.values());
         Collections.sort(list);
         final ArrayList<TransactionEx> result = new ArrayList<>();
         for (TransactionEx entry : list) {
            if (entry.time < since) {
               break;
            }
            result.add(entry);
         }
         return result;
      }

      @Override
      public Collection<TransactionEx> getUnconfirmedTransactions() {
         List<TransactionEx> unconfirmed = new LinkedList<>();
         for (TransactionEx tex : _transactions.values()) {
            if (tex.height == -1) {
               unconfirmed.add(tex);
            }
         }
         return unconfirmed;
      }

      @Override
      public Collection<TransactionEx> getYoungTransactions(int maxConfirmations, int blockChainHeight) {
         List<TransactionEx> young = new LinkedList<>();
         for (TransactionEx tex : _transactions.values()) {
            int confirmations = tex.calculateConfirmations(blockChainHeight);
            if (confirmations <= maxConfirmations) {
               young.add(tex);
            }
         }
         return young;
      }

      @Override
      public boolean hasTransaction(Sha256Hash txid) {
         return _transactions.containsKey(txid);
      }

      @Override
      public void putOutgoingTransaction(Sha256Hash txid, byte[] rawTransaction) {
         _outgoingTransactions.put(txid, rawTransaction);
      }

      @Override
      public Map<Sha256Hash, byte[]> getOutgoingTransactions() {
         return new HashMap<>(_outgoingTransactions);
      }

      @Override
      public boolean isOutgoingTransaction(Sha256Hash txid) {
         return _outgoingTransactions.containsKey(txid);
      }

      @Override
      public void removeOutgoingTransaction(Sha256Hash txid) {
         _outgoingTransactions.remove(txid);
      }

      @Override
      public void putTxRefersParentTransaction(Sha256Hash txId, List<OutPoint> refersOutputs) {
         for (OutPoint outpoint : refersOutputs) {
            _txRefersParentTxOpus.put(txId, outpoint);
         }
      }

      @Override
      public void deleteTxRefersParentTransaction(Sha256Hash txId) {
         _txRefersParentTxOpus.remove(txId);
      }

      @Override
      public Collection<Sha256Hash> getTransactionsReferencingOutPoint(OutPoint outPoint) {
         ArrayList<Sha256Hash> ret = new ArrayList<>();
         for (Map.Entry<Sha256Hash, OutPoint> entry : _txRefersParentTxOpus.entrySet()) {
            if (entry.getValue().equals(outPoint)) {
               ret.add(entry.getKey());
            }
         }
         return ret;
      }
   }

   private class InMemoryBtcILAccountBacking implements Bip44BtcILAccountBacking, SingleAddressBtcILAccountBacking {

      private final Map<OutPoint, TransactionOutputEx> _unspentOuputs = new HashMap<>();
      private final Map<Sha256Hash, TransactionEx> _transactions = new HashMap<>();
      private final Map<OutPoint, TransactionOutputEx> _parentOutputs = new HashMap<>();
      private final Map<Sha256Hash, byte[]> _outgoingTransactions = new HashMap<>();
      private final HashMap<Sha256Hash, OutPoint> _txRefersParentTxOpus = new HashMap<>();

      @Override
      public void updateAccountContext(SingleAddressAccountContextIL context) {

      }

      @Override
      public void updateAccountContext(HDAccountContextIL context) {
         _bip44ContextILs.put(context.getId(), new HDAccountContextIL(context));
      }

      @Override
      public Collection<BitcoinILTransactionOutputEx> getAllUnspentOutputs() {
         return null;
      }

      @Override
      public BitcoinILTransactionOutputEx getUnspentOutput(BitcoinILOutPoint outPoint) {
         return null;
      }

      @Override
      public void deleteUnspentOutput(BitcoinILOutPoint outPoint) {

      }

      @Override
      public void putUnspentOutput(BitcoinILTransactionOutputEx output) {

      }

      @Override
      public void putParentTransactionOuputs(List<BitcoinILTransactionOutputEx> outputsList) {

      }

      @Override
      public void putParentTransactionOutput(BitcoinILTransactionOutputEx output) {

      }

      @Override
      public BitcoinILTransactionOutputEx getParentTransactionOutput(BitcoinILOutPoint outPoint) {
         return null;
      }

      @Override
      public boolean hasParentTransactionOutput(BitcoinILOutPoint outPoint) {
         return false;
      }

      @Override
      public void putTransaction(BitcoinILTransactionEx transaction) {

      }

      @Override
      public void putTransactions(Collection<? extends BitcoinILTransactionEx> transactions) {

      }

      @Override
      public BitcoinILTransactionEx getTransaction(BitcoinILSha256Hash hash) {
         return null;
      }

      @Override
      public void deleteTransaction(BitcoinILSha256Hash hash) {

      }

      @Override
      public List<BitcoinILTransactionEx> getTransactionHistory(int offset, int limit) {
         return null;
      }

      @Override
      public List<BitcoinILTransactionEx> getTransactionsSince(long since) {
         return null;
      }

      @Override
      public Collection<BitcoinILTransactionEx> getUnconfirmedTransactions() {
         return null;
      }

      @Override
      public Collection<BitcoinILTransactionEx> getYoungTransactions(int maxConfirmations, int blockChainHeight) {
         return null;
      }

      @Override
      public boolean hasTransaction(BitcoinILSha256Hash txid) {
         return false;
      }

      @Override
      public void putOutgoingTransaction(BitcoinILSha256Hash txid, byte[] rawTransaction) {

      }

      @Override
      public Map<BitcoinILSha256Hash, byte[]> getOutgoingTransactions() {
         return null;
      }

      @Override
      public boolean isOutgoingTransaction(BitcoinILSha256Hash txid) {
         return false;
      }

      @Override
      public void removeOutgoingTransaction(BitcoinILSha256Hash txid) {

      }

      @Override
      public void deleteTxRefersParentTransaction(BitcoinILSha256Hash txId) {

      }

      @Override
      public Collection<BitcoinILSha256Hash> getTransactionsReferencingOutPoint(BitcoinILOutPoint outPoint) {
         return null;
      }

      @Override
      public void putTxRefersParentTransaction(BitcoinILSha256Hash txId, List<BitcoinILOutPoint> refersOutputs) {

      }

      @Override
      public void beginTransaction() {

      }

      @Override
      public void setTransactionSuccessful() {

      }

      @Override
      public void endTransaction() {

      }

      @Override
      public void clear() {

      }
   }
}
