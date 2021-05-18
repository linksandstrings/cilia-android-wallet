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

import com.google.common.collect.Lists;
import com.mrd.bitillib.FeeEstimator;
import com.mrd.bitillib.FeeEstimatorBuilder;
import com.mrd.bitillib.PopBuilder;
import com.mrd.bitillib.StandardBitcoinILTransactionBuilder;
import com.mrd.bitillib.StandardBitcoinILTransactionBuilder.InsufficientBTCILException;
import com.mrd.bitillib.StandardBitcoinILTransactionBuilder.BTCILOutputTooSmallException;
import com.mrd.bitillib.UnsignedTransaction;
import com.mrd.bitillib.crypto.BipDerivationType;
import com.mrd.bitillib.crypto.BitcoinILSigner;
import com.mrd.bitillib.crypto.IPrivateKeyRing;
import com.mrd.bitillib.crypto.IPublicKeyRing;
import com.mrd.bitillib.crypto.InMemoryPrivateKey;
import com.mrd.bitillib.crypto.PublicKey;
import com.mrd.bitillib.model.*;
import com.mrd.bitillib.model.BitcoinILTransaction.BitcoinILTransactionParsingException;
import com.mrd.bitillib.model.TransactionInput;
import com.mrd.bitillib.model.TransactionOutput;
import com.mrd.bitillib.model.UnspentTransactionOutput;
import com.mrd.bitillib.util.BitUtils;
import com.mrd.bitillib.util.ByteReader;
import com.mrd.bitillib.util.HashUtils;
import com.mrd.bitillib.util.BitcoinILSha256Hash;
import com.mrd.bitlib.model.AddressType;
import com.mrd.bitlib.util.HexUtils;
import com.mycelium.wapi.api.Wapi;
import com.mycelium.wapi.api.WapiException;
import com.mycelium.wapi.api.WapiResponse;
import com.mycelium.wapi.api.lib.BitcoinILTransactionExApi;
import com.mycelium.wapi.api.request.BroadcastTransactionRequest;
import com.mycelium.wapi.api.request.CheckBitcoinILTransactionsRequest;
import com.mycelium.wapi.api.request.GetBitcoinILTransactionsRequest;
import com.mycelium.wapi.api.request.BitcoinILQueryUnspentOutputsRequest;
import com.mycelium.wapi.api.response.BroadcastBitcoinILTransactionResponse;
import com.mycelium.wapi.api.response.CheckBitcoinILTransactionsResponse;
import com.mycelium.wapi.api.response.GetBitcoinILTransactionsResponse;
import com.mycelium.wapi.api.response.BroadcastTransactionResponse;
import com.mycelium.wapi.api.response.BitcoinILQueryUnspentOutputsResponse;
import com.mycelium.wapi.model.BalanceSatoshis;
import com.mycelium.wapi.model.BitcoinILTransactionStatus;
import com.mycelium.wapi.model.BtcILTransactionDetails;
import com.mycelium.wapi.model.BitcoinILTransactionEx;
import com.mycelium.wapi.model.BitcoinILTransactionOutputEx;
import com.mycelium.wapi.model.BitcoinILTransactionOutputSummary;
import com.mycelium.wapi.wallet.AddressUtils;
import com.mycelium.wapi.wallet.AesKeyCipher;
import com.mycelium.wapi.wallet.BroadcastResult;
import com.mycelium.wapi.wallet.BroadcastResultType;
import com.mycelium.wapi.wallet.TransactionData;
import com.mycelium.wapi.wallet.ColuTransferInstructionsParser;
import com.mycelium.wapi.wallet.ConfirmationRiskProfileLocal;
import com.mycelium.wapi.wallet.Address;
import com.mycelium.wapi.wallet.Fee;
import com.mycelium.wapi.wallet.InputViewModel;
import com.mycelium.wapi.wallet.OutputViewModel;
import com.mycelium.wapi.wallet.Transaction;
import com.mycelium.wapi.wallet.TransactionSummary;
import com.mycelium.wapi.wallet.KeyCipher;
import com.mycelium.wapi.wallet.KeyCipher.InvalidKeyCipher;
import com.mycelium.wapi.wallet.WalletAccount;
import com.mycelium.wapi.wallet.WalletManager.Event;
import com.mycelium.wapi.wallet.btcil.coins.BitcoinILMain;
import com.mycelium.wapi.wallet.btcil.coins.BitcoinILTest;
import com.mycelium.wapi.wallet.btcil.single.SingleAddressAccountIL;
import com.mycelium.wapi.wallet.coins.Balance;
import com.mycelium.wapi.wallet.coins.CryptoCurrency;
import com.mycelium.wapi.wallet.coins.Value;
import com.mycelium.wapi.wallet.currency.CurrencyBasedBalance;
import com.mycelium.wapi.wallet.currency.ExactBitcoinValue;
import com.mycelium.wapi.wallet.currency.ExactCurrencyValue;
import com.mycelium.wapi.wallet.exceptions.BuildTransactionException;
import com.mycelium.wapi.wallet.exceptions.InsufficientFundsException;
import com.mycelium.wapi.wallet.exceptions.OutputTooSmallException;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import static com.mrd.bitillib.StandardBitcoinILTransactionBuilder.createOutput;
import static com.mrd.bitillib.TransactionUtils.MINIMUM_OUTPUT_VALUE;
import static java.util.Collections.singletonList;

public abstract class AbstractBtcILAccount extends SynchronizeAbleWalletBtcILAccount {
   private static final int COINBASE_MIN_CONFIRMATIONS = 100;
   private static final int MAX_TRANSACTIONS_TO_HANDLE_SIMULTANEOUSLY = 199;
   private final ColuTransferInstructionsParser coluTransferInstructionsParser;

   public interface EventHandler {
      void onEvent(UUID accountId, Event event);
   }

   protected final NetworkParameters _network;
   protected final Wapi _wapi;
   protected final Logger _logger;
   protected boolean _allowZeroConfSpending = true;      //on per default, we warn users if they use it
   protected BalanceSatoshis _cachedBalance;

   private EventHandler _eventHandler;
   private final BtcILAccountBacking _backing;
   protected int syncTotalRetrievedTransactions = 0;

   protected AbstractBtcILAccount(BtcILAccountBacking backing, NetworkParameters network, Wapi wapi) {
      _network = network;
      _logger = Logger.getLogger(AbstractBtcILAccount.class.getSimpleName());
      _wapi = wapi;
      _backing = backing;
      coluTransferInstructionsParser = new ColuTransferInstructionsParser();
   }

   @Override
   public void setAllowZeroConfSpending(boolean allowZeroConfSpending) {
      _allowZeroConfSpending = allowZeroConfSpending;
   }

   @Override
   public Transaction createTx(Address address, Value amount, Fee fee, @Nullable TransactionData data)
           throws BuildTransactionException, InsufficientFundsException, OutputTooSmallException {
      FeePerKbFeeIL btcFee = (FeePerKbFeeIL)fee;
      BtcILTransaction btcTransaction =  new BtcILTransaction(getCoinType(), (BtcILAddress)address, amount, btcFee.getFeePerKb());
      ArrayList<BtcILReceiver> receivers = new ArrayList<>();
      receivers.add(new BtcILReceiver(btcTransaction.getDestination().getAddress(), btcTransaction.getAmount().getValueAsLong()));
      try {
         btcTransaction.setUnsignedTx(createUnsignedTransaction(receivers, btcTransaction.getFeePerKb().getValueAsLong()));
         return btcTransaction;
      } catch (BTCILOutputTooSmallException ex) {
         throw new OutputTooSmallException(ex);
      } catch (InsufficientBTCILException ex) {
         throw new InsufficientFundsException(ex);
      } catch (StandardBitcoinILTransactionBuilder.UnableToBuildTransactionException ex) {
         throw new BuildTransactionException(ex);
      }
   }

   @Override
   public void signTx(Transaction request, KeyCipher keyCipher ) throws InvalidKeyCipher {
      BtcILTransaction btcSendRequest = (BtcILTransaction) request;
      btcSendRequest.setTransaction(signTransaction(btcSendRequest.getUnsignedTx(), AesKeyCipher.defaultKeyCipher()));
   }

   public BtcILTransaction createTxFromOutputList(OutputList outputs, long minerFeePerKbToUse)
           throws BuildTransactionException, InsufficientFundsException, OutputTooSmallException {
      try {
         return new BtcILTransaction(getCoinType(), createUnsignedTransaction(outputs, minerFeePerKbToUse));
      } catch (BTCILOutputTooSmallException ex) {
         throw new OutputTooSmallException(ex);
      } catch (InsufficientBTCILException ex) {
         throw new InsufficientFundsException(ex);
      } catch (StandardBitcoinILTransactionBuilder.UnableToBuildTransactionException ex) {
         throw new BuildTransactionException(ex);
      }

   }

   @Override
   public boolean isExchangeable(){
      return true;
   }

   @Override
   public Transaction getTx(byte[] transactionId) {
      BitcoinILTransactionEx tex = _backing.getTransaction(BitcoinILSha256Hash.of(transactionId));
      BitcoinILTransaction tx = BitcoinILTransactionEx.toTransaction(tex);
      if (tx == null) {
         return null;
      }

      return new BtcILTransaction(getCoinType(), tx);
   }
   /**
    * set the event handler for this account
    *
    * @param eventHandler the event handler for this account
    */
   public void setEventHandler(EventHandler eventHandler) {
      _eventHandler = eventHandler;
   }

   protected void postEvent(Event event) {
      if (_eventHandler != null) {
         _eventHandler.onEvent(this.getId(), event);
      }
   }

   /**
    * Determine whether a transaction was sent from one of our own addresses.
    * <p>
    * This is a costly operation as we first have to lookup the transaction and
    * then it's funding outputs
    *
    * @param txid the ID of the transaction to investigate
    * @return true if one of the funding outputs were sent from one of our own
    * addresses
    */
   protected boolean isFromMe(BitcoinILSha256Hash txid) {
      BitcoinILTransaction t = BitcoinILTransactionEx.toTransaction(_backing.getTransaction(txid));
      return t != null && isFromMe(t);
   }

