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

package com.mycelium.wapi.wallet.btcil.single;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.mrd.bitillib.crypto.BipDerivationType;
import com.mrd.bitillib.crypto.InMemoryPrivateKey;
import com.mrd.bitillib.crypto.PublicKey;
import com.mrd.bitillib.model.BitcoinILAddress;
import com.mrd.bitlib.model.AddressType;
import com.mrd.bitillib.model.NetworkParameters;
import com.mrd.bitillib.model.BitcoinILTransaction;
import com.mrd.bitillib.util.BitcoinILSha256Hash;
import com.mycelium.wapi.api.Wapi;
import com.mycelium.wapi.api.WapiException;
import com.mycelium.wapi.api.request.QueryBitcoinILTransactionInventoryRequest;
import com.mycelium.wapi.api.response.GetBitcoinILTransactionsResponse;
import com.mycelium.wapi.api.response.QueryBitcoinILTransactionInventoryResponse;
import com.mycelium.wapi.model.BalanceSatoshis;
import com.mycelium.wapi.model.BitcoinILTransactionEx;
import com.mycelium.wapi.wallet.AesKeyCipher;
import com.mycelium.wapi.wallet.BroadcastResult;
import com.mycelium.wapi.wallet.ExportableAccountIL;
import com.mycelium.wapi.wallet.SingleAddressBtcILAccountBacking;
import com.mycelium.wapi.wallet.Transaction;
import com.mycelium.wapi.wallet.KeyCipher;
import com.mycelium.wapi.wallet.KeyCipher.InvalidKeyCipher;
import com.mycelium.wapi.wallet.SyncMode;
import com.mycelium.wapi.wallet.WalletManager.Event;
import com.mycelium.wapi.wallet.btcil.AbstractBtcILAccount;
import com.mycelium.wapi.wallet.btcil.BtcILTransaction;
import com.mycelium.wapi.wallet.btcil.ChangeAddressModeIL;
import com.mycelium.wapi.wallet.btcil.ReferenceIL;

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

public class SingleAddressAccountIL extends AbstractBtcILAccount implements ExportableAccountIL {
   private SingleAddressAccountContextIL _context;
   private List<BitcoinILAddress> _addressList;
   private PublicPrivateKeyStoreIL _keyStore;
   private PublicKey publicKey;
   private SingleAddressBtcILAccountBacking _backing;
   private ReferenceIL<ChangeAddressModeIL> changeAddressModeReference;
   public boolean toRemove = false;

   public SingleAddressAccountIL(SingleAddressAccountContextIL context, PublicPrivateKeyStoreIL keyStore,
                                 NetworkParameters network, SingleAddressBtcILAccountBacking backing, Wapi wapi,
                                 ReferenceIL<ChangeAddressModeIL> changeAddressModeReference) {
      this(context, keyStore, network, backing, wapi, changeAddressModeReference, true);
   }

