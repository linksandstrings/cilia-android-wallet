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
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import com.google.common.base.Preconditions;
import com.mrd.bitillib.model.BitcoinILAddress;
import com.mrd.bitlib.model.BitcoinAddress;
import com.mrd.bitlib.model.AddressType;
import com.cilia.wallet.MbwManager;
import com.cilia.wallet.R;
import com.cilia.wallet.activity.util.ValueExtensionsKt;
import com.mycelium.wapi.wallet.AddressUtils;
import com.mycelium.wapi.wallet.Address;
import com.mycelium.wapi.wallet.WalletAccount;
import com.mycelium.wapi.wallet.btc.AbstractBtcAccount;
import com.mycelium.wapi.wallet.btcil.AbstractBtcILAccount;
import com.mycelium.wapi.wallet.coins.Balance;
import com.mycelium.wapi.wallet.coins.AssetInfo;

import java.util.UUID;

public class ColdStorageSummaryActivity extends Activity {
   private static final int SEND_MAIN_REQUEST_CODE = 1;
   private MbwManager _mbwManager;
   private WalletAccount _account;

   public static void callMe(Activity currentActivity, UUID account) {
      Intent intent = new Intent(currentActivity, ColdStorageSummaryActivity.class)
              .putExtra("account", account)
              .addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
      currentActivity.startActivity(intent);
   }

   /** Called when the activity is first created. */
   @Override
   public void onCreate(Bundle savedInstanceState) {
      requestWindowFeature(Window.FEATURE_NO_TITLE);
      getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
      super.onCreate(savedInstanceState);
      setContentView(R.layout.cold_storage_summary_activity);
      _mbwManager = MbwManager.getInstance(getApplication());

      // Get intent parameters
      UUID accountId = Preconditions.checkNotNull((UUID) getIntent().getSerializableExtra("account"));
      if (_mbwManager.getWalletManager(true).getAccountIds().contains(accountId)) {
         _account = _mbwManager.getWalletManager(true).getAccount(accountId);
      } else {
         //this can happen if we were in background for long time and then came back
         //just go back and have the user scan again is probably okay as a workaround
         finish();
      }
   }

   @Override
   protected void onResume() {
      updateUi();
      super.onResume();
   }

