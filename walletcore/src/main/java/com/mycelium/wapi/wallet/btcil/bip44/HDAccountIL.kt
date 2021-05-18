package com.mycelium.wapi.wallet.btcil.bip44

import com.google.common.base.Optional
import com.google.common.base.Preconditions
import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import com.google.common.collect.ImmutableList
import com.google.common.collect.Lists
import com.mrd.bitillib.crypto.BipDerivationType
import com.mrd.bitillib.crypto.BipDerivationType.Companion.getDerivationTypeByAddress
import com.mrd.bitillib.crypto.InMemoryPrivateKey
import com.mrd.bitillib.crypto.PublicKey
import com.mrd.bitillib.model.*
import com.mrd.bitillib.util.BitcoinILSha256Hash
import com.mrd.bitlib.model.AddressType
import com.mycelium.wapi.api.Wapi
import com.mycelium.wapi.api.WapiException
import com.mycelium.wapi.api.request.QueryBitcoinILTransactionInventoryRequest
import com.mycelium.wapi.model.BitcoinILTransactionEx
import com.mycelium.wapi.wallet.*
import com.mycelium.wapi.wallet.KeyCipher.InvalidKeyCipher
import com.mycelium.wapi.wallet.WalletManager.Event
import com.mycelium.wapi.wallet.btcil.*
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import kotlin.math.min

