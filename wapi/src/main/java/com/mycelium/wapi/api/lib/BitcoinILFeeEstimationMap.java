package com.mycelium.wapi.api.lib;

import com.megiontechnologies.BitcoinILs;

import java.util.HashMap;
import java.util.Map;

public class BitcoinILFeeEstimationMap extends HashMap<Integer, BitcoinILs> {
   public BitcoinILFeeEstimationMap(Map<? extends Integer, ? extends BitcoinILs> m) {
      super(m);
   }

   public BitcoinILFeeEstimationMap() {
   }

   public BitcoinILFeeEstimationMap(int initialCapacity) {
      super(initialCapacity);
   }

   public BitcoinILFeeEstimationMap(int initialCapacity, float loadFactor) {
      super(initialCapacity, loadFactor);
   }

   public BitcoinILs put(Integer key, BitcoinILs value, double correction) {
      BitcoinILs valueAdjusted = BitcoinILs.valueOf((long)((double)value.getLongValue() * correction)) ;
      return super.put(key, valueAdjusted);
   }
}