   /**
    * Determine whether a transaction was sent from one of our own addresses.
    * <p>
    * This is a costly operation as we have to lookup funding outputs of the
    * transaction
    *
    * @param t the transaction to investigate
    * @return true iff one of the funding outputs were sent from one of our own
    * addresses
    */
   protected boolean isFromMe(BitcoinILTransaction t) {
      for (TransactionInput input : t.inputs) {
         BitcoinILTransactionOutputEx funding = _backing.getParentTransactionOutput(input.outPoint);
         if (funding == null || funding.isCoinBase) {
            continue;
         }
         ScriptOutput fundingScript = ScriptOutput.fromScriptBytes(funding.script);
         BitcoinILAddress fundingAddress = fundingScript.getAddress(_network);
         if (isMine(fundingAddress)) {
            return true;
         }
      }
      return false;
   }

   /**
    * Determine whether a transaction output was sent from one of our own
    * addresses
    *
    * @param output the output to investigate
    * @return true iff the putput was sent from one of our own addresses
    */
   protected boolean isMine(BitcoinILTransactionOutputEx output) {
      ScriptOutput script = ScriptOutput.fromScriptBytes(output.script);
      return isMine(script);
   }

   /**
    * Determine whether an output script was created by one of our own addresses
    *
    * @param script the script to investigate
    * @return true iff the script was created by one of our own addresses
    */
   protected boolean isMine(ScriptOutput script) {
      BitcoinILAddress address = script.getAddress(_network);
      return isMine(address);
   }

   //only for BtcLegacyAddress?
   public boolean isMineAddress(Address address) {
      try {
         boolean isBtcAddress = (address instanceof BtcILAddress);
         if (!isBtcAddress) {
            return false;
         }

         return isMine(((BtcILAddress) address).getAddress());
      } catch (IllegalStateException e) {
         e.printStackTrace();
         return false;
      }
   }

   public boolean isMineAddress(String address){
      Address addr = AddressUtils.from(_network.isProdnet()? BitcoinILMain.get() : BitcoinILTest.get(), address);
      return isMineAddress(addr);
   }


   protected static UUID addressToUUID(BitcoinILAddress address) {
      return new UUID(BitUtils.uint64ToLong(address.getAllAddressBytes(), 0), BitUtils.uint64ToLong(
              address.getAllAddressBytes(), 8));
   }

   /**
    * Checks for all UTXO of the requested addresses and deletes or adds them locally if necessary
    * returns -1 if something went wrong or otherwise the number of new UTXOs added to the local
    * database
    */
   protected int synchronizeUnspentOutputs(Collection<BitcoinILAddress> addresses) {
      // Get the current unspent outputs as dictated by the block chain
      BitcoinILQueryUnspentOutputsResponse unspentOutputResponse;
      try {
         unspentOutputResponse = _wapi.queryBitcoinILUnspentOutputs(new BitcoinILQueryUnspentOutputsRequest(Wapi.VERSION, addresses))
                 .getResult();
      } catch (WapiException e) {
         _logger.log(Level.SEVERE, "Server connection failed with error code: " + e.errorCode, e);
         postEvent(Event.SERVER_CONNECTION_ERROR);
         return -1;
      }
      Collection<BitcoinILTransactionOutputEx> remoteUnspent = unspentOutputResponse.unspent;
      // Store the current block height
      setBlockChainHeight(unspentOutputResponse.height);
      // Make a map for fast lookup
      Map<BitcoinILOutPoint, BitcoinILTransactionOutputEx> remoteMap = toMap(remoteUnspent);

      // Get the current unspent outputs as it is believed to be locally
      Collection<BitcoinILTransactionOutputEx> localUnspent = _backing.getAllUnspentOutputs();
      // Make a map for fast lookup
      Map<BitcoinILOutPoint, BitcoinILTransactionOutputEx> localMap = toMap(localUnspent);

      Set<BitcoinILSha256Hash> transactionsToAddOrUpdate = new HashSet<>();
      Set<BitcoinILAddress> addressesToDiscover = new HashSet<>();

      // Find remotely removed unspent outputs
      for (BitcoinILTransactionOutputEx l : localUnspent) {
         BitcoinILTransactionOutputEx r = remoteMap.get(l.outPoint);
         if (r == null) {
            // An output has gone. Maybe it was spent in another wallet, or
            // never confirmed due to missing fees, double spend, or mutated.

            // we need to fetch associated transactions, to see the outgoing tx in the history
            ScriptOutput scriptOutput = ScriptOutput.fromScriptBytes(l.script);
            boolean removeLocally = true;

            // Start of the hack to prevent actual local data removal if server still didn't process just sent tx
            youngTransactions:
            for (BitcoinILTransactionEx transactionEx : _backing.getTransactionsSince(System.currentTimeMillis() -
                    TimeUnit.SECONDS.toMillis(15))) {
               BitcoinILTransactionOutputEx output;
               int i = 0;
               while ((output = BitcoinILTransactionEx.getTransactionOutput(transactionEx, i++)) != null) {
                  if (output.equals(l) && !_backing.hasParentTransactionOutput(l.outPoint)) {
                     removeLocally = false;
                     break youngTransactions;
                  }
               }
            }
            // End of hack

            if (scriptOutput != null && removeLocally) {
               BitcoinILAddress address = scriptOutput.getAddress(_network);
               if (addresses.contains(address)) {
                  // the output was associated with an address we were scanning for
                  // we should have got back that output from the servers
                  // this means it got probably spent via another wallet
                  // scan this address for all associated transaction to keep the history in sync
                  if (!address.equals(BitcoinILAddress.getNullAddress(_network))) {
                     addressesToDiscover.add(address);
                  }
               } else {
                  removeLocally = false;
               }
            }

            if (removeLocally) {
               // delete the UTXO locally
               _backing.deleteUnspentOutput(l.outPoint);
            }
         }
      }

      int newUtxos = 0;

      // Find remotely added unspent outputs
      List<BitcoinILTransactionOutputEx> unspentOutputsToAddOrUpdate = new LinkedList<>();
      for (BitcoinILTransactionOutputEx r : remoteUnspent) {
         BitcoinILTransactionOutputEx l = localMap.get(r.outPoint);
         if (l == null) {
            // We might have already spent transaction, but if getUnspent used connection to different server
            // it would not know that output is already spent.
            l = _backing.getParentTransactionOutput(r.outPoint);
         }
         if (l == null || l.height != r.height) {
            // New remote output or new height (Maybe it confirmed or we
            // might even have had a reorg). Either way we just update it
            unspentOutputsToAddOrUpdate.add(r);
            transactionsToAddOrUpdate.add(r.outPoint.txid);
            // Note: We are not adding the unspent output to the DB just yet. We
            // first want to verify the full set of funding transactions of the
            // transaction that this unspent output belongs to
            if (l == null) {
               // this is a new UTXO. it might be necessary to sync older addresses too
               // as this might be a change utxo from spending smth from a address we own
               // too but did not sync here
               newUtxos ++;
            }
         }
      }

      // Fetch updated or added transactions
      if (!transactionsToAddOrUpdate.isEmpty()) {
         GetBitcoinILTransactionsResponse response;
         try {
            response = getTransactionsBatched(transactionsToAddOrUpdate).getResult();
            List<BitcoinILTransactionEx> txs = Lists.newLinkedList(response.transactions);
            handleNewExternalTransactions(txs);
         } catch (WapiException e) {
            _logger.log(Level.SEVERE, "Server connection failed with error code: " + e.errorCode, e);
            postEvent(Event.SERVER_CONNECTION_ERROR);
            return -1;
         }
         try {
            _backing.beginTransaction();
            // Finally update out list of unspent outputs with added or updated
            // outputs
            for (BitcoinILTransactionOutputEx output : unspentOutputsToAddOrUpdate) {
               // check if the output really belongs to one of our addresses
               // prevent getting out local cache into a undefined state, if the server screws up
               if (isMine(output)) {
                  _backing.putUnspentOutput(output);
               } else {
                  _logger.log(Level.SEVERE, "We got an UTXO that does not belong to us: " + output.toString());
               }
            }
            _backing.setTransactionSuccessful();
         } finally {
            _backing.endTransaction();
         }
      }

      // if we removed some UTXO because of a sync, it means that there are transactions
      // we don't yet know about. Run a discover for all addresses related to the UTXOs we removed
      if (!addressesToDiscover.isEmpty()) {
         try {
            doDiscoveryForAddresses(Lists.newArrayList(addressesToDiscover));
         } catch (WapiException ignore) {
         }
      }
      return newUtxos;
   }

   protected WapiResponse<GetBitcoinILTransactionsResponse> getTransactionsBatched(Collection<BitcoinILSha256Hash> txids)  {
      final GetBitcoinILTransactionsRequest fullRequest = new GetBitcoinILTransactionsRequest(Wapi.VERSION, txids);
      return _wapi.getBitcoinILTransactions(fullRequest);
   }

   protected abstract Set<BipDerivationType> doDiscoveryForAddresses(List<BitcoinILAddress> lookAhead) throws WapiException;

   private static Map<BitcoinILOutPoint, BitcoinILTransactionOutputEx> toMap(Collection<BitcoinILTransactionOutputEx> list) {
      Map<BitcoinILOutPoint, BitcoinILTransactionOutputEx> map = new HashMap<>();
      for (BitcoinILTransactionOutputEx t : list) {
         map.put(t.outPoint, t);
      }
      return map;
   }

   protected void handleNewExternalTransactions(Collection<BitcoinILTransactionEx> transactions) throws WapiException {
      handleNewExternalTransactions(transactions, false);
   }

   // HACK: skipping local handling of known transactions breaks the sync process. This should
   // be fixed somewhere else to make allKnown obsolete.
   protected void handleNewExternalTransactions(Collection<BitcoinILTransactionEx> transactions, boolean allKnown) throws WapiException {
      ArrayList<BitcoinILTransactionEx> all = new ArrayList<>(transactions);
      for (int i = 0; i < all.size(); i += MAX_TRANSACTIONS_TO_HANDLE_SIMULTANEOUSLY) {
         int endIndex = Math.min(all.size(), i + MAX_TRANSACTIONS_TO_HANDLE_SIMULTANEOUSLY);
         Collection<BitcoinILTransactionEx> sub = all.subList(i, endIndex);
         handleNewExternalTransactionsInt(sub, allKnown);
         updateSyncProgress();
      }
   }

