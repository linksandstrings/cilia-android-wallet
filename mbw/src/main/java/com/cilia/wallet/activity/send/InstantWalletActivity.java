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
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.FragmentActivity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;

import com.mrd.bitlib.crypto.BipSss;
import com.mrd.bitlib.crypto.HdKeyNode;
import com.mrd.bitlib.crypto.InMemoryPrivateKey;
import com.cilia.wallet.MbwManager;
import com.cilia.wallet.R;
import com.cilia.wallet.Utils;
import com.cilia.wallet.activity.BipSsImportActivity;
import com.cilia.wallet.activity.EnterWordListActivity;
import com.cilia.wallet.activity.InstantMasterseedActivity;
import com.cilia.wallet.activity.ScanActivity;
import com.cilia.wallet.activity.StringHandlerActivity;
import com.cilia.wallet.activity.modern.Toaster;
import com.cilia.wallet.content.HandleConfigFactory;
import com.cilia.wallet.content.ResultType;
import com.cilia.wallet.extsig.keepkey.activity.InstantKeepKeyActivity;
import com.cilia.wallet.extsig.trezor.activity.InstantTrezorActivity;
import com.mycelium.wapi.content.AssetUri;
import com.mycelium.wapi.wallet.Address;
import com.mycelium.wapi.wallet.WalletManager;
import com.mycelium.wapi.wallet.btc.bip44.UnrelatedHDAccountConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;

import static com.cilia.wallet.activity.util.IntentExtentionsKt.getAddress;
import static com.cilia.wallet.activity.util.IntentExtentionsKt.getAssetUri;
import static com.cilia.wallet.activity.util.IntentExtentionsKt.getBtcilPrivateKey;
import static com.cilia.wallet.activity.util.IntentExtentionsKt.getHdKeyNode;
import static com.cilia.wallet.activity.util.IntentExtentionsKt.getPrivateKey;
import static com.cilia.wallet.activity.util.IntentExtentionsKt.getShare;

public class InstantWalletActivity extends FragmentActivity {
   public static final int REQUEST_SCAN = 0;
   private static final int REQUEST_TREZOR = 1;
   private static final int IMPORT_WORDLIST = 2;
   private static final int REQUEST_KEEPKEY = 3;

   public static void callMe(Activity currentActivity) {
      Intent intent = new Intent(currentActivity, InstantWalletActivity.class);
      currentActivity.startActivity(intent);
   }

   /**
    * Called when the activity is first created.
    */
   @Override
   public void onCreate(Bundle savedInstanceState) {
      this.requestWindowFeature(Window.FEATURE_NO_TITLE);
      this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
      super.onCreate(savedInstanceState);
      setContentView(R.layout.instant_wallet_activity);

      findViewById(R.id.btClipboard).setOnClickListener(new OnClickListener() {
         @Override
         public void onClick(View arg0) {
            handleString(Utils.getClipboardString(InstantWalletActivity.this));
         }
      });

      findViewById(R.id.btMasterseed).setOnClickListener(new OnClickListener() {

         @Override
         public void onClick(View arg0) {
            EnterWordListActivity.callMe(InstantWalletActivity.this, IMPORT_WORDLIST, true);
         }
      });

      findViewById(R.id.btScan).setOnClickListener(new OnClickListener() {

         @Override
         public void onClick(View arg0) {
            ScanActivity.callMe(InstantWalletActivity.this, REQUEST_SCAN, HandleConfigFactory.spendFromColdStorage());
         }
      });

      findViewById(R.id.btTrezor).setOnClickListener(new OnClickListener() {
         @Override
         public void onClick(View arg0) {
            InstantTrezorActivity.callMe(InstantWalletActivity.this, REQUEST_TREZOR);
         }
      });

      findViewById(R.id.btKeepKey).setOnClickListener(new OnClickListener() {
         @Override
         public void onClick(View arg0) {
            InstantKeepKeyActivity.callMe(InstantWalletActivity.this, REQUEST_KEEPKEY);
         }
      });
   }

   private void handleString(String str) {
      Intent intent = StringHandlerActivity.getIntent(this,
              HandleConfigFactory.spendFromColdStorage(), str);
      startActivityForResult(intent, REQUEST_SCAN);
   }

   @Override
   protected void onResume() {
      super.onResume();
      StringHandlerActivity.ParseAbility canHandle = StringHandlerActivity.canHandle(
              HandleConfigFactory.spendFromColdStorage(),
              Utils.getClipboardString(this),
              MbwManager.getInstance(this).getNetwork());
      boolean fromClipboardEnabled = canHandle != StringHandlerActivity.ParseAbility.NO;
      findViewById(R.id.btClipboard).setEnabled(fromClipboardEnabled);
   }

