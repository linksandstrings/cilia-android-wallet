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

package com.mycelium.wapi.model;

import com.google.common.base.Optional;
import com.mrd.bitillib.model.BitcoinILAddress;
import com.mrd.bitillib.util.BitcoinILSha256Hash;
import com.mycelium.wapi.wallet.ConfirmationRiskProfileLocal;
import com.mycelium.wapi.wallet.currency.CurrencyValue;

import java.util.List;


public class BitcoinILTransactionSummary implements Comparable<BitcoinILTransactionSummary> {
   public final BitcoinILSha256Hash txid;
   public final CurrencyValue value;
   public final boolean isIncoming;
   public final long time;
   public final int height;
   public final int confirmations;
   public final boolean isQueuedOutgoing;
   public final Optional<ConfirmationRiskProfileLocal> confirmationRiskProfile;
   public final Optional<BitcoinILAddress> destinationAddress;
   public final List<BitcoinILAddress> toAddresses;

   public BitcoinILTransactionSummary(BitcoinILSha256Hash txid, CurrencyValue value, boolean isIncoming, long time, int height,
                                      int confirmations, boolean isQueuedOutgoing, ConfirmationRiskProfileLocal confirmationRiskProfile,
                                      Optional<BitcoinILAddress> destinationAddress, List<BitcoinILAddress> toAddresses) {
      this.txid = txid;
      this.value = value;
      this.isIncoming = isIncoming;
      this.time = time;
      this.height = height;
      this.confirmations = confirmations;
      this.isQueuedOutgoing = isQueuedOutgoing;
      this.confirmationRiskProfile = Optional.fromNullable(confirmationRiskProfile);
      this.destinationAddress = destinationAddress;
      this.toAddresses = toAddresses;
   }

   @Override
   public int compareTo(BitcoinILTransactionSummary other) {
      // First sort by confirmations
      if (confirmations < other.confirmations) {
         return 1;
      } else if (confirmations > other.confirmations) {
         return -1;
      } else {
         // Then sort by outgoing status
         if (isQueuedOutgoing != other.isQueuedOutgoing) {
            return isQueuedOutgoing ? 1 : -1;
         }
         // Finally sort by time
         if (time < other.time) {
            return -1;
         } else if (time > other.time) {
            return 1;
         }
         return 0;
      }
   }

   @Override
   public int hashCode() {
      return txid.hashCode();
   }

   @Override
   public boolean equals(Object obj) {
      if (obj == this) {
         return true;
      }
      if (!(obj instanceof BitcoinILTransactionSummary)) {
         return false;
      }
      BitcoinILTransactionSummary other = (BitcoinILTransactionSummary) obj;
      return other.txid.equals(this.txid);
   }

   public boolean canCancel() {
      return isQueuedOutgoing;
   }

   public boolean hasAddressBook() {
      return destinationAddress.isPresent();
   }

   public boolean hasDetails() {
      return true;
   }
}
