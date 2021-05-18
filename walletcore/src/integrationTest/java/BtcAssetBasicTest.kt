import com.google.common.base.Optional
import com.mrd.bitlib.crypto.Bip39
import com.mrd.bitlib.crypto.InMemoryPrivateKey
import com.mrd.bitlib.crypto.RandomSource
import com.mrd.bitlib.model.AddressType
import com.mrd.bitlib.model.NetworkParameters
import com.mycelium.generated.wallet.database.WalletDB
import com.mycelium.net.HttpEndpoint
import com.mycelium.net.HttpsEndpoint
import com.mycelium.net.ServerEndpoints
import com.mycelium.wapi.api.WapiClientElectrumX
import com.mycelium.wapi.api.jsonrpc.TcpEndpoint
import com.mycelium.wapi.wallet.*
import com.mycelium.wapi.wallet.btc.*
import com.mycelium.wapi.wallet.btc.bip44.*
import com.mycelium.wapi.wallet.btc.single.*
import com.mycelium.wapi.wallet.masterseed.MasterSeedManager
import com.mycelium.wapi.wallet.coins.Value
import com.mycelium.wapi.wallet.metadata.IMetaDataStorage
import com.mycelium.wapi.wallet.metadata.MetadataKeyCategory

import java.security.SecureRandom

import org.junit.Test as test
import com.mycelium.wapi.wallet.SynchronizeFinishedListener
import org.mockito.Mockito
import java.util.*


class BtcAssetBasicTest {
    private class MemoryBasedStorage : IMetaDataStorage {
        private val keyCategoryValueMap = HashMap<String, String>()
        override fun storeKeyCategoryValueEntry(keyCategory: MetadataKeyCategory, value: String) {
            keyCategoryValueMap[keyCategory.category + "_" + keyCategory.key] = value
        }

        override fun getKeyCategoryValueEntry(key: String, category: String, defaultValue: String) = ""

        override fun getFirstKeyForCategoryValue(category: String, value: String): Optional<String> {
            for ((key, value1) in keyCategoryValueMap) {
                val keyAndCategory = key.split("_".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (category == keyAndCategory[1] && value1 == value) {
                    return Optional.of(keyAndCategory[0])
                }
            }
            return Optional.absent()
        }
    }

    private class MyRandomSource internal constructor() : RandomSource {
        internal var _rnd: SecureRandom = SecureRandom(byteArrayOf(42))

        override fun nextBytes(bytes: ByteArray) {
            _rnd.nextBytes(bytes)
        }
    }

