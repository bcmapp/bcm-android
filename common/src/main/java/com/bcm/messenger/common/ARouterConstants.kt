package com.bcm.messenger.common

/**
 * Created by ling on 2018/3/6.
 */
object ARouterConstants {

    const val CONSTANT_RELEASE = "release"
    const val CONSTANT_BACKGROUND_DIM = 0.3f

    const val CONSTANT_LEFT_BRACKET = "["

    const val CONSTANT_RIGHT_BRACKET = "]"

    const val PASSWORD_LEN_MIN = 8


    const val PASSWORD_REGEX = "^[\\S]{8,}"

    const val CHAT_AT_CHAR = "@"

    const val SDCARD_ROOT_FOLDER = "bcm"

    const val PRIVATE_MEDIA_CHAT = -1001L
    const val PRIVATE_TEXT_CHAT = -1000L


    object PREFIX {
        const val APP = "/app"
        const val CHAT = "/chat"
        const val GROUP = "/group"
        const val CONTACT = "/contact"
        const val WALLET = "/wallet"
        const val USER = "/user"
        const val LOGIN = "/login"
        const val COMMON = "/common"
        const val VPN = "/vpn"
        const val AME = "/ame"
        const val ADHOC = "/adhoc"
        const val FRAMEWORK = "/framework"
        const val OTHER = "/other"
    }

    object Account {
        const val ACCOUNT_CONTEXT = PREFIX.APP + "/account_context"
    }

    /**
     * activity path
     */
    object Activity {
        /**
         *
         */
        const val APP_DEV_SETTING = PREFIX.APP + "/dev/setting"
        const val PROXY_SETTING = PREFIX.USER + "/proxy_setting"

        /**
         * launch activity
         */
        const val APP_LAUNCH_PATH = PREFIX.APP + "/launch"

        /**
         * home activity
         */
        const val APP_HOME_PATH = PREFIX.APP + "/home"

        /**
         * adhoc home
         */
        const val APP_HOME_AD_HOC_MAIN = PREFIX.ADHOC + "/ad_hoc_main"


        /**
         * user register
         */
        const val USER_REGISTER_PATH = PREFIX.USER + "/register"

        /**
         * user page
         */
        const val CHAT_USER_PATH = PREFIX.CHAT + "/user_page"

        /**
         * media explorer
         */
        const val CHAT_MEDIA_BROWSER = PREFIX.CHAT + "/media_browser"

        /**
         * private chat
         */
        const val CHAT_CONVERSATION_PATH = PREFIX.CHAT + "/conversation"

        /**
         * group chat
         */
        const val CHAT_GROUP_CONVERSATION = PREFIX.CHAT + "/chat_group_conversation"

        /**
         * video call
         */
        const val CHAT_CALL_PATH = PREFIX.CHAT + "/call"

        /**
         * thread list
         */
        const val CHAT_MESSAGE_PATH = PREFIX.CHAT + "/message_list"

        /**
         * group create
         */
        const val CHAT_GROUP_CREATE = PREFIX.CHAT + "/group_create"

        /**
         * storage clean
         */
        const val CLEAN_STORAGE = PREFIX.CHAT + "/clean_storage"

        /**
         * feed back
         */
        const val FEEDBACK = PREFIX.USER + "/feedback"

        /**
         * note
         */
        const val NOTE = PREFIX.USER + "/note"

        const val WALLET_SEND_TRANSACTION = PREFIX.WALLET + "/send"

        /**
         * map
         */
        const val MAP = PREFIX.CHAT + "/map"

        /**
         * map preview
         */
        const val MAP_PREVIEW = PREFIX.CHAT + "/map_preview"

        /**
         * web view
         */
        const val WEB = PREFIX.AME + "/web"

        /**
         * qr code
         */
        const val ME_QR = PREFIX.USER + "/qr"

        /**
         * account history
         */
        const val ME_ACCOUNT = PREFIX.USER + "/account"

        /**
         * account guilde
         */
        const val ME_KEYBOX_GUIDE = PREFIX.USER + "/keybox_guide"

        /**
         * account box
         */
        const val ME_KEYBOX = PREFIX.USER + "/keybox"

        /**
         * password verify
         */
        const val VERIFY_PASSWORD = PREFIX.USER + "/verify_password"

        /**
         * forward
         */
        const val FORWARD = PREFIX.CHAT + "/forward"


        const val PROFILE_EDIT = PREFIX.USER + "/profile_edit"

        /**
         * account destroy
         */
        const val ACCOUNT_DESTROY = PREFIX.USER + "/account_destroy"

        /**
         * qr show
         */
        const val QR_DISPLAY = PREFIX.CHAT + "/qr_display"


        /**
         * pin input
         */
        const val PIN_INPUT = PREFIX.USER + "/pin_input"


        const val EDIT_NAME = PREFIX.USER + "/edit_name"


        const val REQUEST_FRIEND = PREFIX.CONTACT + "/request_friend"


