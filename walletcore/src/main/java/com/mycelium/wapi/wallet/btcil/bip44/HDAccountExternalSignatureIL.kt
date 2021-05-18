package com.mycelium.wapi.wallet.btcil.bip44

import com.mrd.bitillib.UnsignedTransaction
import com.mrd.bitillib.crypto.BipDerivationType
import com.mrd.bitillib.crypto.HdKeyNode
import com.mrd.bitillib.model.NetworkParameters
import com.mrd.bitillib.model.BitcoinILTransaction
import com.mycelium.wapi.api.Wapi
import com.mycelium.wapi.wallet.ExportableAccountIL
import com.mycelium.wapi.wallet.KeyCipher
import com.mycelium.wapi.wallet.LoadingProgressTracker
import com.mycelium.wapi.wallet.SecureKeyValueStore
import com.mycelium.wapi.wallet.btcil.Bip44BtcILAccountBacking
import com.mycelium.wapi.wallet.btcil.ChangeAddressModeIL
import com.mycelium.wapi.wallet.btcil.ReferenceIL

class HDAccountExternalSignatureIL(
        context: HDAccountContextIL,
        keyManagerMap: MutableMap<BipDerivationType, HDAccountKeyManagerIL>,
        network: NetworkParameters,
        backing: Bip44BtcILAccountBacking,
        wapi: Wapi,
        private val sigProvider: ExternalSignatureProviderIL,
        changeAddressModeReference: ReferenceIL<ChangeAddressModeIL>
) : HDAccountIL(context, keyManagerMap, network, backing, wapi, changeAddressModeReference) {

    @Override
    fun getBIP44AccountType() = sigProvider.biP44AccountType

    @Throws(KeyCipher.InvalidKeyCipher::class)
    override fun signTransaction(unsigned: UnsignedTransaction, cipher: KeyCipher): BitcoinILTransaction? {
        checkNotArchived()
        if (!isValidEncryptionKey(cipher)) {
            throw KeyCipher.InvalidKeyCipher()
        }

        // Get the signatures from the external signature provider
        return sigProvider.getSignedTransaction(unsigned, this)
    }

    override fun canSpend() = true

    override fun getExportData(cipher: KeyCipher): ExportableAccountIL.Data {
        // we dont have a private key we can export, always set it as absent
        val publicDataMap = keyManagerMap.keys.map { derivationType ->
            derivationType to (keyManagerMap[derivationType]!!.publicAccountRoot
                    .serialize(_network, derivationType))
        }.toMap()
        return ExportableAccountIL.Data(null, publicDataMap)
    }

    fun upgradeAccount(accountRoots: List<HdKeyNode>, secureKeyValueStore: SecureKeyValueStore): Boolean {
        if (context.indexesMap.size < accountRoots.size) {
            for (root in accountRoots) {
                if (context.indexesMap[root.derivationType] == null) {
                    keyManagerMap[root.derivationType] = HDPubOnlyAccountKeyManagerIL.createFromPublicAccountRoot(root, _network,
                            context.accountIndex, secureKeyValueStore.getSubKeyStore(context.accountSubId), root.derivationType)
                    context.indexesMap[root.derivationType] = AccountIndexesContextIL(-1, -1, 0)
                }
            }
            externalAddresses = initAddressesMap()
            internalAddresses = initAddressesMap()
            ensureAddressIndexes()

            LoadingProgressTracker.clearLastFullUpdateTime()
            context.persist(backing)
            return true
        }
        return false
    }

    override fun canSign(): Boolean = false
}
