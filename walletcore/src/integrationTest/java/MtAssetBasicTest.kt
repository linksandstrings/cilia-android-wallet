package com.mycelium.wapi

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
import com.mycelium.wapi.wallet.btc.bip44.BitcoinHDModule
import com.mycelium.wapi.wallet.btc.single.BitcoinSingleAddressModule
import com.mycelium.wapi.wallet.btc.single.PublicPrivateKeyStore
import com.mycelium.wapi.wallet.coins.Value
import com.mycelium.wapi.wallet.colu.*
import com.mycelium.wapi.wallet.colu.coins.MTCoinTest
import com.mycelium.wapi.wallet.exceptions.BuildTransactionException
import com.mycelium.wapi.wallet.exceptions.InsufficientFundsException
import com.mycelium.wapi.wallet.exceptions.OutputTooSmallException
import com.mycelium.wapi.wallet.exceptions.TransactionBroadcastException
import com.mycelium.wapi.wallet.masterseed.MasterSeedManager
import com.mycelium.wapi.wallet.metadata.IMetaDataStorage
import com.mycelium.wapi.wallet.metadata.MetadataKeyCategory
import org.junit.Test
import org.mockito.Mockito
import java.security.SecureRandom
import java.util.HashMap
import javax.net.ssl.SSLSocketFactory

class MtAssetBasicTest {
    private class MemoryBasedStorage : IMetaDataStorage {
        private val keyCategoryValueMap = HashMap<String, String>()
        override fun storeKeyCategoryValueEntry(keyCategory: MetadataKeyCategory, value: String) {
            keyCategoryValueMap[keyCategory.category + "_" + keyCategory.key] = value
        }

        override fun getKeyCategoryValueEntry(key: String, category: String, defaultValue: String): String? {
            return ""
        }

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

    @Test
    fun test() {
        val backing = InMemoryBtcWalletManagerBacking()
        val coluBacking = InMemoryColuWalletManagerBacking()

        val testnetWapiEndpoints = ServerEndpoints(arrayOf<HttpEndpoint>(HttpsEndpoint("https://mws30.mycelium.com/wapitestnet", "ED:C2:82:16:65:8C:4E:E1:C7:F6:A2:2B:15:EC:30:F9:CD:48:F8:DB")))
        val coloredCoinsApiURLs = arrayOf("http://coloredcoins-test.mycelium.com:28342/")
        val coluBlockExplorerApiURLs = arrayOf("http://coloredcoins-test.mycelium.com:28332/api/")

        val masterSeed = Bip39.generateSeedFromWordList(arrayOf("oil", "oil", "oil", "oil", "oil", "oil", "oil", "oil", "oil", "oil", "oil", "oil"), "")

        val network = NetworkParameters.testNetwork

        val tcpEndpoints = arrayOf(TcpEndpoint("electrumx-aws-test.mycelium.com", 19335))
        val bitcoinILtcpEndpoints = arrayOf(TcpEndpoint("electrumx-aws-test.mycelium.com", 19335))
//        val wapiClient = WapiClientElectrumX(testnetWapiEndpoints, tcpEndpoints, "0")
        val wapiClient = WapiClientElectrumX(testnetWapiEndpoints, tcpEndpoints, bitcoinILtcpEndpoints,"0", 29)

        val socketFactory = SSLSocketFactory.getDefault()

        val coluClient = ColuClient(network, coloredCoinsApiURLs, coluBlockExplorerApiURLs, socketFactory as SSLSocketFactory?)

        val store = SecureKeyValueStore(backing, MyRandomSource())


        val currenciesSettingsMap = HashMap<String, CurrencySettings>()
        val btcSettings = BTCSettings(AddressType.P2SH_P2WPKH, Reference(ChangeAddressMode.P2SH_P2WPKH))
        currenciesSettingsMap[BitcoinHDModule.ID] = btcSettings

        val listener = SynchronizeFinishedListener()

        val walletManager = WalletManager(
                network,
                wapiClient,
                currenciesSettingsMap,
                null,
                Mockito.mock(WalletDB::class.java))
        walletManager.setIsNetworkConnected(true)
        walletManager.walletListener = listener

        val masterSeedManager = MasterSeedManager(store)
        try {

            // create and add Colu Module
            masterSeedManager.configureBip32MasterSeed(masterSeed, AesKeyCipher.defaultKeyCipher())
            val storage = MemoryBasedStorage()
            val saModule = BitcoinSingleAddressModule(backing, PublicPrivateKeyStore(store), network,
                    wapiClient, BTCSettings(AddressType.P2SH_P2WPKH, Reference(ChangeAddressMode.PRIVACY)), walletManager,
                    storage, null, null)

            val coluModule = ColuModule(network, PublicPrivateKeyStore(store), ColuApiImpl(coluClient), wapiClient, coluBacking, object : AccountListener {
                override fun balanceUpdated(walletAccount: WalletAccount<*>) {

                }
            }, storage, saModule)
            walletManager.add(coluModule)

            val coluAccount1 = walletManager.getAccount(walletManager.createAccounts(PrivateColuConfig(InMemoryPrivateKey("cN5hvHD3kLwDCbE9ZpSpJ7eeLjchiF589TXFxWPbxp9vhaVH3SFw", network), MTCoinTest, AesKeyCipher.defaultKeyCipher()))[0]) as ColuAccount
            val coluAccount2 = walletManager.getAccount(walletManager.createAccounts(PrivateColuConfig(InMemoryPrivateKey("cRGNAkjgYVF4Kte6QuFtrwaMCpq9bWJsjno3xyuk8quubfvL3vvo", network), MTCoinTest, AesKeyCipher.defaultKeyCipher()))[0]) as ColuAccount

            walletManager.startSynchronization()
            listener.waitForSyncFinished()

            val coinType = coluAccount1.coinType
            val address1 = AddressUtils.from(coinType, coluAccount1.receiveAddress.toString()) as BtcAddress

            println("Colu 1 Account balance: " + coluAccount1.accountBalance.spendable.toString())
            println("Colu 2 Account balance: " + coluAccount2.accountBalance.spendable.toString())

            createTransaction(coluAccount2, address1)
            walletManager.startSynchronization()
            listener.waitForSyncFinished()

            println("Colu 1 Account balance: " + coluAccount1.accountBalance.spendable.toString())
            println("Colu 2 Account balance: " + coluAccount2.accountBalance.spendable.toString())

        } catch (ex: TransactionBroadcastException) {
            ex.printStackTrace()
        } catch (ex: BuildTransactionException) {
            ex.printStackTrace()
        } catch (ex: InsufficientFundsException) {
            ex.printStackTrace()
        } catch (ex: OutputTooSmallException) {
            ex.printStackTrace()
        } catch (ex: KeyCipher.InvalidKeyCipher) {
            ex.printStackTrace()
        }
        assert(true)
    }

    fun createTransaction(account: ColuAccount, address: BtcAddress) {
        val tx = account.createTx(AddressUtils.from(account.coinType, address.toString()) as BtcAddress, Value.valueOf(account.coinType, 1000L), FeePerKbFee(Value.valueOf(account.coinType, 200L)))
        if (tx != null) {
            account.signTx(tx, AesKeyCipher.defaultKeyCipher())
            account.broadcastTx(tx)
        }
    }
}