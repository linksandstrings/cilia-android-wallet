package com.mycelium.wapi.wallet.btc.bip44;

import com.mrd.bitlib.UnsignedTransaction;
import com.mrd.bitlib.model.BitcoinTransaction;

/**
 * Hardware wallets provide signatures so accounts can work without the private keys themselves.
 */
public interface ExternalSignatureProvider {
   BitcoinTransaction getSignedTransaction(UnsignedTransaction unsigned, HDAccountExternalSignature forAccount);
   int getBIP44AccountType();
   String getLabelOrDefault();
}