   private void handleNewExternalTransactionsInt(@Nonnull Collection<BitcoinILTransactionEx> transactions, boolean allKnown) throws WapiException {
      // Transform and put into two arrays with matching indexes
      List<BitcoinILTransaction> txArray = new ArrayList<>(transactions.size());
      for (BitcoinILTransactionEx tex : transactions) {
         try {
            txArray.add(BitcoinILTransaction.fromByteReader(new ByteReader(tex.binary)));
         } catch (BitcoinILTransactionParsingException e) {
            // We hit a transaction that we cannot parse. Log but otherwise ignore it
            _logger.log(Level.SEVERE, "Received transaction that we cannot parse: " + tex.txid.toString());
         }
      }

      // Grab and handle parent transactions
      fetchStoreAndValidateParentOutputs(txArray, this instanceof SingleAddressAccountIL);

      // Store transaction locally
      if (!allKnown) {
         _backing.putTransactions(transactions);
         syncTotalRetrievedTransactions += transactions.size();
      }

      for (BitcoinILTransaction t : txArray) {
         onNewTransaction(t);
      }
   }

   public void fetchStoreAndValidateParentOutputs(List<BitcoinILTransaction> transactions, boolean doRemoteFetching) throws WapiException {
      Map<BitcoinILSha256Hash, BitcoinILTransactionEx> parentTransactions = new HashMap<>();
      Map<BitcoinILOutPoint, BitcoinILTransactionOutputEx> parentOutputs = new HashMap<>();

      // Find list of parent outputs to fetch
      Collection<BitcoinILSha256Hash> toFetch = new HashSet<>();
      for (BitcoinILTransaction t : transactions) {

         for (TransactionInput in : t.inputs) {
            if (in.outPoint.txid.equals(BitcoinILOutPoint.COINBASE_OUTPOINT.txid)) {
               // Coinbase input, so no parent
               continue;
            }
            BitcoinILTransactionOutputEx parentOutput = _backing.getParentTransactionOutput(in.outPoint);
            if (parentOutput != null) {
               // We already have the parent output, no need to fetch the entire
               // parent transaction
               parentOutputs.put(parentOutput.outPoint, parentOutput);
               continue;
            }
            BitcoinILTransactionEx parentTransaction = _backing.getTransaction(in.outPoint.txid);
            if (parentTransaction == null) {
               // Check current transactions list for parents
               for (BitcoinILTransaction transaction : transactions) {
                  if (transaction.getId().equals(in.outPoint.txid)) {
                     parentTransaction = BitcoinILTransactionEx.fromUnconfirmedTransaction(transaction);
                     break;
                  }
               }
            }

            if (parentTransaction != null) {
               // We had the parent transaction in our own transactions, no need to
               // fetch it remotely
               parentTransactions.put(parentTransaction.txid, parentTransaction);
            } else if (doRemoteFetching) {
               // Need to fetch it
               toFetch.add(in.outPoint.txid);
            }
         }
      }

      // Fetch missing parent transactions
      if (toFetch.size() > 0) {
         GetBitcoinILTransactionsResponse result = getTransactionsBatched(toFetch).getResult();
         for (BitcoinILTransactionExApi tx : result.transactions) {
            // Verify transaction hash. This is important as we don't want to
            // have a transaction output associated with an outpoint that
            // doesn't match.
            // This is the end users protection against a rogue server that lies
            // about the value of an output and makes you pay a large fee.
            BitcoinILSha256Hash hash = HashUtils.doubleSha256(tx.binary).reverse();
            if (hash.equals(tx.hash)) {
               parentTransactions.put(tx.txid, tx);
            } else {
               //TODO: Document what's happening here.
               //Question: Crash and burn? Really? How about user feedback? Here, wapi returned a transaction that doesn't hash to the txid it is supposed to txhash to, right?
               throw new RuntimeException("Failed to validate transaction hash from server. Expected: " + tx.txid
                       + " Calculated: " + hash);
            }
         }
      }

      // We should now have all parent transactions or parent outputs. There is
      // a slight probability that one of them was not found due to double
      // spends and/or malleability and network latency etc.

      // Now figure out which parent outputs we need to persist
      List<BitcoinILTransactionOutputEx> toPersist = new LinkedList<>();
      for (BitcoinILTransaction t : transactions) {
         for (TransactionInput in : t.inputs) {
            if (in.outPoint.txid.equals(BitcoinILOutPoint.COINBASE_OUTPOINT.txid)) {
               // coinbase input, so no parent
               continue;
            }
            BitcoinILTransactionOutputEx parentOutput = parentOutputs.get(in.outPoint);
            if (parentOutput != null) {
               // We had it all along
               continue;
            }
            BitcoinILTransactionEx parentTex = parentTransactions.get(in.outPoint.txid);
            if (parentTex != null) {
               // Parent output not found, maybe we already have it
               parentOutput = BitcoinILTransactionEx.getTransactionOutput(parentTex, in.outPoint.index);
               toPersist.add(parentOutput);
            }
         }
      }

      // Persist
      if (toPersist.size() <= MAX_TRANSACTIONS_TO_HANDLE_SIMULTANEOUSLY) {
         _backing.putParentTransactionOuputs(toPersist);
      } else {
         // We have quite a list of transactions to handle, do it in batches
         ArrayList<BitcoinILTransactionOutputEx> all = new ArrayList<>(toPersist);
         for (int i = 0; i < all.size(); i += MAX_TRANSACTIONS_TO_HANDLE_SIMULTANEOUSLY) {
            int endIndex = Math.min(all.size(), i + MAX_TRANSACTIONS_TO_HANDLE_SIMULTANEOUSLY);
            List<BitcoinILTransactionOutputEx> sub = all.subList(i, endIndex);
            _backing.putParentTransactionOuputs(sub);
         }
      }
   }

   protected BalanceSatoshis calculateLocalBalance() {
      Collection<BitcoinILTransactionOutputEx> unspentOutputs = new HashSet<>(_backing.getAllUnspentOutputs());
      long confirmed = 0;
      long pendingChange = 0;
      long pendingSending = 0;
      long pendingReceiving = 0;

      //
      // Determine the value we are receiving and create a set of outpoints for fast lookup
      //
      Set<BitcoinILOutPoint> unspentOutPoints = new HashSet<>();
      for (BitcoinILTransactionOutputEx output : unspentOutputs) {
         if (isColuDustOutput(output)) {
            continue;
         }
         if (output.height == -1) {
            if (isFromMe(output.outPoint.txid)) {
               pendingChange += output.value;
            } else {
               pendingReceiving += output.value;
            }
         } else {
            confirmed += output.value;
         }
         unspentOutPoints.add(output.outPoint);
      }

      //
      // Determine the value we are sending
      //

      // Get the current set of unconfirmed transactions
      List<BitcoinILTransaction> unconfirmed = new ArrayList<>();
      for (BitcoinILTransactionEx tex : _backing.getUnconfirmedTransactions()) {
         try {
            BitcoinILTransaction t = BitcoinILTransaction.fromByteReader(new ByteReader(tex.binary));
            unconfirmed.add(t);
         } catch (BitcoinILTransactionParsingException e) {
            // never happens, we have parsed it before
         }
      }

      for (BitcoinILTransaction t : unconfirmed) {
         // For each input figure out if WE are sending it by fetching the
         // parent transaction and looking at the address
         boolean weSend = false;
         for (TransactionInput input : t.inputs) {
            // Find the parent transaction
            if (input.outPoint.txid.equals(BitcoinILSha256Hash.ZERO_HASH)) {
               continue;
            }
            BitcoinILTransactionOutputEx parent = _backing.getParentTransactionOutput(input.outPoint);
            if (parent == null) {
               _logger.log(Level.SEVERE, "Unable to find parent transaction output: " + input.outPoint);
               continue;
            }
            TransactionOutput parentOutput = transform(parent);
            BitcoinILAddress fundingAddress = parentOutput.script.getAddress(_network);
            if (isMine(fundingAddress)) {
               // One of our addresses are sending coins
               pendingSending += parentOutput.value;
               weSend = true;
            }
         }

         // Now look at the outputs and if it contains change for us, then subtract that from the sending amount
         // if it is already spent in another transaction
         for (int i = 0; i < t.outputs.length; i++) {
            TransactionOutput output = t.outputs[i];
            BitcoinILAddress destination = output.script.getAddress(_network);
            if (weSend && isMine(destination)) {
               // The funds are sent from us to us
               BitcoinILOutPoint outPoint = new BitcoinILOutPoint(t.getId(), i);
               if (!unspentOutPoints.contains(outPoint)) {
                  // This output has been spent, subtract it from the amount sent
                  pendingSending -= output.value;
               }
            }
         }
      }

      return new BalanceSatoshis(confirmed, pendingReceiving, pendingSending, pendingChange, System.currentTimeMillis(),
              getBlockChainHeight(), true, _allowZeroConfSpending);
   }

   private TransactionOutput transform(BitcoinILTransactionOutputEx parent) {
      ScriptOutput script = ScriptOutput.fromScriptBytes(parent.script);
      return new TransactionOutput(parent.value, script);
   }

