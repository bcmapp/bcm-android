package com.bcm.messenger.contacts.logic

import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils
import com.bcm.messenger.utility.BloomByteArray


/**
 * Created by wjh on 2019/5/28
 */
class BcmBloomFilter(private val rootSeed: Long, private val hashFuncs: Int, private val tweek: Long) {

    private var mDataArray: BloomByteArray = BloomByteArray(0)

    @Synchronized
    fun getDataSize(): Int {
        return mDataArray.bitSize()
    }

    @Synchronized
    fun getDataArray(): ByteArray {
        return mDataArray.toPlainArray()
    }

    @Synchronized
    fun updateDataArray(bitSize: Int) {
        mDataArray = BloomByteArray(bitSize)
    }

    @Synchronized
    fun updateDataArray(bloomArray: ByteArray) {
        mDataArray = BloomByteArray(bloomArray)
    }

    @Synchronized
    fun getBit(bitIndex: Int): Boolean {
        return mDataArray[bitIndex]
    }

    /**
     * Returns true if the given object matches the filter either because it was inserted, or because we have a
     * false-positive.
     */
    @Synchronized
    operator fun contains(item: ByteArray): Boolean {
        val data = mDataArray.toPlainArray()
        for (i in 0 until hashFuncs) {
            val pos = murmurHash3(data, tweek, i, item)
            if (!mDataArray[pos]) {
                return false
            }
        }
        return true
    }

    /**
     * Insert the given arbitrary data into the filter
     */
    @Synchronized
    fun insert(item: ByteArray): IntArray {
        val result = IntArray(hashFuncs)
        val data = mDataArray.toPlainArray()
        for (i in 0 until hashFuncs) {
            val pos = murmurHash3(data, tweek, i, item)
            result[i] = pos
            mDataArray.set(pos)
        }
        return result
    }

    @Synchronized
    fun find(item: ByteArray): List<Pair<Int, Boolean>> {
        val result = ArrayList<Pair<Int, Boolean>>(hashFuncs)
        val data = mDataArray.toPlainArray()
        for (i in 0 until hashFuncs) {
            val pos = murmurHash3(data, tweek, i, item)
            result.add(Pair(pos, mDataArray[pos]))
        }
        return result
    }


    /**
     * Applies the MurmurHash3 (x86_32) algorithm to the given data.
     * See this [C++ code for the original.](https://github.com/aappleby/smhasher/blob/master/src/MurmurHash3.cpp)
     */
    private fun murmurHash3(data: ByteArray, nTweak: Long, hashNum: Int, item: ByteArray): Int {
        var h1 = hashNum * rootSeed + nTweak
        h1 = BCMEncryptUtils.murmurHash3(h1, item)
        return ((h1 and 0xFFFFFFFFL) % (data.size * 8)).toInt()
    }


}