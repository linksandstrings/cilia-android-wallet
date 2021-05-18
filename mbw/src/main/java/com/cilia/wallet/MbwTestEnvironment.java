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

package com.cilia.wallet;

import com.mrd.bitlib.model.NetworkParameters;
import com.mycelium.net.HttpEndpoint;
import com.mycelium.net.HttpsEndpoint;
import com.mycelium.net.ServerEndpoints;
import com.mycelium.net.TorHttpsEndpoint;
import com.cilia.wallet.activity.util.BlockExplorer;
import com.cilia.wallet.external.BuySellServiceDescriptor;
import com.cilia.wallet.external.LocalTraderServiceDescription;
import com.cilia.wallet.external.SepaServiceDescription;
import com.cilia.wallet.external.SimplexServiceDescription;
import com.mycelium.wapi.wallet.btc.coins.BitcoinTest;
import com.mycelium.wapi.wallet.btcil.coins.BitcoinILTest;
import com.mycelium.wapi.wallet.eth.coins.EthTest;
import com.mycelium.wapi.wallet.fio.coins.FIOTest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MbwTestEnvironment extends MbwEnvironment {
   @Override
   public NetworkParameters getNetwork() {
      return NetworkParameters.testNetwork;
   }

   @Override
   public com.mrd.bitillib.model.NetworkParameters getBitcoinILNetwork(){
      return com.mrd.bitillib.model.NetworkParameters.testNetwork;
   }

   /**
    * Local Trader API for testnet
    */
   private static final ServerEndpoints testnetLtEndpoints = new ServerEndpoints(new HttpEndpoint[]{
           new HttpsEndpoint("https://mws30.mycelium.com/lttestnet", "ED:C2:82:16:65:8C:4E:E1:C7:F6:A2:2B:15:EC:30:F9:CD:48:F8:DB"),
           new TorHttpsEndpoint("https://grrhi6bwwpiarsfl.onion/lttestnet", "D0:09:70:40:98:71:E0:0E:62:08:1A:36:4C:BC:C7:2E:51:40:50:4C"),
   });

   @Override
   public ServerEndpoints getLtEndpoints() {
      return testnetLtEndpoints;
   }

   /**
    * Wapi
    */
   private static final ServerEndpoints testnetWapiEndpoints = new ServerEndpoints(new HttpEndpoint[]{
           new HttpsEndpoint("https://mws30.mycelium.com/wapitestnet", "ED:C2:82:16:65:8C:4E:E1:C7:F6:A2:2B:15:EC:30:F9:CD:48:F8:DB"),
           new TorHttpsEndpoint("https://ti4v3ipng2pqutby.onion/wapitestnet", "75:3E:8A:87:FA:95:9F:C6:1A:DB:2A:09:43:CE:52:74:27:B1:80:4B"),
   });

   @Override
   public ServerEndpoints getWapiEndpoints() {
      return testnetWapiEndpoints;
   }

   /**
    * Available BlockExplorers
    * <p>
    * The first is the default block explorer if the requested one is not available
    */
   private static final Map<String, List<BlockExplorer>> testnetExplorerClearEndpoints = new HashMap<String, List<BlockExplorer>>() {
      {
         put(BitcoinTest.get().getName(), new ArrayList<BlockExplorer>() {{
            add(new BlockExplorer("SBT", "smartbit", "https://testnet.smartbit.com.au/address/", "https://testnet.smartbit.com.au/tx/", null, null));
            add(new BlockExplorer("BTL", "blockTrail", "https://www.blocktrail.com/tBTC/address/", "https://www.blocktrail.com/tBTC/tx/", null, null));
            add(new BlockExplorer("BPY", "BitPay", "https://test-insight.bitpay.com/address/", "https://test-insight.bitpay.com/tx/", null, null));
            add(new BlockExplorer("BEX", "blockExplorer", "http://blockexplorer.com/testnet/address/", "https://blockexplorer.com/testnet/tx/", null, null));
            add(new BlockExplorer("BCY", "blockCypher", "https://live.blockcypher.com/btc-testnet/address/", "https://live.blockcypher.com/btc-testnet/tx/", null, null));
         }});
         put(BitcoinILTest.get().getName(), new ArrayList<BlockExplorer>() {{
            add(new BlockExplorer("CLA", "cilia", "https://testexplorer.cilia.camp/address/", "https://explorer.cilia.camp/tx/", null, null));
            add(new BlockExplorer("BIL", "bitcoinil", "https://testexplorer.bitcoinil.org/address/", "https://testexplorer.bitcoinil.org/tx/", null, null));
         }});
         put(EthTest.INSTANCE.getName(), new ArrayList<BlockExplorer>() {{
            add(new BlockExplorer("ETS", "etherscan.io", "https://ropsten.etherscan.io/address/", "https://ropsten.etherscan.io/tx/0x", null, null));
         }});
         put(FIOTest.INSTANCE.getName(), new ArrayList<BlockExplorer>() {{
            add(new BlockExplorer("FBI", "fio.bloks.io", "https://fio-test.bloks.io/account/", "https://fio-test.bloks.io/transaction/", null, null));
            add(new BlockExplorer("EFI", "explorer.fioprotocol.io", "https://explorer.testnet.fioprotocol.io/account/", "https://explorer.testnet.fioprotocol.io/transaction/", null, null));
         }});
      }
   };

   public Map<String, List<BlockExplorer>> getBlockExplorerMap() {
      return new HashMap<>(testnetExplorerClearEndpoints);
   }

   public List<BuySellServiceDescriptor> getBuySellServices() {
      return new ArrayList<BuySellServiceDescriptor>() {{
         add(new SimplexServiceDescription());
         add(new SepaServiceDescription());
         add(new LocalTraderServiceDescription());
      }};
   }
}
