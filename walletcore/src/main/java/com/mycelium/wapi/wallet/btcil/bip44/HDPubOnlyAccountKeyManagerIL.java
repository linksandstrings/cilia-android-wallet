package com.mycelium.wapi.wallet.btcil.bip44;

import com.google.common.base.Preconditions;
import com.mrd.bitillib.crypto.BipDerivationType;
import com.mrd.bitillib.model.NetworkParameters;
import com.mrd.bitillib.util.ByteReader;
import com.mrd.bitillib.crypto.HdKeyNode;
import com.mycelium.wapi.wallet.SecureKeyValueStore;


public class HDPubOnlyAccountKeyManagerIL extends HDAccountKeyManagerIL {

   public static HDPubOnlyAccountKeyManagerIL createFromPublicAccountRoot(HdKeyNode accountRoot,
                                                                          NetworkParameters network, int accountIndex, SecureKeyValueStore secureKeyValueStore, BipDerivationType derivationType) {

      // store the public accountRoot as plaintext
      secureKeyValueStore.storePlaintextValue(getAccountNodeId(network, accountIndex, derivationType),
              accountRoot.getPublicNode().toCustomByteFormat());

      // Create the external chain root. Store the public node in plain text
      HdKeyNode externalChainRoot = accountRoot.createChildNode(0);
      secureKeyValueStore.storePlaintextValue(getChainNodeId(network, accountIndex, false, derivationType),
              externalChainRoot.getPublicNode().toCustomByteFormat());

      // Create the change chain root. Store the public node in plain text
      HdKeyNode changeChainRoot = accountRoot.createChildNode(1);
      secureKeyValueStore.storePlaintextValue(getChainNodeId(network, accountIndex, true, derivationType),
              changeChainRoot.getPublicNode().toCustomByteFormat());
      return new HDPubOnlyAccountKeyManagerIL(accountIndex, network, secureKeyValueStore, derivationType);
   }

   public HDPubOnlyAccountKeyManagerIL(int accountIndex, NetworkParameters network,
                                       SecureKeyValueStore secureKeyValueStore, BipDerivationType derivationType) {
      super(secureKeyValueStore, derivationType);
      _accountIndex = accountIndex;
      _network = network;

      // Load the external and internal public nodes
      try {
         _publicAccountRoot =
                 HdKeyNode.fromCustomByteformat(secureKeyValueStore.getPlaintextValue(getAccountNodeId(network,
                         accountIndex, derivationType)));
         Preconditions.checkState(!_publicAccountRoot.isPrivateHdKeyNode());
         _publicExternalChainRoot =
                 HdKeyNode.fromCustomByteformat(secureKeyValueStore.getPlaintextValue(getChainNodeId(network,
                         accountIndex, false, derivationType)));
         Preconditions.checkState(!_publicExternalChainRoot.isPrivateHdKeyNode());
         _publicChangeChainRoot =
                 HdKeyNode.fromCustomByteformat(secureKeyValueStore.getPlaintextValue(getChainNodeId(network,
                         accountIndex, true, derivationType)));
         Preconditions.checkState(!_publicChangeChainRoot.isPrivateHdKeyNode());
      } catch (ByteReader.InsufficientBytesException e) {
         throw new RuntimeException(e);
      }
   }
}
