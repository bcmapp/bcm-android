package com.bcm.messenger.common.config

import com.bcm.messenger.utility.BloomByteArray
import org.spongycastle.util.encoders.DecoderException
import org.spongycastle.util.encoders.Hex

class BcmFeatureSupport(supportData: ByteArray) {
    companion object {
        const val FEATURE_ENABLE = 0 //启用功能较验
        const val FEATURE_BIDIRECTIONAL_CONTACT = 1 //索引1 表示支持双向好友
        const val FEATURE_AWS = 2
        const val GROUP_SECURE_V3 = 3
        const val IGNORE_TYPE = 4 //Android 占坑类型,不要用
    }

    constructor(bits: Int): this(ByteArray(BloomByteArray.bitsSize2ByteSize(bits)))

    @Throws(DecoderException::class)
    constructor(functionHexString: String): this(Hex.decode(functionHexString))

    private val support = BloomByteArray(supportData)

    /**
     * 激活某个功能
     */
    fun enableFunction(functionIndex:Int) {
        if (functionIndex >= 0 && functionIndex < support.bitSize()) {
            this.support.set(functionIndex)
        }
    }

    /**
     * 是否支持双向好友关系, true 支持，false 不支持
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