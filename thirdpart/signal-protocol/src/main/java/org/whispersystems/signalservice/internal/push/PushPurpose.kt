package org.whispersystems.signalservice.internal.push

/**
 * Created by Kin on 2019/9/2
 */
enum class PushPurpose(val index: Int) {
    SILENT(-1),
    NORMAL(0),
    CALLING(1)
}