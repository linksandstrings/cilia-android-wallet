package com.mycelium.wapi.wallet.btc.bip44

import com.mrd.bitillib.crypto.InMemoryPrivateKey
import com.mrd.bitlib.UnsignedTransaction
import com.mrd.bitlib.crypto.BipDerivationType
import com.mrd.bitlib.crypto.HdKeyNode
import com.mrd.bitlib.model.NetworkParameters
import com.mrd.bitlib.model.BitcoinTransaction
import com.mycelium.wapi.api.Wapi
import com.mycelium.wapi.wallet.ExportableAccount
import com.mycelium.wapi.wallet.KeyCipher
import com.mycelium.wapi.wallet.LoadingProgressTracker
import com.mycelium.wapi.wallet.SecureKeyValueStore
import com.mycelium.wapi.wallet.btc.Bip44BtcAccountBacking
import com.mycelium.wapi.wallet.btc.ChangeAddressMode
import com.mycelium.wapi.wallet.btc.Reference

class HDAccountExternalSignature(
        context: HDAccountContext,
        keyManagerMap: MutableMap<BipDerivationType, HDAccountKeyManager>,
        network: NetworkParameters,
        backing: Bip44BtcAccountBacking,
        wapi: Wapi,
        private val sigProvider: ExternalSignatureProvider,
        changeAddressModeReference: Reference<ChangeAddressMode>
) : HDAccount(context, keyManagerMap, network, backing, wapi, changeAddressModeReference) {

    @Override
    fun getBIP44AccountType() = sigProvider.biP44AccountType

    @Throws(KeyCipher.InvalidKeyCipher::class)
    override fun signTransaction(unsigned: UnsignedTransaction, cipher: KeyCipher): BitcoinTransaction? {
        checkNotArchived()
        if (!isValidEncryptionKey(cipher)) {
            throw KeyCipher.InvalidKeyCipher()
        }

        // Get the signatures from the external signature provider
        return sigProvider.getSignedTransaction(unsigned, this)
    }

    override fun canSpend() = true

    override fun getExportData(cipher: KeyCipher): ExportableAccount.Data {
        // we dont have a private key we can export, always set it as absent
        val publicDataMap = keyManagerMap.keys.map { derivationType ->
            derivationType to (keyManagerMap[derivationType]!!.publicAccountRoot
                    .serialize(_network, derivationType))
        }.toMap()
        return ExportableAccount.Data(null, publicDataMap)
    }

    fun upgradeAccount(accountRoots: List<HdKeyNode>, secureKeyValueStore: SecureKeyValueStore): Boolean {
        if (context.indexesMap.size < accountRoots.size) {
            for (root in accountRoots) {
                if (context.indexesMap[root.derivationType] == null) {
                    keyManagerMap[root.derivationType] = HDPubOnlyAccountKeyManager.createFromPublicAccountRoot(root, _network,
                            context.accountIndex, secureKeyValueStore.getSubKeyStore(context.accountSubId), root.derivationType)
                    context.indexesMap[root.derivationType] = AccountIndexesContext(-1, -1, 0)
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
    override fun getBitcoinILPrivateKey(cipher: KeyCipher?): InMemoryPrivateKey {
        TODO("Not yet implemented")
    }
}