    @test
    fun test() {
        val backing = InMemoryBtcWalletManagerBacking()

        val testnetWapiEndpoints = ServerEndpoints(arrayOf<HttpEndpoint>(HttpsEndpoint("https://mws30.mycelium.com/wapitestnet", "ED:C2:82:16:65:8C:4E:E1:C7:F6:A2:2B:15:EC:30:F9:CD:48:F8:DB")))


        val tcpEndpoints = arrayOf(TcpEndpoint("electrumx-c.mycelium.com", 19335))
//        val wapiClient = WapiClientElectrumX(testnetWapiEndpoints, tcpEndpoints, "0")
        val bitcoinILtcpEndpoints = arrayOf(TcpEndpoint("electrumx-c.mycelium.com", 19335))
        val wapiClient = WapiClientElectrumX(testnetWapiEndpoints, tcpEndpoints,bitcoinILtcpEndpoints, "0",0)

        val store = SecureKeyValueStore(backing, MyRandomSource())

        val masterSeed = Bip39.generateSeedFromWordList(Collections.nCopies(12, "oil"), "")

        val network = NetworkParameters.testNetwork

        val currenciesSettingsMap = HashMap<String, CurrencySettings>()
        val btcSettings = BTCSettings(AddressType.P2SH_P2WPKH, Reference(ChangeAddressMode.P2SH_P2WPKH))
        currenciesSettingsMap[BitcoinHDModule.ID] = btcSettings

        val listener = SynchronizeFinishedListener()

        val walletDB = Mockito.mock(WalletDB::class.java)
        val walletManager = WalletManager(network, wapiClient, currenciesSettingsMap, null, walletDB)
        walletManager.setIsNetworkConnected(true)
        walletManager.walletListener = listener

        val masterSeedManager = MasterSeedManager(store)
        // create and add HD Module
        masterSeedManager.configureBip32MasterSeed(masterSeed, AesKeyCipher.defaultKeyCipher())
        val storage = MemoryBasedStorage()

        val bitcoinHDModule = BitcoinHDModule(backing as BtcWalletManagerBacking<HDAccountContext>, store, network, wapiClient, btcSettings, storage, null, null, null)
        walletManager.add(bitcoinHDModule)

        // create sample HD account
        val hdAccount = walletManager.getAccount(walletManager.createAccounts(AdditionalHDAccountConfig())[0]) as HDAccount

        val publicPrivateKeyStore = PublicPrivateKeyStore(store)

        val bitcoinSingleAddressModule = BitcoinSingleAddressModule(backing, publicPrivateKeyStore, network, wapiClient, btcSettings, walletManager, storage, null, AbstractBtcAccount.EventHandler { accountId, event ->  })
        walletManager.add(bitcoinSingleAddressModule)

        val saAccount = walletManager.getAccount(walletManager.createAccounts(PrivateSingleConfig(InMemoryPrivateKey("cPdS4cHJg3nsDjT5pHteFMuJoY9j22PXUyxyhN6VVNwBLqtG6ukJ", network), AesKeyCipher.defaultKeyCipher()))[0]) as SingleAddressAccount

        val coinType = hdAccount.coinType
        val address_P2PKH = AddressUtils.from(coinType, hdAccount.getReceivingAddress(AddressType.P2PKH).toString()) as BtcAddress
        val address_P2WPKH = AddressUtils.from(coinType, hdAccount.getReceivingAddress(AddressType.P2WPKH).toString()) as BtcAddress
        val address_P2SH_P2WPKH = AddressUtils.from(coinType, hdAccount.getReceivingAddress(AddressType.P2WPKH).toString()) as BtcAddress

        val address_sa_P2PKH = AddressUtils.from(coinType, saAccount.getReceivingAddress(AddressType.P2PKH).toString()) as BtcAddress
        val address_sa_P2WPKH = AddressUtils.from(coinType, saAccount.getReceivingAddress(AddressType.P2WPKH).toString()) as BtcAddress
        val address_sa_P2SH_P2WPKH = AddressUtils.from(coinType, saAccount.getReceivingAddress(AddressType.P2WPKH).toString()) as BtcAddress

        walletManager.startSynchronization()
        listener.waitForSyncFinished()

        println("HD Account balance: " + hdAccount.accountBalance.spendable.toString())

        createTransaction(hdAccount, address_sa_P2PKH)
        walletManager.startSynchronization()
        listener.waitForSyncFinished()
        createTransaction(hdAccount, address_sa_P2WPKH)
        walletManager.startSynchronization()
        listener.waitForSyncFinished()
        createTransaction(hdAccount, address_sa_P2SH_P2WPKH)
        walletManager.startSynchronization()
        listener.waitForSyncFinished()

        walletManager.startSynchronization()
        listener.waitForSyncFinished()

        println("HD Account balance: ${hdAccount.accountBalance.spendable}")
        println("SA Account balance: ${saAccount.accountBalance.spendable}")

        createTransaction(saAccount, address_P2PKH)
        walletManager.startSynchronization()
        listener.waitForSyncFinished()
        createTransaction(saAccount, address_P2WPKH)
        walletManager.startSynchronization()
        listener.waitForSyncFinished()
        createTransaction(saAccount, address_P2SH_P2WPKH)
        walletManager.startSynchronization()
        listener.waitForSyncFinished()

        println("SA Account balance: ${saAccount.accountBalance.spendable}")
    }

    private fun createTransaction(account: AbstractBtcAccount, address: BtcAddress) {
        val tx = account.createTx(AddressUtils.from(account.coinType, address.toString()) as BtcAddress, Value.valueOf(account.coinType, 10000L), FeePerKbFee(Value.valueOf(account.coinType, 2000L)))
        account.signTx(tx, AesKeyCipher.defaultKeyCipher())
        account.broadcastTx(tx)
    }
}