   private void updateUi(){
      Balance balance = _account.getAccountBalance();

      // Description
      if (_account.canSpend()) {
         ((TextView) findViewById(R.id.tvDescription)).setText(getString(R.string.cs_private_key_description, _account.getCoinType().getName()));
      } else {
         ((TextView) findViewById(R.id.tvDescription)).setText(getString(R.string.cs_address_description, _account.getCoinType().getName()));
      }

      if (_account instanceof AbstractBtcAccount) {
         findViewById(R.id.tvAddress).setVisibility(View.GONE);

         AbstractBtcAccount account = (AbstractBtcAccount) _account;
         BitcoinAddress p2pkhAddress = account.getReceivingAddress(AddressType.P2PKH);
         if (p2pkhAddress != null) {
            final TextView P2PKH = findViewById(R.id.tvAddressP2PKH);
            P2PKH.setVisibility(View.VISIBLE);
            P2PKH.setText(p2pkhAddress.toMultiLineString());
         }
         BitcoinAddress p2shAddress = account.getReceivingAddress(AddressType.P2SH_P2WPKH);
         if (p2shAddress != null) {
            final TextView P2SH = findViewById(R.id.tvAddressP2SH);
            P2SH.setVisibility(View.VISIBLE);
            P2SH.setText(p2shAddress.toMultiLineString());
         }
         BitcoinAddress p2wpkhAddress = account.getReceivingAddress(AddressType.P2WPKH);
         if (p2wpkhAddress != null) {
            final TextView P2WPKH = findViewById(R.id.tvAddressP2WPKH);
            P2WPKH.setVisibility(View.VISIBLE);
            P2WPKH.setText(p2wpkhAddress.toMultiLineString());
         }

      } else if (_account instanceof AbstractBtcILAccount) {
         findViewById(R.id.tvAddress).setVisibility(View.GONE);

         AbstractBtcILAccount account = (AbstractBtcILAccount) _account;
         BitcoinILAddress p2pkhAddress = account.getReceivingAddress(AddressType.P2PKH);
         if (p2pkhAddress != null) {
            final TextView P2PKH = findViewById(R.id.tvAddressP2PKH);
            P2PKH.setVisibility(View.VISIBLE);
            P2PKH.setText(p2pkhAddress.toMultiLineString());
         }
         BitcoinILAddress p2shAddress = account.getReceivingAddress(AddressType.P2SH_P2WPKH);
         if (p2shAddress != null) {
            final TextView P2SH = findViewById(R.id.tvAddressP2SH);
            P2SH.setVisibility(View.VISIBLE);
            P2SH.setText(p2shAddress.toMultiLineString());
         }
         BitcoinILAddress p2wpkhAddress = account.getReceivingAddress(AddressType.P2WPKH);
         if (p2wpkhAddress != null) {
            final TextView P2WPKH = findViewById(R.id.tvAddressP2WPKH);
            P2WPKH.setVisibility(View.VISIBLE);
            P2WPKH.setText(p2wpkhAddress.toMultiLineString());
         }

      } else {
         // Address
         Address receivingAddress = _account.getReceiveAddress();
         ((TextView) findViewById(R.id.tvAddress)).setText(AddressUtils.toMultiLineString(receivingAddress.toString()));
      }

      // Balance
      ((TextView) findViewById(R.id.tvBalance)).setText(ValueExtensionsKt.toStringWithUnit(balance.getSpendable(),
              _mbwManager.getDenomination(_account.getCoinType())));

      Double price = _mbwManager.getCurrencySwitcher().getExchangeRatePrice(_account.getCoinType());

      // Fiat
      TextView tvFiat = findViewById(R.id.tvFiat);
      if (!_mbwManager.hasFiatCurrency() || price == null) {
         tvFiat.setVisibility(View.INVISIBLE);
      } else {
         AssetInfo currency = _mbwManager.getFiatCurrency(_account.getCoinType());
         String converted = _mbwManager.getExchangeRateManager().get(balance.getSpendable()
                 , currency).toFriendlyString();
         tvFiat.setText(getResources().getString(R.string.approximate_fiat_value, currency.getSymbol()
                 , converted != null ? converted : ""));
      }

      // Show/Hide Receiving
      if (balance.pendingReceiving.moreThanZero()) {
         String receivingString = ValueExtensionsKt.toStringWithUnit(balance.pendingReceiving,
                 _mbwManager.getDenomination(_account.getCoinType()));
         String receivingText = getResources().getString(R.string.receiving, receivingString);
         TextView tvReceiving = findViewById(R.id.tvReceiving);
         tvReceiving.setText(receivingText);
         tvReceiving.setVisibility(View.VISIBLE);
      } else {
         findViewById(R.id.tvReceiving).setVisibility(View.GONE);
      }

      // Show/Hide Sending
      if (balance.getSendingToForeignAddresses().moreThanZero()) {
         String sendingString = ValueExtensionsKt.toStringWithUnit(balance.getSendingToForeignAddresses(),
                 _mbwManager.getDenomination(_account.getCoinType()));
         String sendingText = getResources().getString(R.string.sending, sendingString);
         TextView tvSending = findViewById(R.id.tvSending);
         tvSending.setText(sendingText);
         tvSending.setVisibility(View.VISIBLE);
      } else {
         findViewById(R.id.tvSending).setVisibility(View.GONE);
      }

      // Send Button
      Button btSend = findViewById(R.id.btSend);
      if (_account.canSpend()) {
         if (balance.getSpendable().isPositive()) {
            btSend.setEnabled(true);
            btSend.setOnClickListener(new OnClickListener() {
               @Override
               public void onClick(View arg0) {
                  Intent intent = SendCoinsActivity.getIntent(ColdStorageSummaryActivity.this, _account.getId(), true);
                  ColdStorageSummaryActivity.this.startActivityForResult(intent, SEND_MAIN_REQUEST_CODE);
               }
            });
         } else {
            btSend.setEnabled(false);
         }
      } else {
         btSend.setVisibility(View.GONE);
      }
   }

   @Override
   protected void onActivityResult(int requestCode, int resultCode, Intent data) {
      if (requestCode == SEND_MAIN_REQUEST_CODE) {
         setResult(resultCode, data);
         finish();
      } else {
         super.onActivityResult(requestCode, resultCode, data);
      }
   }
}