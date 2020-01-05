package com.bcm.messenger.chats.group.core;

/**
 * @author ling created in 2018/5/28
 **/
public class GroupCoreConstants {
    /**
     * group manage url
     */
    public static final String CREATE_GROUP_URL_V2 = "/v2/group/deliver/create";
    public static final String CREATE_GROUP_URL_V3 = "/v3/group/deliver/create";
    public static final String UPDATE_GROUP_URL_V3 = "/v3/group/deliver/update";
    public static final String UPDATE_GROUP_URL_V2 = "/v2/group/deliver/update";
    public static final String GET_GROUP_INFO_URL = "/v1/group/deliver/query_info";
    public static final String LEAVE_GROUP_URL_V3 = "/v3/group/deliver/leave";
    public static final String LEAVE_GROUP_URL = "/v1/group/deliver/leave";
    public static final String QUERY_GROUP_INFO_BATCH = "/v1/group/deliver/query_info_batch";
    public static final String QUERY_OFFLINE_MSG_STATE = "/v1/group/deliver/query_last_mid";
    public static final String QUERY_GROUP_PENDING_LIST = "/v1/group/deliver/query_group_pending_list";
    public static final String JOIN_GROUP_REVIEW = "/v1/group/deliver/review_join_request";
    public static final String JOIN_GROUP_REVIEW_V3 = "/v3/group/deliver/review_join_request";
    public static final String JOIN_GROUP_UPLOAD_PSW = "/v1/group/deliver/upload_password";//Automatic review
    public static final String CHECK_OWNER_CONFIRM_STATE = "/v1/group/deliver/get_owner_confirm";
    public static final String JOIN_GROUP_BY_CODE_V3 = "/v3/group/deliver/join_group_by_code";
    public static final String JOIN_GROUP_BY_CODE = "/v1/group/deliver/join_group_by_code";
    public static final String CHECK_QR_CODE_VALID = "/v1/group/deliver/is_qr_code_valid";
    public static final String GROUP_SHORT_SHARE = "/v1/opaque_data"; //short link create url


    public static final String GET_GROUP_KEYS = "/v3/group/deliver/group_keys";
    public static final String REFRESH_GROUP_KES = "/v3/group/deliver/fire_group_keys_update";
    public static final String UPLOAD_GROUP_KES = "/v3/group/deliver/group_keys_update";
    public static final String PREPARE_UPLOAD_GROUP_KES = "/v3/group/deliver/prepare_key_update";
    public static final String GROUP_ADD_ME = "/v3/group/deliver/add_me";
    public static final String GROUP_GET_PREKEY = "/v3/group/deliver/dh_keys";
    public static final String GROUP_LATEST_GROUP_KEY = "/v3/group/deliver/latest_group_keys";

    /**
     * group member manage url
     */
    public static final String INVITE_MEMBER_TO_GROUP_URL = "/v2/group/deliver/invite";
    public static final String INVITE_MEMBER_TO_GROUP_URL_V3 = "/v3/group/deliver/invite";
    public static final String GET_GROUP_MEMBER_URL = "/v1/group/deliver/query_member";
    public static final String GET_GROUP_MEMBERS_URL = "/v3/group/deliver/members";


    public static final String KICK_GROUP_MEMBER_URL_V3 = "/v3/group/deliver/kick";
    public static final String KICK_GROUP_MEMBER_URL = "/v1/group/deliver/kick";
    public static final String GET_GROUP_MEMBER_PROFILE_URL = "/v1/profile";
    public static final String QUERY_GROUP_MEMBER_PAGE = "/v1/group/deliver/member_list_ordered";



    /**
     * group message url
     */
    public static final String SEND_GROUP_MESSAGE_URL = "/v1/group/deliver/send_msg";
    public static final String GET_GROUP_MESSAGE_WITH_RANGE_URL = "/v1/group/deliver/get_msg";
    public static final String ACK_GROUP_MESSAGE__URL = "/v1/group/deliver/ack_msg";


    public static final String SEND_GROUP_UPDATE_USER = "/v1/group/deliver/update_user";

    public static final String RECALL_MESSAGE = "/v1/group/deliver/recall_msg";

}