   /**
    * Broadcast outgoing transactions.
    * <p>
    * This method should only be called from the wallet manager
    *
    * @return false if synchronization failed due to failed blockchain
    * connection
    */
   @Override
   public synchronized boolean broadcastOutgoingTransactions() {
      checkNotArchived();
      List<BitcoinILSha256Hash> broadcastedIds = new LinkedList<>();
      Map<BitcoinILSha256Hash, byte[]> transactions = _backing.getOutgoingTransactions();

      int malformedTransactionsCount = 0;

      for (byte[] rawTransaction : transactions.values()) {
         BitcoinILTransaction transaction;
         try {
            transaction = BitcoinILTransaction.fromBytes(rawTransaction);
         } catch (BitcoinILTransactionParsingException e) {
            _logger.log(Level.SEVERE, "Unable to parse transaction from bytes: " + HexUtils.toHex(rawTransaction), e);
            return  false;
         }
         BroadcastResult result = broadcastTransaction(transaction);
         if (result.getResultType() == BroadcastResultType.SUCCESS) {
            broadcastedIds.add(transaction.getId());
            _backing.removeOutgoingTransaction(transaction.getId());
         } else if (result.getResultType() == BroadcastResultType.REJECT_MALFORMED) {
            malformedTransactionsCount++;
         }
      }

      if (malformedTransactionsCount > 0) {
         postEvent(Event.MALFORMED_OUTGOING_TRANSACTIONS_FOUND);
      }

      if (!broadcastedIds.isEmpty()) {
         onTransactionsBroadcasted(broadcastedIds);
      }
      return true;
   }

   @Override
   public BitcoinILTransactionEx getTransaction(BitcoinILSha256Hash txid) {
      return _backing.getTransaction(txid);
   }

   @Override
   public synchronized BroadcastResult broadcastTransaction(BitcoinILTransaction transaction) {
      checkNotArchived();
      try {
         WapiResponse<BroadcastBitcoinILTransactionResponse> response = _wapi.broadcastBitcoinILTransaction(
                 new BroadcastTransactionRequest(Wapi.VERSION, transaction.toBytes()));
         int errorCode = response.getErrorCode();
         if (errorCode == Wapi.ERROR_CODE_SUCCESS) {
            if (response.getResult().success) {
               markTransactionAsSpent(BitcoinILTransactionEx.fromUnconfirmedTransaction(transaction));
               postEvent(Event.BROADCASTED_TRANSACTION_ACCEPTED);
               return new BroadcastResult(BroadcastResultType.SUCCESS);
            } else {
               // This transaction was rejected must be double spend or
               // malleability, delete it locally.
               _logger.log(Level.SEVERE, "Failed to broadcast transaction due to a double spend or malleability issue");
               postEvent(Event.BROADCASTED_TRANSACTION_DENIED);
               return new BroadcastResult(BroadcastResultType.REJECT_DUPLICATE);
            }
         } else if (errorCode == Wapi.ERROR_CODE_NO_SERVER_CONNECTION) {
            postEvent(Event.SERVER_CONNECTION_ERROR);
            _logger.log(Level.SEVERE, "Server connection failed with ERROR_CODE_NO_SERVER_CONNECTION");
            return new BroadcastResult(BroadcastResultType.NO_SERVER_CONNECTION);
         } else if(errorCode == Wapi.ElectrumxError.REJECT_MALFORMED.getErrorCode()) {
            if (response.getErrorMessage().contains("min relay fee not met")) {
               return new BroadcastResult(response.getErrorMessage(), BroadcastResultType.REJECT_INSUFFICIENT_FEE);
            } else {
               return new BroadcastResult(response.getErrorMessage(), BroadcastResultType.REJECT_MALFORMED);
            }
         } else if(errorCode == Wapi.ElectrumxError.REJECT_DUPLICATE.getErrorCode()) {
            return new BroadcastResult(response.getErrorMessage(), BroadcastResultType.REJECT_DUPLICATE);
         } else if(errorCode == Wapi.ElectrumxError.REJECT_NONSTANDARD.getErrorCode()) {
            return new BroadcastResult(response.getErrorMessage(), BroadcastResultType.REJECT_NONSTANDARD);
         } else if(errorCode == Wapi.ElectrumxError.REJECT_INSUFFICIENT_FEE.getErrorCode()) {
            return new BroadcastResult(response.getErrorMessage(), BroadcastResultType.REJECT_INSUFFICIENT_FEE);
         } else {
            postEvent(Event.BROADCASTED_TRANSACTION_DENIED);
            _logger.log(Level.SEVERE, "Server connection failed with error: " + errorCode);
            return new BroadcastResult(BroadcastResultType.REJECTED);
         }
      } catch (WapiException e) {
         postEvent(Event.SERVER_CONNECTION_ERROR);
         _logger.log(Level.SEVERE, "Server connection failed with error code: " + e.errorCode, e);
         return new BroadcastResult(BroadcastResultType.NO_SERVER_CONNECTION);
      }
   }

   protected void checkNotArchived() {
      final String usingArchivedAccount = "Using archived account";
      if (isArchived()) {
         _logger.log(Level.SEVERE,usingArchivedAccount);
         throw new RuntimeException(usingArchivedAccount);
      }
   }

   @Override
   public abstract boolean isArchived();

   @Override
   public abstract boolean isActive();

   protected abstract void onNewTransaction(BitcoinILTransaction t);

   protected void onTransactionsBroadcasted(List<BitcoinILSha256Hash> txids) {
   }

   @Override
   public abstract boolean canSpend();

   @Override
   public List<com.mycelium.wapi.model.BitcoinILTransactionSummary> getTransactionHistory(int offset, int limit) {
      // Note that this method is not synchronized, and we might fetch the transaction history while synchronizing
      // accounts. That should be ok as we write to the DB in a sane order.

      List<com.mycelium.wapi.model.BitcoinILTransactionSummary> history = new ArrayList<>();
      checkNotArchived();
      List<BitcoinILTransactionEx> list = _backing.getTransactionHistory(offset, limit);
      for (BitcoinILTransactionEx tex : list) {
         com.mycelium.wapi.model.BitcoinILTransactionSummary item = transform(tex, getBlockChainHeight());
         if (item != null) {
            history.add(item);
         }
      }
      return history;
   }

   @Override
   public abstract int getBlockChainHeight();

   protected abstract void setBlockChainHeight(int blockHeight);

   @Override
   public BitcoinILTransaction signTransaction(UnsignedTransaction unsigned, KeyCipher cipher)
           throws InvalidKeyCipher {
      checkNotArchived();
      if (!isValidEncryptionKey(cipher)) {
         throw new InvalidKeyCipher();
      }
      // Make all signatures, this is the CPU intensive part
      List<byte[]> signatures = StandardBitcoinILTransactionBuilder.generateSignatures(
              unsigned.getSigningRequests(),
              new PrivateKeyRingIL(cipher)
      );

      // Apply signatures and finalize transaction
      return StandardBitcoinILTransactionBuilder.finalizeTransaction(unsigned, signatures);
   }

   private synchronized void queueTransaction(BitcoinILTransactionEx transaction) {
      // Store transaction in outgoing buffer, so we can broadcast it
      // later
      byte[] rawTransaction = transaction.binary;
      _backing.putOutgoingTransaction(transaction.txid, rawTransaction);
      markTransactionAsSpent(transaction);
   }

   @Override
   public synchronized boolean deleteTransaction(BitcoinILSha256Hash transactionId) {
      BitcoinILTransactionEx tex = _backing.getTransaction(transactionId);
      if (tex == null) {
         return false;
      }
      BitcoinILTransaction tx = BitcoinILTransactionEx.toTransaction(tex);
      _backing.beginTransaction();
      try {
         // See if any of the outputs are stored locally and remove them
         for (int i = 0; i < tx.outputs.length; i++) {
            BitcoinILOutPoint outPoint = new BitcoinILOutPoint(tx.getId(), i);
            BitcoinILTransactionOutputEx utxo = _backing.getUnspentOutput(outPoint);
            if (utxo != null) {
               _backing.deleteUnspentOutput(outPoint);
            }
         }
         // remove it from the accountBacking
         _backing.deleteTransaction(transactionId);
         _backing.setTransactionSuccessful();
      } finally {
         _backing.endTransaction();
      }
      updateLocalBalance(); //will still need a new sync besides re-calculating
      return true;
   }

   @Override
   public void removeAllQueuedTransactions() {
      Map<BitcoinILSha256Hash, byte[]> outgoingTransactions = _backing.getOutgoingTransactions();

      for(BitcoinILSha256Hash key : outgoingTransactions.keySet()) {
         cancelQueuedTransaction(key);
      }
   }

   @Override
   public synchronized boolean cancelQueuedTransaction(BitcoinILSha256Hash transaction) {
      Map<BitcoinILSha256Hash, byte[]> outgoingTransactions = _backing.getOutgoingTransactions();

      if (!outgoingTransactions.containsKey(transaction)) {
         return false;
      }

      BitcoinILTransaction tx;
      try {
         tx = BitcoinILTransaction.fromBytes(outgoingTransactions.get(transaction));
      } catch (BitcoinILTransactionParsingException e) {
         return false;
      }

      _backing.beginTransaction();
      try {

         // See if any of the outputs are stored locally and remove them
         for (int i = 0; i < tx.outputs.length; i++) {
            BitcoinILOutPoint outPoint = new BitcoinILOutPoint(tx.getId(), i);
            BitcoinILTransactionOutputEx utxo = _backing.getUnspentOutput(outPoint);
            if (utxo != null) {
               _backing.deleteUnspentOutput(outPoint);
            }
         }

         // Remove a queued transaction from our outgoing buffer
         _backing.removeOutgoingTransaction(transaction);

         // remove it from the accountBacking
         _backing.deleteTransaction(transaction);
         _backing.setTransactionSuccessful();
      } finally {
         _backing.endTransaction();
      }

      // calc the new balance to remove the outgoing amount
      // the total balance will still be wrong, as we already deleted some UTXOs to build the queued transaction
      // these will get restored after the next sync
      updateLocalBalance();

      //markTransactionAsSpent(transaction);
      return true;
   }

   @Override
   public void queueTransaction(@NotNull Transaction transaction) {
      byte[] txBytes = transaction.txBytes();
      int now = (int) (System.currentTimeMillis() / 1000);
      try {
         BitcoinILTransaction btcTx = BitcoinILTransaction.fromBytes(txBytes);
         BitcoinILTransactionEx tex = new BitcoinILTransactionEx(btcTx.getId(), btcTx.getHash(), -1, now, txBytes);
         queueTransaction(tex);
      } catch (BitcoinILTransactionParsingException e) {
         _logger.log(Level.INFO,String.format("Unable to parse transaction %s: %s", HexUtils.toHex(transaction.getId()), e.getMessage()));
      }
   }

