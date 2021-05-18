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

package com.mycelium.lt.api.params;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mrd.bitlib.model.BitcoinAddress;

public class BtcILSellPriceParameters {
   @JsonProperty
   public BitcoinAddress peerId;
   @JsonProperty
   public BitcoinAddress ownerId;
   @JsonProperty
   public String currency;
   @JsonProperty
   public int fiatTraded;
   @JsonProperty
   public String priceFormulaId;
   @JsonProperty
   public double premium;

   public BtcILSellPriceParameters(@JsonProperty("ownerId") BitcoinAddress ownerId, @JsonProperty("peerId") BitcoinAddress peerId,
                                   @JsonProperty("currency") String currency, @JsonProperty("fiatTraded") int fiatTraded,
                                   @JsonProperty("priceFormulaId") String priceFormulaId, @JsonProperty("premium") double premium) {
      this.ownerId = ownerId;
      this.peerId = peerId;
      this.currency = currency;
      this.fiatTraded = fiatTraded;
      this.priceFormulaId = priceFormulaId;
      this.premium = premium;
   }

   @SuppressWarnings("unused")
   private BtcILSellPriceParameters() {
      // For Jackson
   }
}
