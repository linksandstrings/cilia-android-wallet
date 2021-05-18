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

package com.mycelium.wapi.wallet.btc.single;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.mrd.bitlib.crypto.BipDerivationType;
import com.mrd.bitlib.crypto.InMemoryPrivateKey;
import com.mrd.bitlib.crypto.PublicKey;
import com.mrd.bitlib.model.BitcoinAddress;
import com.mrd.bitlib.model.AddressType;
import com.mrd.bitlib.model.NetworkParameters;
import com.mrd.bitlib.model.BitcoinTransaction;
import com.mrd.bitlib.util.Sha256Hash;
import com.mycelium.wapi.api.Wapi;
import com.mycelium.wapi.api.WapiException;
import com.mycelium.wapi.api.request.QueryTransactionInventoryRequest;
import com.mycelium.wapi.api.response.GetTransactionsResponse;
import com.mycelium.wapi.api.response.QueryTransactionInventoryResponse;
import com.mycelium.wapi.model.BalanceSatoshis;
import com.mycelium.wapi.model.TransactionEx;
import com.mycelium.wapi.wallet.AesKeyCipher;
import com.mycelium.wapi.wallet.BroadcastResult;
import com.mycelium.wapi.wallet.ExportableAccount;
import com.mycelium.wapi.wallet.Transaction;
import com.mycelium.wapi.wallet.KeyCipher;
import com.mycelium.wapi.wallet.KeyCipher.InvalidKeyCipher;
import com.mycelium.wapi.wallet.SingleAddressBtcAccountBacking;
import com.mycelium.wapi.wallet.SyncMode;
import com.mycelium.wapi.wallet.WalletManager.Event;
import com.mycelium.wapi.wallet.btc.AbstractBtcAccount;
import com.mycelium.wapi.wallet.btc.BtcTransaction;
import com.mycelium.wapi.wallet.btc.ChangeAddressMode;
import com.mycelium.wapi.wallet.btc.Reference;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class SingleAddressAccount extends AbstractBtcAccount implements ExportableAccount {
   private SingleAddressAccountContext _context;
   private List<BitcoinAddress> _addressList;
   private PublicPrivateKeyStore _keyStore;
   private PublicKey publicKey;
   private SingleAddressBtcAccountBacking _backing;
   private Reference<ChangeAddressMode> changeAddressModeReference;
   public boolean toRemove = false;

   public SingleAddressAccount(SingleAddressAccountContext context, PublicPrivateKeyStore keyStore,
                               NetworkParameters network, SingleAddressBtcAccountBacking backing, Wapi wapi,
                               Reference<ChangeAddressMode> changeAddressModeReference) {
      this(context, keyStore, network, backing, wapi, changeAddressModeReference, true);
   }

   public SingleAddressAccount(SingleAddressAccountContext context, PublicPrivateKeyStore keyStore,
                               NetworkParameters network, SingleAddressBtcAccountBacking backing, Wapi wapi,
                               Reference<ChangeAddressMode> changeAddressModeReference, boolean shouldPersistAddress) {
      super(backing, network, wapi);
      this.changeAddressModeReference = changeAddressModeReference;
      _backing = backing;
      _context = context;
      _addressList = new ArrayList<>(3);
      _keyStore = keyStore;
      publicKey = _keyStore.getPublicKey(getAddress().getAllAddressBytes());
      if (shouldPersistAddress) {
         persistAddresses();
      }
       _addressList.addAll(context.getAddresses().values());
       _cachedBalance = _context.isArchived()
               ? new BalanceSatoshis(0, 0, 0, 0, 0, 0, false, _allowZeroConfSpending)
               : calculateLocalBalance();
   }

   private void persistAddresses() {
      try {
         InMemoryPrivateKey privateKey = getPrivateKey(AesKeyCipher.defaultKeyCipher());
         if (privateKey != null) {
            Map<AddressType, BitcoinAddress> allPossibleAddresses = privateKey.getPublicKey().getAllSupportedAddresses(_network, true);
            if (allPossibleAddresses.size() != _context.getAddresses().size()) {
               for (BitcoinAddress address : allPossibleAddresses.values()) {
                  if (!address.equals(_context.getAddresses().get(address.getType()))) {
                     _keyStore.setPrivateKey(address.getAllAddressBytes(), privateKey, AesKeyCipher.defaultKeyCipher());
                  }
               }
               _context.setAddresses(allPossibleAddresses);
               _context.persist(_backing);
            }
         }
      } catch (InvalidKeyCipher invalidKeyCipher) {
         _logger.log(Level.SEVERE,invalidKeyCipher.getMessage());
      }
   }

   public static UUID calculateId(BitcoinAddress address) {
      return addressToUUID(address);
   }

   void markToRemove() {
       toRemove = true;
   }

   @Override
   public synchronized void archiveAccount() {
      if (_context.isArchived()) {
         return;
      }
      clearInternalStateInt(true);
      _context.persistIfNecessary(_backing);
   }

   @Override
   public synchronized void activateAccount() {
      if (!_context.isArchived()) {
         return;
      }
      clearInternalStateInt(false);
      _context.persistIfNecessary(_backing);
   }

   @Override
   public void dropCachedData() {
      if (_context.isArchived()) {
         return;
      }
      clearInternalStateInt(false);
      _context.persistIfNecessary(_backing);
   }

   @Override
   public boolean isValidEncryptionKey(KeyCipher cipher) {
      return _keyStore.isValidEncryptionKey(cipher);
   }

   @Override
   public boolean isDerivedFromInternalMasterseed() {
      return false;
   }

   private void clearInternalStateInt(boolean isArchived) {
      _backing.clear();
      _context = new SingleAddressAccountContext(_context.getId(), _context.getAddresses(), isArchived, 0,
              _context.getDefaultAddressType());
      _context.persist(_backing);
      _cachedBalance = null;
      if (isActive()) {
         _cachedBalance = calculateLocalBalance();
      }
   }

   @Override
   public synchronized boolean doSynchronization(SyncMode mode) {
      checkNotArchived();
      syncTotalRetrievedTransactions = 0;
      try {
         if (synchronizeUnspentOutputs(_addressList) == -1) {
            return false;
         }

         // Monitor young transactions
         if (!monitorYoungTransactions()) {
            return false;
         }

         //lets see if there are any transactions we need to discover
         if (!mode.ignoreTransactionHistory) {
            if (!discoverTransactions()) {
               return false;
            }
         }

         // recalculate cached Balance
         updateLocalBalance();

         _context.persistIfNecessary(_backing);
         return true;
      } finally {
         syncTotalRetrievedTransactions = 0;
      }
   }

   @Override
   public List<AddressType> getAvailableAddressTypes() {
      if (publicKey != null && !publicKey.isCompressed()) {
         return Collections.singletonList(AddressType.P2PKH);
      }
      return new ArrayList<>(_context.getAddresses().keySet());
   }

   @Override
   public BitcoinAddress getReceivingAddress(AddressType addressType) {
      return getAddress(addressType);
   }

   @Override
   public void setDefaultAddressType(AddressType addressType) {
      _context.setDefaultAddressType(addressType);
      _context.persistIfNecessary(_backing);
   }

   private boolean discoverTransactions() {
      // Get the latest transactions
      List<Sha256Hash> discovered;
      List<Sha256Hash> txIds = new ArrayList<>();
      for (BitcoinAddress address : _addressList) {
         try {
            final QueryTransactionInventoryResponse result = _wapi.queryTransactionInventory(new QueryTransactionInventoryRequest(Wapi.VERSION, Collections.singletonList(address)))
                    .getResult();
            txIds.addAll(result.txIds);
            setBlockChainHeight(result.height);
         } catch (WapiException e) {
            if (e.errorCode == Wapi.ERROR_CODE_NO_SERVER_CONNECTION) {
               _logger.log(Level.SEVERE, "Server connection failed with error code: " + e.errorCode, e);
               postEvent(Event.SERVER_CONNECTION_ERROR);
               return false;
            } else if (e.errorCode == Wapi.ERROR_CODE_RESPONSE_TOO_LARGE) {
               postEvent(Event.TOO_MANY_TRANSACTIONS);
            }
         }
      }

      // get out right there if there is nothing to work with
      if (txIds.size() == 0) {
          return true;
      }
      discovered = txIds;

      // Figure out whether there are any transactions we need to fetch
      List<Sha256Hash> toFetch = new LinkedList<>();
      for (Sha256Hash id : discovered) {
         if (!_backing.hasTransaction(id)) {
            toFetch.add(id);
         }
      }

      // Fetch any missing transactions
      int chunkSize = 50;
      for (int fromIndex = 0; fromIndex < toFetch.size(); fromIndex += chunkSize) {
         try {
            int toIndex = Math.min(fromIndex + chunkSize, toFetch.size());
            GetTransactionsResponse response = getTransactionsBatched(toFetch.subList(fromIndex, toIndex)).getResult();
            Collection<TransactionEx> transactionsEx = Lists.newLinkedList(response.transactions);
            handleNewExternalTransactions(transactionsEx);
         } catch (WapiException e) {
            _logger.log(Level.SEVERE, "Server connection failed with error code: " + e.errorCode, e);
            postEvent(Event.SERVER_CONNECTION_ERROR);
            return false;
         }
      }
      return true;
   }

   @Override
   public Optional<BitcoinAddress> getReceivingAddress() {
      //removed checkNotArchived, cause we wont to know the address for archived acc
      //to display them as archived accounts in "Accounts" tab
      return Optional.of(getAddress());
   }

   @Override
   public boolean canSpend() {
      return _keyStore.hasPrivateKey(getAddress().getAllAddressBytes());
   }

   @Override
   public boolean canSign() {
      return true;
   }

   @Override
   public boolean isMine(BitcoinAddress address) {
      return _addressList.contains(address);
   }

   @Override
   public int getBlockChainHeight() {
      checkNotArchived();
      return _context.getBlockHeight();
   }

   @Override
   protected void setBlockChainHeight(int blockHeight) {
      checkNotArchived();
      _context.setBlockHeight(blockHeight);
   }

   @Override
   protected void persistContextIfNecessary() {
      _context.persistIfNecessary(_backing);
   }

   @Override
   public boolean isArchived() {
      // public method that needs no synchronization
      return _context.isArchived();
   }

   @Override
   public boolean isActive() {
      // public method that needs no synchronization
      return !isArchived() && !toRemove;
   }

   @Override
   protected void onNewTransaction(BitcoinTransaction t) {
      // Nothing to do for this account type
   }

   @Override
   public boolean isOwnInternalAddress(BitcoinAddress address) {
      return isMine(address);
   }

   @Override
   public boolean isOwnExternalAddress(BitcoinAddress address) {
      return isMine(address);
   }

   @Override
   public UUID getId() {
      return _context.getId();
   }

   @Override
   public BitcoinAddress getChangeAddress() {
      return getAddress();
   }

   @Override
   protected BitcoinAddress getChangeAddress(BitcoinAddress destinationAddress) {
      BitcoinAddress result;
      switch (changeAddressModeReference.get()) {
         case P2WPKH:
            result = getAddress(AddressType.P2WPKH);
            break;
         case P2SH_P2WPKH:
            result = getAddress(AddressType.P2SH_P2WPKH);
            break;
         case PRIVACY:
            result = getAddress(destinationAddress.getType());
            break;
         default:
            throw new IllegalStateException();
      }
      if (result == null) {
         result = getAddress();
      }
      return result;
   }

   @Override
   protected BitcoinAddress getChangeAddress(List<BitcoinAddress> destinationAddresses) {
      Map<AddressType, Integer> mostUsedTypesMap = new HashMap<>();
      for (BitcoinAddress address : destinationAddresses) {
         Integer currentValue = mostUsedTypesMap.get(address.getType());
         if (currentValue == null) {
            currentValue = 0;
         }
         mostUsedTypesMap.put(address.getType(), currentValue + 1);
      }
      int max = 0;
      AddressType maxedOn = null;
      for (AddressType addressType : mostUsedTypesMap.keySet()) {
         if (mostUsedTypesMap.get(addressType) > max) {
            max = mostUsedTypesMap.get(addressType);
            maxedOn = addressType;
         }
      }
      BitcoinAddress result;
      switch (changeAddressModeReference.get()) {
         case P2WPKH:
            result = getAddress(AddressType.P2WPKH);
            break;
         case P2SH_P2WPKH:
            result = getAddress(AddressType.P2SH_P2WPKH);
            break;
         case PRIVACY:
            result = getAddress(maxedOn);
            break;
         default:
            throw new IllegalStateException();
      }
      if (result == null) {
         result = getAddress();
      }
      return result;
   }

   @Override
   protected InMemoryPrivateKey getPrivateKey(PublicKey publicKey, KeyCipher cipher) throws InvalidKeyCipher {
      if (getPublicKey().equals(publicKey) || new PublicKey(publicKey.getPubKeyCompressed()).equals(publicKey)) {
         InMemoryPrivateKey privateKey = getPrivateKey(cipher);
         if (publicKey.isCompressed()) {
            return new InMemoryPrivateKey(privateKey.getPrivateKeyBytes(), true);
         } else {
            return privateKey;
         }
      }

      return null;
   }

   @Override
   protected InMemoryPrivateKey getPrivateKeyForAddress(BitcoinAddress address, KeyCipher cipher) throws InvalidKeyCipher {
      if (_addressList.contains(address)) {
         InMemoryPrivateKey privateKey = getPrivateKey(cipher);
         if (address.getType() == AddressType.P2SH_P2WPKH || address.getType() == AddressType.P2WPKH) {
            return new InMemoryPrivateKey(privateKey.getPrivateKeyBytes(), true);
         } else {
            return privateKey;
         }
      } else {
         return null;
      }
   }

   @Override
   protected PublicKey getPublicKeyForAddress(BitcoinAddress address) {
      if (_addressList.contains(address)) {
         return getPublicKey();
      } else {
         return null;
      }
   }

   @Override
   public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("Simple ID: ").append(getId());
      if (isArchived()) {
         sb.append(" Archived");
      } else {
         if (_cachedBalance == null) {
            sb.append(" Balance: not known");
         } else {
            sb.append(" Balance: ").append(_cachedBalance);
         }
         Optional<BitcoinAddress> receivingAddress = getReceivingAddress();
         sb.append(" Receiving Address: ").append(receivingAddress.isPresent() ? receivingAddress.get().toString() : "");
         sb.append(" Spendable Outputs: ").append(getSpendableOutputs(0).size());
      }
      return sb.toString();
   }

   public void forgetPrivateKey(KeyCipher cipher) throws InvalidKeyCipher {
      if (getPublicKey() == null) {
         _keyStore.forgetPrivateKey(getAddress().getAllAddressBytes(), cipher);
      } else {
         for (BitcoinAddress address : getPublicKey().getAllSupportedAddresses(_network, true).values()) {
            _keyStore.forgetPrivateKey(address.getAllAddressBytes(), cipher);
         }
      }
   }

   public InMemoryPrivateKey getPrivateKey(KeyCipher cipher) throws InvalidKeyCipher {
      return _keyStore.getPrivateKey(getAddress().getAllAddressBytes(), cipher);
   }

   @Override
   public com.mrd.bitillib.crypto.InMemoryPrivateKey getBitcoinILPrivateKey(KeyCipher cipher) throws InvalidKeyCipher {
      return null;
   }

   /**
    * This method is used for Colu account, so method should NEVER persist addresses as only P2PKH addresses are used for Colu
    */
   public void setPrivateKey(InMemoryPrivateKey privateKey, KeyCipher cipher) throws InvalidKeyCipher {
      _keyStore.setPrivateKey(getAddress().getAllAddressBytes(), privateKey, cipher);
   }

   public PublicKey getPublicKey() {
      return _keyStore.getPublicKey(getAddress().getAllAddressBytes());
   }

   /**
    * @return default address
    */
   public BitcoinAddress getAddress() {
      BitcoinAddress defaultAddress = getAddress(_context.getDefaultAddressType());
      if (defaultAddress != null) {
         return defaultAddress;
      } else {
         return _context.getAddresses().values().iterator().next();
      }
   }

   public BitcoinAddress getAddress(AddressType type) {
      if (publicKey != null && !publicKey.isCompressed()) {
         if (type == AddressType.P2SH_P2WPKH || type == AddressType.P2WPKH) {
            return null;
         }
      }
      return _context.getAddresses().get(type);
   }

   @NotNull
   @Override
   public Data getExportData(@NotNull KeyCipher cipher) {
      Optional<String> privKey = Optional.absent();
      Map<BipDerivationType, String> publicDataMap = new HashMap<>();
      if (canSpend()) {
         try {
            InMemoryPrivateKey privateKey = _keyStore.getPrivateKey(getAddress().getAllAddressBytes(), cipher);
            privKey = Optional.of(privateKey.getBase58EncodedPrivateKey(getNetwork()));
         } catch (InvalidKeyCipher ignore) {
         }
      }
      for (AddressType type : getAvailableAddressTypes()) {
         BitcoinAddress address = getAddress(type);
         if (address != null) {
            publicDataMap.put(BipDerivationType.Companion.getDerivationTypeByAddressType(type),
                    address.toString());
         }
      }
      return new Data(privKey, publicDataMap);
   }

   @Override
   protected Set<BipDerivationType> doDiscoveryForAddresses(List<BitcoinAddress> lookAhead) {
      // not needed for SingleAddressAccount
      return Collections.emptySet();
   }


   @Override
   public BroadcastResult broadcastTx(Transaction tx) {
      BtcTransaction btcTransaction = (BtcTransaction)tx;
      return broadcastTransaction(btcTransaction.getTx());
   }

}