   private void markTransactionAsSpent(BitcoinILTransactionEx transaction) {
      _backing.beginTransaction();
      final BitcoinILTransaction parsedTransaction;
      try {
         parsedTransaction = BitcoinILTransaction.fromBytes(transaction.binary);
      } catch (BitcoinILTransactionParsingException e) {
         _logger.log(Level.INFO,String.format("Unable to parse transaction %s: %s", transaction.txid, e.getMessage()));
         return;
      }
      try {
         // Remove inputs from unspent, marking them as spent
         for (TransactionInput input : parsedTransaction.inputs) {
            BitcoinILTransactionOutputEx parentOutput = _backing.getUnspentOutput(input.outPoint);
            if (parentOutput != null) {
               _backing.deleteUnspentOutput(input.outPoint);
               _backing.putParentTransactionOutput(parentOutput);
            }
         }

         // See if any of the outputs are for ourselves and store them as
         // unspent
         for (int i = 0; i < parsedTransaction.outputs.length; i++) {
            TransactionOutput output = parsedTransaction.outputs[i];
            if (isMine(output.script)) {
               _backing.putUnspentOutput(new BitcoinILTransactionOutputEx(new BitcoinILOutPoint(parsedTransaction.getId(), i), -1,
                       output.value, output.script.getScriptBytes(), false));
            }
         }

         // Store transaction locally, so we have it in our history and don't
         // need to fetch it in a minute
         _backing.putTransaction(transaction);
         _backing.setTransactionSuccessful();
      } finally {
         _backing.endTransaction();
      }

      // Tell account that we have a new transaction
      onNewTransaction(parsedTransaction);

      // Calculate local balance cache. It has changed because we have done
      // some spending
      updateLocalBalance();
      persistContextIfNecessary();
   }

   protected abstract void persistContextIfNecessary();

   @Override
   public NetworkParameters getNetwork() {
      return _network;
   }

   // TODO: 07.10.17 these values are subject to change and not a solid way to detect cc outputs.
   public static final int COLU_MAX_DUST_OUTPUT_SIZE_TESTNET = 600;
   public static final int COLU_MAX_DUST_OUTPUT_SIZE_MAINNET = 10000;

   //Retrieves indexes of colu outputs if the transaction is determined to be colu transaction
   //In the case of non-colu transaction returns empty list
   private List<Integer> getColuOutputIndexes(BitcoinILTransaction tx) throws ParseException {
      if (tx == null) {
         return new ArrayList<>();
      }
      for(int i = 0 ; i < tx.outputs.length;i++) {
         TransactionOutput curOutput = tx.outputs[i];
         byte[] scriptBytes = curOutput.script.getScriptBytes();
         //Check the protocol identifier 0x4343 ASCII representation of the string CC ("Colored Coins")
         if (curOutput.value == 0 && coluTransferInstructionsParser.isValidColuScript(scriptBytes)) {
            List<Integer> indexesList = coluTransferInstructionsParser.retrieveOutputIndexesFromScript(scriptBytes);
            //Since all assets with remaining amounts are automatically transferred to the last output,
            //add the last output to indexes list.
            //At least CC transaction could consist of have two outputs if it has no change - dust output that represents
            //transferred assets value and an empty output containing OP_RETURN data.
            //If the CC transaction has the change to transfer, it will be represented at least as the third dust output
            if (tx.outputs.length > 2) {
               indexesList.add(tx.outputs.length - 1);
            }
            return indexesList;
         }
      }

      return new ArrayList<>();
   }

   private boolean isColuTransaction(BitcoinILTransaction tx) {
      try {
         return !getColuOutputIndexes(tx).isEmpty();
      } catch (ParseException e) {
         // the current only use case is safe to be treated as not colored-coin even though we might misinterpret a colored-coin script.
         return false;
      }
   }

   private boolean isColuDustOutput(BitcoinILTransactionOutputEx output) {
      BitcoinILTransaction transaction = BitcoinILTransactionEx.toTransaction(_backing.getTransaction(output.outPoint.txid));
      try {
         if (getColuOutputIndexes(transaction).contains(output.outPoint.index)) {
            return true;
         }
      } catch (ParseException e) {
         // better safe than sorry:
         // if we can't interpret the script, we assume it is a colore coin output as before introducing the script interpretation.
         // usually we can read the script, so bigger colored coins txos get interpreted as such and smaller utxos are spendable
         int coluDustOutputSize = _network.isTestnet() ? COLU_MAX_DUST_OUTPUT_SIZE_TESTNET : COLU_MAX_DUST_OUTPUT_SIZE_MAINNET;
         if (output.value <= coluDustOutputSize) {
            return true;
         }
      }
      return false;
   }

   protected Collection<BitcoinILTransactionOutputEx> getSpendableOutputs(long minerFeePerKbToUse) {
      return getSpendableOutputs(minerFeePerKbToUse, false);
   }

   /**
    * @param minerFeePerKbToUse Determines the dust level, at which including a UTXO costs more than it is worth.
    * @return all UTXOs that are spendable now, as they are neither locked coinbase outputs nor unconfirmed received coins if _allowZeroConfSpending is not set nor dust.
    */
   private Collection<BitcoinILTransactionOutputEx> getSpendableOutputs(long minerFeePerKbToUse, boolean skipDustCheck) {
      long satDustOutput = StandardBitcoinILTransactionBuilder.MAX_INPUT_SIZE * minerFeePerKbToUse / 1000;
      Collection<BitcoinILTransactionOutputEx> allUnspentOutputs = _backing.getAllUnspentOutputs();

      // Prune confirmed outputs for coinbase outputs that are not old enough
      // for spending. Also prune unconfirmed receiving coins except for change
      Iterator<BitcoinILTransactionOutputEx> it = allUnspentOutputs.iterator();
      while (it.hasNext()) {
         BitcoinILTransactionOutputEx output = it.next();
         // we remove all outputs that don't cover their costs (dust)
         // coinbase outputs are not spendable and this should not be overridden
         // Unless we allow zero confirmation spending we prune all unconfirmed outputs sent from foreign addresses
         if (!skipDustCheck && output.value < satDustOutput ||
                 output.isCoinBase && getBlockChainHeight() - output.height < COINBASE_MIN_CONFIRMATIONS ||
                 !_allowZeroConfSpending && output.height == -1 && !isFromMe(output.outPoint.txid)) {
            it.remove();
         } else {
            if (isColuDustOutput(output)) {
               it.remove();
            }
         }
      }
      return allUnspentOutputs;
   }

   protected abstract BitcoinILAddress getChangeAddress(BitcoinILAddress destinationAddress);

   public abstract BitcoinILAddress getChangeAddress();

   protected abstract BitcoinILAddress getChangeAddress(List<BitcoinILAddress> destinationAddresses);

   private static Collection<UnspentTransactionOutput> transform(Collection<BitcoinILTransactionOutputEx> source) {
      List<UnspentTransactionOutput> outputs = new ArrayList<>();
      for (BitcoinILTransactionOutputEx s : source) {
         ScriptOutput script = ScriptOutput.fromScriptBytes(s.script);
         outputs.add(new UnspentTransactionOutput(s.outPoint, s.height, s.value, script));
      }
      return outputs;
   }

   @Override
   public synchronized Value calculateMaxSpendableAmount(Value minerFeePerKbToUse, BtcILAddress destinationAddress) {

      BitcoinILAddress destAddress = destinationAddress != null ? destinationAddress.getAddress() : null;

      checkNotArchived();
      Collection<UnspentTransactionOutput> spendableOutputs = transform(getSpendableOutputs(minerFeePerKbToUse.getValueAsLong()));
      long satoshis = 0;

      // sum up the maximal available number of satoshis (i.e. sum of all spendable outputs)
      for (UnspentTransactionOutput output : spendableOutputs) {
         satoshis += output.value;
      }

      // TODO: 25.06.17 the following comment was justifying to assume two outputs, which might wrongly lead to no spendable funds or am I reading the wrongly? I assume one output only for the max.
      // we will use all of the available inputs and it will be only one output
      // but we use "2" here, because the tx-estimation in StandardBitcoinILTransactionBuilder always includes an
      // output into its estimate - so add one here too to arrive at the same tx fee
      FeeEstimatorBuilder estimatorBuilder = new FeeEstimatorBuilder().setArrayOfInputs(spendableOutputs)
              .setMinerFeePerKb(minerFeePerKbToUse.getValueAsLong());
      addOutputToEstimation(destAddress, estimatorBuilder);
      FeeEstimator estimator = estimatorBuilder.createFeeEstimator();
      long feeToUse = estimator.estimateFee();

      satoshis -= feeToUse;
      if (satoshis <= 0) {
         return Value.zeroValue(_network.isProdnet() ? BitcoinILMain.get() : BitcoinILTest.get());
      }

      // Create transaction builder
      StandardBitcoinILTransactionBuilder stb = new StandardBitcoinILTransactionBuilder(_network);

      AddressType destinationAddressType;
      if (destinationAddress != null) {
         destinationAddressType = destinationAddress.getType();
      } else {
         destinationAddressType = AddressType.P2PKH;
      }
      // Try and add the output
      try {
         // Note, null address used here, we just use it for measuring the transaction size
         stb.addOutput(BitcoinILAddress.getNullAddress(_network, destinationAddressType), satoshis);
      } catch (BTCILOutputTooSmallException e1) {
         // The amount we try to send is lower than what the network allows
         return Value.zeroValue(_network.isProdnet() ? BitcoinILMain.get() : BitcoinILTest.get());
      }

      // Try to create an unsigned transaction
      try {
         stb.createUnsignedTransaction(spendableOutputs, getChangeAddress(BitcoinILAddress.getNullAddress(_network, destinationAddressType)),
                 new PublicKeyRingIL(), _network, minerFeePerKbToUse.getValueAsLong());
         // We have enough to pay the fees, return the amount as the maximum
         return Value.valueOf(_network.isProdnet() ? BitcoinILMain.get() : BitcoinILTest.get(), satoshis);
      } catch (InsufficientBTCILException | StandardBitcoinILTransactionBuilder.UnableToBuildTransactionException e) {
         return Value.zeroValue(_network.isProdnet() ? BitcoinILMain.get() : BitcoinILTest.get());
      }
   }