   public SingleAddressAccountIL(SingleAddressAccountContextIL context, PublicPrivateKeyStoreIL keyStore,
                                 NetworkParameters network, SingleAddressBtcILAccountBacking backing, Wapi wapi,
                                 ReferenceIL<ChangeAddressModeIL> changeAddressModeReference, boolean shouldPersistAddress) {
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
         InMemoryPrivateKey privateKey = getBitcoinILPrivateKey(AesKeyCipher.defaultKeyCipher());
         if (privateKey != null) {
            Map<AddressType, BitcoinILAddress> allPossibleAddresses = privateKey.getPublicKey().getAllSupportedAddresses(_network, true);
            if (allPossibleAddresses.size() != _context.getAddresses().size()) {
               for (BitcoinILAddress address : allPossibleAddresses.values()) {
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

   public static UUID calculateId(BitcoinILAddress address) {
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
      _context = new SingleAddressAccountContextIL(_context.getId(), _context.getAddresses(), isArchived, 0,
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
   public BitcoinILAddress getReceivingAddress(AddressType addressType) {
      return getAddress(addressType);
   }

   @Override
   public void setDefaultAddressType(AddressType addressType) {
      _context.setDefaultAddressType(addressType);
      _context.persistIfNecessary(_backing);
   }

   private boolean discoverTransactions() {
      // Get the latest transactions
      List<BitcoinILSha256Hash> discovered;
      List<BitcoinILSha256Hash> txIds = new ArrayList<>();
      for (BitcoinILAddress address : _addressList) {
         try {
            final QueryBitcoinILTransactionInventoryResponse result = _wapi.queryBitcoinILTransactionInventory(new QueryBitcoinILTransactionInventoryRequest(Wapi.VERSION, Collections.singletonList(address)))
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
      List<BitcoinILSha256Hash> toFetch = new LinkedList<>();
      for (BitcoinILSha256Hash id : discovered) {
         if (!_backing.hasTransaction(id)) {
            toFetch.add(id);
         }
      }

      // Fetch any missing transactions
      int chunkSize = 50;
      for (int fromIndex = 0; fromIndex < toFetch.size(); fromIndex += chunkSize) {
         try {
            int toIndex = Math.min(fromIndex + chunkSize, toFetch.size());
            GetBitcoinILTransactionsResponse response = getTransactionsBatched(toFetch.subList(fromIndex, toIndex)).getResult();
            Collection<BitcoinILTransactionEx> transactionsEx = Lists.newLinkedList(response.transactions);
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
   public Optional<BitcoinILAddress> getReceivingAddress() {
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
   public boolean isMine(BitcoinILAddress address) {
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
   protected void onNewTransaction(BitcoinILTransaction t) {
      // Nothing to do for this account type
   }

   @Override
   public boolean isOwnInternalAddress(BitcoinILAddress address) {
      return isMine(address);
   }

   @Override
   public boolean isOwnExternalAddress(BitcoinILAddress address) {
      return isMine(address);
   }

   @Override
   public UUID getId() {
      return _context.getId();
   }

   @Override
   public com.mrd.bitlib.crypto.InMemoryPrivateKey getPrivateKey(KeyCipher cipher) throws InvalidKeyCipher {
      return null;
   }

   @Override
   public BitcoinILAddress getChangeAddress() {
      return getAddress();
   }

   @Override
   protected BitcoinILAddress getChangeAddress(BitcoinILAddress destinationAddress) {
      BitcoinILAddress result;
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
   protected BitcoinILAddress getChangeAddress(List<BitcoinILAddress> destinationAddresses) {
      Map<AddressType, Integer> mostUsedTypesMap = new HashMap<>();
      for (BitcoinILAddress address : destinationAddresses) {
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
      BitcoinILAddress result;
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
   protected InMemoryPrivateKey getBitcoinILPrivateKey(PublicKey publicKey, KeyCipher cipher) throws InvalidKeyCipher {
      if (getPublicKey().equals(publicKey) || new PublicKey(publicKey.getPubKeyCompressed()).equals(publicKey)) {
         InMemoryPrivateKey privateKey = getBitcoinILPrivateKey(cipher);
         if (publicKey.isCompressed()) {
            return new InMemoryPrivateKey(privateKey.getPrivateKeyBytes(), true);
         } else {
            return privateKey;
         }
      }

      return null;
   }

   @Override
   protected InMemoryPrivateKey getPrivateKeyForAddress(BitcoinILAddress address, KeyCipher cipher) throws InvalidKeyCipher {
      if (_addressList.contains(address)) {
         InMemoryPrivateKey privateKey = getBitcoinILPrivateKey(cipher);
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
   protected PublicKey getPublicKeyForAddress(BitcoinILAddress address) {
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
         Optional<BitcoinILAddress> receivingAddress = getReceivingAddress();
         sb.append(" Receiving Address: ").append(receivingAddress.isPresent() ? receivingAddress.get().toString() : "");
         sb.append(" Spendable Outputs: ").append(getSpendableOutputs(0).size());
      }
      return sb.toString();
   }

   public void forgetPrivateKey(KeyCipher cipher) throws InvalidKeyCipher {
      if (getPublicKey() == null) {
         _keyStore.forgetPrivateKey(getAddress().getAllAddressBytes(), cipher);
      } else {
         for (BitcoinILAddress address : getPublicKey().getAllSupportedAddresses(_network, true).values()) {
            _keyStore.forgetPrivateKey(address.getAllAddressBytes(), cipher);
         }
      }
   }

   public InMemoryPrivateKey getBitcoinILPrivateKey(KeyCipher cipher) throws InvalidKeyCipher {
      return _keyStore.getBitcoinILPrivateKey(getAddress().getAllAddressBytes(), cipher);
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
   public BitcoinILAddress getAddress() {
      BitcoinILAddress defaultAddress = getAddress(_context.getDefaultAddressType());
      if (defaultAddress != null) {
         return defaultAddress;
      } else {
         return _context.getAddresses().values().iterator().next();
      }
   }

   public BitcoinILAddress getAddress(AddressType type) {
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
            InMemoryPrivateKey privateKey = _keyStore.getBitcoinILPrivateKey(getAddress().getAllAddressBytes(), cipher);
            privKey = Optional.of(privateKey.getBase58EncodedPrivateKey(getNetwork()));
         } catch (InvalidKeyCipher ignore) {
         }
      }
      for (AddressType type : getAvailableAddressTypes()) {
         BitcoinILAddress address = getAddress(type);
         if (address != null) {
            publicDataMap.put(BipDerivationType.Companion.getDerivationTypeByAddressType(type),
                    address.toString());
         }
      }
      return new Data(privKey, publicDataMap);
   }

   @Override
   protected Set<BipDerivationType> doDiscoveryForAddresses(List<BitcoinILAddress> lookAhead) {
      // not needed for SingleAddressAccount
      return Collections.emptySet();
   }


   @Override
   public BroadcastResult broadcastTx(Transaction tx) {
      BtcILTransaction btcTransaction = (BtcILTransaction)tx;
      return broadcastTransaction(btcTransaction.getTx());
   }

}
