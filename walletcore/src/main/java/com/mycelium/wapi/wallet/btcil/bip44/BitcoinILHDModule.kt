package com.mycelium.wapi.wallet.btcil.bip44

import com.mrd.bitillib.crypto.BipDerivationType
import com.mrd.bitillib.crypto.HdKeyNode
import com.mrd.bitlib.crypto.RandomSource
import com.mrd.bitillib.model.BitcoinILAddress
import com.mrd.bitillib.model.NetworkParameters
import com.mrd.bitlib.crypto.Bip39
import com.mycelium.wapi.api.Wapi
import com.mycelium.wapi.wallet.*
import com.mycelium.wapi.wallet.AesKeyCipher
import com.mycelium.wapi.wallet.KeyCipher.InvalidKeyCipher
import com.mycelium.wapi.wallet.btc.BtcWalletManagerBacking
import com.mycelium.wapi.wallet.btcil.*
import com.mycelium.wapi.wallet.btcil.bip44.HDAccountContextIL.Companion.ACCOUNT_TYPE_FROM_MASTERSEED
import com.mycelium.wapi.wallet.btcil.bip44.HDAccountContextIL.Companion.ACCOUNT_TYPE_UNRELATED_X_PRIV
import com.mycelium.wapi.wallet.btcil.bip44.HDAccountContextIL.Companion.ACCOUNT_TYPE_UNRELATED_X_PUB
import com.mycelium.wapi.wallet.btcil.bip44.HDAccountContextIL.Companion.ACCOUNT_TYPE_UNRELATED_X_PUB_EXTERNAL_SIG_KEEPKEY
import com.mycelium.wapi.wallet.btcil.bip44.HDAccountContextIL.Companion.ACCOUNT_TYPE_UNRELATED_X_PUB_EXTERNAL_SIG_LEDGER
import com.mycelium.wapi.wallet.btcil.bip44.HDAccountContextIL.Companion.ACCOUNT_TYPE_UNRELATED_X_PUB_EXTERNAL_SIG_TREZOR
import com.mycelium.wapi.wallet.btcil.coins.BitcoinILMain
import com.mycelium.wapi.wallet.btcil.coins.BitcoinILTest
import com.mycelium.wapi.wallet.manager.Config
import com.mycelium.wapi.wallet.manager.WalletModule
import com.mycelium.wapi.wallet.masterseed.MasterSeedManager
import com.mycelium.wapi.wallet.metadata.IMetaDataStorage
import java.util.*
import kotlin.collections.ArrayList