   private void addOutputToEstimation(BitcoinILAddress outputAddress, FeeEstimatorBuilder estimatorBuilder) {
      AddressType type = outputAddress != null ? outputAddress.getType() : AddressType.P2PKH;
      estimatorBuilder.addOutput(type);
   }

   protected abstract InMemoryPrivateKey getBitcoinILPrivateKey(PublicKey publicKey, KeyCipher cipher)
           throws InvalidKeyCipher;

   protected abstract InMemoryPrivateKey getPrivateKeyForAddress(BitcoinILAddress address, KeyCipher cipher)
           throws InvalidKeyCipher;

   public abstract List<AddressType> getAvailableAddressTypes();

   public abstract BitcoinILAddress getReceivingAddress(AddressType addressType);

   public abstract void setDefaultAddressType(AddressType addressType);

   protected abstract PublicKey getPublicKeyForAddress(BitcoinILAddress address);

   @Override
   public synchronized UnsignedTransaction createUnsignedTransaction(List<BtcILReceiver> receivers, long minerFeeToUse)
           throws BTCILOutputTooSmallException, InsufficientBTCILException, StandardBitcoinILTransactionBuilder.UnableToBuildTransactionException {
      checkNotArchived();

      // Determine the list of spendable outputs
      Collection<UnspentTransactionOutput> spendable = transform(getSpendableOutputs(minerFeeToUse));

      // Create the unsigned transaction
      StandardBitcoinILTransactionBuilder stb = new StandardBitcoinILTransactionBuilder(_network);
      List<BitcoinILAddress> addressList = new ArrayList<>();
      for (BtcILReceiver receiver : receivers) {
         stb.addOutput(receiver.address, receiver.amount);
         addressList.add(receiver.address);
      }
      BitcoinILAddress changeAddress = getChangeAddress(addressList);
      return stb.createUnsignedTransaction(spendable, changeAddress, new PublicKeyRingIL(),
              _network, minerFeeToUse);
   }

   @Override
   public UnsignedTransaction createUnsignedTransaction(OutputList outputs, long minerFeeToUse) throws BTCILOutputTooSmallException, InsufficientBTCILException, StandardBitcoinILTransactionBuilder.UnableToBuildTransactionException {
      checkNotArchived();

      // Determine the list of spendable outputs
      Collection<UnspentTransactionOutput> spendable = transform(getSpendableOutputs(minerFeeToUse));

      // Create the unsigned transaction
      StandardBitcoinILTransactionBuilder stb = new StandardBitcoinILTransactionBuilder(_network);
      stb.addOutputs(outputs);
      BitcoinILAddress changeAddress = getChangeAddress();
      return stb.createUnsignedTransaction(spendable, changeAddress, new PublicKeyRingIL(),
              _network, minerFeeToUse);
   }

   /**
    * Create a new, unsigned transaction that spends from a UTXO of the provided transaction.
    * @see WalletBtcILAccount#createUnsignedTransaction(List, long)
    *
    * @param txid transaction to spend from
    * @param minerFeeToUse fee to use to pay up for txid and the new transaction
    * @param satoshisPaid amount already paid by parent transaction
    */
   public UnsignedTransaction createUnsignedCPFPTransaction(BitcoinILSha256Hash txid, long minerFeeToUse, long satoshisPaid) throws InsufficientBTCILException, StandardBitcoinILTransactionBuilder.UnableToBuildTransactionException {
      checkNotArchived();
      List<UnspentTransactionOutput> utxos = new ArrayList<>(transform(getSpendableOutputs(minerFeeToUse, true)));
      BtcILTransactionDetails parent = getTransactionDetails(txid);
      long totalSpendableSatoshis = 0;
      boolean haveOutputToBump = false;
      List<UnspentTransactionOutput> utxosToSpend = new ArrayList<>();
      for (UnspentTransactionOutput utxo : utxos) {
         if (utxo.outPoint.txid.equals(txid)) {
            // moving the bumpable UTXO to the beginning for the transaction to be built:
            utxos.remove(utxo);
            utxos.add(0, utxo);
            haveOutputToBump = true;
            break;
         }
      }
      if (!haveOutputToBump) {
         throw new StandardBitcoinILTransactionBuilder.UnableToBuildTransactionException("We have no UTXO");
      }
      BitcoinILAddress changeAddress = getChangeAddress();
      long parentChildFeeSat;
      FeeEstimatorBuilder builder = new FeeEstimatorBuilder().setArrayOfInputs(utxosToSpend);
      addOutputToEstimation(changeAddress, builder);
      long childSize = builder.createFeeEstimator().estimateTransactionSize();
      long parentChildSize = parent.rawSize + childSize;
      parentChildFeeSat = parentChildSize * minerFeeToUse / 1000 - satoshisPaid;
      if (parentChildFeeSat < childSize * minerFeeToUse / 1000) {
         // if child doesn't get itself to target priority, it's not needed to boost a parent to it.
         throw new StandardBitcoinILTransactionBuilder.UnableToBuildTransactionException("parent needs no boosting");
      }
      do {
         UnspentTransactionOutput utxo = utxos.remove(0);
         utxosToSpend.add(utxo);
         totalSpendableSatoshis += utxo.value;
         builder = new FeeEstimatorBuilder().setArrayOfInputs(utxosToSpend);
         addOutputToEstimation(changeAddress, builder);
         childSize = builder.createFeeEstimator().estimateTransactionSize();
         parentChildSize = parent.rawSize + childSize;
         parentChildFeeSat = parentChildSize * minerFeeToUse / 1000 - satoshisPaid;
         long value = totalSpendableSatoshis - parentChildFeeSat;
         if (value >= MINIMUM_OUTPUT_VALUE) {
            List<TransactionOutput> outputs = singletonList(createOutput(changeAddress, value, _network));
            return new UnsignedTransaction(outputs, utxosToSpend, new PublicKeyRingIL(), _network, 0, UnsignedTransaction.NO_SEQUENCE);
         }
      } while (!utxos.isEmpty());
      throw new InsufficientBTCILException(0, parentChildFeeSat);
   }

   @Override
   public BalanceSatoshis getBalance() {
      // public method that needs no synchronization
      checkNotArchived();
      // We make a copy of the reference for a reason. Otherwise the balance
      // might change right when we make a copy
      BalanceSatoshis b = _cachedBalance;
      return b != null ? new BalanceSatoshis(b.confirmed, b.pendingReceiving, b.pendingSending, b.pendingChange, b.updateTime,
              b.blockHeight, isSyncing(), b.allowsZeroConfSpending)
              : new BalanceSatoshis(0, 0, 0, 0, 0, 0, isSyncing(), false);
   }

   @Override
   public CurrencyBasedBalance getCurrencyBasedBalance() {
      BalanceSatoshis balance = getBalance();
      long spendableBalance = balance.getSpendableBalance();
      long sendingBalance = balance.getSendingBalance();
      long receivingBalance = balance.getReceivingBalance();

      if (spendableBalance < 0) {
         throw new IllegalArgumentException(String.format(Locale.getDefault(), "spendableBalance < 0: %d; account: %s", spendableBalance, this.getClass().toString()));
      }
      if (sendingBalance < 0) {
         sendingBalance = 0;
      }
      if (receivingBalance < 0) {
         receivingBalance = 0;
      }

      ExactCurrencyValue confirmed = ExactBitcoinValue.from(spendableBalance);
      ExactCurrencyValue sending = ExactBitcoinValue.from(sendingBalance);
      ExactCurrencyValue receiving = ExactBitcoinValue.from(receivingBalance);
      return new CurrencyBasedBalance(confirmed, sending, receiving);
   }

   /**
    * Update the balance by summing up the unspent outputs in local persistence.
    *
    * @return true if the balance changed, false otherwise
    */
   protected boolean updateLocalBalance() {
      BalanceSatoshis balance = calculateLocalBalance();
      if (!balance.equals(_cachedBalance)) {
         _cachedBalance = balance;
         postEvent(Event.BALANCE_CHANGED);
         return true;
      }
      return false;
   }

   private com.mycelium.wapi.model.BitcoinILTransactionSummary transform(BitcoinILTransactionEx tex, int blockChainHeight) {
      BitcoinILTransaction tx;
      try {
         tx = BitcoinILTransaction.fromByteReader(new ByteReader(tex.binary));
      } catch (BitcoinILTransactionParsingException e) {
         // Should not happen as we have parsed the transaction earlier
         _logger.log(Level.SEVERE, "Unable to parse ");
         return null;
      }

      boolean isColuTransaction = isColuTransaction(tx);

      if (isColuTransaction) {
         return null;
      }

      // Outputs
      long satoshis = 0;
      List<BitcoinILAddress> toAddresses = new ArrayList<>();
      BitcoinILAddress destAddress = null;
      for (TransactionOutput output : tx.outputs) {
         final BitcoinILAddress address = output.script.getAddress(_network);
         if (isMine(output.script)) {
            satoshis += output.value;
         } else {
            destAddress = address;
         }
         if (address != null && !address.equals(BitcoinILAddress.getNullAddress(_network))) {
            toAddresses.add(address);
         }
      }

      // Inputs
      if (!tx.isCoinbase()) {
         for (TransactionInput input : tx.inputs) {
            // find parent output
            BitcoinILTransactionOutputEx funding = _backing.getParentTransactionOutput(input.outPoint);
            if (funding == null) {
               funding = _backing.getUnspentOutput(input.outPoint);
            }
            if (funding == null) {
               continue;
            }
            if (isMine(funding)) {
               satoshis -= funding.value;
            }
         }
      }
      // else {
      //    For coinbase transactions there is nothing to subtract
      // }
      int confirmations;
      if (tex.height == -1) {
         confirmations = 0;
      } else {
         confirmations = Math.max(0, blockChainHeight - tex.height + 1);
      }

      // only track a destinationAddress if it is an outgoing transaction (i.e. send money to someone)
      // to prevent the user that he tries to return money to an address he got bitcoin from.
      if (satoshis >= 0) {
         destAddress = null;
      }

      boolean isQueuedOutgoing = _backing.isOutgoingTransaction(tx.getId());

      // see if we have a riskAssessment for this tx available in memory (i.e. valid for last sync)
      final ConfirmationRiskProfileLocal risk = riskAssessmentForUnconfirmedTx.get(tx.getId());

      return new com.mycelium.wapi.model.BitcoinILTransactionSummary(
              tx.getId(),
              ExactBitcoinValue.from(Math.abs(satoshis)),
              satoshis >= 0,
              tex.time,
              tex.height,
              confirmations,
              isQueuedOutgoing,
              risk,
              com.google.common.base.Optional.fromNullable(destAddress),
              toAddresses);
   }