        const val GROUP_JOIN_REQUEST = PREFIX.GROUP + "/request_join"


        const val GROUP_JOIN_CHECK = PREFIX.GROUP + "/check_join"

        const val GROUP_SHARE_FORWARD = PREFIX.GROUP + "/share_forward"

        const val CONTACT_SHARE_FORWARD = PREFIX.CONTACT + "/share_forward"


        const val GROUP_SHARE_DESCRIPTION = PREFIX.GROUP + "/share_description"


        const val GROUP_CONTACT_MAIN = PREFIX.CONTACT + "/group_main"

        const val WALLET_MAIN = PREFIX.WALLET + "/main"


        const val SCAN_NEW = PREFIX.USER + "/scan_new"


        const val SYSTEM_SHARE = PREFIX.APP + "/system_share"


        const val ADHOC_CONVERSATION = PREFIX.ADHOC + "/chat_conversation"


        const val FRIEND_REQUEST_LIST = PREFIX.CONTACT + "/friend_request_list"

        const val CONTACT_SEND = PREFIX.CONTACT + "/contact_send"

        const val SETTINGS = PREFIX.USER + "/settings"
    }


    /**
     * fragment path
     */
    object Fragment {

        const val WALLET_HOST = PREFIX.WALLET + "/host"


        const val CONTACTS_HOST = PREFIX.CONTACT + "/host"


        const val CONTACTS_GROUP = PREFIX.CONTACT + "/group"


        const val CONTACTS_INDIVIDUAL = PREFIX.CONTACT + "/individual"
        const val SELECT_CONTACTS = PREFIX.CONTACT + "/select"
        const val SELECT_SINGLE = PREFIX.CONTACT + "/single_select"

        const val FORWARD_FRAGMENT = PREFIX.CHAT + "/forward_fragment"

    }


    /**
     * provider path
     */
    object Provider {
        const val PROVIDER_CONTACTS_BASE = PREFIX.CONTACT + "/provider/base"

        const val PROVIDER_CONVERSATION_BASE = PREFIX.CHAT + "/provider/base"

        const val PROVIDER_GROUP_BASE = PREFIX.GROUP + "/provider/base"

        const val PROVIDER_WALLET_BASE = PREFIX.WALLET + "/provider/base"

        const val PROVIDER_APPLICATION_BASE = PREFIX.APP + "/provider/application"

        const val PROVIDER_USER_BASE = PREFIX.USER + "/provider/base"

        const val PROVIDER_LOGIN_BASE = PREFIX.LOGIN + "/provider/base"

        const val PROVIDER_AD_HOC = PREFIX.ADHOC + "/provider/ad_hoc"
        const val REPORT_BASE = PREFIX.COMMON + "/provider/base"

        const val PROVIDER_UMENG = PREFIX.OTHER + "/provider/umeng"

        const val PROVIDER_AMAP = PREFIX.OTHER + "/provider/amap"

        const val PROVIDER_AFLIB = PREFIX.OTHER + "provider/aflib"
    }

    object PARAM {

        const val PARAM_MASTER_SECRET = "master_secret"
        const val PARAM_NOTIFY_DATA = "bcmdata"
        const val PARAM_ROUTE_PATH = "route_path"
        const val PARAM_ADDRESS = "address"
        const val PARAM_UID = "uid"
        const val PARAM_THREAD = "thread_id"
        const val PARAM_IMAGE_URI = "image_uri"
        const val PARAM_BACK_HOME = "back_to_main_home"
        const val PARAM_GROUP_ID = "groupId"
        const val PARAM_GROUP_ROLE = "role"
        const val PARAM_INDEX_ID = "indexId"
        const val PARAM_OFFLINE_MESSAGE = "offline_message"
        const val PARAM_CLIENT_INFO = "other_client_info"
        const val PARAM_ADHOC_SESSION = "param_session"
        const val PARAM_NICK = "nick"
        const val PARAM_APK = "apk_path"
        const val PARAM_UPGRADE = "app_upgrade"

        const val PARAM_LOGIN_FROM_REGISTER = "login_enter_from_register"

        const val WEB_URL = "web_url"
        const val WEB_TITLE = "web_title"
        const val WEB_FRAGMENT = "web_fragment"

        const val PARAM_QR_CODE = "qr_code"

        const val PARAM_DELETE = "delete"

        const val PARAM_EDIT = "edit"

        const val PARAM_CREATE_GROUP_CHANNEL = "create_group_channel"

        const val PARAM_HOME_TAB_SELECT = "home_tab_select"

        const val PARAM_HAS_REQUEST = "has_request"

        const val PARAM_BROWSER_TYPE = "browser_type"

        const val PARAM_ENTER_ANIM = "enter_anim"
        const val PARAM_EXIT_ANIM = "exit_anim"

        const val PARAM_LOCALE = "locale_extra"

        const val PARAM_ACCOUNT_ID = "param_account_id"
        const val PARAM_DATA = "param_transfer_data"