class BitcoinILHDModule(internal val backing: BtcWalletManagerBacking<HDAccountContextIL>,
                        internal val secureStore: SecureKeyValueStore,
                        internal val networkParameters: NetworkParameters,
                        internal var _wapi: Wapi,
                        internal var settings: BTCSettingsIL,
                        internal val metadataStorage: IMetaDataStorage,
                        internal val signatureProviders: ExternalSignatureProviderProxyIL?,
                        internal val loadingProgressUpdater: LoadingProgressUpdater?,
                        internal val eventHandler: AbstractBtcILAccount.EventHandler?) :
        WalletModule(metadataStorage) {

    override fun getAccountById(id: UUID): WalletAccount<*>? {
        return accounts[id]
    }

    init {
        assetsList.add(if (networkParameters.isProdnet) BitcoinILMain.get() else BitcoinILTest.get())
    }

    override fun setCurrencySettings(currencySettings: CurrencySettings) {
        this.settings = currencySettings as BTCSettingsIL
    }

    private val accounts = mutableMapOf<UUID, HDAccountIL>()

    override val id = ID

    override fun getAccounts(): List<WalletAccount<*>> = accounts.values.toList()

    override fun loadAccounts(): Map<UUID, WalletAccount<*>> {
        LoadingProgressTracker.subscribe(loadingProgressUpdater!!)
        val result = mutableMapOf<UUID, WalletAccount<*>>()
        val contexts = backing.loadBip44AccountILContexts()
        var counter = 1
        for (context in contexts) {
            if (context.accountIndex < 1000){
                if (loadingProgressUpdater.status is LoadingProgressStatus.Loading) {
                    LoadingProgressTracker.setStatus(LoadingProgressStatus.Migrating())
                }
                val keyManagerMap = HashMap<BipDerivationType, HDAccountKeyManagerIL>()

                loadKeyManagers(context, keyManagerMap)

                val accountBacking = backing.getBip44AccountILBacking(context.id)
                val account: WalletAccount<*>
                when (context.accountType) {
                    ACCOUNT_TYPE_UNRELATED_X_PUB ->
                        account = HDPubOnlyAccountIL(context, keyManagerMap, networkParameters, accountBacking, _wapi)
                    ACCOUNT_TYPE_UNRELATED_X_PUB_EXTERNAL_SIG_TREZOR ->
                        account = HDAccountExternalSignatureIL(context, keyManagerMap, networkParameters, accountBacking, _wapi,
                                signatureProviders!!.get(ACCOUNT_TYPE_UNRELATED_X_PUB_EXTERNAL_SIG_TREZOR), settings.changeAddressModeReference)
                    ACCOUNT_TYPE_UNRELATED_X_PUB_EXTERNAL_SIG_LEDGER ->
                        account = HDAccountExternalSignatureIL(context, keyManagerMap, networkParameters, accountBacking, _wapi,
                                signatureProviders!!.get(ACCOUNT_TYPE_UNRELATED_X_PUB_EXTERNAL_SIG_LEDGER), settings.changeAddressModeReference)
                    ACCOUNT_TYPE_UNRELATED_X_PUB_EXTERNAL_SIG_KEEPKEY ->
                        account = HDAccountExternalSignatureIL(context, keyManagerMap, networkParameters, accountBacking, _wapi,
                                signatureProviders!!.get(ACCOUNT_TYPE_UNRELATED_X_PUB_EXTERNAL_SIG_KEEPKEY), settings.changeAddressModeReference)
                    else -> account = HDAccountIL(context, keyManagerMap, networkParameters, accountBacking, _wapi,
                            settings.changeAddressModeReference)
                }
                result[account.id] = account
                account.label = readLabel(account.id)
                accounts[account.id] = account as HDAccountIL
                account.setEventHandler(eventHandler)
                LoadingProgressTracker.clearLastFullUpdateTime()

                if (loadingProgressUpdater.status is LoadingProgressStatus.Migrating || loadingProgressUpdater.status is LoadingProgressStatus.MigratingNOfMHD) {
                    LoadingProgressTracker.setStatus(LoadingProgressStatus.MigratingNOfMHD(Integer.toString(counter++), Integer.toString(contexts.size)))
                } else {
                    LoadingProgressTracker.setStatus(LoadingProgressStatus.LoadingNOfMHD(Integer.toString(counter++), Integer.toString(contexts.size)))
                }
            }
        }
        return result
    }

    private fun loadKeyManagers(context: HDAccountContextIL, keyManagerMap: HashMap<BipDerivationType, HDAccountKeyManagerIL>) {
        for (entry in context.indexesMap) {
            when (context.accountType) {
                ACCOUNT_TYPE_FROM_MASTERSEED -> keyManagerMap[entry.key] = HDAccountKeyManagerIL(context.accountIndex, networkParameters, secureStore, entry.key)
                ACCOUNT_TYPE_UNRELATED_X_PRIV -> {
                    val subKeyStore = secureStore.getSubKeyStore(context.accountSubId)
                    keyManagerMap[entry.key] = HDAccountKeyManagerIL(context.accountIndex, networkParameters, subKeyStore, entry.key)
                }
                ACCOUNT_TYPE_UNRELATED_X_PUB,
                ACCOUNT_TYPE_UNRELATED_X_PUB_EXTERNAL_SIG_TREZOR,
                ACCOUNT_TYPE_UNRELATED_X_PUB_EXTERNAL_SIG_LEDGER,
                ACCOUNT_TYPE_UNRELATED_X_PUB_EXTERNAL_SIG_KEEPKEY -> {
                    val subKeyStore = secureStore.getSubKeyStore(context.accountSubId)
                    keyManagerMap[entry.key] = HDPubOnlyAccountKeyManagerIL(context.accountIndex, networkParameters, subKeyStore, entry.key)
                }
            }
        }
    }

    override fun createAccount(config: Config): WalletAccount<*> {
        val result: WalletAccount<*>
        when (config) {
            is UnrelatedHDAccountConfigIL -> {
                val accountIndex = 0  // use any index for this account, as we don't know and we don't care
                val keyManagerMap = HashMap<BipDerivationType, HDAccountKeyManagerIL>()
                val derivationTypes = ArrayList<BipDerivationType>()

                // get a subKeyStorage, to ensure that the data for this key does not get mixed up
                // with other derived or imported keys.
                val secureStorage = secureStore.createNewSubKeyStore()

                for (hdKeyNode in config.hdKeyNodes) {
                    val derivationType = hdKeyNode.derivationType
                    derivationTypes.add(derivationType)
                    if (hdKeyNode.isPrivateHdKeyNode) {
                        try {
                            keyManagerMap[derivationType] = HDAccountKeyManagerIL.createFromAccountRoot(hdKeyNode, networkParameters,
                                    accountIndex, secureStorage, AesKeyCipher.defaultKeyCipher(), derivationType)
                        } catch (invalidKeyCipher: InvalidKeyCipher) {
                            throw RuntimeException(invalidKeyCipher)
                        }

                    } else {
                        keyManagerMap[derivationType] = HDPubOnlyAccountKeyManagerIL.createFromPublicAccountRoot(hdKeyNode,
                                networkParameters, accountIndex, secureStorage, derivationType)
                    }
                }
                val id = keyManagerMap[derivationTypes[0]]!!.accountId

                // Generate the context for the account
                val accountType = if (config.hdKeyNodes[0].isPrivateHdKeyNode) {
                    ACCOUNT_TYPE_UNRELATED_X_PRIV
                } else {
                    ACCOUNT_TYPE_UNRELATED_X_PUB
                }
                val context = HDAccountContextIL(id, accountIndex, false, accountType,
                        secureStorage.subId, derivationTypes)
                backing.beginTransaction()
                try {
                    backing.createBip44AccountILContext(context)
                    // Get the accountBacking for the new account
                    val accountBacking = backing.getBip44AccountILBacking(context.id)

                    // Create actual account
                    result = if (config.hdKeyNodes[0].isPrivateHdKeyNode) {
                        HDAccountIL(context, keyManagerMap, networkParameters, accountBacking, _wapi, settings.changeAddressModeReference)
                    } else {
                        HDPubOnlyAccountIL(context, keyManagerMap, networkParameters, accountBacking, _wapi)
                    }

                    // Finally persist context and add account
                    context.persist(accountBacking)
                    backing.setTransactionSuccessful()
                } finally {
                    backing.endTransaction()
                }
            }
            is AdditionalHDAccountConfigIL -> {
                // Get the master seed
                val masterSeed = MasterSeedManager.getMasterSeed(secureStore, AesKeyCipher.defaultKeyCipher())

                val accountIndex = getCurrentBip44Index() + 1
//                val accountIndex = 1
                val currentIndex = getCurrentBip44Index()
                // Create the base keys for the account
                val keyManagerMap = HashMap<BipDerivationType, HDAccountKeyManagerIL>()
                for (derivationType in BipDerivationType.values()) {
                    // Generate the root private key
                    val root = HdKeyNode.fromSeed(masterSeed.bip32Seed, derivationType)
                    keyManagerMap[derivationType] = HDAccountKeyManagerIL.createNew(root, networkParameters, accountIndex,
                            secureStore, AesKeyCipher.defaultKeyCipher(), derivationType)
                }
                val id = keyManagerMap[BipDerivationType.BIP44]!!.accountId
                // Generate the context for the account
                val context = HDAccountContextIL(id,
                        accountIndex, false, settings.defaultAddressType)

                backing.beginTransaction()
                try {
                    backing.createBip44AccountILContext(context)

                    // Get the accountBacking for the new account
                    val accountBacking = backing.getBip44AccountILBacking(context.id)

                    // Create actual account
                    result = HDAccountIL(context, keyManagerMap, networkParameters, accountBacking, _wapi,
                            settings.changeAddressModeReference)

                    // Finally persist context and add account
                    context.persist(accountBacking)
                    backing.setTransactionSuccessful()
                } finally {
                    backing.endTransaction()
                }
            }
            is ExternalSignaturesAccountConfigIL -> {
                val accountIndex = config.accountIndex
//                val accountIndex = 1
                val keyManagerMap = HashMap<BipDerivationType, HDAccountKeyManagerIL>()
                val derivationTypes = ArrayList<BipDerivationType>()

                // get a subKeyStorage, to ensure that the data for this key does not get mixed up
                // with other derived or imported keys.
                val secureStorage = secureStore.createNewSubKeyStore()

                for (hdKeyNode in config.hdKeyNodes) {
                    val derivationType = hdKeyNode.derivationType
                    derivationTypes.add(derivationType)
                    keyManagerMap[derivationType] = HDPubOnlyAccountKeyManagerIL.createFromPublicAccountRoot(hdKeyNode,
                            networkParameters, accountIndex, secureStorage, derivationType)
                }
                val id = keyManagerMap[derivationTypes[0]]!!.accountId

                // Generate the context for the account
                val context = HDAccountContextIL(id, accountIndex, false, config.provider.biP44AccountType,
                        secureStorage.subId, derivationTypes, settings.defaultAddressType)
                backing.beginTransaction()
                try {
                    backing.createBip44AccountILContext(context)

                    // Get the accountBacking for the new account
                    val accountBacking = backing.getBip44AccountILBacking(context.id)

                    // Create actual account
                    result = HDAccountExternalSignatureIL(context, keyManagerMap, networkParameters, accountBacking, _wapi,
                            config.provider, settings.changeAddressModeReference)

                    // Finally persist context and add account
                    context.persist(accountBacking)
                    backing.setTransactionSuccessful()
                } finally {
                    backing.endTransaction()
                }
            }
            else -> throw IllegalStateException("Account can't be created")
        }
        accounts[result.id] = result as HDAccountIL
        result.setEventHandler(eventHandler)

        result.label = createLabel(config, result.id)
        return result
    }

    private fun createLabel(config: Config, id: UUID): String? {
        // can't fetch hardware wallet's account labels for temp accounts
        // as we don't pass a signatureProviders and no need anyway
        if (config is ExternalSignaturesAccountConfigIL && signatureProviders == null)
            return null

        val label = createLabel(getBaseLabel(config))
        storeLabel(id, label)
        return label
    }

    private fun getBaseLabel(cfg: Config): String {
        return when (cfg) {
            is AdditionalHDAccountConfigIL -> "BitcoinIL " + (getCurrentBip44Index() + 1)
//            is AdditionalHDAccountConfigIL -> "BitcoinIL HD"
            is ExternalSignaturesAccountConfigIL ->
                signatureProviders!!.get(cfg.provider.biP44AccountType).labelOrDefault + " #" + (cfg.hdKeyNodes[0].index + 1)
            is UnrelatedHDAccountConfigIL -> if (cfg.hdKeyNodes[0].isPrivateHdKeyNode) "BitcoinIL 1" else "Imported"
            else -> throw IllegalArgumentException("Unsupported config")
        }
    }

    fun getAccountByIndex(index: Int): HDAccountIL? {
        return accounts.values.firstOrNull { it.accountIndex == index }
    }

    fun getCurrentBip44Index() = accounts.values
        .filter { it.isDerivedFromInternalMasterseed }
        .maxBy { it.accountIndex }
        ?.accountIndex
        ?: -1

    fun hasBip32MasterSeed(): Boolean = secureStore.hasCiphertextValue(MasterSeedManager.MASTER_SEED_ID)

    /**
     * To create an additional HD account from the master seed, the master seed must be present and
     * all existing master seed accounts must have had transactions (no gap accounts)
     */
//    fun canCreateAdditionalBip44Account(): Boolean =
//            hasBip32MasterSeed() && accounts.values.filter { it.isDerivedFromInternalMasterseed }
//                    .all { it.hasHadActivity() }

    fun canCreateAdditionalBip44Account(): Boolean =
            hasBip32MasterSeed() && accounts.values.filter { it.isDerivedFromInternalMasterseed  && (it.accountIndex < 1000)}
                    .all { it.hasHadActivity() }

    override fun canCreateAccount(config: Config): Boolean {
        return config is UnrelatedHDAccountConfigIL ||
                (config is AdditionalHDAccountConfigIL && canCreateAdditionalBip44Account()) ||
                config is ExternalSignaturesAccountConfigIL
    }

    override fun deleteAccount(walletAccount: WalletAccount<*>, keyCipher: KeyCipher): Boolean =
            if (walletAccount is HDAccountIL || walletAccount is HDPubOnlyAccountIL) {
                accounts.remove(walletAccount.id)
                backing.deleteBip44AccountContext(walletAccount.id)
                true
            } else {
                false
            }

    fun upgradeExtSigAccount(accountRoots: List<HdKeyNode>, account: HDAccountExternalSignatureIL): Boolean =
            account.upgradeAccount(accountRoots, secureStore)

    companion object {
        const val ID: String = "BitcoinILHD"
    }

    fun getGapsBug(): Set<Int> {
        val accountIndices = accounts.values
                .filter { it.isDerivedFromInternalMasterseed  && (it.accountIndex < 1000)}
                .map { it.accountIndex }
        val allIndices = 0..(accountIndices.max() ?: 0)
        return allIndices.subtract(accountIndices)
    }

    fun getGapAddresses(cipher: KeyCipher): List<BitcoinILAddress> {
        val gaps: Set<Int> = getGapsBug()
        // Get the master seed
        val masterSeed: Bip39.MasterSeed = MasterSeedManager.getMasterSeed(secureStore, cipher)
        val tempSecureBacking = InMemoryBtcILWalletManagerBacking()

        val tempSecureKeyValueStore = SecureKeyValueStore(tempSecureBacking, RandomSource {
            // randomness not needed for the temporary keystore
        })

        val addresses: MutableList<BitcoinILAddress> = mutableListOf()
        for (gapIndex in gaps.indices) {
            for (derivationType: BipDerivationType in BipDerivationType.values()) {
                // Generate the root private key
                val root: HdKeyNode = HdKeyNode.fromSeed(masterSeed.bip32Seed, derivationType)
                val keyManager: HDAccountKeyManagerIL = HDAccountKeyManagerIL.createNew(root, networkParameters, gapIndex, tempSecureKeyValueStore, cipher, derivationType)
                addresses.add(keyManager.getAddress(false, 0)) // get first external address for the account in the gap
            }
        }
        return addresses
    }

    fun createArchivedGapFiller(cipher: KeyCipher, accountIndex: Int): UUID {
        // Get the master seed
        val masterSeed: Bip39.MasterSeed = MasterSeedManager.getMasterSeed(secureStore, cipher)

        synchronized(accounts) {
            backing.beginTransaction()
            try {
                // Create the base keys for the account
                val keyManagerMap = mutableMapOf<BipDerivationType, HDAccountKeyManagerIL>()
                for (derivationType in BipDerivationType.values()) {
                    // Generate the root private key
                    val root = HdKeyNode.fromSeed(masterSeed.bip32Seed, derivationType)
                    keyManagerMap[derivationType] = HDAccountKeyManagerIL.createNew(root, networkParameters, accountIndex,
                            secureStore, cipher, derivationType)
                }


                // Generate the context for the account
                val context = HDAccountContextIL(
                        keyManagerMap[BipDerivationType.BIP44]!!.accountId, accountIndex, false, settings.defaultAddressType)
                backing.createBip44AccountILContext(context)

                // Get the backing for the new account
                val accountBacking: Bip44BtcILAccountBacking = backing.getBip44AccountILBacking(context.id)

                // Create actual account
                val account = HDAccountIL(context, keyManagerMap, networkParameters, accountBacking, _wapi, settings.changeAddressModeReference)

                // Finally persist context and add account
                context.persist(accountBacking)
                account.archiveAccount()
                accounts[account.id] = account
                backing.setTransactionSuccessful()
                return account.id
            } finally {
                backing.endTransaction()
            }
        }
    }
}

/**
 * Get the active BTC HD-accounts managed by the wallet manager
 * , excluding on-the-fly-accounts and single-key accounts
 *
 * @return the list of accounts
 */
fun WalletManager.getBTCILBip44Accounts() = getAccounts().filter { it is HDAccountIL && it.isVisible }

/**
 * Get the active BTC HD-accounts managed by the wallet manager
 * , excluding on-the-fly-accounts and single-key accounts
 *
 * @return the list of accounts
 */
fun WalletManager.getActiveHDAccountsIL(): List<WalletAccount<*>> = getAccounts().filter { it is HDAccountIL && it.isActive }

/**
 * Get the active HD-accounts managed by the wallet manager, excluding on-the-fly-accounts and single-key accounts
 *
 * @return the list of accounts
 */
fun WalletManager.getActiveMasterseedHDAccountsIL(): List<WalletAccount<*>> = getAccounts().filter { it is HDAccountIL && it.isDerivedFromInternalMasterseed }

/**
 * Get the active accounts managed by the wallet manager
 *
 * @return the list of accounts
 */
fun WalletManager.getActiveMasterseedAccounts(): List<WalletAccount<*>> = getAccounts().filter { it.isActive && it.isDerivedFromInternalMasterseed }