   @Override
   public List<BitcoinILTransactionOutputSummary> getUnspentTransactionOutputSummary() {
      // Note that this method is not synchronized, and we might fetch the transaction history while synchronizing
      // accounts. That should be ok as we write to the DB in a sane order.

      // Get all unspent outputs for this account
      Collection<BitcoinILTransactionOutputEx> outputs = _backing.getAllUnspentOutputs();

      // Transform it to a list of summaries
      List<BitcoinILTransactionOutputSummary> list = new ArrayList<>();
      for (BitcoinILTransactionOutputEx output : outputs) {

         ScriptOutput script = ScriptOutput.fromScriptBytes(output.script);
         BitcoinILAddress address;
         if (script == null) {
            address = BitcoinILAddress.getNullAddress(_network);
            // This never happens as we have parsed this script before
         } else {
            address = script.getAddress(_network);
         }
         int confirmations;
         if (output.height == -1) {
            confirmations = 0;
         } else {
            confirmations = Math.max(0, getBlockChainHeight() - output.height + 1);
         }

         BitcoinILTransactionOutputSummary summary = new BitcoinILTransactionOutputSummary(output.outPoint, output.value, output.height, confirmations, address);
         list.add(summary);
      }
      // Sort & return
      Collections.sort(list);
      return list;
   }

   protected boolean monitorYoungTransactions() {
      Collection<BitcoinILTransactionEx> list = _backing.getYoungTransactions(5, getBlockChainHeight());
      if (list.isEmpty()) {
         return true;
      }
      List<BitcoinILSha256Hash> txids = new ArrayList<>(list.size());
      for (BitcoinILTransactionEx tex : list) {
         txids.add(tex.txid);
      }
      CheckBitcoinILTransactionsResponse result;
      try {
         result = _wapi.checkBitcoinILTransactions(new CheckBitcoinILTransactionsRequest(txids)).getResult();
      } catch (WapiException e) {
         postEvent(Event.SERVER_CONNECTION_ERROR);
         _logger.log(Level.SEVERE, "Server connection failed with error code: " + e.errorCode, e);
         // We failed to check transactions
         return false;
      }
      for (BitcoinILTransactionStatus t : result.transactions) {
         BitcoinILTransactionEx localTransactionEx = _backing.getTransaction(t.txid);
         BitcoinILTransaction parsedTransaction;
         if (localTransactionEx != null) {
            try {
               parsedTransaction = BitcoinILTransaction.fromBytes(localTransactionEx.binary);
            } catch (BitcoinILTransactionParsingException ignore) {
               parsedTransaction = null;
            }
         } else {
            parsedTransaction = null;
         }

         // check if this transaction is unconfirmed and spends any inputs that got already spend
         // by any other transaction we know
         boolean isDoubleSpend = false;
         if (parsedTransaction != null && localTransactionEx.height == -1) {
            for (TransactionInput input : parsedTransaction.inputs) {
               Collection<BitcoinILSha256Hash> otherTx = _backing.getTransactionsReferencingOutPoint(input.outPoint);
               // remove myself
               otherTx.remove(parsedTransaction.getId());
               if (!otherTx.isEmpty()) {
                  isDoubleSpend = true;
               }
            }
         }

         // if this transaction summary has a risk assessment set, remember it
         if (t.rbfRisk || t.unconfirmedChainLength > 0 || isDoubleSpend) {
            riskAssessmentForUnconfirmedTx.put(t.txid, new ConfirmationRiskProfileLocal(t.unconfirmedChainLength, t.rbfRisk, isDoubleSpend));
         } else {
            // otherwise just remove it if we ever got one
            riskAssessmentForUnconfirmedTx.remove(t.txid);
         }

         // does the server know anything about this tx?
         if (!t.found) {
            if (localTransactionEx != null) {
               // We have a transaction locally that did not get reported back by the server
               // put it into the outgoing queue and mark it as "not transmitted" (even as it might be an incoming tx)
               queueTransaction(localTransactionEx);
            } else {
               // we haven't found it locally (shouldn't happen here) - so delete it to be sure
               _backing.deleteTransaction(t.txid);
            }
            continue;
         } else {
            // we got it back from the server and it got confirmations - remove it from out outgoing queue
            if (t.height > -1 || _backing.isOutgoingTransaction(t.txid)) {
               _backing.removeOutgoingTransaction(t.txid);
            }
         }

         // update the local transaction
         if (localTransactionEx != null && (localTransactionEx.height != t.height)) {
            // The transaction got a new height. There could be
            // several reasons for that. It confirmed, or might also be a reorg.
            BitcoinILTransactionEx newTex = new BitcoinILTransactionEx(localTransactionEx.txid, localTransactionEx.hash, t.height, localTransactionEx.time, localTransactionEx.binary);
            _logger.log(Level.INFO, String.format("Replacing: %s With: %s", localTransactionEx.toString(), newTex.toString()));
            _backing.putTransaction(newTex);
            postEvent(Event.TRANSACTION_HISTORY_CHANGED);
         }
      }
      return true;
   }

   // local cache for received risk assessments for unconfirmed transactions - does not get persisted in the db
   protected HashMap<BitcoinILSha256Hash, ConfirmationRiskProfileLocal> riskAssessmentForUnconfirmedTx = new HashMap<>();

   public class PublicKeyRingIL implements IPublicKeyRing {
      @Override
      public PublicKey findPublicKeyByAddress(BitcoinILAddress address) {
         PublicKey publicKey = getPublicKeyForAddress(address);
         if (publicKey != null) {
            if (address.getType() == AddressType.P2SH_P2WPKH
                    || address.getType() == AddressType.P2WPKH) {
               return new PublicKey(publicKey.getPubKeyCompressed());
            }
            return publicKey;
         }
         // something unexpected happened - the account might be in a undefined state
         // drop local cached data (transaction history, addresses - metadata will be kept)
         dropCachedData();

         // let the app crash anyway, so that we get notified. after restart it should resync the account completely
         throw new RuntimeException(String.format("Unable to find public key for address %s acc:%s", address.toString(), AbstractBtcILAccount.this.getClass().toString()));
      }
   }

   public class PrivateKeyRingIL extends PublicKeyRingIL implements IPublicKeyRing, IPrivateKeyRing {

      KeyCipher _cipher;

      public PrivateKeyRingIL(KeyCipher cipher) {
         _cipher = cipher;
      }

      @Override
      public BitcoinILSigner findSignerByPublicKey(PublicKey publicKey) {
         InMemoryPrivateKey privateKey;
         try {
            privateKey = getBitcoinILPrivateKey(publicKey, _cipher);
         } catch (InvalidKeyCipher e) {
            throw new RuntimeException("Unable to decrypt private key for public key " + publicKey.toString());
         }
         if (privateKey != null) {
            return privateKey;
         }
         throw new RuntimeException("Unable to find private key for public key " + publicKey.toString());
      }
   }

   @Override
   public com.mycelium.wapi.model.BitcoinILTransactionSummary getTransactionSummary(BitcoinILSha256Hash txid) {
      BitcoinILTransactionEx tx = _backing.getTransaction(txid);
      return transform(tx, tx.height);
   }

   @Override
   public BtcILTransactionDetails getTransactionDetails(BitcoinILSha256Hash txid) {
      // Note that this method is not synchronized, and we might fetch the transaction history while synchronizing
      // accounts. That should be ok as we write to the DB in a sane order.

      BitcoinILTransactionEx tex = _backing.getTransaction(txid);
      BitcoinILTransaction tx = BitcoinILTransactionEx.toTransaction(tex);
      if (tx == null) {
         throw new RuntimeException();
      }

      List<BtcILTransactionDetails.Item> inputs = new ArrayList<>(tx.inputs.length);
      if (tx.isCoinbase()) {
         // We have a coinbase transaction. Create one input with the sum of the outputs as its value,
         // and make the address the null address
         long value = 0;
         for (TransactionOutput out : tx.outputs) {
            value += out.value;
         }
         inputs.add(new BtcILTransactionDetails.Item(BitcoinILAddress.getNullAddress(_network), value, true));
      } else {
         // Populate the inputs
         for (TransactionInput input : tx.inputs) {
            // Get the parent transaction
            BitcoinILTransactionOutputEx parentOutput = _backing.getParentTransactionOutput(input.outPoint);
            if (parentOutput == null) {
               // We never heard about the parent, skip
               continue;
            }
            // Determine the parent address
            BitcoinILAddress parentAddress;
            ScriptOutput parentScript = ScriptOutput.fromScriptBytes(parentOutput.script);
            if (parentScript == null) {
               // Null address means we couldn't figure out the address, strange script
               parentAddress = BitcoinILAddress.getNullAddress(_network);
            } else {
               parentAddress = parentScript.getAddress(_network);
            }
            inputs.add(new BtcILTransactionDetails.Item(parentAddress, parentOutput.value, false));
         }
      }
      // Populate the outputs
      BtcILTransactionDetails.Item[] outputs = new BtcILTransactionDetails.Item[tx.outputs.length];
      for (int i = 0; i < tx.outputs.length; i++) {
         BitcoinILAddress address = tx.outputs[i].script.getAddress(_network);
         outputs[i] = new BtcILTransactionDetails.Item(address, tx.outputs[i].value, false);
      }

      return new BtcILTransactionDetails(
              txid, tex.height, tex.time,
              inputs.toArray(new BtcILTransactionDetails.Item[inputs.size()]), outputs,
              tx.vsize()
      );
   }

