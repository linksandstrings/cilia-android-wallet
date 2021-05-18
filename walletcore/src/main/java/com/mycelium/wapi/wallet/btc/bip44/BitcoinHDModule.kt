package com.mycelium.wapi.wallet.btc.bip44

import com.mrd.bitlib.crypto.Bip39
import com.mrd.bitlib.crypto.BipDerivationType
import com.mrd.bitlib.crypto.HdKeyNode
import com.mrd.bitlib.crypto.RandomSource
import com.mrd.bitlib.model.BitcoinAddress
import com.mrd.bitlib.model.NetworkParameters
import com.mycelium.wapi.api.Wapi
import com.mycelium.wapi.wallet.*
import com.mycelium.wapi.wallet.AesKeyCipher
import com.mycelium.wapi.wallet.KeyCipher.InvalidKeyCipher
import com.mycelium.wapi.wallet.btc.*
import com.mycelium.wapi.wallet.btc.bip44.HDAccountContext.Companion.ACCOUNT_TYPE_FROM_MASTERSEED
import com.mycelium.wapi.wallet.btc.bip44.HDAccountContext.Companion.ACCOUNT_TYPE_UNRELATED_X_PRIV
import com.mycelium.wapi.wallet.btc.bip44.HDAccountContext.Companion.ACCOUNT_TYPE_UNRELATED_X_PUB
import com.mycelium.wapi.wallet.btc.bip44.HDAccountContext.Companion.ACCOUNT_TYPE_UNRELATED_X_PUB_EXTERNAL_SIG_KEEPKEY
import com.mycelium.wapi.wallet.btc.bip44.HDAccountContext.Companion.ACCOUNT_TYPE_UNRELATED_X_PUB_EXTERNAL_SIG_LEDGER
import com.mycelium.wapi.wallet.btc.bip44.HDAccountContext.Companion.ACCOUNT_TYPE_UNRELATED_X_PUB_EXTERNAL_SIG_TREZOR
import com.mycelium.wapi.wallet.btc.coins.BitcoinMain
import com.mycelium.wapi.wallet.btc.coins.BitcoinTest
import com.mycelium.wapi.wallet.manager.Config
import com.mycelium.wapi.wallet.manager.WalletModule
import com.mycelium.wapi.wallet.masterseed.MasterSeedManager
import com.mycelium.wapi.wallet.metadata.IMetaDataStorage
import java.util.*
import kotlin.collections.ArrayList


