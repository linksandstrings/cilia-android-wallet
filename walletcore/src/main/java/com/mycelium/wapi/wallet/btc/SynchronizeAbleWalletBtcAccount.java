package com.mycelium.wapi.wallet.btc;

import com.google.common.collect.ImmutableMap;
import com.mrd.bitlib.StandardTransactionBuilder;
import com.mrd.bitlib.UnsignedTransaction;
import com.mrd.bitlib.model.NetworkParameters;
import com.mrd.bitlib.model.OutputList;
import com.mrd.bitlib.model.BitcoinTransaction;
import com.mrd.bitlib.util.Sha256Hash;
import com.mycelium.wapi.model.*;
import com.mycelium.wapi.wallet.BroadcastResult;
import com.mycelium.wapi.wallet.KeyCipher;
import com.mycelium.wapi.wallet.SyncMode;
import com.mycelium.wapi.wallet.coins.Value;
import com.mycelium.wapi.wallet.currency.CurrencyBasedBalance;

import java.util.Date;
import java.util.HashMap;
import java.util.List;

import kotlin.jvm.Volatile;

public abstract class SynchronizeAbleWalletBtcAccount implements WalletBtcAccount {
   private static final ImmutableMap<SyncMode.Mode, Integer> MIN_SYNC_INTERVAL = ImmutableMap.of(
         SyncMode.Mode.FAST_SYNC, 1000,
         SyncMode.Mode.ONE_ADDRESS, 1000,
         SyncMode.Mode.NORMAL_SYNC, 30 * 1000,
         SyncMode.Mode.FULL_SYNC, 120 * 1000
   );

   private final HashMap<SyncMode.Mode, Date> _lastSync = new HashMap<>(SyncMode.Mode.values().length);

   @Volatile
   private volatile boolean isSyncing;

   private String accountLabel;

   /**
    * Checks if the account needs to be synchronized, according to the provided SyncMode
    *
    * @param syncMode the requested sync mode
    * @return true if sync is needed
    */
   private boolean needsSynchronization(SyncMode syncMode){
      if (syncMode.ignoreSyncInterval) {
         return true;
      }

      // check how long ago the last sync for this mode
      Date lastSync;
      if ( (lastSync = _lastSync.get(syncMode.mode)) != null){
         long lastSyncAge = new Date().getTime() - lastSync.getTime();
         return lastSyncAge > getSyncInterval(syncMode);
      } else {
         // never synced for this mode before - just do it. now.
         return true;
      }
   }

   /**
    * Returns the normal sync interval for this mode
    * if synchronize() is called faster than this interval (and ignoreSyncInterval=false), the sync is disregarded
    *
    * @param syncMode the Mode to get the interval for
    * @return the interval in milliseconds
    */
   private Integer getSyncInterval(SyncMode syncMode) {
      return MIN_SYNC_INTERVAL.get(syncMode.mode);
   }

   /**
    * Synchronize this account
    * <p/>
    * This method should only be called from the wallet manager
    *
    * @param mode set synchronization parameters
    * @return false if synchronization failed due to failed blockchain
    * connection
    */
   public boolean synchronize(SyncMode mode){
      if (needsSynchronization(mode)){
         isSyncing = true;
         boolean synced =  doSynchronization(mode);
         isSyncing = false;
         // if sync went well, remember current time for this sync mode
         if (synced){
            _lastSync.put(mode.mode, new Date());
         }

         return synced;
      } else {
         return true;
      }
   }

   @Override
   public boolean isSyncing() {
      return isSyncing;
   }

   public boolean isVisible() {
      return true;
   }

   /**
    * Do the necessary steps to synchronize this account.
    * This function has to be implemented for the individual accounts and will only be called, if it is
    * needed (according to various timeouts, etc)
    *
    * @param mode SyncMode
    * @return true if sync was successful
    */
   protected abstract boolean doSynchronization(SyncMode mode);

   @Override
   public abstract boolean broadcastOutgoingTransactions();

   @Override
   public abstract BroadcastResult broadcastTransaction(BitcoinTransaction transaction);

   @Override
   public abstract BitcoinTransaction signTransaction(UnsignedTransaction unsigned, KeyCipher cipher)
         throws KeyCipher.InvalidKeyCipher;

   @Override
   public abstract boolean deleteTransaction(Sha256Hash transactionId);

   @Override
   public abstract boolean cancelQueuedTransaction(Sha256Hash transaction);

   @Override
   public String getLabel() {
      return accountLabel;
   }

   @Override
   public void setLabel(String label) {
      accountLabel = label;
   }

   @Override
   public abstract NetworkParameters getNetwork();

   @Override
   public abstract Value calculateMaxSpendableAmount(Value minerFeeToUse, BtcAddress destinationAddress);

   @Override
   public abstract UnsignedTransaction createUnsignedTransaction(List<BtcReceiver> receivers, long minerFeeToUse)
         throws StandardTransactionBuilder.BtcOutputTooSmallException, StandardTransactionBuilder.InsufficientBtcException, StandardTransactionBuilder.UnableToBuildTransactionException;

   @Override
   public abstract UnsignedTransaction createUnsignedTransaction(OutputList outputs, long minerFeeToUse) throws StandardTransactionBuilder.BtcOutputTooSmallException, StandardTransactionBuilder.InsufficientBtcException, StandardTransactionBuilder.UnableToBuildTransactionException;

   @Override
   public abstract BalanceSatoshis getBalance();

   @Override
   public abstract CurrencyBasedBalance getCurrencyBasedBalance();

   @Override
   public abstract List<TransactionOutputSummary> getUnspentTransactionOutputSummary();

   @Override
   public abstract TransactionSummary getTransactionSummary(Sha256Hash txid);

   @Override
  public boolean onlySyncWhenActive() {
      return false;
   }
}
