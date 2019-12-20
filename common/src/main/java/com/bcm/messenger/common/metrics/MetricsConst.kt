package com.bcm.messenger.common.metrics

/**
 * Created by Kin on 2019/8/23
 */

//counter topic start
const val COUNTER_TOPIC_METRIC_SERVER = "count_metric_server"
const val COUNTER_TOPIC_BCM_SERVER = "count_bcm_server"
const val COUNTER_TOPIC_LBS = "count_lbs"

const val COUNTER_CALLER_GET_TURN_SERVER = "count_caller_get_turn_server"
const val COUNTER_CALLER_SEND_OFFER = "count_caller_send_offer"
const val COUNTER_CALLER_RECEIVED_ANSWER = "count_caller_received_answer"
const val COUNTER_CALLER_ICE_UPDATE = "count_caller_ice_update"
const val COUNTER_CALLER_ICE_CONNECTED = "count_caller_ice_connected"

const val COUNTER_CALLEE_GET_TURN_SERVER = "count_callee_get_turn_server"
const val COUNTER_CALLEE_SEND_ANSWER = "count_callee_send_answer"
const val COUNTER_CALLEE_ICE_UPDATE = "count_callee_ice_update"
const val COUNTER_CALLEE_ICE_CONNECTED = "count_callee_ice_connected"
//counter topic end

//network api topic start
const val API_TOPIC_AWS_S3 = "api_aws_s3"
const val API_TOPIC_BS2 = "api_bs2_server"
const val API_TOPIC_BCM_SERVER = "api_bcm_server"
const val API_TOPIC_LBS_SERVER = "api_lbs_server"
const val API_TOPIC_MEDIA_SERVER = "api_media_server"
const val API_METRICS_SERVER = "api_metrics_server"
const val API_TOPIC_ALIYUN = "api_aliyun"
//network api topic end

//network connect topic start
const val NET_TOPIC_BCM_SERVER = "net_bcm_server"
const val NET_TOPIC_LBS_SERVER = "net_lbs_server"
const val NET_TOPIC_MEDIA_SERVER = "net_media_server"
//network connect topic end

//app topic start
const val APP_TOPIC_BCM = "bcm_app"
//app topic end

//launch key start
const val APP_LAUNCH = "launch"
//launch key end

//lbs histogram key start
const val LBS_LBS = "lbs"
//lbs histogram key end

//metric counter start
const val COUNTER_METRIC_CONNECT_SUCCESS = "connect_success"
const val COUNTER_METRIC_CONNECT_FAIL = "connect_fail"
//metric counter end

//login counter start
const val COUNTER_WEBSOCKET_SUCCESS = "websocket_success"
const val COUNTER_WEBSOCKET_FAIL = "websocket_fail"
//login counter end

//lbs counter start
const val COUNTER_LBS_SUCCESS = "lbs_success"
const val COUNTER_LBS_FAIL = "lbs_fail"
//lbs counter end

const val METRIC_SUCCESS = "0"
const val METRIC_FAILED = "-1"

const val CALL_SUCCESS = "success"
const val CALL_FAILED = "fail"