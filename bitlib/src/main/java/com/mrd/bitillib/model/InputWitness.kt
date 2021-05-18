package com.mrd.bitillib.model

import com.mrd.bitillib.util.ByteWriter
import java.io.Serializable
import java.util.*

class InputWitness(val pushCount: Int) : Serializable {
    private val stack = ArrayList<ByteArray>(Math.min(pushCount, MAX_INITIAL_ARRAY_LENGTH))

    fun setStack(i: Int, value: ByteArray) {
        while (i >= stack.size) {
            stack.add(byteArrayOf())
        }
        stack[i] = value
    }

    fun toByteWriter(writer: ByteWriter) {
        writer.putCompactInt(stack.size.toLong())
        for (element in stack) {
            writer.putCompactInt(element.size.toLong())
            writer.putBytes(element)
        }
    }

    companion object {
        @JvmField
        val EMPTY = InputWitness(0)
        const val MAX_INITIAL_ARRAY_LENGTH = 20
    }
}
