package com.mycelium.wapi.wallet.btcil.bip44;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

public class ExternalSignatureProviderProxyIL {

   private final Map<Integer, ExternalSignatureProviderIL> signatureProviders;

   public ExternalSignatureProviderProxyIL(ExternalSignatureProviderIL... signatureProviders) {
      ImmutableMap.Builder<Integer, ExternalSignatureProviderIL> mapBuilder
            = new ImmutableMap.Builder<Integer, ExternalSignatureProviderIL>();
      for (ExternalSignatureProviderIL signatureProvider : signatureProviders) {
         mapBuilder.put(signatureProvider.getBIP44AccountType(), signatureProvider);
      }

      this.signatureProviders = mapBuilder.build();
   }

   public ExternalSignatureProviderIL get(int bip44AccountType) {
      return signatureProviders.get(bip44AccountType);
   }

}
