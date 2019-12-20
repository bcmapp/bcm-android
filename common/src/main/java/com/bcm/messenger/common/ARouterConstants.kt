package com.bcm.messenger.common

/**
 * bcm所有常量工具类
 * Created by ling on 2018/3/6.
 */
object ARouterConstants {

    const val CONSTANT_RELEASE = "release"
    /**
     * 默认背景阴影深度
     */
    const val CONSTANT_BACKGROUND_DIM = 0.3f
    /**
     * 左括弧
     */
    const val CONSTANT_LEFT_BRACKET = "["
    /**
     * 右括弧
     */
    const val CONSTANT_RIGHT_BRACKET = "]"

    /**
     * 最小密码长度
     */
    const val PASSWORD_LEN_MIN = 8

    /**
     * 当前密码正则
     */
    const val PASSWORD_REGEX = "^[\\S]{8,}"

    const val CHAT_AT_CHAR = "@"//特殊的at字段
    /**
     * SDCARD 根目录名称
     * 所有要存储在sdcard的文件都必须在改目录下
     */
    const val SDCARD_ROOT_FOLDER = "bcm"

    const val PRIVATE_MEDIA_CHAT = -1001L   //删除私聊附件
    const val PRIVATE_TEXT_CHAT = -1000L    //删除私聊文本

    /**
     * 分组前缀
     */
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

    /**
     * activity path
     * 注意：跨模块调用的activity才在这里定义，模块内部activity直接强调用
     */
    object Activity {
        /**
         *
         */
        const val APP_DEV_SETTING = PREFIX.APP + "/dev/setting"

        /**
         * 代理配置
         */
        const val PROXY_SETTING = PREFIX.USER + "/proxy_setting"

        /**
         * 首页activity
         */
        const val APP_LAUNCH_PATH = PREFIX.APP + "/launch"

        /**
         * 首页activity
         */
        const val APP_HOME_PATH = PREFIX.APP + "/home"

        /**
         * 无网首页
         */
        const val APP_HOME_AD_HOC_MAIN = PREFIX.ADHOC + "/ad_hoc_main"


        /**
         * 用户注册页
         */
        const val USER_REGISTER_PATH = PREFIX.USER + "/register"

        /**
         * 聊天对象信息页
         */
        const val CHAT_USER_PATH = PREFIX.CHAT + "/user_page"

        /**
         * 聊天文件explorer
         */
        const val CHAT_MEDIA_BROWSER = PREFIX.CHAT + "/media_browser"

        /**
         * 私聊页面
         */
        const val CHAT_CONVERSATION_PATH = PREFIX.CHAT + "/conversation"

        /**
         * 群聊
         */
        const val CHAT_GROUP_CONVERSATION = PREFIX.CHAT + "/chat_group_conversation"

        /**
         * 语音视频通话页面
         */
        const val CHAT_CALL_PATH = PREFIX.CHAT + "/call"

        /**
         * 消息页
         */
        const val CHAT_MESSAGE_PATH = PREFIX.CHAT + "/message_list"

        /**
         * 群聊创建页
         */
        const val CHAT_GROUP_CREATE = PREFIX.CHAT + "/group_create"

        /**
         * 资源清理
         */
        const val CLEAN_STORAGE = PREFIX.CHAT + "/clean_storage"

        /**
         * 反馈
         */
        const val FEEDBACK = PREFIX.USER + "/feedback"

        /**
         * 记事本
         */
        const val NOTE = PREFIX.USER + "/note"

        /**
         * 发起转账页
         */
        const val WALLET_SEND_TRANSACTION = PREFIX.WALLET + "/send"

        /**
         * 地图
         */
        const val MAP = PREFIX.CHAT + "/map"

        /**
         * 地图预览
         */
        const val MAP_PREVIEW = PREFIX.CHAT + "/map_preview"

        /**
         * 网页
         */
        const val WEB = PREFIX.AME + "/web"

        /**
         * 个人的二维码页面
         */
        const val ME_QR = PREFIX.USER + "/qr"

