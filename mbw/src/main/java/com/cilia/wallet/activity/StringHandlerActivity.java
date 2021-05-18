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
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.mrd.bitlib.crypto.Bip38;
import com.mrd.bitlib.crypto.Bip39;
import com.mrd.bitlib.crypto.BipSss;
import com.mrd.bitlib.crypto.HdKeyNode;
import com.mrd.bitlib.crypto.InMemoryPrivateKey;
import com.mrd.bitlib.crypto.MrdExport;
import com.mrd.bitlib.crypto.MrdExport.DecodingException;
import com.mrd.bitlib.crypto.MrdExport.V1.EncryptionParameters;
import com.mrd.bitlib.crypto.MrdExport.V1.InvalidChecksumException;
import com.mrd.bitlib.model.NetworkParameters;
import com.mrd.bitlib.util.HexUtils;
import com.cilia.wallet.MbwManager;
import com.cilia.wallet.R;
import com.cilia.wallet.Record;
import com.cilia.wallet.activity.export.DecryptBip38PrivateKeyActivity;
import com.cilia.wallet.activity.export.MrdDecryptDataActivity;
import com.cilia.wallet.bitid.BitIDSignRequest;
import com.cilia.wallet.content.Action;
import com.cilia.wallet.content.ResultType;
import com.cilia.wallet.content.StringHandleConfig;
import com.mycelium.wallet.pop.PopRequest;
import com.mycelium.wapi.content.AssetUri;
import com.mycelium.wapi.wallet.Address;

import java.util.UUID;

public class StringHandlerActivity extends Activity {
   public static final String CONFIG = "config";
   public static final String CONTENT = "content";
   public static final String RESULT_ERROR = "error";
   public static final String RESULT_PRIVATE_KEY = "privkey";
   public static final String RESULT_BTCIL_PRIVATE_KEY = "btcil_privkey";
   public static final String RESULT_HD_NODE = "hdnode";
   public static final String RESULT_URI_KEY = "uri";
   public static final String RESULT_SHARE_KEY = "share";
   public static final String RESULT_ADDRESS_KEY = "address";
   public static final String RESULT_ADDRESS_STRING_KEY = "address_string";
   public static final String RESULT_TYPE_KEY = "type";
   public static final String RESULT_ACCOUNT_KEY = "account";
   public static final String RESULT_MASTER_SEED_KEY = "master_seed";
   public static final String RESULT_POP_REQUEST = "pop_request";
   public static final String RESULT_BIT_ID_REQUEST = "bit_id_request";

   public static Intent getIntent(Context currentActivity, StringHandleConfig stringHandleConfig, String contentString) {
      return new Intent(currentActivity, StringHandlerActivity.class)
              .putExtra(CONFIG, stringHandleConfig)
              .putExtra(CONTENT, contentString);
   }

   public static ParseAbility canHandle(StringHandleConfig stringHandleConfig, String contentString, NetworkParameters network) {
      if (isMrdEncryptedPrivateKey(contentString)
              || isMrdEncryptedMasterSeed(contentString)
              || Bip38.isBip38PrivateKey(contentString)) {
         return ParseAbility.MAYBE;
      }
      for (Action action : stringHandleConfig.getAllActions()) {
         if (action.canHandle(network, contentString)) {
            return ParseAbility.YES;
         }
      }
      return ParseAbility.NO;
   }

   public enum ParseAbility {YES, MAYBE, NO}

   public static final int IMPORT_ENCRYPTED_PRIVATE_KEY_CODE = 1;
   public static final int IMPORT_ENCRYPTED_MASTER_SEED_CODE = 2;
   public static final int IMPORT_ENCRYPTED_BIP38_PRIVATE_KEY_CODE = 3;
   public static final int IMPORT_SSS_CONTENT_CODE = 4;
   public static final int SEND_INITIALIZATION_CODE = 5;

   private MbwManager mbwManager;
   private StringHandleConfig stringHandleConfig = null;