open class HDAccountIL(
        protected var context: HDAccountContextIL,
        protected val keyManagerMap: MutableMap<BipDerivationType, HDAccountKeyManagerIL>,
        network: NetworkParameters,
        protected val backing: Bip44BtcILAccountBacking,
        wapi: Wapi,
        protected val changeAddressModeReference: ReferenceIL<ChangeAddressModeIL>
) : AbstractBtcILAccount(backing, network, wapi), ExportableAccountIL {

    // Used to determine which bips this account support
    private val derivePaths = context.indexesMap.keys

    protected var externalAddresses: MutableMap<BipDerivationType, BiMap<BitcoinILAddress, Int>> = initAddressesMap()
    protected var internalAddresses: MutableMap<BipDerivationType, BiMap<BitcoinILAddress, Int>> = initAddressesMap()
    private val safeLastExternalIndex: MutableMap<BipDerivationType, Int> = mutableMapOf()
    private val safeLastInternalIndex: MutableMap<BipDerivationType, Int> = mutableMapOf()
    private var receivingAddressMap: MutableMap<AddressType, BitcoinILAddress> = mutableMapOf()

    // public method that needs no synchronization
    val accountIndex: Int
        get() = context.accountIndex

    //used for message signing picker
    //get all used external plus the next unused
    //get all used internal
    val allAddresses: List<BitcoinILAddress>
        get() {
            val addresses = ArrayList<BitcoinILAddress>()

            derivePaths.forEach { derivationType ->
                val externalIndex = context.getLastExternalIndexWithActivity(derivationType) + 1
                val external = externalAddresses[derivationType]!!.inverse()
                for (i in 0..externalIndex) {
                    addresses.add(external[i]!!)
                }

                val internalIndex = context.getLastInternalIndexWithActivity(derivationType)
                val internal = internalAddresses[derivationType]!!.inverse()
                for (i in 0..internalIndex) {
                    addresses.add(internal[i]!!)
                }
            }
            return addresses
        }

    open fun getPrivateKeyCount() = derivePaths.sumBy {
        context.getLastExternalIndexWithActivity(it) +
                2 + context.getLastInternalIndexWithActivity(it) + 1
    }

    fun getPublicKey(): PublicKey? {
        //TODO: implement later
        return null
    }

    val accountType = context.accountType

    init {
        if (!isArchived) {
            ensureAddressIndexes()
            _cachedBalance = calculateLocalBalance()
        }
        initSafeLastIndexes(false)
    }

    override fun getId() = context.id

    // public method that needs no synchronization
    override fun isArchived() = context.isArchived()

    // public method that needs no synchronization
    override fun isActive() = !isArchived

    @Synchronized
    override fun archiveAccount() {
        if (context.isArchived()) {
            return
        }
        clearInternalStateInt(true)
    }

    @Synchronized
    override fun activateAccount() {
        if (!context.isArchived()) {
            return
        }
        clearInternalStateInt(false)
    }

    override fun getPrivateKey(cipher: KeyCipher?): com.mrd.bitlib.crypto.InMemoryPrivateKey {
        TODO("Not yet implemented")
    }

    override fun dropCachedData() {
        if (context.isArchived()) {
            return
        }
        clearInternalStateInt(false)
        context.persistIfNecessary(backing)
    }

    override fun isValidEncryptionKey(cipher: KeyCipher) = keyManagerMap.values.any { it.isValidEncryptionKey(cipher) }

    override fun isDerivedFromInternalMasterseed() = accountType == HDAccountContextIL.ACCOUNT_TYPE_FROM_MASTERSEED

    private fun clearInternalStateInt(isArchived: Boolean) {
        backing.clear()
        externalAddresses = initAddressesMap()
        internalAddresses = initAddressesMap()
        receivingAddressMap.clear()
        _cachedBalance = null
        initContext(isArchived)
        initSafeLastIndexes(true)
        if (isActive) {
            ensureAddressIndexes()
            _cachedBalance = calculateLocalBalance()
        }
    }

    private fun initSafeLastIndexes(reset: Boolean) {
        listOf(BipDerivationType.BIP44,
                BipDerivationType.BIP49,
                BipDerivationType.BIP84).forEach {
            safeLastExternalIndex[it] = if (reset) 0 else context.getLastExternalIndexWithActivity(it)
            safeLastInternalIndex[it] = if (reset) 0 else context.getLastInternalIndexWithActivity(it)
        }
    }
    override fun getAvailableAddressTypes(): List<AddressType> =
        derivePaths.asSequence().map { it.addressType }.toList()


    override fun setDefaultAddressType(addressType: AddressType) {
        context.defaultAddressType = addressType
        context.persistIfNecessary(backing)
    }

    protected fun initContext(isArchived: Boolean) {
        context = HDAccountContextIL(context.id, context.accountIndex, isArchived, context.accountType, context.accountSubId, derivePaths, context.defaultAddressType)
        context.persist(backing)
    }

    /**
     * Figure out whether this account has ever had any activity.
     *
     *
     * An account has had activity if it has one or more external addresses with
     * transaction history.
     *
     * @return true if this account has ever had any activity, false otherwise
     */
    fun hasHadActivity(): Boolean {
        // public method that needs no synchronization
        return derivePaths.any { context.getLastExternalIndexWithActivity(it) != -1 }
    }

    /**
     * Ensure that all addresses in the look ahead window have been created
     */
    protected fun ensureAddressIndexes(boostedLookAhead: Boolean = false) {
        derivePaths.forEachIndexed { index, derivationType ->
            ensureAddressIndexes(true, true, boostedLookAhead, derivationType)
            ensureAddressIndexes(false, true, boostedLookAhead, derivationType)
            // The current receiving address is the next external address just above
            // the last
            // external address with activity
            val receivingAddress = externalAddresses[derivationType]!!.inverse()[context.getLastExternalIndexWithActivity(derivationType) + 1]
            if (receivingAddress != null && receivingAddress != receivingAddressMap[receivingAddress.type]) {
                receivingAddressMap[receivingAddress.type] = receivingAddress
                postEvent(Event.RECEIVING_ADDRESS_CHANGED)
            }
            LoadingProgressTracker.setPercent((index + 1) * 100 / derivePaths.size)
        }
    }

    private fun ensureAddressIndexes(isChangeChain: Boolean, fullLookAhead: Boolean, boostedLookAhead: Boolean, derivationType: BipDerivationType) {
        var index: Int
        val addressMap: BiMap<BitcoinILAddress, Int>
        if (isChangeChain) {
            index = context.getLastInternalIndexWithActivity(derivationType)
            index += if (fullLookAhead) {
                INTERNAL_FULL_ADDRESS_LOOK_AHEAD_LENGTH
            } else {
                INTERNAL_MINIMAL_ADDRESS_LOOK_AHEAD_LENGTH
            }
            addressMap = internalAddresses[derivationType]!!
        } else {
            index = context.getLastExternalIndexWithActivity(derivationType)
            index += when {
                boostedLookAhead -> EXTERNAL_BOOSTED_ADDRESS_LOOK_AHEAD_LENGTH
                fullLookAhead -> EXTERNAL_FULL_ADDRESS_LOOK_AHEAD_LENGTH
                else -> EXTERNAL_MINIMAL_ADDRESS_LOOK_AHEAD_LENGTH
            }
            addressMap = externalAddresses[derivationType]!!
        }
        while (index >= 0) {
            if (addressMap.inverse().containsKey(index)) {
                return
            }
            addressMap[keyManagerMap[derivationType]!!.getAddress(isChangeChain, index)] = index
            index--
        }
    }

    private fun getAddressesToSync(mode: SyncMode): List<BitcoinILAddress> {
        var addresses = mutableListOf<BitcoinILAddress>()
        derivePaths.forEach { derivationType ->
            val currentInternalAddressId = context.getLastInternalIndexWithActivity(derivationType) + 1
            val currentExternalAddressId = context.getLastExternalIndexWithActivity(derivationType) + 1
            when (mode.mode!!) {
                SyncMode.Mode.BOOSTED -> {
                    // check the full change-chain and external-chain
                    addresses.addAll(getAddressRange(true, currentInternalAddressId,
                            currentInternalAddressId + INTERNAL_FULL_ADDRESS_LOOK_AHEAD_LENGTH, derivationType))
                    addresses.addAll(getAddressRange(false, currentExternalAddressId,
                            currentExternalAddressId + mode.mode.lookAhead, derivationType))
                    ensureAddressIndexes(boostedLookAhead = true)
                }
                SyncMode.Mode.FULL_SYNC -> {
                    // check the full change-chain and external-chain
                    addresses.addAll(getAddressRange(true, 0,
                            currentInternalAddressId + INTERNAL_FULL_ADDRESS_LOOK_AHEAD_LENGTH, derivationType))
                    addresses.addAll(getAddressRange(false, 0,
                            currentExternalAddressId + EXTERNAL_FULL_ADDRESS_LOOK_AHEAD_LENGTH, derivationType))
                }
                SyncMode.Mode.NORMAL_SYNC -> {
                    // check the current change address plus small lookahead;
                    // plus the current external address plus a small range before and after it
                    addresses.addAll(getAddressRange(true, currentInternalAddressId,
                            currentInternalAddressId + INTERNAL_MINIMAL_ADDRESS_LOOK_AHEAD_LENGTH, derivationType))
                    addresses.addAll(getAddressRange(false, currentExternalAddressId - 3,
                            currentExternalAddressId + EXTERNAL_MINIMAL_ADDRESS_LOOK_AHEAD_LENGTH, derivationType))

                }
                SyncMode.Mode.FAST_SYNC -> {
                    // check only the current change address
                    // plus the current external plus small lookahead
                    addresses.add(keyManagerMap[derivationType]!!
                            .getAddress(true, currentInternalAddressId + 1) as BitcoinILAddress)
                    addresses.addAll(getAddressRange(false, currentExternalAddressId,
                            currentExternalAddressId + 2, derivationType))
                }
                SyncMode.Mode.ONE_ADDRESS -> {
                    if ( mode.addressToSync == null) {
                        throw IllegalArgumentException("Unexpected SyncMode")
                    }
                    // only check for the supplied address
                    addresses = if (isMineAddress(mode.addressToSync)) {
                        Lists.newArrayList(BitcoinILAddress.fromString(mode.addressToSync.toString()))
                    } else {
                        throw IllegalArgumentException("Address " + mode.addressToSync + " is not part of my account addresses")
                    }
                }
            }
        }
        return ImmutableList.copyOf(addresses)
    }

    protected fun getAddressRange(isChangeChain: Boolean, fromIndex: Int, toIndex: Int,
                                  derivationType: BipDerivationType): List<BitcoinILAddress> {
        val clippedFromIndex = Math.max(0, fromIndex) // clip at zero
        val ret = ArrayList<BitcoinILAddress>(toIndex - clippedFromIndex + 1)
        for (i in clippedFromIndex..toIndex) {
            ret.add(keyManagerMap[derivationType]!!.getAddress(isChangeChain, i))
        }
        return ret
    }

    @Synchronized
    public override fun doSynchronization(proposedMode: SyncMode): Boolean {
        var mode = proposedMode
        checkNotArchived()
        syncTotalRetrievedTransactions = 0
        _logger.log(Level.INFO, "Starting sync: $mode")
        if (needsDiscovery()) {
            mode = SyncMode.FULL_SYNC_CURRENT_ACCOUNT_FORCED
        }
        try {
            if (mode.mode == SyncMode.Mode.FULL_SYNC) {
                // Discover new addresses once in a while
                if (!discovery()) {
                    return false
                }
            }

            // Update unspent outputs
            return updateUnspentOutputs(mode)
        } finally {
            syncTotalRetrievedTransactions = 0
        }
    }

    private fun needsDiscovery() = !isArchived &&
            context.getLastDiscovery() + FORCED_DISCOVERY_INTERVAL_MS < System.currentTimeMillis()

    @Synchronized
    private fun discovery(): Boolean {
        try {
            // discovered as in "discovered maybe something. further exploration is needed."
            // thus, method is done once discovered is empty.
            var discovered = derivePaths.toSet()
            do {
                discovered = doDiscovery(discovered)
            } while (discovered.isNotEmpty())
        } catch (e: WapiException) {
            _logger.log(Level.SEVERE, "Server connection failed with error code: " + e.errorCode, e)
            postEvent(Event.SERVER_CONNECTION_ERROR)
            return false
        }

        context.setLastDiscovery(System.currentTimeMillis())
        context.persistIfNecessary(backing)
        return true
    }

    /**
     * Do a look ahead on the external address chain. If any transactions were
     * found the external and internal last active addresses are updated, and the
     * transactions and their parent transactions stored.
     *
     * @return true if something was found and the call should be repeated.
     * @throws com.mycelium.wapi.api.WapiException
     */
    @Throws(WapiException::class)
    private fun doDiscovery(derivePaths: Set<BipDerivationType>): Set<BipDerivationType> {
        // Ensure that all addresses in the look ahead window have been created
        ensureAddressIndexes()
        return doDiscoveryForAddresses(derivePaths.flatMap { getAddressesToDiscover(it) })
    }

    private fun getAddressesToDiscover(derivationType: BipDerivationType): List<BitcoinILAddress> {
        // getAddressesToDiscover has to progress covering all addresses while last address with
        // activity might advance in jumps from sending to oneself from an old UTXO address to one
        // 30 later.
        // Make look ahead address list
        val lastExternalIndex = min(context.getLastExternalIndexWithActivity(derivationType), safeLastExternalIndex[derivationType] ?: 0)
        val lastInternalIndex = min(context.getLastInternalIndexWithActivity(derivationType), safeLastInternalIndex[derivationType] ?: 0)
        safeLastExternalIndex[derivationType] = lastExternalIndex + EXTERNAL_FULL_ADDRESS_LOOK_AHEAD_LENGTH
        safeLastInternalIndex[derivationType] = lastInternalIndex + INTERNAL_FULL_ADDRESS_LOOK_AHEAD_LENGTH
        val lookAhead =
                (externalAddresses[derivationType]!!.filter {
                    lastExternalIndex <= it.value && it.value <= lastExternalIndex + EXTERNAL_FULL_ADDRESS_LOOK_AHEAD_LENGTH
                } + internalAddresses[derivationType]!!.filter {
                    lastInternalIndex <= it.value && it.value <= lastInternalIndex + INTERNAL_FULL_ADDRESS_LOOK_AHEAD_LENGTH
                }).map { it.key }
        return lookAhead
    }

    @Throws(WapiException::class)
    override fun doDiscoveryForAddresses(addresses: List<BitcoinILAddress>): Set<BipDerivationType> {
        // Do look ahead query
        val result = _wapi.queryBitcoinILTransactionInventory(
                QueryBitcoinILTransactionInventoryRequest(Wapi.VERSION, addresses)).result
        blockChainHeight = result.height
        val ids = result.txIds
        if (ids.isEmpty()) {
            // nothing found
            return emptySet()
        }

        val lastExternalIndexesBefore = derivePaths.map { it to context.getLastExternalIndexWithActivity(it) }.toMap()
        val lastInternalIndexesBefore = derivePaths.map { it to context.getLastInternalIndexWithActivity(it) }.toMap()
        // query DB only once to sort TXIDs into new and old ones. Unconfirmed transactions are
        // "new" in this sense until we know which block they fell into.
        val newIds = mutableSetOf<BitcoinILSha256Hash>()
        val knownTransactions = mutableSetOf<BitcoinILTransactionEx>()
        ids.forEach {
            val dbTransaction = backing.getTransaction(it)
            if (dbTransaction?.height ?: 0 > 0) {
                // we have it and know its block
                knownTransactions.add(dbTransaction)
            } else {
                // we have to query for details
                newIds.add(it)
            }
        }
        newIds.chunked(50).forEach { fewIds ->
            val transactions: Collection<BitcoinILTransactionEx> = getTransactionsBatched(fewIds).result.transactions
            handleNewExternalTransactions(transactions)
        }
        handleNewExternalTransactions(knownTransactions, true)
        return derivePaths.filter { derivationType ->
            // only include if the last external or internal index has changed
                    (lastExternalIndexesBefore[derivationType] != context.getLastExternalIndexWithActivity(derivationType)
                            || lastInternalIndexesBefore[derivationType] != context.getLastInternalIndexWithActivity(derivationType))
        }.toSet()
    }

    private fun updateUnspentOutputs(mode: SyncMode): Boolean {
        var checkAddresses = getAddressesToSync(mode)

        val newUtxos = synchronizeUnspentOutputs(checkAddresses)

        if (newUtxos == -1) {
            return false
        }

        if (newUtxos > 0 && mode.mode != SyncMode.Mode.FULL_SYNC) {
            // we got new UTXOs but did not made a full sync. The UTXO might be coming
            // from change outputs spending from addresses we are currently not checking
            // -> rerun the synchronizeUnspentOutputs for a FULL_SYNC
            checkAddresses = getAddressesToSync(SyncMode.FULL_SYNC_CURRENT_ACCOUNT_FORCED)
            if (synchronizeUnspentOutputs(checkAddresses) == -1) {
                return false
            }
        }

        // update state of recent received transaction to update their confirmation state
        if (mode.mode != SyncMode.Mode.ONE_ADDRESS) {
            // Monitor young transactions
            if (!monitorYoungTransactions()) {
                return false
            }
        }

        updateLocalBalance()

        context.persistIfNecessary(backing)
        return true
    }

    private fun tightenInternalAddressScanRange() {
        // Find the lowest internal index at which we have an unspent output
        val unspent = backing.allUnspentOutputs
        val minInternalIndexesMap = mutableMapOf<BipDerivationType, Int>()
        derivePaths.associateByTo(minInternalIndexesMap, { it }, { Int.MAX_VALUE })
        for (output in unspent) {
            val outputScript = ScriptOutput.fromScriptBytes(output.script)
                    ?: continue // never happens, we have parsed it before
            val address = outputScript.getAddress(_network)
            val derivationType = getDerivationTypeByAddress(address)
            val index = internalAddresses[derivationType]!![address]
                    ?: continue
            val minInternalIndex = minInternalIndexesMap[derivationType]!!
            minInternalIndexesMap[derivationType] = Math.min(minInternalIndex, index)
        }

        // XXX also, from all the outgoing unconfirmed transactions we have, check
        // if any of them have outputs that we send from our change chain. If the
        // related address is lower than the one we had above, use its index as
        // the first monitored one.

        derivePaths.forEach { derivationType ->
            context.setFirstMonitoredInternalIndex(derivationType,
                    if (minInternalIndexesMap[derivationType] == Integer.MAX_VALUE) {
                        // there are no unspent outputs in our change chain
                        Math.max(0, context.getFirstMonitoredInternalIndex(derivationType))
                    } else {
                        minInternalIndexesMap[derivationType]!!
                    })
        }
    }

    // Get the next internal address just above the last address with activity
    public override fun getChangeAddress(destinationAddress: BitcoinILAddress): BitcoinILAddress =
            getChangeAddress(listOf(destinationAddress))

    public override fun getChangeAddress(destinationAddresses: List<BitcoinILAddress>): BitcoinILAddress =
            when (changeAddressModeReference.get()!!) {
                ChangeAddressModeIL.P2WPKH -> getChangeAddress(BipDerivationType.BIP84)
                ChangeAddressModeIL.P2SH_P2WPKH -> getChangeAddress(BipDerivationType.BIP49)
                ChangeAddressModeIL.PRIVACY -> {
                    val mostCommonOutputType = destinationAddresses.groupingBy {
                        BipDerivationType.getDerivationTypeByAddress(it)
                    }.eachCount().maxBy { it.value }!!.key
                    getChangeAddress(mostCommonOutputType)
                }
                ChangeAddressModeIL.NONE -> throw IllegalStateException()
            }

    override fun getChangeAddress(): BitcoinILAddress {
        return when (changeAddressModeReference.get()!!) {
            ChangeAddressModeIL.P2WPKH -> getChangeAddress(BipDerivationType.BIP84)
            ChangeAddressModeIL.P2SH_P2WPKH, ChangeAddressModeIL.PRIVACY -> getChangeAddress(BipDerivationType.BIP49)
            ChangeAddressModeIL.NONE -> throw IllegalStateException()
        }
    }

    private fun getChangeAddress(preferredDerivationType: BipDerivationType): BitcoinILAddress {
        val derivationType = if (derivePaths.contains(preferredDerivationType)) {
            preferredDerivationType
        } else {
            derivePaths.first()
        }
        return internalAddresses[derivationType]!!
                .inverse()[context.getLastInternalIndexWithActivity(derivationType) + 1]!!
    }

    override fun getReceivingAddress(): Optional<BitcoinILAddress> {
        // if this account is archived, we cant ensure that we have the most recent ReceivingAddress (or any at all)
        // so return absent.
        return if (isArchived) {
            Optional.absent()
        } else {
            val receivingAddress = getReceivingAddress(context.defaultAddressType)
                    ?: receivingAddressMap.values.firstOrNull()
            Optional.fromNullable(receivingAddress)
        }
    }

    override fun getReceivingAddress(addressType: AddressType): BitcoinILAddress? {
        return receivingAddressMap[addressType]
    }

    override fun isMine(address: BitcoinILAddress): Boolean {
        val derivationType = getDerivationTypeByAddress(address)
        return internalAddresses[derivationType]?.containsKey(address) ?: false ||
                externalAddresses[derivationType]?.containsKey(address) ?: false
    }

    // check whether we need to update our last index for activity
    override fun onNewTransaction(t: BitcoinILTransaction) = updateLastIndexWithActivity(t)

    override fun onTransactionsBroadcasted(txids: List<BitcoinILSha256Hash>) {
        // See if we can reduce the internal scan range
        tightenInternalAddressScanRange()
        context.persistIfNecessary(backing)
    }

    /**
     * Update the index for the last external and internal address with activity.
     *
     * @param t transaction
     */
    private fun updateLastIndexWithActivity(t: BitcoinILTransaction) {
        // Investigate whether the transaction sends us any coins
        for (out in t.outputs) {
            val receivingAddress = out.script.getAddress(_network)
            val derivationType = getDerivationTypeByAddress(receivingAddress)
            updateLastExternalIndex(receivingAddress, derivationType)
            updateLastInternalIndex(receivingAddress, derivationType)
        }
        ensureAddressIndexes()
    }

    /**
     * Update the new last external address with activity
     *
     * @param externalIndex new index
     */
    protected fun updateLastExternalIndex(receivingAddress: BitcoinILAddress, derivationType: BipDerivationType) {
        externalAddresses[derivationType]?.get(receivingAddress)?.also { externalIndex ->
            // Sends coins to an external address, update internal max index if necessary
            if (context.getLastExternalIndexWithActivity(derivationType) < externalIndex) {
                context.setLastExternalIndexWithActivity(derivationType, externalIndex)
            }
        }
    }

    /**
     * Update the new last internal address with activity.
     *
     * @param receivingAddress
     */
    protected fun updateLastInternalIndex(receivingAddress: BitcoinILAddress, derivationType: BipDerivationType) {
        internalAddresses[derivationType]?.get(receivingAddress)?.also { internalIndex ->
            // Sends coins to an internal address, update internal max index if necessary
            if (context.getLastInternalIndexWithActivity(derivationType) < internalIndex) {
                context.setLastInternalIndexWithActivity(derivationType, internalIndex)
            }
        }
    }

    @Throws(InvalidKeyCipher::class)
    public override fun getBitcoinILPrivateKey(publicKey: PublicKey, cipher: KeyCipher): InMemoryPrivateKey? {
        for (address in publicKey.getAllSupportedAddresses(_network).values) {
            return getPrivateKeyForAddress(address, cipher)
                    ?: continue
        }
        return null
    }

    @Throws(InvalidKeyCipher::class)
    public override fun getPrivateKeyForAddress(address: BitcoinILAddress, cipher: KeyCipher): InMemoryPrivateKey? {
        val derivationType = getDerivationTypeByAddress(address)
        if (!availableAddressTypes.contains(address.type)) {
                return null
        }
        val indexLookUp = getIndexLookup(address, derivationType)
        return if (indexLookUp == null) {
            // still not found? give up...
            null
        } else {
            keyManagerMap[derivationType]!!.getBitcoinILPrivateKey(indexLookUp.isChange, indexLookUp.index!!, cipher)
        }
    }

    override fun getPublicKeyForAddress(address: BitcoinILAddress): PublicKey? {
        val derivationType = getDerivationTypeByAddress(address)
        if (!availableAddressTypes.contains(address.type)) {
            return null
        }
        val indexLookUp = getIndexLookup(address, derivationType)
        return if (indexLookUp == null) {
            // still not found? give up...
            null
        } else {
            Preconditions.checkNotNull(keyManagerMap[derivationType]!!.getPublicKey(indexLookUp.isChange, indexLookUp
                    .index!!))
        }

    }

    private fun getIndexLookup(address: BitcoinILAddress, derivationType: BipDerivationType): IndexLookUpIL? {
        var indexLookUp = IndexLookUpIL.forAddress(address, externalAddresses[derivationType]!!, internalAddresses[derivationType]!!)
        if (indexLookUp == null) {
            // we did not find it - to be sure, generate all addresses and search again
            ensureAddressIndexes()
            indexLookUp = IndexLookUpIL.forAddress(address, externalAddresses[derivationType]!!, internalAddresses[derivationType]!!)
        }
        return indexLookUp
    }

    override fun toString(): String {
        val sb = StringBuilder("HD ID: ").append(id)
        if (isArchived) {
            sb.append(" Archived")
        } else {
            if (_cachedBalance == null) {
                sb.append(" Balance: not known")
            } else {
                sb.append(" Balance: $_cachedBalance")
            }
            val receivingAddress = receivingAddress
            sb.append(" Receiving Address: ${if (receivingAddress.isPresent) receivingAddress.get().toString() else ""}")
            toStringMonitoredAddresses(sb)
            sb.append(" Spendable Outputs: ${getSpendableOutputs(0).size}")
        }
        return sb.toString()
    }

    protected fun toStringMonitoredAddresses(sb: StringBuilder) {
        sb.append(" Monitored Addresses:")
        derivePaths.forEach { derivationType ->
            sb.append(" BIP: ${derivationType.name} external= ${context.getLastExternalIndexWithActivity
            (derivationType) + 2}")
                    .append(" internal=${context.getLastInternalIndexWithActivity(derivationType) + 1 -
                            context.getFirstMonitoredInternalIndex(derivationType)}")
        }
    }

    fun getAddressId(address: BitcoinILAddress): Optional<Array<Int>> {
        if (address.type !in availableAddressTypes) {
            return Optional.absent()
        }
        val derivationType = getDerivationTypeByAddress(address)
        val (changeIndex, addressMap) =  when (address) {
            in externalAddresses[derivationType]!!.keys -> Pair(0, externalAddresses)
            in internalAddresses[derivationType]!!.keys -> Pair(1, internalAddresses)
            else -> return Optional.absent()
        }
        return Optional.of(arrayOf(changeIndex, addressMap[derivationType]!![address]!!))
    }

    // returns true if this is one of our already used or monitored internal (="change") addresses
    override fun isOwnInternalAddress(address: BitcoinILAddress): Boolean {
        val addressId = getAddressId(address)
        return addressId.isPresent && addressId.get()[0] == 1
    }

    // returns true if this is one of our already used or monitored external (=normal receiving) addresses
    override fun isOwnExternalAddress(address: BitcoinILAddress): Boolean {
        val addressId = getAddressId(address)
        return addressId.isPresent && addressId.get()[0] == 0
    }

    override fun canSpend() = true

    override fun getBlockChainHeight(): Int {
        // public method that needs no synchronization
        checkNotArchived()
        return context.getBlockHeight()
    }

    public override fun setBlockChainHeight(blockHeight: Int) {
        checkNotArchived()
        context.setBlockHeight(blockHeight)
    }

    override fun persistContextIfNecessary() {
        context.persistIfNecessary(backing)
    }

    override fun getExportData(cipher: KeyCipher): ExportableAccountIL.Data {
        val privateDataMap = if (canSpend()) {
            try {
                keyManagerMap.keys.map { derivationType ->
                    derivationType to (keyManagerMap[derivationType]!!.getPrivateAccountRoot(cipher, derivationType)
                            .serialize(_network, derivationType))
                }.toMap()
            } catch (ignore: InvalidKeyCipher) {
                null
            }
        } else {
            null
        }
        val publicDataMap = keyManagerMap.keys.map { derivationType ->
            derivationType to (keyManagerMap[derivationType]!!.publicAccountRoot
                    .serialize(_network, derivationType))
        }.toMap()
        return ExportableAccountIL.Data(privateDataMap, publicDataMap)
    }

    // deletes everything account related from the accountBacking
    // this method is only allowed for accounts that use a SubValueKeystore
    fun clearBacking() = keyManagerMap.values.forEach(HDAccountKeyManagerIL::deleteSubKeyStore)

    // Helper class to find the mapping for a Address in the internal or external chain
    private class IndexLookUpIL private constructor(val isChange: Boolean, val index: Int?) {
        companion object {
            fun forAddress(address: BitcoinILAddress, external: Map<BitcoinILAddress, Int>, internal: Map<BitcoinILAddress, Int>): IndexLookUpIL? {
                var index: Int? = external[address]
                return if (index == null) {
                    index = internal[address]
                    if (index == null) {
                        null
                    } else {
                        // found it in the internal(=change)-chain
                        IndexLookUpIL(true, index)
                    }
                } else {
                    // found it in the external chain
                    IndexLookUpIL(false, index)
                }
            }
        }
    }

    protected fun initAddressesMap(): MutableMap<BipDerivationType, BiMap<BitcoinILAddress, Int>> = derivePaths
            .map { it to HashBiMap.create<BitcoinILAddress, Int>() }.toMap().toMutableMap()

    companion object {
        const val EXTERNAL_BOOSTED_ADDRESS_LOOK_AHEAD_LENGTH = 200
        const val EXTERNAL_FULL_ADDRESS_LOOK_AHEAD_LENGTH = 20
        const val INTERNAL_FULL_ADDRESS_LOOK_AHEAD_LENGTH = 20
        private const val EXTERNAL_MINIMAL_ADDRESS_LOOK_AHEAD_LENGTH = 4
        private const val INTERNAL_MINIMAL_ADDRESS_LOOK_AHEAD_LENGTH = 1
        private const val INTERNAL_MINIMAL_ADDRESS_LOOK_BACK_LENGTH = 2
        private const val EXTERNAL_MINIMAL_ADDRESS_LOOK_BACK_LENGTH = 3
        private val FORCED_DISCOVERY_INTERVAL_MS = TimeUnit.DAYS.toMillis(1)
    }


    override fun signTx(request: Transaction, keyCipher: KeyCipher) {
        val btcSendRequest = request as BtcILTransaction
        val tx = signTransaction(btcSendRequest.unsignedTx, AesKeyCipher.defaultKeyCipher())
        if (tx != null) btcSendRequest.setTransaction(tx)
    }

    override fun broadcastTx(tx: Transaction) :BroadcastResult {
        val btcTx = tx as BtcILTransaction
        return broadcastTransaction(btcTx.tx)
    }

    @Throws(InvalidKeyCipher::class)
    override fun getBitcoinILPrivateKey(cipher: KeyCipher): com.mrd.bitillib.crypto.InMemoryPrivateKey? {
        // This method should NOT be called for HD account since it has more than one private key
        throw RuntimeException("Calling getBitcoinILPrivateKey() is not supported for HD account")
    }

    override fun canSign(): Boolean = true
}