        /**
         * 账号页
         */
        const val ME_ACCOUNT = PREFIX.USER + "/account"

        /**
         * 账号引导页
         */
        const val ME_KEYBOX_GUIDE = PREFIX.USER + "/keybox_guide"

        /**
         * 账号管理页
         */
        const val ME_KEYBOX = PREFIX.USER + "/keybox"

        /**
         * 账号管理页
         */
        const val VERIFY_PASSWORD = PREFIX.USER + "/verify_password"

        /**
         * 转发页
         */
        const val FORWARD = PREFIX.CHAT + "/forward"

        /**
         * profile信息编辑页面
         */
        const val PROFILE_EDIT = PREFIX.USER + "/profile_edit"

        /**
         * 账号销毁
         */
        const val ACCOUNT_DESTROY = PREFIX.USER + "/account_destroy"

        /**
         * 二维码展示
         */
        const val QR_DISPLAY = PREFIX.CHAT + "/qr_display"


        /**
         * pin码输入
         */
        const val PIN_INPUT = PREFIX.USER + "/pin_input"

        /**
         * 编辑昵称
         */
        const val EDIT_NAME = PREFIX.USER + "/edit_name"

        /**
         * 请求加好友
         */
        const val REQUEST_FRIEND = PREFIX.CONTACT + "/request_friend"

        /**
         * 入群申请页
         */
        const val GROUP_JOIN_REQUEST = PREFIX.GROUP + "/request_join"

        /**
         * 入群审核页
         */
        const val GROUP_JOIN_CHECK = PREFIX.GROUP + "/check_join"

        /**
         * 群分享转发
         */
        const val GROUP_SHARE_FORWARD = PREFIX.GROUP + "/share_forward"

        /**
         * 个人名片转发
         */
        const val CONTACT_SHARE_FORWARD = PREFIX.CONTACT + "/share_forward"

        /**
         * 群分享二维码链接展示页面
         */
        const val GROUP_SHARE_DESCRIPTION = PREFIX.GROUP + "/share_description"

        /**
         * 群contact
         */
        const val GROUP_CONTACT_MAIN = PREFIX.CONTACT + "/group_main"

        /**
         * 钱包主页
         */
        const val WALLET_MAIN = PREFIX.WALLET + "/main"

        /**
         * 扫一扫
         */
        const val SCAN_NEW = PREFIX.USER + "/scan_new"

        /**
         * 系统分享
         */
        const val SYSTEM_SHARE = PREFIX.APP + "/system_share"

        /**
         * 无网聊天页
         */
        const val ADHOC_CONVERSATION = PREFIX.ADHOC + "/chat_conversation"

        /**
         * 好友请求列表
         */
        const val FRIEND_REQUEST_LIST = PREFIX.CONTACT + "/friend_request_list"

        const val CONTACT_SEND = PREFIX.CONTACT + "/contact_send"
    }


    /**
     * fragment path
     */
    object Fragment {
        /**
         * 首页-钱包fragment
         */
        const val WALLET_HOST = PREFIX.WALLET + "/host"

        /**
         * 首页-联系人
         */
        const val CONTACTS_HOST = PREFIX.CONTACT + "/host"

        /**
         * 首页-联系群组
         */
        const val CONTACTS_GROUP = PREFIX.CONTACT + "/group"

        /**
         * 首页-联系人
         */
        const val CONTACTS_INDIVIDUAL = PREFIX.CONTACT + "/individual"

        /**
         * 多个联系tag选择（个人和群组）
         */
        const val SELECT_CONTACTS = PREFIX.CONTACT + "/select"

        /**
         * 单个联系选择（个人或群组）
         */
        const val SELECT_SINGLE = PREFIX.CONTACT + "/single_select"

        const val FORWARD_FRAGMENT = PREFIX.CHAT + "/forward_fragment"

    }


    /**
     * provider path
     */
    object Provider {
        /**
         * 联系人对外接口
         */
        const val PROVIDER_CONTACTS_BASE = PREFIX.CONTACT + "/provider/base"

        /**
         * 聊天部分对外接口
         */
        const val PROVIDER_CONVERSATION_BASE = PREFIX.CHAT + "/provider/base"

