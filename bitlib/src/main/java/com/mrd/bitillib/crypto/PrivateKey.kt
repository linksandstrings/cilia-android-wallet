/*
 * Copyright 2013 - 2018 Megion Research & Development GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mrd.bitillib.crypto

import com.mrd.bitillib.util.ByteWriter
import com.mrd.bitillib.util.HashUtils
import com.mrd.bitillib.util.BitcoinILSha256Hash

import java.io.Serializable


abstract class PrivateKey : BitcoinILSigner, Serializable {
    abstract val publicKey: PublicKey

    override fun makeStandardBitcoinILSignature(transactionSigningHash: BitcoinILSha256Hash): ByteArray {
        val signature = signMessage(transactionSigningHash)
        val writer = ByteWriter(1024)
        // Add signature
        writer.putBytes(signature)
        // Add hash type
        writer.put(HASH_TYPE.toByte())
        return writer.toBytes()
    }

    private fun signMessage(message: BitcoinILSha256Hash): ByteArray {
        return generateSignature(message).derEncode()
    }

    // Sign the message with a signature based random k-Value
    protected abstract fun generateSignature(message: BitcoinILSha256Hash, randomSource: RandomSource): Signature

    // Sign the message deterministic, according to rfc6979
    protected abstract fun generateSignature(message: BitcoinILSha256Hash): Signature

    override fun hashCode(): Int {
        return publicKey.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is PrivateKey) {
            return false
        }
        return publicKey == other.publicKey
    }

    fun signMessage(message: String): SignedMessage {
        val data = Signatures.formatMessageForSigning(message)
        val hash = HashUtils.doubleSha256(data)
        return signHash(hash)
    }

    fun signHash(hashToSign: BitcoinILSha256Hash): SignedMessage {
        val sig = generateSignature(hashToSign)

        // Now we have to work backwards to figure out the recId needed to recover the signature.
        val targetPubKey = publicKey
        val compressed = targetPubKey.isCompressed
        var recId = -1
        for (i in 0..3) {
            val k = SignedMessage.recoverFromSignature(i, sig, hashToSign, compressed)
            if (k != null && targetPubKey == k) {
                recId = i
                break
            }
        }
        return SignedMessage.from(sig, targetPubKey, recId)
    }

    companion object {
        private const val serialVersionUID = 1L
        private const val HASH_TYPE = 1
    }
}