   public UnsignedTransaction createUnsignedPop(BitcoinILSha256Hash txid, byte[] nonce) {
      checkNotArchived();

      try {
         BitcoinILTransactionEx txExToProve = _backing.getTransaction(txid);
         BitcoinILTransaction txToProve = BitcoinILTransaction.fromByteReader(new ByteReader(txExToProve.binary));

         List<UnspentTransactionOutput> funding = new ArrayList<>(txToProve.inputs.length);
         for (TransactionInput input : txToProve.inputs) {
            BitcoinILTransactionEx inTxEx = _backing.getTransaction(input.outPoint.txid);
            BitcoinILTransaction inTx = BitcoinILTransaction.fromByteReader(new ByteReader(inTxEx.binary));
            UnspentTransactionOutput unspentOutput = new UnspentTransactionOutput(input.outPoint, inTxEx.height,
                    inTx.outputs[input.outPoint.index].value,
                    inTx.outputs[input.outPoint.index].script);

            funding.add(unspentOutput);
         }

         TransactionOutput popOutput = createPopOutput(txid, nonce);

         PopBuilder popBuilder = new PopBuilder(_network);

         return popBuilder.createUnsignedPop(singletonList(popOutput), funding,
                 new PublicKeyRingIL(), _network);
      } catch (BitcoinILTransactionParsingException e) {
         throw new RuntimeException("Cannot parse transaction: " + e.getMessage(), e);
      }
   }

   @Override
   public List<TransactionSummary> getTransactionsSince(long receivingSince) {
      List<TransactionSummary> history = new ArrayList<>();
      checkNotArchived();
      List<BitcoinILTransactionEx> list = _backing.getTransactionsSince(receivingSince);
      for (BitcoinILTransactionEx tex : list) {
         TransactionSummary tx = getTxSummary(tex.txid.getBytes());
         if(tx != null) {
            history.add(tx);
         }
      }
      return history;
   }

   public List<TransactionSummary> getTransactionSummaries(int offset, int limit) {
      // Note that this method is not synchronized, and we might fetch the transaction history while synchronizing
      // accounts. That should be ok as we write to the DB in a sane order.

      checkNotArchived();
      List<BitcoinILTransactionEx> list = _backing.getTransactionHistory(offset, limit);
      List<TransactionSummary> history = new ArrayList<>();
      for (BitcoinILTransactionEx tex: list) {
         TransactionSummary tx = getTxSummary(tex.txid.getBytes());
         if(tx != null) {
            history.add(tx);
         }
      }
      return history;
   }

   @Override
   public TransactionSummary getTxSummary(byte[] transactionId){
      checkNotArchived();
      BitcoinILTransactionEx tex = _backing.getTransaction(BitcoinILSha256Hash.of(transactionId));
      BitcoinILTransaction tx = BitcoinILTransactionEx.toTransaction(tex);
      if (tx == null) {
         return null;
      }

      long satoshisReceived = 0;
      long satoshisSent = 0;
      long satoshisTransferred = 0;

      List<Address> destinationAddresses = new ArrayList<>();

      ArrayList<OutputViewModel> outputs = new ArrayList<>();
      for (TransactionOutput output : tx.outputs) {
         BitcoinILAddress address = output.script.getAddress(_network);
         if (isMine(output.script)) {
            satoshisTransferred += output.value;
         } else {
            destinationAddresses.add(AddressUtils.fromBitcoinILAddress(address));
         }
         satoshisReceived += output.value;

         if (address != null && !address.equals(BitcoinILAddress.getNullAddress(_network))) {
            outputs.add(new OutputViewModel(AddressUtils.fromBitcoinILAddress(address), Value.valueOf(getCoinType(), output.value), false));
         }
      }
      ArrayList<InputViewModel> inputs = new ArrayList<>(); //need to create list of outputs

      // Inputs
      if (tx.isCoinbase()) {
         // We have a coinbase transaction. Create one input with the sum of the outputs as its value,
         // and make the address the null address
         long value = 0;
         for (TransactionOutput out : tx.outputs) {
            value += out.value;
         }
         inputs.add(new InputViewModel(getDummyAddress(), Value.valueOf(getCoinType(), value), true));
      } else {
         for (TransactionInput input : tx.inputs) {
            // find parent output
            BitcoinILTransactionOutputEx funding = _backing.getParentTransactionOutput(input.outPoint);
            if (funding == null) {
               continue;
            }
            if (isMine(funding)) {
               satoshisTransferred -= funding.value;
            }
            satoshisSent += funding.value;

            BitcoinILAddress address = ScriptOutput.fromScriptBytes(funding.script).getAddress(_network);
            inputs.add(new InputViewModel(AddressUtils.fromBitcoinILAddress(address), Value.valueOf(getCoinType(), funding.value), false));
         }
      }

      int confirmations;
      if (tex.height == -1) {
         confirmations = 0;
      } else {
         confirmations = Math.max(0, getBlockChainHeight() - tex.height + 1);
      }
      boolean isQueuedOutgoing = _backing.isOutgoingTransaction(tx.getId());
      return new TransactionSummary(getCoinType(), tx.getId().getBytes(), tx.getHash().getBytes(), Value.valueOf(getCoinType(), satoshisTransferred), tex.time, tex.height,
              confirmations, isQueuedOutgoing, inputs, outputs, destinationAddresses, riskAssessmentForUnconfirmedTx.get(tx.getId()),
              tx.vsize(), Value.valueOf(getCoinType(), Math.abs(satoshisReceived - satoshisSent)));
   }

   private TransactionOutput createPopOutput(BitcoinILSha256Hash txidToProve, byte[] nonce) {
      ByteBuffer byteBuffer = ByteBuffer.allocate(41);
      byteBuffer.put((byte) Script.OP_RETURN);

      byteBuffer.put((byte) 1).put((byte) 0); // version 1, little endian

      byteBuffer.put(txidToProve.getBytes()); // txid

      if (nonce == null || nonce.length != 6) {
         throw new IllegalArgumentException("Invalid nonce. Expected 6 bytes.");
      }
      byteBuffer.put(nonce); // nonce

      ScriptOutput scriptOutput = ScriptOutput.fromScriptBytes(byteBuffer.array());
      return new TransactionOutput(0L, scriptOutput);
   }

   @Override
   public CryptoCurrency getCoinType() {
      return _network.isProdnet() ? BitcoinILMain.get() : BitcoinILTest.get();
   }

   @Override
   public CryptoCurrency getBasedOnCoinType() {
      return getCoinType();
   }

   @Override
   public Balance getAccountBalance() {
      CryptoCurrency coinType = getCoinType();
      return new Balance(Value.valueOf(coinType, _cachedBalance.confirmed),
              Value.valueOf(coinType, _cachedBalance.pendingReceiving),
              Value.valueOf(coinType, _cachedBalance.pendingSending),
              Value.valueOf(coinType, _cachedBalance.pendingChange));
   }

   @Override
   public boolean onlySyncWhenActive() {
      return false;
   }

   public BtcILAccountBacking getAccountBacking() {
      return this._backing;
   }

   public int getSyncTotalRetrievedTransactions() {
      return syncTotalRetrievedTransactions;
   }

   public void updateSyncProgress() {
      postEvent(Event.SYNC_PROGRESS_UPDATED);
   }

   @Override
   public Address getReceiveAddress(){
      if(getReceivingAddress().isPresent()) {
         return AddressUtils.fromBitcoinILAddress(getReceivingAddress().get());
      } else {
         return null;
      }
   }

   @Override
   public int getTypicalEstimatedTransactionSize() {
      FeeEstimatorBuilder estimatorBuilder = new FeeEstimatorBuilder();
      FeeEstimator estimator = estimatorBuilder.setLegacyInputs(1)
              .setLegacyOutputs(2)
              .createFeeEstimator();
      return estimator.estimateTransactionSize();
   }

   @Override
   public BtcILAddress getDummyAddress() {
      return new BtcILAddress(getCoinType(), BitcoinILAddress.getNullAddress(getNetwork()));
   }

   @Override
   public BtcILAddress getDummyAddress(String subType) {
      BitcoinILAddress address = BitcoinILAddress.getNullAddress(getNetwork(), AddressType.valueOf(subType));
      return new BtcILAddress(getCoinType(), address);
   }

   @Override
   public List<WalletAccount> getDependentAccounts() {
      // BTC accounts do not have any dependent accounts
      return new ArrayList<>();
   }

   @Override
   public List<OutputViewModel> getUnspentOutputViewModels() {
      List<BitcoinILTransactionOutputSummary> outputSummaryList = getUnspentTransactionOutputSummary();
      List<OutputViewModel> result = new ArrayList<>();
      for(BitcoinILTransactionOutputSummary output : outputSummaryList) {
         Address addr = new BtcILAddress(getCoinType(), output.address);
         result.add(new OutputViewModel(addr, Value.valueOf(getCoinType(), output.value), false));
      }
      return result;
   }

   @Override
   public boolean isSpendingUnconfirmed(Transaction tx) {
      BtcILTransaction btcTx = (BtcILTransaction)tx;
      UnsignedTransaction unsignedTransaction = btcTx.getUnsignedTx();
      for (UnspentTransactionOutput out : unsignedTransaction.getFundingOutputs()) {
         BitcoinILAddress address = out.script.getAddress(getNetwork());
         if (out.height == -1 && isOwnExternalAddress(address)) {
            // this is an unconfirmed output from an external address -> we want to warn the user
            // we allow unconfirmed spending of internal (=change addresses) without warning
            return true;
         }
      }
      //no unconfirmed outputs are used as inputs, we are fine
      return false;
   }

   public void updateParentOutputs(byte[] txid) throws WapiException  {
      BitcoinILTransactionEx transactionEx = getTransaction(BitcoinILSha256Hash.of(txid));
      BitcoinILTransaction transaction = BitcoinILTransactionEx.toTransaction(transactionEx);
      fetchStoreAndValidateParentOutputs(Collections.singletonList(transaction),true);
   }
}