        /**
         * 群部分对外接口
         */
        const val PROVIDER_GROUP_BASE = PREFIX.GROUP + "/provider/base"

        /**
         * 钱包对外接口
         */
        const val PROVIDER_WALLET_BASE = PREFIX.WALLET + "/provider/base"

        /**
         * Application对外接口
         */
        const val PROVIDER_APPLICATION_BASE = PREFIX.APP + "/provider/application"

        /**
         * me对外接口
         */
        const val PROVIDER_USER_BASE = PREFIX.USER + "/provider/base"

        /**
         * 登录对外接口
         */
        const val PROVIDER_LOGIN_BASE = PREFIX.LOGIN + "/provider/base"

        const val PROVIDER_AD_HOC = PREFIX.ADHOC + "/provider/ad_hoc"
        const val REPORT_BASE = PREFIX.COMMON + "/provider/base"

        /**
         * 友盟实现的provider
         */
        const val PROVIDER_UMENG = PREFIX.OTHER + "/provider/umeng"

        /**
         * 高德地图德provider
         */
        const val PROVIDER_AMAP = PREFIX.OTHER + "/provider/amap"

        /**
         * appsflyer应用跟踪sdk
         */
        const val PROVIDER_AFLIB = PREFIX.OTHER + "provider/aflib"
    }

    /**
     * 路由需要的参数
     */
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
        const val PARAM_CLIENT_INFO = "other_client_info"//其他客户端信息
        const val PARAM_ADHOC_SESSION = "param_session"
        const val PARAM_NICK = "nick"
        const val PARAM_APK = "apk_path"
        const val PARAM_UPGRADE = "app_upgrade"

        //注册或重新绑定
        const val PARAM_LOGIN_FROM_REGISTER = "login_enter_from_register"

        const val WEB_URL = "web_url" //网页地址
        const val WEB_TITLE = "web_title"//网页标题
        const val WEB_FRAGMENT = "web_fragment"

        const val PARAM_QR_CODE = "qr_code"//二维码

        const val PARAM_DELETE = "delete" // 预览界面删除消息

        const val PARAM_EDIT = "edit"//是否进入编辑模式

        const val PARAM_CREATE_GROUP_CHANNEL = "create_group_channel" // 同时创建新群和Channel

        const val PARAM_HOME_TAB_SELECT = "home_tab_select"//触发home tab选择

        const val PARAM_HAS_REQUEST = "has_request"

        const val PARAM_BROWSER_TYPE = "browser_type"//媒体浏览类型

        const val PARAM_ENTER_ANIM = "enter_anim"//目标activity的进入动画
        const val PARAM_EXIT_ANIM = "exit_anim"//目标activity的退出动画

        const val PARAM_LOCALE = "locale_extra"

        const val PARAM_ACCOUNT_ID = "param_account_id" //账号ID
        const val PARAM_DATA = "param_transfer_data"

        /**
         * 用于NEW CHAT里的联系人选择以及群聊创建时的联系人选择fragment
         */
        object CONTACTS_SELECT {

            const val PARAM_MULTI_SELECT = "multi_select"           //是否多选
            const val PARAM_CHANGE_MODE = "can_change_mode"         //是否可切换模式
            const val PARAM_ADDRESS_LIST = "address_list"           //传输多个地址
            const val PARAM_SHOW_DECORATION = "show_decoration"     //是否显示字母标题
            const val PARAM_SELECT_TYPE = "select_type"             //群组选择类型
            const val PARAM_CONTACT_GROUP = "contact_from_group"     //是否来自于群组联系
            const val PARAM_INCLUDE_ME = "contact_include_me"       //是否包含自己
            const val PARAM_ENABLE_CHECKER = "selector_enable_checker"  //激活检查

            object ENABLE_CHECKER {
                const val CHECKER_DEFAULT = "checker.default"
                const val CHECKER_GROUP_V3 = "checker.group.v3"
            }
        }

        /**
         * 用于私聊以及群组信息
         */
        object PRIVATE_CHAT {

