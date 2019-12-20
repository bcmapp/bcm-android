package com.bcm.messenger.common.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.bcm.messenger.common.database.records.PrivacyProfile

/**
 * Created by Kin on 2019/9/16
 */
@Entity(tableName = RecipientDbModel.TABLE_NAME)
open class RecipientDbModel {
    companion object {
        const val TABLE_NAME = "recipient"
    }

    @PrimaryKey
    var uid = ""
    var block = 0
    @ColumnInfo(name = "mute_until")
    var muteUntil = 0L
    @ColumnInfo(name = "expires_time")
    var expiresTime = 0L
    @ColumnInfo(name = "local_name")
    var localName: String? = null
    @ColumnInfo(name = "local_avatar")
    var localAvatar: String? = null
    @ColumnInfo(name = "profile_key")
    var profileKey: String? = null
    @ColumnInfo(name = "profile_name")
    var profileName: String? = null
    @ColumnInfo(name = "profile_avatar")
    var profileAvatar: String? = null
    @ColumnInfo(name = "profile_sharing_approval")
    var profileSharingApproval = 0
    @ColumnInfo(name = "privacy_profile")
    var privacyProfile = PrivacyProfile()
    var relationship = 0
    @ColumnInfo(name = "support_feature")
    var supportFeature = ""
    @ColumnInfo(name = "contact_part_key")
    var contactPartKey: String? = null
}