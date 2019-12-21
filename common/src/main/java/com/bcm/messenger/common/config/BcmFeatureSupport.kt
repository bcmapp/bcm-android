package com.bcm.messenger.common.config

import com.bcm.messenger.utility.BloomByteArray
import org.spongycastle.util.encoders.DecoderException
import org.spongycastle.util.encoders.Hex

class BcmFeatureSupport(supportData: ByteArray) {
    companion object {
        const val FEATURE_ENABLE = 0 //
        const val FEATURE_BIDIRECTIONAL_CONTACT = 1 //1 
        const val FEATURE_AWS = 2
        const val GROUP_SECURE_V3 = 3
        const val IGNORE_TYPE = 4 //Android ,
    }

    constructor(bits: Int): this(ByteArray(BloomByteArray.bitsSize2ByteSize(bits)))

    @Throws(DecoderException::class)
    constructor(functionHexString: String): this(Hex.decode(functionHexString))

    private val support = BloomByteArray(supportData)

    /**
     * 
     */
    fun enableFunction(functionIndex:Int) {
        if (functionIndex >= 0 && functionIndex < support.bitSize()) {
            this.support.set(functionIndex)
        }
    }

    /**
     * , true ，false 
     */
    fun isSupportBidirectionalContact(): Boolean {
        return isSupport(FEATURE_BIDIRECTIONAL_CONTACT)
    }

    fun isSupportAws(): Boolean {
        return isSupport(FEATURE_AWS)
    }

    fun isSupportGroupSecureV3(): Boolean {
        return isSupport(GROUP_SECURE_V3)
    }

    private fun isSupport(functionIndex:Int): Boolean {
        if (functionIndex < support.bitSize() && functionIndex >=0) {
            return support[functionIndex]
        }
        return false
    }

    override fun toString(): String {
        return Hex.toHexString(support.toPlainArray())
    }
}