        const val PARAM_PREVIOUS_ENTER_ANIM = "previous_enter_anim"//previous activity enter anim
        const val PARAM_PREVIOUS_EXIT_ANIM ="previous_exist_anim"//previous activity exist anim


        object CONTACTS_SELECT {

            const val PARAM_MULTI_SELECT = "multi_select"
            const val PARAM_CHANGE_MODE = "can_change_mode"
            const val PARAM_ADDRESS_LIST = "address_list"
            const val PARAM_SHOW_DECORATION = "show_decoration"
            const val PARAM_SELECT_TYPE = "select_type"
            const val PARAM_CONTACT_GROUP = "contact_from_group"
            const val PARAM_INCLUDE_ME = "contact_include_me"
            const val PARAM_ENABLE_CHECKER = "selector_enable_checker"

            object ENABLE_CHECKER {
                const val CHECKER_DEFAULT = "checker.default"
                const val CHECKER_GROUP_V3 = "checker.group.v3"
            }
        }


        object PRIVATE_CHAT {

            const val IS_ARCHIVED_EXTRA = "is_archived"
            const val TEXT_EXTRA = "draft_text"
            const val DISTRIBUTION_TYPE_EXTRA = "distribution_type"
            const val TIMING_EXTRA = "timing"
            const val LAST_SEEN_EXTRA = "last_seen"
            const val LOCALE_EXTRA = "locale_extra"
        }

        object GROUP {
            const val GROUP = "group" //
            const val MEMBERS_ADDRESS = "members_address"
            const val SELECT_TYPE = "select_type"
        }

        object MAP {
            const val LATITUDE = "latitude" //
            const val LONGTITUDE = "longtidue"
            const val MAPE_TYPE = "map_type"
            const val TITLE = "title"
            const val ADDRESS = "address"
        }

        object PRIVATE_CALL {
            const val PARAM_CALL_TYPE = "call_type"
            const val PARAM_ACTION = "param_call_action"
            const val VALUE_DENY = "value_call_deny"
            const val VALUE_ACCEPT = "value_call_deny"
            const val VALUE_END = "value_call_end"
        }


        object WALLET {

            const val WALLET_ADDRESS = "wallet_address"
            const val ACTIVE_PASSWORD = "active_password"
            const val PRIVATE_KEY = "private_key"
            const val WALLET_COIN = "wallet_coin"
            const val WALLET_LIST = "wallet_list"
            const val WALLET_NAME = "wallet_name"
            const val TRANSFER_DETAIL = "transfer_detail"
            const val TRANSFER_TXHASH = "transfer_txhash"

            const val COIN_TYPE = "coin_type"
            const val ACTIVATE_TYPE = "wallet_active_type"
            const val ACTIVATE_ALL_CREATE = 0
            const val ACTIVATE_ALL_IMPORT = 1
            const val ACTIVATE_AME_CREATE = 2
            const val ACTIVATE_OTHER_IMPORT = 3

        }

        object WEB {
            const val TOOLBAR_STYPE = "toolbar_style"
        }

        object SEARCH {
            const val DISPLAY_ALL = "search_display_all"
            const val HAS_PREVIOUS = "search_has_previous"
            const val HAS_RECENT = "search_has_recent"
            const val CURRENT_KEYWORD = "search_keyword"
            const val FIND_TYPE = "search_find_type"

            const val RECENT_CLAZZ = "search_recent_class"
            const val CURRENT_CLAZZ = "search_current_class"
        }

        object GROUP_SHARE {
            const val GROUP_SHARE_CONTENT = "group_share_content"
        }

        object SCAN {
            const val SCAN_TYPE = "scan_type"
            const val TYPE_ACCOUNT = 1
            const val TYPE_CONTACT = 2
            const val TYPE_SCAN = 3
            const val TYPE_OTHER = 0

            const val SCAN_TITLE = "scan_title"
            const val HANDLE_DELEGATE = "scan_handle_delegate"
            const val SCAN_CHARSET = "scan_charset"
            const val SCAN_RESULT = "scan_result"

            const val TAB = "scan_page_tab"
        }

        object HOME {
            const val ACTION = "home_action"
            const val ROUTER = "home_router"
            const val BUNDLE = "home_bundle"
            const val URI = "home_uri"
        }

        object ADHOC {
            const val SESSION_NAME = "adhoc_session_name"
            const val SESSION_PWD = "adhoc_session_password"
            const val CID = "adhoc_session_cid"
            const val SESSION_MULTISELECT = "adhoc_session_multiselect"
            const val SESSION_TITLE = "adhoc_session_title"
        }

        object ME {
            const val PROFILE_EDIT = "profile_edit"
            const val PROFILE_FOR_LOCAL = "profile_for_local"
        }
    }

    /**
     * ACTIVITY ACTION
     */
    object ACTION {
        const val ACTION_NOTIFICATION = "com.yy.telegram.notification.CLICK"
    }
}