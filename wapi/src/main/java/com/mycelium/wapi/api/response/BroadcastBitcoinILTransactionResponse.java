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

package com.mycelium.wapi.api.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mrd.bitillib.util.BitcoinILSha256Hash;

import java.io.Serializable;

public class BroadcastBitcoinILTransactionResponse implements Serializable {
   private static final long serialVersionUID = 1L;

   /**
    * If true the transaction was accepted by the network. If false the
    * transaction was rejected. Possible causes include: transaction was
    * malformed, transaction spends already spent outputs (double spend)
    */
   @JsonProperty
   public final boolean success;

   /**
    * The ID of the transaction
    */
   @JsonProperty
   public final BitcoinILSha256Hash txid;

   public BroadcastBitcoinILTransactionResponse(@JsonProperty("success") boolean success, @JsonProperty("txid") BitcoinILSha256Hash txid) {
      this.success = success;
      this.txid = txid;
   }

}
