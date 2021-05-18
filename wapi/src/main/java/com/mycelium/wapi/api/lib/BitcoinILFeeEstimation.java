package com.mycelium.wapi.api.lib;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import com.megiontechnologies.BitcoinILs;
import com.mrd.bitillib.model.BitcoinILTransaction;

import java.io.Serializable;
import java.util.Date;

public class BitcoinILFeeEstimation implements Serializable {
   public static final BitcoinILFeeEstimation DEFAULT = new BitcoinILFeeEstimation(
           new BitcoinILFeeEstimationMap(
                   new ImmutableMap.Builder<Integer, BitcoinILs>()
                           .put(1,  BitcoinILs.valueOf(8000))   // 8sat/B
                           .put(3,  BitcoinILs.valueOf(6000))   // 6sat/B
                           .put(10, BitcoinILs.valueOf(3000))   // 3sat/B
                           .put(20, BitcoinILs.valueOf(1000))   // 1sat/B
                           .build())
           , new Date(0)
   );

   @JsonProperty
   private final BitcoinILFeeEstimationMap feeForNBlocks;

   @JsonProperty
   private final Date validFor; // timestamp of this fee estimation

   public BitcoinILFeeEstimation(@JsonProperty("feeForNBlocks") BitcoinILFeeEstimationMap feeForNBlocks,
                                 @JsonProperty("validFor") Date validFor) {
      this.feeForNBlocks = feeForNBlocks;
      this.validFor = validFor;
   }

   @JsonIgnore
   public BitcoinILs getEstimation(int nBlocks){
      if (feeForNBlocks == null) {
         return null;
      }

      while (!feeForNBlocks.containsKey(nBlocks) && nBlocks>=0){
         nBlocks--;
      }

      if (nBlocks <= 0) {
         throw new IllegalArgumentException("nBlocks invalid");
      }

      BitcoinILs bitcoins = feeForNBlocks.get(nBlocks);

      // check if we got a sane value, otherwise return a default value
      if (bitcoins.getLongValue() >= BitcoinILTransaction.MAX_MINER_FEE_PER_KB){
         return DEFAULT.getEstimation(nBlocks);
      } else {
         return bitcoins;
      }
   }

   @JsonIgnore
   public Date getValidFor(){
      return validFor;
   }

   @Override
   public String toString() {
      return "FeeEstimation: " +
            "feeForNBlocks=" + feeForNBlocks +
            '}';
   }

   /**
    * @param maxAge maximum age in millis until it is considered expired
    * @return true if the timestamp `validFor` of this FeeEstimation is older than maxAge. Else false.
    */
   public boolean isExpired(long maxAge) {
      final long feeAgeMillis = System.currentTimeMillis() - validFor.getTime();
      return feeAgeMillis > maxAge;
   }
}

