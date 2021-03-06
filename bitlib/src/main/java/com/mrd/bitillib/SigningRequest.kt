package com.mrd.bitillib

import com.mrd.bitillib.crypto.PublicKey
import com.mrd.bitillib.util.BitcoinILSha256Hash

import java.io.Serializable

/**
 * @param publicKey The public part of the key we will sign with
 * @param toSign The data to make a signature on. For transactions this is the transaction hash
 */
data class SigningRequest(var publicKey: PublicKey, var toSign: BitcoinILSha256Hash) : Serializable
