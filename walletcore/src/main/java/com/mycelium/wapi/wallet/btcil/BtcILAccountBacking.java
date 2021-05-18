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

import com.mrd.bitillib.model.BitcoinILOutPoint;
import com.mrd.bitillib.util.BitcoinILSha256Hash;
import com.mycelium.wapi.model.BitcoinILTransactionEx;
import com.mycelium.wapi.model.BitcoinILTransactionOutputEx;
import com.mycelium.wapi.wallet.CommonAccountBacking;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface BtcILAccountBacking extends CommonAccountBacking {

   Collection<BitcoinILTransactionOutputEx> getAllUnspentOutputs();

   BitcoinILTransactionOutputEx getUnspentOutput(BitcoinILOutPoint outPoint);

   void deleteUnspentOutput(BitcoinILOutPoint outPoint);

   void putUnspentOutput(BitcoinILTransactionOutputEx output);

   void putParentTransactionOuputs(List<BitcoinILTransactionOutputEx> outputsList);

   void putParentTransactionOutput(BitcoinILTransactionOutputEx output);

   BitcoinILTransactionOutputEx getParentTransactionOutput(BitcoinILOutPoint outPoint);

   boolean hasParentTransactionOutput(BitcoinILOutPoint outPoint);

   void putTransaction(BitcoinILTransactionEx transaction);

   void putTransactions(Collection<? extends BitcoinILTransactionEx> transactions);

   BitcoinILTransactionEx getTransaction(BitcoinILSha256Hash hash);

   void deleteTransaction(BitcoinILSha256Hash hash);

   List<BitcoinILTransactionEx> getTransactionHistory(int offset, int limit);

   List<BitcoinILTransactionEx> getTransactionsSince(long since);

   Collection<BitcoinILTransactionEx> getUnconfirmedTransactions();

   Collection<BitcoinILTransactionEx> getYoungTransactions(int maxConfirmations, int blockChainHeight);

   boolean hasTransaction(BitcoinILSha256Hash txid);

   void putOutgoingTransaction(BitcoinILSha256Hash txid, byte[] rawTransaction);

   Map<BitcoinILSha256Hash, byte[]> getOutgoingTransactions();

   boolean isOutgoingTransaction(BitcoinILSha256Hash txid);

   void removeOutgoingTransaction(BitcoinILSha256Hash txid);

   void deleteTxRefersParentTransaction(BitcoinILSha256Hash txId);

   Collection<BitcoinILSha256Hash> getTransactionsReferencingOutPoint(BitcoinILOutPoint outPoint);

   void putTxRefersParentTransaction(BitcoinILSha256Hash txId, List<BitcoinILOutPoint> refersOutputs);
}