   public void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
      super.onActivityResult(requestCode, resultCode, intent);
      if (requestCode == REQUEST_SCAN) {
         if (resultCode != RESULT_OK) {
            ScanActivity.toastScanError(resultCode, intent, this);
         } else {
            MbwManager mbwManager = MbwManager.getInstance(this);
            ResultType type = (ResultType) intent.getSerializableExtra(StringHandlerActivity.RESULT_TYPE_KEY);
            switch (type) {
               case PRIVATE_KEY:
                  InMemoryPrivateKey key = getPrivateKey(intent);
                  com.mrd.bitillib.crypto.InMemoryPrivateKey btcil_key = getBtcilPrivateKey(intent);
                  // ask user what WIF privkey he/she scanned as there are options
                  final int[] selectedItem = new int[1];
                  CharSequence[] choices = new CharSequence[2];
                  choices[0] = "BTCIL";
                  choices[1] = "BTC";
                  new AlertDialog.Builder(this)
                          .setTitle("Choose blockchain")
                          .setSingleChoiceItems(choices, 0, (dialogInterface, i) -> selectedItem[0] = i)
                          .setPositiveButton(this.getString(R.string.ok), (dialogInterface, i) -> {
                             if (selectedItem[0] == 0) {
                                sendWithAccount(mbwManager.createOnTheFlyBtcilAccount(btcil_key));
                             } else {
                                sendWithAccount(mbwManager.createOnTheFlyAccount(key, Utils.getBtcCoinType()));
                             }
                          })
                          .setNegativeButton(this.getString(R.string.cancel), null)
                          .show();
                  break;
               case ADDRESS:
                  Address address = getAddress(intent);
                  sendWithAccount(mbwManager.createOnTheFlyAccount(address));
                  break;
               case ASSET_URI:
                  AssetUri uri = getAssetUri(intent);
                  sendWithAccount(mbwManager.createOnTheFlyAccount(uri.getAddress()));
                  break;
               case HD_NODE:
                  HdKeyNode hdKeyNode = getHdKeyNode(intent);
                  final WalletManager tempWalletManager = mbwManager.getWalletManager(true);
                  UUID account = tempWalletManager.createAccounts(new UnrelatedHDAccountConfig(Collections.singletonList(hdKeyNode))).get(0);
                  tempWalletManager.startSynchronization(account);
                  sendWithAccount(account);
                  break;
               case SHARE:
                  BipSss.Share share = getShare(intent);
                  BipSsImportActivity.callMe(this, share, StringHandlerActivity.IMPORT_SSS_CONTENT_CODE);
                  break;
            }
         }
      } else if (requestCode == StringHandlerActivity.SEND_INITIALIZATION_CODE) {
         if (resultCode == Activity.RESULT_CANCELED) {
            new Toaster(this).toast(R.string.cancelled, false);
         }
         MbwManager.getInstance(this).forgetColdStorageWalletManager();
         // else {
         // We don't call finish() here, so that this activity stays on the back stack.
         // So the user can click back and scan the next cold storage.
         // }
      } else if (requestCode == REQUEST_TREZOR) {
         if (resultCode == RESULT_OK) {
            finish();
         }
      } else if (requestCode == REQUEST_KEEPKEY) {
         if (resultCode == RESULT_OK) {
            finish();
         }
      } else if (requestCode == IMPORT_WORDLIST) {
         if (resultCode == RESULT_OK) {
            ArrayList<String> wordList = intent.getStringArrayListExtra(EnterWordListActivity.MASTERSEED);
            String password = intent.getStringExtra(EnterWordListActivity.PASSWORD);
            InstantMasterseedActivity.callMe(this, wordList.toArray(new String[0]), password);
         }
      } else if (requestCode == StringHandlerActivity.IMPORT_SSS_CONTENT_CODE) {
         if (resultCode == RESULT_OK) {
            finish();
         }
      } else {
         throw new IllegalStateException("unknown return codes after scanning... " + requestCode + " " + resultCode);
      }
   }

   private void sendWithAccount(UUID account) {
      //we don't know yet where and what to send
      SendInitializationActivity.callMeWithResult(this, account, true,
              StringHandlerActivity.SEND_INITIALIZATION_CODE);
   }

   @Override
   public void finish() {
      // drop and create a new TempWalletManager so that no sensitive data remains in memory
      MbwManager.getInstance(this).forgetColdStorageWalletManager();
      super.finish();
   }
}
