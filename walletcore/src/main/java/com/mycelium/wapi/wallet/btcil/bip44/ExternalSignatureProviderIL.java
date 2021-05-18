package com.mycelium.wapi.wallet.btcil.bip44;

import com.mrd.bitillib.UnsignedTransaction;
import com.mrd.bitillib.model.BitcoinILTransaction;

/**
 * Hardware wallets provide signatures so accounts can work without the private keys themselves.
 */
public interface ExternalSignatureProviderIL {
   BitcoinILTransaction getSignedTransaction(UnsignedTransaction unsigned, HDAccountExternalSignatureIL forAccount);
   int getBIP44AccountType();
   String getLabelOrDefault();
}
