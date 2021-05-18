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

package com.cilia.wallet.activity;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;

import com.mrd.bitlib.crypto.InMemoryPrivateKey;
import com.cilia.wallet.BuildConfig;
import com.cilia.wallet.MbwManager;
import com.cilia.wallet.R;
import com.cilia.wallet.activity.modern.Toaster;
import com.cilia.wallet.event.AccountChanged;
import com.cilia.wallet.event.AccountCreated;
import com.cilia.wallet.persistence.MetadataStorage;
import com.mycelium.wapi.api.response.Feature;
import com.mycelium.wapi.wallet.AesKeyCipher;
import com.mycelium.wapi.wallet.colu.PrivateColuConfig;
import com.mycelium.wapi.wallet.colu.coins.ColuMain;
import com.mycelium.wapi.wallet.colu.coins.MASSCoin;
import com.mycelium.wapi.wallet.colu.coins.MASSCoinTest;
import com.mycelium.wapi.wallet.colu.coins.MTCoin;
import com.mycelium.wapi.wallet.colu.coins.MTCoinTest;
import com.mycelium.wapi.wallet.colu.coins.RMCCoin;
import com.mycelium.wapi.wallet.colu.coins.RMCCoinTest;

import java.util.UUID;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

import static android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN;

public class AddColuAccountActivity extends Activity {
    public static final String TAG = "AddColuAccountActivity";

    @BindView(R.id.btColuAddAccount) Button btColuAddAccount;

    ColuMain selectedColuAsset;

    public static Intent getIntent(Context context) {
        return new Intent(context, AddColuAccountActivity.class);
    }

    public static final String RESULT_KEY = "account";
    private MbwManager _mbwManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        getWindow().setFlags(FLAG_FULLSCREEN, FLAG_FULLSCREEN);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.add_colu_account_activity);
        _mbwManager = MbwManager.getInstance(this);
        ButterKnife.bind(this);
        btColuAddAccount.setText(getString(R.string.colu_create_account, ""));
    }

    void setButtonEnabled() {
        btColuAddAccount.setEnabled(true);
    }


    @OnClick(R.id.btColuAddAccount)
    void onColuAddAccountClick() {
        if(selectedColuAsset != null) {
            createColuAccountProtected(selectedColuAsset);
        } else {
            new Toaster(AddColuAccountActivity.this).toast(R.string.colu_select_an_account_type, true);
        }
        //displayTemporaryMessage();
    }

    @OnClick({R.id.radio_mycelium_tokens, R.id.radio_mass_tokens, R.id.radio_rmc_tokens})
    public void onRadioButtonClicked(View view) {
        // Is the button now checked?
        boolean checked = ((RadioButton) view).isChecked();
        ColuMain assetType;
        String name;
        // Check which radio button was clicked
        switch (view.getId()) {
        case R.id.radio_mycelium_tokens:
            if (BuildConfig.FLAVOR.equals("prodnet")) {
                assetType = MTCoin.INSTANCE;
            } else {
                assetType = MTCoinTest.INSTANCE;
            }
            name = "MT";
            break;
        case R.id.radio_mass_tokens:
            if (BuildConfig.FLAVOR.equals("prodnet")) {
                assetType = MASSCoin.INSTANCE;
            } else {
                assetType = MASSCoinTest.INSTANCE;
            }
            name = "Mass";
            break;
        case R.id.radio_rmc_tokens:
            if (BuildConfig.FLAVOR.equals("prodnet")) {
                assetType = RMCCoin.INSTANCE;
            } else {
                assetType = RMCCoinTest.INSTANCE;
            }
            name = "RMC";
            break;
        default:
            return;
        }
        if (checked) {
            selectedColuAsset = assetType;
        }
        btColuAddAccount.setEnabled(true);
        new Toaster(this).toast(name + " selected", true);
    }

    private void createColuAccountProtected(final ColuMain coluAsset) {
        _mbwManager.getVersionManager().showFeatureWarningIfNeeded(
        AddColuAccountActivity.this, Feature.COLU_NEW_ACCOUNT, true, new Runnable() {
            @Override
            public void run() {
                _mbwManager.runPinProtectedFunction(AddColuAccountActivity.this, new Runnable() {
                    @Override
                    public void run() {
                        new AddColuAsyncTask(coluAsset).execute();

                    }
                });
            }
        }
        );
    }

    private class AddColuAsyncTask extends AsyncTask<Void, Integer, UUID> {
        private final boolean alreadyHadColuAccount;
        private final ColuMain coluAsset;
        private final ProgressDialog progressDialog;

        public AddColuAsyncTask(ColuMain coluAsset) {
            this.coluAsset = coluAsset;
            this.alreadyHadColuAccount = _mbwManager.getMetadataStorage().isPairedService(MetadataStorage.PAIRED_SERVICE_COLU);
            progressDialog = ProgressDialog.show(AddColuAccountActivity.this, getString(R.string.colu), getString(R.string.colu_creating_account, coluAsset.getName()));
            progressDialog.setCancelable(false);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            progressDialog.show();
        }

        @Override
        protected UUID doInBackground(Void... params) {
            _mbwManager.getMetadataStorage().setPairedService(MetadataStorage.PAIRED_SERVICE_COLU, true);
            try {
                InMemoryPrivateKey key = new InMemoryPrivateKey(_mbwManager.getRandomSource(), true);
                return _mbwManager.getWalletManager(false)
                            .createAccounts(new PrivateColuConfig(key, coluAsset,AesKeyCipher.defaultKeyCipher())).get(0);
            } catch (Exception e) {
                Log.d(TAG, "Error while creating Colored Coin account for asset " + coluAsset.getName() + ": " + e.getMessage(), e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(UUID account) {
            if (progressDialog != null && progressDialog.isShowing() && !isDestroyed()) {
                progressDialog.dismiss();
            }
            if (account != null) {
                MbwManager.getEventBus().post(new AccountCreated(account));
                MbwManager.getEventBus().post(new AccountChanged(account));
                Intent result = new Intent();
                result.putExtra(RESULT_KEY, account);
                setResult(RESULT_OK, result);
                finish();
            } else {
                // something went wrong - clean up the half ready coluManager
                new Toaster(AddColuAccountActivity.this).toast(R.string.colu_unable_to_create_account, true);
                _mbwManager.getMetadataStorage().setPairedService(MetadataStorage.PAIRED_SERVICE_COLU, alreadyHadColuAccount);
            }
            setButtonEnabled();
        }
    }

    @Override
    public void onResume() {
        MbwManager.getEventBus().register(this);
        setButtonEnabled();
        super.onResume();
    }


    @Override
    public void onPause() {
        MbwManager.getEventBus().unregister(this);
        _mbwManager.getVersionManager().closeDialog();
        super.onPause();
    }

}
