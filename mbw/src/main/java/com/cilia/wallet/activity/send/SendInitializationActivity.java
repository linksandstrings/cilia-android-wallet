/*
 * Copyright 2013, 2014 Megion Research and Development GmbH
 *
 * Licensed under the Microsoft Reference Source License (MS-RSL)
 *
 * This license governs use of the accompanying software. If you use the software, you accept this license.
 * If you do not accept the license, do not use the software.
 *
 * 1. Definitions
 * The terms "reproduce," "reproduction," and "distribution" have the same meaning here as under U.S. copyright law.
 * "You" means the licensee of the software.
 * "Your company" means the company you worked for when you downloaded the software.
 * "Reference use" means use of the software within your company as a reference, in read only form, for the sole purposes
 * of debugging your products, maintaining your products, or enhancing the interoperability of your products with the
 * software, and specifically excludes the right to distribute the software outside of your company.
 * "Licensed patents" means any Licensor patent claims which read directly on the software as distributed by the Licensor
 * under this license.
 *
 * 2. Grant of Rights
 * (A) Copyright Grant- Subject to the terms of this license, the Licensor grants you a non-transferable, non-exclusive,
 * worldwide, royalty-free copyright license to reproduce the software for reference use.
 * (B) Patent Grant- Subject to the terms of this license, the Licensor grants you a non-transferable, non-exclusive,
 * worldwide, royalty-free patent license under licensed patents for reference use.
 *
 * 3. Limitations
 * (A) No Trademark License- This license does not grant you any rights to use the Licensor’s name, logo, or trademarks.
 * (B) If you begin patent litigation against the Licensor over patents that you think may apply to the software
 * (including a cross-claim or counterclaim in a lawsuit), your license to the software ends automatically.
 * (C) The software is licensed "as-is." You bear the risk of using it. The Licensor gives no express warranties,
 * guarantees or conditions. You may have additional consumer rights under your local laws which this license cannot
 * change. To the extent permitted under your local laws, the Licensor excludes the implied warranties of merchantability,
 * fitness for a particular purpose and non-infringement.
 */

package com.cilia.wallet.activity.send;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;

import com.google.common.base.Preconditions;
import com.cilia.wallet.MbwManager;
import com.cilia.wallet.R;
import com.cilia.wallet.Utils;
import com.cilia.wallet.event.SyncFailed;
import com.cilia.wallet.event.SyncStopped;
import com.mycelium.wapi.content.AssetUri;
import com.mycelium.wapi.wallet.WalletAccount;
import com.squareup.otto.Subscribe;

import java.util.UUID;

public class SendInitializationActivity extends Activity {
   private MbwManager _mbwManager;
   private WalletAccount _account;
   private AssetUri _uri;
   private boolean _isColdStorage;
   private Handler _synchronizingHandler;
   private Handler _slowNetworkHandler;
   private byte[] _rawPr;

   public static void callMe(Activity currentActivity, UUID account, boolean isColdStorage) {
      //we dont know anything specific yet
      Intent intent = prepareSendingIntent(currentActivity, account, (AssetUri) null, isColdStorage)
              .addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
      currentActivity.startActivity(intent);
   }

   public static Intent getIntent(Activity currentActivity, UUID account, boolean isColdStorage) {
      return prepareSendingIntent(currentActivity, account, (AssetUri)null, isColdStorage);
   }

   public static void callMeWithResult(Activity currentActivity, UUID account, AssetUri uri, boolean isColdStorage, int request) {
      Intent intent = prepareSendingIntent(currentActivity, account, uri, isColdStorage);
      currentActivity.startActivityForResult(intent, request);
   }

   public static void callMeWithResult(Activity currentActivity, UUID account, boolean isColdStorage, int request) {
      Intent intent = prepareSendingIntent(currentActivity, account, (AssetUri)null, isColdStorage);
      currentActivity.startActivityForResult(intent, request);
   }

   public static void callMe(Activity currentActivity, UUID account, AssetUri uri, boolean isColdStorage) {
      Intent intent = prepareSendingIntent(currentActivity, account, uri, isColdStorage)
              .addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
      currentActivity.startActivity(intent);
   }

   public static void callMe(Activity currentActivity, UUID account, byte[] rawPaymentRequest, boolean isColdStorage) {
      Intent intent = prepareSendingIntent(currentActivity, account, rawPaymentRequest, isColdStorage)
              .addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
      currentActivity.startActivity(intent);
   }

   private static Intent prepareSendingIntent(Activity currentActivity, UUID account, AssetUri uri, boolean isColdStorage) {
      return new Intent(currentActivity, SendInitializationActivity.class)
              .putExtra("account", account)
              .putExtra("uri", uri)
              .putExtra("isColdStorage", isColdStorage);
   }