   @Override
   public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      mbwManager = MbwManager.getInstance(this);
      Intent intent = getIntent();
      stringHandleConfig = Preconditions.checkNotNull((StringHandleConfig) intent.getSerializableExtra(CONFIG));
      String content = Preconditions.checkNotNull(intent.getStringExtra(CONTENT));
      handleContentString(content);
   }

   @Override
   public void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
      if (Activity.RESULT_CANCELED == resultCode) {
         finishError(R.string.cancelled);
         return;
      }

      String content;
      switch (requestCode) {
         case IMPORT_ENCRYPTED_BIP38_PRIVATE_KEY_CODE:
            content = intent.getStringExtra("base58Key");
            break;
         case IMPORT_ENCRYPTED_PRIVATE_KEY_CODE:
            content = handleDecryptedMrdPrivateKey(intent);
            break;
         case IMPORT_ENCRYPTED_MASTER_SEED_CODE:
            content = HexUtils.toHex(handleDecryptedMrdMasterSeed(intent).toBytes(false));
            break;
         case IMPORT_SSS_CONTENT_CODE:
            content = intent.getStringExtra("secret");
            break;
         default:
            //todo: what kind of error should we throw?
            return;
      }
      handleContentString(content);
   }

   private void handleContentString(String content) {
      if (isMrdEncryptedPrivateKey(content)) {
         Optional<String> key = handleMrdEncryptedPrivateKey(content);
         if (key.isPresent()) {
            content = key.get();
         } else {
            // the handleMRdEncrypted method has started the decryption, which will trigger another onActivity
            return;
         }
      } else if (isMrdEncryptedMasterSeed(content)) {
         Optional<Bip39.MasterSeed> masterSeed = handleMrdEncryptedMasterSeed(content);
         if (masterSeed.isPresent()) {
            content = HexUtils.toHex(masterSeed.get().toBytes(false));
         } else {
            // the handleMRdEncrypted method has started the decryption, which will trigger another onActivity
            return;
         }
      } else if (Bip38.isBip38PrivateKey(content)) {
         DecryptBip38PrivateKeyActivity.callMe(this, content, StringHandlerActivity.IMPORT_ENCRYPTED_BIP38_PRIVATE_KEY_CODE);
         //we do not finish cause after decryption another onActivityResult will be called
         return;
      }

      boolean wasHandled = false;
      for (Action action : stringHandleConfig.getAllActions()) {
         if (action.canHandle(mbwManager.getNetwork(), content) && action.handle(this, content)) {
            wasHandled = true;
            break;
         }
      }
      if (!wasHandled) {
         finishError(R.string.unrecognized_format);
      }
   }

   private static boolean isMrdEncryptedPrivateKey(String string) {
      try {
         MrdExport.V1.Header header = MrdExport.V1.extractHeader(string);
         return header.type == MrdExport.V1.Header.Type.UNCOMPRESSED ||
               header.type == MrdExport.V1.Header.Type.COMPRESSED;
      } catch (DecodingException e) {
         return false;
      }
   }

   private Optional<String> handleMrdEncryptedPrivateKey(String encryptedPrivateKey) {
      EncryptionParameters encryptionParameters = mbwManager.getCachedEncryptionParameters();
      // Try and decrypt with cached parameters if we have them
      if (encryptionParameters != null) {
         try {
            String key = MrdExport.V1.decryptPrivateKey(encryptionParameters, encryptedPrivateKey, mbwManager.getNetwork());
            Preconditions.checkNotNull(Record.fromString(key, mbwManager.getNetwork()));
            return Optional.of(key);
         } catch (InvalidChecksumException e) {
            // We cannot reuse the cached password, fall through and decrypt
            // with an entered password
         } catch (DecodingException e) {
            finishError(R.string.unrecognized_format);
            return Optional.absent();
         }
      }
      // Start activity to ask the user to enter a password and decrypt the key
      MrdDecryptDataActivity.callMe(this, encryptedPrivateKey, IMPORT_ENCRYPTED_PRIVATE_KEY_CODE);
      return Optional.absent();
   }

   private String handleDecryptedMrdPrivateKey(Intent intent) {
      String key = intent.getStringExtra("base58Key");
      // Cache the encryption parameters for next import
      EncryptionParameters encryptionParameters = (EncryptionParameters) intent
            .getSerializableExtra("encryptionParameters");
      mbwManager.setCachedEncryptionParameters(encryptionParameters);
      return key;
   }

   private static boolean isMrdEncryptedMasterSeed(String string) {
      try {
         MrdExport.V1.Header header = MrdExport.V1.extractHeader(string);
         return header.type == MrdExport.V1.Header.Type.MASTER_SEED;
      } catch (DecodingException e) {
         return false;
      }
   }

   private Optional<Bip39.MasterSeed> handleMrdEncryptedMasterSeed(String encryptedMasterSeed) {
      EncryptionParameters encryptionParameters = mbwManager.getCachedEncryptionParameters();
      // Try and decrypt with cached parameters if we have them
      if (encryptionParameters != null) {
         try {
            return Optional.of(MrdExport.V1.decryptMasterSeed(encryptionParameters, encryptedMasterSeed, mbwManager.getNetwork()));
         } catch (InvalidChecksumException e) {
            // We cannot reuse the cached password, fall through and decrypt
            // with an entered password
         } catch (DecodingException e) {
            finishError(R.string.unrecognized_format);
            return Optional.absent();
         }
      }
      // Start activity to ask the user to enter a password and decrypt the master seed
      MrdDecryptDataActivity.callMe(this, encryptedMasterSeed, IMPORT_ENCRYPTED_MASTER_SEED_CODE);
      return Optional.absent();
   }

   private Bip39.MasterSeed handleDecryptedMrdMasterSeed(Intent intent) {
      Bip39.MasterSeed masterSeed = (Bip39.MasterSeed) intent.getSerializableExtra("masterSeed");
      // Cache the encryption parameters for next import
      EncryptionParameters encryptionParameters = (EncryptionParameters) intent
            .getSerializableExtra("encryptionParameters");
      mbwManager.setCachedEncryptionParameters(encryptionParameters);
      return masterSeed;
   }

   public void finishError(int resId) {
      Intent result = new Intent();
      result.putExtra(RESULT_ERROR, getResources().getString(resId));
      setResult(RESULT_CANCELED, result);
      finish();
   }

   public void finishOk(AssetUri assetUri) {
      Intent result = new Intent();
      result.putExtra(RESULT_URI_KEY, assetUri);
      result.putExtra(RESULT_TYPE_KEY, ResultType.ASSET_URI);
      setResult(RESULT_OK, result);
      finish();
   }

   public void finishOk(BipSss.Share share) {
      Intent result = new Intent();
      result.putExtra(RESULT_SHARE_KEY, share);
      result.putExtra(RESULT_TYPE_KEY, ResultType.SHARE);
      setResult(RESULT_OK, result);
      finish();
   }

   public void finishOk(InMemoryPrivateKey key , com.mrd.bitillib.crypto.InMemoryPrivateKey btcil_key) {
      Intent result = new Intent();
      result.putExtra(RESULT_PRIVATE_KEY, key);
      result.putExtra(RESULT_BTCIL_PRIVATE_KEY, btcil_key);
      result.putExtra(RESULT_TYPE_KEY, ResultType.PRIVATE_KEY);
      setResult(RESULT_OK, result);
      finish();
   }

   public void finishOk(HdKeyNode hdKey) {
      Intent result = new Intent();
      result.putExtra(RESULT_HD_NODE, hdKey);
      result.putExtra(RESULT_TYPE_KEY, ResultType.HD_NODE);
      setResult(RESULT_OK, result);
      finish();
   }

   public void finishOk(String address) {
      Intent result = new Intent();
      result.putExtra(RESULT_ADDRESS_KEY, address);
      result.putExtra(RESULT_TYPE_KEY, ResultType.ADDRESS_STRING);
      setResult(RESULT_OK, result);
      finish();
   }

   public void finishOk(Address address) {
      Intent result = new Intent();
      result.putExtra(RESULT_ADDRESS_KEY, address);
      result.putExtra(RESULT_TYPE_KEY, ResultType.ADDRESS);
      setResult(RESULT_OK, result);
      finish();
   }

   public void finishOk(UUID account) {
      Intent result = new Intent();
      result.putExtra(RESULT_ACCOUNT_KEY, account);
      result.putExtra(RESULT_TYPE_KEY, ResultType.ACCOUNT);
      setResult(RESULT_OK, result);
      finish();
   }

   public void finishOk(Bip39.MasterSeed masterSeed) {
      Intent result = new Intent();
      result.putExtra(RESULT_MASTER_SEED_KEY, masterSeed);
      result.putExtra(RESULT_TYPE_KEY, ResultType.MASTER_SEED);
      setResult(RESULT_OK, result);
      finish();
   }

   public void finishOk(Uri uri) {
      Intent result = new Intent();
      result.putExtra(RESULT_TYPE_KEY, ResultType.URI);
      result.putExtra(RESULT_URI_KEY, uri);
      setResult(RESULT_OK, result);
      finish();
   }

   public void finishOk(PopRequest popRequest) {
      Intent result = new Intent();
      result.putExtra(RESULT_TYPE_KEY, ResultType.POP_REQUEST);
      result.putExtra(RESULT_POP_REQUEST, popRequest);
      setResult(RESULT_OK, result);
      finish();
   }

   public void finishOk(BitIDSignRequest request) {
      Intent result = new Intent();
      result.putExtra(RESULT_TYPE_KEY, ResultType.BIT_ID_REQUEST);
      result.putExtra(RESULT_BIT_ID_REQUEST, request);
      setResult(RESULT_OK, result);
      finish();
   }

   public void finishOk() {
      Intent result = new Intent();
      result.putExtra(RESULT_TYPE_KEY, ResultType.NONE);
      setResult(RESULT_OK, result);
      finish();
   }

   public NetworkParameters getNetwork() {
      return mbwManager.getNetwork();
   }
   public com.mrd.bitillib.model.NetworkParameters getBitcoinILNetwork() {
      return mbwManager.getBitcoinILNetwork();
   }
}
