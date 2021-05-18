package com.mycelium.wapi.wallet.btcil.single

import com.mrd.bitillib.crypto.InMemoryPrivateKey
import com.mrd.bitillib.crypto.PublicKey
import com.mrd.bitlib.model.AddressType
import com.mycelium.wapi.wallet.KeyCipher
import com.mycelium.wapi.wallet.btcil.BtcILAddress
import com.mycelium.wapi.wallet.manager.Config

open class LabeledConfigIL(val label: String = ""): Config

open class AddressSingleConfigIL @JvmOverloads constructor(val address: BtcILAddress, label: String = "") : LabeledConfigIL(label)

open class PublicSingleConfigIL @JvmOverloads constructor(val publicKey: PublicKey, label: String = "") : LabeledConfigIL(label)

open class PrivateSingleConfigIL @JvmOverloads constructor(val privateKey: InMemoryPrivateKey, val
        cipher: KeyCipher, label: String = "", val addressType: AddressType? = null) : LabeledConfigIL(label)