class BitcoinHDModule(internal val backing: BtcWalletManagerBacking<HDAccountContext>,
                      internal val secureStore: SecureKeyValueStore,
                      internal val networkParameters: NetworkParameters,
                      internal var _wapi: Wapi,
                      internal var settings: BTCSettings,
                      internal val metadataStorage: IMetaDataStorage,
                      internal val signatureProviders: ExternalSignatureProviderProxy?,
                      internal val loadingProgressUpdater: LoadingProgressUpdater?,
                      internal val eventHandler: AbstractBtcAccount.EventHandler?) :
        WalletModule(metadataStorage) {

    override fun getAccountById(id: UUID): WalletAccount<*>? {
        return accounts[id]
    }

    init {
        assetsList.add(if (networkParameters.isProdnet) BitcoinMain.get() else BitcoinTest.get())
    }

    override fun setCurrencySettings(currencySettings: CurrencySettings) {
        this.settings = currencySettings as BTCSettings
    }

    private val accounts = mutableMapOf<UUID, HDAccount>()

    override val id = ID

    override fun getAccounts(): List<WalletAccount<*>> = accounts.values.toList()

    override fun loadAccounts(): Map<UUID, WalletAccount<*>> {
        LoadingProgressTracker.subscribe(loadingProgressUpdater!!)
        val result = mutableMapOf<UUID, WalletAccount<*>>()
        val contexts = backing.loadBip44AccountContexts()
        var counter = 1
        for (context in contexts) {
            if (context.accountIndex > 999)
            {
                if (loadingProgressUpdater.status is LoadingProgressStatus.Loading) {
                    LoadingProgressTracker.setStatus(LoadingProgressStatus.Migrating())
                }
                val keyManagerMap = HashMap<BipDerivationType, HDAccountKeyManager>()

                loadKeyManagers(context, keyManagerMap)

                val accountBacking = backing.getBip44AccountBacking(context.id)
                val account: WalletAccount<*>
                when (context.accountType) {
                    ACCOUNT_TYPE_UNRELATED_X_PUB ->
                        account = HDPubOnlyAccount(context, keyManagerMap, networkParameters, accountBacking, _wapi)
                    ACCOUNT_TYPE_UNRELATED_X_PUB_EXTERNAL_SIG_TREZOR ->
                        account = HDAccountExternalSignature(context, keyManagerMap, networkParameters, accountBacking, _wapi,
                                signatureProviders!!.get(ACCOUNT_TYPE_UNRELATED_X_PUB_EXTERNAL_SIG_TREZOR), settings.changeAddressModeReference)
                    ACCOUNT_TYPE_UNRELATED_X_PUB_EXTERNAL_SIG_LEDGER ->
                        account = HDAccountExternalSignature(context, keyManagerMap, networkParameters, accountBacking, _wapi,
                                signatureProviders!!.get(ACCOUNT_TYPE_UNRELATED_X_PUB_EXTERNAL_SIG_LEDGER), settings.changeAddressModeReference)
                    ACCOUNT_TYPE_UNRELATED_X_PUB_EXTERNAL_SIG_KEEPKEY ->
                        account = HDAccountExternalSignature(context, keyManagerMap, networkParameters, accountBacking, _wapi,
                                signatureProviders!!.get(ACCOUNT_TYPE_UNRELATED_X_PUB_EXTERNAL_SIG_KEEPKEY), settings.changeAddressModeReference)
                    else -> account = HDAccount(context, keyManagerMap, networkParameters, accountBacking, _wapi,
                            settings.changeAddressModeReference)
                }
                result[account.id] = account
                account.label = readLabel(account.id)
                accounts[account.id] = account as HDAccount
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

    private fun loadKeyManagers(context: HDAccountContext, keyManagerMap: HashMap<BipDerivationType, HDAccountKeyManager>) {
        for (entry in context.indexesMap) {
            when (context.accountType) {
                ACCOUNT_TYPE_FROM_MASTERSEED -> keyManagerMap[entry.key] = HDAccountKeyManager(context.accountIndex, networkParameters, secureStore, entry.key)
                ACCOUNT_TYPE_UNRELATED_X_PRIV -> {
                    val subKeyStore = secureStore.getSubKeyStore(context.accountSubId)
                    keyManagerMap[entry.key] = HDAccountKeyManager(context.accountIndex, networkParameters, subKeyStore, entry.key)
                }
                ACCOUNT_TYPE_UNRELATED_X_PUB,
                ACCOUNT_TYPE_UNRELATED_X_PUB_EXTERNAL_SIG_TREZOR,
                ACCOUNT_TYPE_UNRELATED_X_PUB_EXTERNAL_SIG_LEDGER,
                ACCOUNT_TYPE_UNRELATED_X_PUB_EXTERNAL_SIG_KEEPKEY -> {
                    val subKeyStore = secureStore.getSubKeyStore(context.accountSubId)
                    keyManagerMap[entry.key] = HDPubOnlyAccountKeyManager(context.accountIndex, networkParameters, subKeyStore, entry.key)
                }
            }
        }
    }

    override fun createAccount(config: Config): WalletAccount<*> {
        val result: WalletAccount<*>
        when (config) {
            is UnrelatedHDAccountConfig -> {
                val accountIndex = 1000  // use any index for this account, as we don't know and we don't care
                val keyManagerMap = HashMap<BipDerivationType, HDAccountKeyManager>()
                val derivationTypes = ArrayList<BipDerivationType>()

                // get a subKeyStorage, to ensure that the data for this key does not get mixed up
                // with other derived or imported keys.
                val secureStorage = secureStore.createNewSubKeyStore()

                for (hdKeyNode in config.hdKeyNodes) {
                    val derivationType = hdKeyNode.derivationType
                    derivationTypes.add(derivationType)
                    if (hdKeyNode.isPrivateHdKeyNode) {
                        try {
                            keyManagerMap[derivationType] = HDAccountKeyManager.createFromAccountRoot(hdKeyNode, networkParameters,
                                    accountIndex, secureStorage, AesKeyCipher.defaultKeyCipher(), derivationType)
                        } catch (invalidKeyCipher: InvalidKeyCipher) {
                            throw RuntimeException(invalidKeyCipher)
                        }

                    } else {
                        keyManagerMap[derivationType] = HDPubOnlyAccountKeyManager.createFromPublicAccountRoot(hdKeyNode,
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
                val context = HDAccountContext(id, accountIndex, false, accountType,
                        secureStorage.subId, derivationTypes)
                backing.beginTransaction()
                try {
                    backing.createBip44AccountContext(context)
                    // Get the accountBacking for the new account
                    val accountBacking = backing.getBip44AccountBacking(context.id)

                    // Create actual account
                    result = if (config.hdKeyNodes[0].isPrivateHdKeyNode) {
                        HDAccount(context, keyManagerMap, networkParameters, accountBacking, _wapi, settings.changeAddressModeReference)
                    } else {
                        HDPubOnlyAccount(context, keyManagerMap, networkParameters, accountBacking, _wapi)
                    }

                    // Finally persist context and add account
                    context.persist(accountBacking)
                    backing.setTransactionSuccessful()
                } finally {
                    backing.endTransaction()
                }
            }
            is AdditionalHDAccountConfig -> {
                // Get the master seed
                val masterSeed = MasterSeedManager.getMasterSeed(secureStore, AesKeyCipher.defaultKeyCipher())

                val accountIndex = getCurrentBip44Index() + 1
//                val currentIndex = getCurrentBip44Index()
//                val accountIndex = 0
                // Create the base keys for the account
                val keyManagerMap = HashMap<BipDerivationType, HDAccountKeyManager>()
                for (derivationType in BipDerivationType.values()) {
                    // Generate the root private key
                    val root = HdKeyNode.fromSeed(masterSeed.bip32Seed, derivationType)
                    keyManagerMap[derivationType] = HDAccountKeyManager.createNew(root, networkParameters, accountIndex,
                            secureStore, AesKeyCipher.defaultKeyCipher(), derivationType)
                }

                // Generate the context for the account
                val context = HDAccountContext(keyManagerMap[BipDerivationType.BIP44]!!.accountId,
                        accountIndex, false, settings.defaultAddressType)

                backing.beginTransaction()
                try {
                    backing.createBip44AccountContext(context)

                    // Get the accountBacking for the new account
                    val accountBacking = backing.getBip44AccountBacking(context.id)

                    // Create actual account
                    result = HDAccount(context, keyManagerMap, networkParameters, accountBacking, _wapi,
                            settings.changeAddressModeReference)

                    // Finally persist context and add account
                    context.persist(accountBacking)
                    backing.setTransactionSuccessful()
                } finally {
                    backing.endTransaction()
                }
            }
            is ExternalSignaturesAccountConfig -> {
                val accountIndex = config.accountIndex
//                val accountIndex = 0
//                val accountIndex = 0
                val keyManagerMap = HashMap<BipDerivationType, HDAccountKeyManager>()
                val derivationTypes = ArrayList<BipDerivationType>()

                // get a subKeyStorage, to ensure that the data for this key does not get mixed up
                // with other derived or imported keys.
                val secureStorage = secureStore.createNewSubKeyStore()

                for (hdKeyNode in config.hdKeyNodes) {
                    val derivationType = hdKeyNode.derivationType
                    derivationTypes.add(derivationType)
                    keyManagerMap[derivationType] = HDPubOnlyAccountKeyManager.createFromPublicAccountRoot(hdKeyNode,
                            networkParameters, accountIndex, secureStorage, derivationType)
                }
                val id = keyManagerMap[derivationTypes[0]]!!.accountId

                // Generate the context for the account
                val context = HDAccountContext(id, accountIndex, false, config.provider.biP44AccountType,
                        secureStorage.subId, derivationTypes, settings.defaultAddressType)
                backing.beginTransaction()
                try {
                    backing.createBip44AccountContext(context)

                    // Get the accountBacking for the new account
                    val accountBacking = backing.getBip44AccountBacking(context.id)

                    // Create actual account
                    result = HDAccountExternalSignature(context, keyManagerMap, networkParameters, accountBacking, _wapi,
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
        accounts[result.id] = result as HDAccount
        result.setEventHandler(eventHandler)

        result.label = createLabel(config, result.id)
        return result
    }

    private fun createLabel(config: Config, id: UUID): String? {
        // can't fetch hardware wallet's account labels for temp accounts
        // as we don't pass a signatureProviders and no need anyway
        if (config is ExternalSignaturesAccountConfig && signatureProviders == null)
            return null

        val label = createLabel(getBaseLabel(config))
        storeLabel(id, label)
        return label
    }

    private fun getBaseLabel(cfg: Config): String {
        return when (cfg) {
            is AdditionalHDAccountConfig -> "Account " + (getCurrentBip44Index() - 999)
            is ExternalSignaturesAccountConfig ->
                signatureProviders!!.get(cfg.provider.biP44AccountType).labelOrDefault + " #" + (cfg.hdKeyNodes[0].index + 1)
            is UnrelatedHDAccountConfig -> if (cfg.hdKeyNodes[0].isPrivateHdKeyNode) "Account 1" else "Imported"
            else -> throw IllegalArgumentException("Unsupported config")
        }
    }

    fun getAccountByIndex(index: Int): HDAccount? {
        return accounts.values.firstOrNull { it.accountIndex == index }
    }

    fun getCurrentBip44Index() = accounts.values
        .filter { it.isDerivedFromInternalMasterseed && (it.accountIndex > 999) }
        .maxBy { it.accountIndex }
        ?.accountIndex
        ?: 999

    fun hasBip32MasterSeed(): Boolean = secureStore.hasCiphertextValue(MasterSeedManager.MASTER_SEED_ID)

    /**
     * To create an additional HD account from the master seed, the master seed must be present and
     * all existing master seed accounts must have had transactions (no gap accounts)
     */
//    fun canCreateAdditionalBip44Account(): Boolean =
//            hasBip32MasterSeed() && accounts.values.filter { it.isDerivedFromInternalMasterseed }
//                    .all { it.hasHadActivity() }

    fun canCreateAdditionalBip44Account(): Boolean =
            hasBip32MasterSeed() && accounts.values.filter { it.isDerivedFromInternalMasterseed && (it.accountIndex > 999) }
                    .all { it.hasHadActivity() }

    override fun canCreateAccount(config: Config): Boolean {
        return config is UnrelatedHDAccountConfig ||
                (config is AdditionalHDAccountConfig && canCreateAdditionalBip44Account()) ||
                config is ExternalSignaturesAccountConfig
    }


    override fun deleteAccount(walletAccount: WalletAccount<*>, keyCipher: KeyCipher): Boolean =
            if (walletAccount is HDAccount || walletAccount is HDPubOnlyAccount) {
                accounts.remove(walletAccount.id)
                backing.deleteBip44AccountContext(walletAccount.id)
                true
            } else {
                false
            }

    fun upgradeExtSigAccount(accountRoots: List<HdKeyNode>, account: HDAccountExternalSignature): Boolean =
            account.upgradeAccount(accountRoots, secureStore)

    companion object {
        const val ID: String = "BitcoinHD"
    }

    fun getGapsBug(): Set<Int> {
        val accountIndices = accounts.values
                .filter { it.isDerivedFromInternalMasterseed && (it.accountIndex > 999)}
                .map { it.accountIndex }
        val allIndices = 1000..(accountIndices.max() ?: 1000)
        return allIndices.subtract(accountIndices)
    }

    fun getGapAddresses(cipher: KeyCipher): List<BitcoinAddress> {
        val gaps: Set<Int> = getGapsBug()
        // Get the master seed
        val masterSeed: Bip39.MasterSeed = MasterSeedManager.getMasterSeed(secureStore, cipher)
        val tempSecureBacking = InMemoryBtcWalletManagerBacking()

        val tempSecureKeyValueStore = SecureKeyValueStore(tempSecureBacking, RandomSource {
            // randomness not needed for the temporary keystore
        })

        val addresses: MutableList<BitcoinAddress> = mutableListOf()
        for (gapIndex in gaps.indices) {
            for (derivationType: BipDerivationType in BipDerivationType.values()) {
                // Generate the root private key
                val root: HdKeyNode = HdKeyNode.fromSeed(masterSeed.bip32Seed, derivationType)
                val keyManager: HDAccountKeyManager = HDAccountKeyManager.createNew(root, networkParameters, gapIndex, tempSecureKeyValueStore, cipher, derivationType)
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
                val keyManagerMap = mutableMapOf<BipDerivationType, HDAccountKeyManager>()
                for (derivationType in BipDerivationType.values()) {
                    // Generate the root private key
                    val root = HdKeyNode.fromSeed(masterSeed.bip32Seed, derivationType)
                    keyManagerMap[derivationType] = HDAccountKeyManager.createNew(root, networkParameters, accountIndex,
                            secureStore, cipher, derivationType)
                }


                // Generate the context for the account
                val context = HDAccountContext(
                        keyManagerMap[BipDerivationType.BIP44]!!.accountId, accountIndex, false, settings.defaultAddressType)
                backing.createBip44AccountContext(context)

                // Get the backing for the new account
                val accountBacking: Bip44BtcAccountBacking = backing.getBip44AccountBacking(context.id)

                // Create actual account
                val account = HDAccount(context, keyManagerMap, networkParameters, accountBacking, _wapi, settings.changeAddressModeReference)

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
fun WalletManager.getBTCBip44Accounts() = getAccounts().filter { it is HDAccount && it.isVisible }

/**
 * Get the active BTC HD-accounts managed by the wallet manager
 * , excluding on-the-fly-accounts and single-key accounts
 *
 * @return the list of accounts
 */
fun WalletManager.getActiveHDAccounts(): List<WalletAccount<*>> = getAccounts().filter { it is HDAccount && it.isActive }

/**
 * Get the active HD-accounts managed by the wallet manager, excluding on-the-fly-accounts and single-key accounts
 *
 * @return the list of accounts
 */
fun WalletManager.getActiveMasterseedHDAccounts(): List<WalletAccount<*>> = getAccounts().filter { it is HDAccount && it.isDerivedFromInternalMasterseed }

/**
 * Get the active accounts managed by the wallet manager
 *
 * @return the list of accounts
 */
fun WalletManager.getActiveMasterseedAccounts(): List<WalletAccount<*>> = getAccounts().filter { it.isActive && it.isDerivedFromInternalMasterseed}