   public static void callMeWithResult(Activity currentActivity, UUID account, byte[] rawPaymentRequest, boolean isColdStorage, int request) {
      Intent intent = prepareSendingIntent(currentActivity, account, rawPaymentRequest, isColdStorage);
      currentActivity.startActivityForResult(intent, request);
   }
   private static Intent prepareSendingIntent(Activity currentActivity, UUID account, byte[] rawPaymentRequest, boolean isColdStorage) {
      return new Intent(currentActivity, SendInitializationActivity.class)
              .putExtra("account", account)
              .putExtra("rawPr", rawPaymentRequest)
              .putExtra("isColdStorage", isColdStorage);
   }

   @Override
   public void onCreate(Bundle savedInstanceState) {
      this.requestWindowFeature(Window.FEATURE_NO_TITLE);
      super.onCreate(savedInstanceState);
      setContentView(R.layout.send_initialization_activity);
      _mbwManager = MbwManager.getInstance(getApplication());
      // Get intent parameters
      UUID accountId = Preconditions.checkNotNull((UUID) getIntent().getSerializableExtra("account"));

      _uri = (AssetUri) getIntent().getSerializableExtra("uri");
      _rawPr =  getIntent().getByteArrayExtra("rawPr");
      _isColdStorage = getIntent().getBooleanExtra("isColdStorage", false);
      String crashHint = TextUtils.join(", ", getIntent().getExtras().keySet()) + " (account id was " + accountId + ")";
      WalletAccount account = _mbwManager.getWalletManager(_isColdStorage).getAccount(accountId);
      _account = Preconditions.checkNotNull(account, crashHint);

      if (!_isColdStorage) {
         continueIfReadyOrNonUtxos();
      }
   }

   @Override
   protected void onResume() {
      if (isFinishing()) {
         return;
      }
      MbwManager.getEventBus().register(this);

      // Show delayed messages so the user does not grow impatient
      _synchronizingHandler = new Handler();
      _synchronizingHandler.postDelayed(showSynchronizing, 2000);
      _slowNetworkHandler = new Handler();
      _slowNetworkHandler.postDelayed(showSlowNetwork, 6000);

      // If we don't have a fresh exchange rate, now is a good time to request one, as we will need it in a minute
      if (_mbwManager.getCurrencySwitcher().getExchangeRatePrice(_account.getCoinType()) == null) {
         _mbwManager.getExchangeRateManager().requestRefresh();
      }

      // If we are in cold storage spending mode we wish to synchronize the wallet
      if (_isColdStorage) {
         _mbwManager.getWalletManager(true).startSynchronization();
      } else {
         continueIfReadyOrNonUtxos();
      }
      super.onResume();
   }

   @Override
   protected void onPause() {
      if (_synchronizingHandler != null) {
         _synchronizingHandler.removeCallbacks(showSynchronizing);
      }
      if (_slowNetworkHandler != null) {
         _slowNetworkHandler.removeCallbacks(showSlowNetwork);
      }
      MbwManager.getEventBus().unregister(this);
      super.onPause();
   }

   private Runnable showSynchronizing = new Runnable() {

      @Override
      public void run() {
         findViewById(R.id.tvSynchronizing).setVisibility(View.VISIBLE);
      }
   };

   private Runnable showSlowNetwork = new Runnable() {

      @Override
      public void run() {
         findViewById(R.id.tvSlowNetwork).setVisibility(View.VISIBLE);
      }
   };

   @Subscribe
   public void syncFailed(SyncFailed event) {
      Utils.toastConnectionError(this);
      // If we are in cold storage spending mode there is no point in continuing.
      // If we continued we would think that there were no funds on the private key
      if (_isColdStorage) {
         finish();
      }
   }

   @Subscribe
   public void syncStopped(SyncStopped sync) {
      continueIfReadyOrNonUtxos();
   }

   private void continueIfReadyOrNonUtxos() {
      if (isFinishing()) {
         return;
      }
      if (_account.isSyncing() && (_account.getCoinType().isUtxosBased() || _isColdStorage)) {
         // wait till its finished syncing
         // no need wait for non utxo's based accounts
         return;
      }
      goToSendActivity();
   }

   private void goToSendActivity() {
      if (_isColdStorage) {
         ColdStorageSummaryActivity.callMe(this, _account.getId());
      } else {
         Intent intent;
         if (_rawPr != null) {
            intent = SendCoinsActivity.getIntent(this, _account.getId(), _rawPr, false);
         } else if (_uri != null) {
            intent = SendCoinsActivity.getIntent(this, _account.getId(), _uri, false);
         } else {
            intent = SendCoinsActivity.getIntent(this, _account.getId(), false);
         }
         intent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
         this.startActivity(intent);
      }
      finish();
   }
}
