package com.bcm.messenger.utility

import com.bcm.messenger.utility.logger.ALog
import java.util.*
import java.util.concurrent.atomic.AtomicReferenceArray
import kotlin.experimental.and
import kotlin.experimental.or

class BloomByteArray {
    companion object {
        private const val LONG_ADDRESSABLE_BITS = 3

        fun bitsSize2ByteSize(bits:Int): Int {
            return Math.ceil(bits / 8.0).toInt()
        }
    }

    private lateinit var data: AtomicReferenceArray<Byte>
    private var bitCount = IntAddable(0)

    constructor(data: ByteArray) {
        if (data.isEmpty()) {
            return
        }

        this.data = AtomicReferenceArray(data.toTypedArray())
        this.bitCount = IntAddable(0)
        var bitCount = 0
        for (value in data) {
            bitCount += java.lang.Long.bitCount(value.toLong())
        }
        this.bitCount.add(bitCount)
    }

    constructor(bits: Int):this(ByteArray(bitsSize2ByteSize(bits))) {

    }

    /** Returns true if the bit changed value.  */
    fun set(bitIndex: Int): Boolean {
        if (get(bitIndex)) {
            return false
        }

        val iIndex = bitIndex.ushr(3)
        val mask = (1 shl bitIndex%8).toByte()

        var oldValue: Byte
        var newValue: Byte
        do {
            oldValue = data.get(iIndex)
            newValue = oldValue or mask
            if (oldValue == newValue) {
                return false
            }
        } while (!data.compareAndSet(iIndex, oldValue, newValue))

        // We turned the bit on, so increment bitCount.
        bitCount.increment()
        return true
    }

    operator fun get(bitIndex: Int): Boolean {
        return data.get(bitIndex.ushr(LONG_ADDRESSABLE_BITS)).and ((1 shl (bitIndex%8)).toByte()).toInt() != 0
    }

    /**
     * Careful here: if threads are mutating the atomicLongArray while this method is executing, the
     * final long[] will be a "rolling snapshot" of the state of the bit array. This is usually good
     * enough, but should be kept in mind.
     */
    fun toPlainArray(): ByteArray {
        val data = this.data
        val array = ByteArray(data.length())
        for (i in array.indices) {
            array[i] = data.get(i)
        }
        return array
    }

    /** Number of bits  */
    fun bitSize(): Int {
        return data.length() * 8
    }

    /**
     * Number of set bits (1s).
     *
     *
     * Note that because of concurrent set calls and uses of atomics, this bitCount is a (very)
     * close *estimate* of the actual number of bits set. It's not possible to do better than an
     * estimate without locking. Note that the number, if not exactly accurate, is *always*
     * underestimating, never overestimating.
     */
    fun bitCount(): Int {
        return bitCount.sum()
    }

    fun copy(): BloomByteArray {
        return BloomByteArray(toPlainArray())
    }

    /**
     * Combines the two BitArrays using bitwise OR.
     *
     *
     * NOTE: Because of the use of atomics, if the other LockFreeBitArray is being mutated while
     * this operation is executing, not all of those new 1's may be set in the final state of this
     * LockFreeBitArray. The ONLY guarantee provided is that all the bits that were set in the other
     * LockFreeBitArray at the start of this method will be set in this LockFreeBitArray at the end
     * of this method.
     */
    fun putAll(other: BloomByteArray) {

        if (data.length() != other.data.length()) {
            ALog.e("BloomByteArray", "BitArrays must be of equal length (${data.length()} != %${other.data.length()})")
            return
        }

        for (i in 0 until data.length()) {
            val otherLong = other.data.get(i)

            var ourLongOld: Byte
            var ourLongNew: Byte
            var changedAnyBits = true
            do {
                ourLongOld = data.get(i)
                ourLongNew = ourLongOld.or(otherLong)
                if (ourLongOld == ourLongNew) {
                    changedAnyBits = false
                    break
                }
            } while (!data.compareAndSet(i, ourLongOld, ourLongNew))

            if (changedAnyBits) {
                val bitsAdded = java.lang.Long.bitCount(ourLongNew.toLong()) - java.lang.Long.bitCount(ourLongOld.toLong())
                bitCount.add(bitsAdded)
            }
        }
    }

    override fun equals(o: Any?): Boolean {
        if (o is BloomByteArray) {
            return Arrays.equals(toPlainArray(), o.toPlainArray())
        }
        return false
    }

    override fun hashCode(): Int {
        return Arrays.hashCode(toPlainArray())
    }


    internal class IntAddable(i:Int) {
        private var value:Int = i

        @Synchronized fun add(i:Int) {
            this.value += i
        }

        @Synchronized fun increment() {
            ++this.value
        }

        @Synchronized fun decrement() {
            --this.value
        }

        @Synchronized fun sum(): Int {
            return this.value
        }
    }
}