            const val IS_ARCHIVED_EXTRA = "is_archived"
            const val TEXT_EXTRA = "draft_text"
            const val DISTRIBUTION_TYPE_EXTRA = "distribution_type"
            const val TIMING_EXTRA = "timing"
            const val LAST_SEEN_EXTRA = "last_seen"
            const val LOCALE_EXTRA = "locale_extra"
        }

        /**
         * 用于群
         */
        object GROUP {
            const val GROUP = "group" //
            const val MEMBERS_ADDRESS = "members_address" //成员地址
            const val SELECT_TYPE = "select_type"
        }

        object MAP {
            const val LATITUDE = "latitude" //
            const val LONGTITUDE = "longtidue"
            const val MAPE_TYPE = "map_type"  //地图类型 1为google，2为高德
            const val TITLE = "title"
            const val ADDRESS = "address"
        }

        /**
         * 用于语音通话的参数信息
         */
        object PRIVATE_CALL {
            const val PARAM_CALL_TYPE = "call_type" //表示通话类型，0，是语音，1是前置摄像头视频，2是后置摄像头视频
            const val PARAM_ACTION = "param_call_action"    //标示通话动作
            const val VALUE_DENY = "value_call_deny"
            const val VALUE_ACCEPT = "value_call_deny"
            const val VALUE_END = "value_call_end"
        }

        /**
         * 用于数字钱包模块
         */
        object WALLET {

            const val WALLET_ADDRESS = "wallet_address"//钱包地址
            const val ACTIVE_PASSWORD = "active_password"
            const val PRIVATE_KEY = "private_key"   //钱包私钥
            const val WALLET_COIN = "wallet_coin"
            const val WALLET_LIST = "wallet_list"
            const val WALLET_NAME = "wallet_name"//钱包默认名称
            const val TRANSFER_DETAIL = "transfer_detail"//交易记录详情
            const val TRANSFER_TXHASH = "transfer_txhash"//交易后得到的结果

            const val COIN_TYPE = "coin_type"//币种类型
            const val ACTIVATE_TYPE = "wallet_active_type"//本地钱包激活类型，有如下几种类型
            const val ACTIVATE_ALL_CREATE = 0
            const val ACTIVATE_ALL_IMPORT = 1
            const val ACTIVATE_AME_CREATE = 2
            const val ACTIVATE_OTHER_IMPORT = 3

        }

        /**
         * 网页样式
         */
        object WEB {
            const val TOOLBAR_STYPE = "toolbar_style"
        }

        /**
         * 搜索专属属性
         */
        object SEARCH {
            const val DISPLAY_ALL = "search_display_all"
            const val HAS_PREVIOUS = "search_has_previous"
            const val HAS_RECENT = "search_has_recent"
            const val CURRENT_KEYWORD = "search_keyword"
            const val FIND_TYPE = "search_find_type"

            const val RECENT_CLAZZ = "search_recent_class"
            const val CURRENT_CLAZZ = "search_current_class"
        }

        /**
         * 群分享专属属性
         */
        object GROUP_SHARE {
            const val GROUP_SHARE_CONTENT = "group_share_content"//群分享名片内容
        }

        /**
         * 扫一扫专属属性
         */
        object SCAN {
            const val SCAN_TYPE = "scan_type"//扫码类型（来自账号，普通加好友或其他）
            const val TYPE_ACCOUNT = 1
            const val TYPE_CONTACT = 2
            const val TYPE_OTHER = 0

            const val SCAN_TITLE = "scan_title"
            const val HANDLE_DELEGATE = "scan_handle_delegate"//是否处理结果委托给扫一扫处理
            const val SCAN_CHARSET = "scan_charset"//扫码字节编码
            const val SCAN_RESULT = "scan_result"//扫码结果
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
            const val PROFILE_FOR_LOCAL = "profile_for_local" //是否用于展示备注
        }
    }

    /**
     * ACTIVITY ACTION
     */
    object ACTION {
        const val ACTION_NOTIFICATION = "com.yy.telegram.notification.CLICK" //通知执行的跳转action
    }
}