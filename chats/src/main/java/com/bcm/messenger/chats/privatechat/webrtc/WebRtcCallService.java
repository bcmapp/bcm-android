package com.bcm.messenger.chats.privatechat.webrtc;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.telephony.TelephonyManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.bcm.messenger.chats.privatechat.core.BcmChatCore;
import com.bcm.messenger.chats.privatechat.core.ChatHttp;
import com.bcm.messenger.chats.privatechat.webrtc.WebRtcDataProtos.Connected;
import com.bcm.messenger.chats.privatechat.webrtc.WebRtcDataProtos.Data;
import com.bcm.messenger.chats.privatechat.webrtc.WebRtcDataProtos.Hangup;
import com.bcm.messenger.chats.privatechat.webrtc.audio.BluetoothStateManager;
import com.bcm.messenger.chats.privatechat.webrtc.audio.OutgoingRinger;
import com.bcm.messenger.chats.privatechat.webrtc.audio.SignalAudioManager;
import com.bcm.messenger.chats.privatechat.webrtc.locks.LockManager;
import com.bcm.messenger.common.ARouterConstants;
import com.bcm.messenger.common.core.Address;
import com.bcm.messenger.common.core.AmeLanguageUtilsKt;
import com.bcm.messenger.common.database.model.PrivateChatDbModel;
import com.bcm.messenger.common.database.repositories.Repository;
import com.bcm.messenger.common.event.MessageReceiveNotifyEvent;
import com.bcm.messenger.common.metrics.MetricsConstKt;
import com.bcm.messenger.common.metrics.ReportUtil;
import com.bcm.messenger.common.preferences.TextSecurePreferences;
import com.bcm.messenger.common.provider.AMESelfData;
import com.bcm.messenger.common.provider.accountmodule.IChatModule;
import com.bcm.messenger.common.recipients.Recipient;
import com.bcm.messenger.common.utils.AppUtil;
import com.bcm.messenger.common.utils.AppUtilKotlinKt;
import com.bcm.messenger.utility.AppContextHolder;
import com.bcm.messenger.utility.Util;
import com.bcm.messenger.utility.concurrent.FutureTaskListener;
import com.bcm.messenger.utility.concurrent.ListenableFutureTask;
import com.bcm.messenger.utility.dispatcher.AmeDispatcher;
import com.bcm.messenger.utility.logger.ALog;
import com.bcm.messenger.utility.permission.PermissionUtil;
import com.bcm.route.api.BcmRouter;
import com.google.gson.Gson;

import org.greenrobot.eventbus.EventBus;
import org.webrtc.AudioTrack;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.VideoTrack;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.calls.AnswerMessage;
import org.whispersystems.signalservice.api.messages.calls.BusyMessage;
import org.whispersystems.signalservice.api.messages.calls.HangupMessage;
import org.whispersystems.signalservice.api.messages.calls.IceUpdateMessage;
import org.whispersystems.signalservice.api.messages.calls.OfferMessage;
import org.whispersystems.signalservice.api.messages.calls.SignalServiceCallMessage;
import org.whispersystems.signalservice.api.messages.calls.TurnServerInfo;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;
import org.whispersystems.signalservice.internal.util.concurrent.SettableFuture;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import kotlin.Pair;

import static com.bcm.messenger.chats.privatechat.webrtc.CallNotificationBuilder.TYPE_CALL_DEFAULT;
import static com.bcm.messenger.chats.privatechat.webrtc.CallNotificationBuilder.TYPE_ESTABLISHED;
import static com.bcm.messenger.chats.privatechat.webrtc.CallNotificationBuilder.TYPE_INCOMING_RINGING;
import static com.bcm.messenger.chats.privatechat.webrtc.CallNotificationBuilder.TYPE_OUTGOING_RINGING;


public class WebRtcCallService extends Service implements PeerConnection.Observer, DataChannel.Observer, BluetoothStateManager.BluetoothStateListener, PeerConnectionWrapper.CameraEventListener {

    private static final String TAG = "WebRtcCallService";

    public enum CallState {
        STATE_IDLE,
        STATE_DIALING,
        STATE_ANSWERING,
        STATE_REMOTE_RINGING,
        STATE_LOCAL_RINGING,
        STATE_CONNECTED
    }

    private static final String DATA_CHANNEL_NAME = "signaling";

    public static final String EXTRA_REMOTE_ADDRESS = "remote_address";
    public static final String EXTRA_MUTE = "mute_value";
    public static final String EXTRA_AVAILABLE = "enabled_value";
    public static final String EXTRA_REMOTE_DESCRIPTION = "remote_description";
    public static final String EXTRA_TIMESTAMP = "timestamp";
    public static final String EXTRA_CALL_ID = "call_id";
    public static final String EXTRA_ICE_SDP = "ice_sdp";
    public static final String EXTRA_ICE_SDP_MID = "ice_sdp_mid";
    public static final String EXTRA_ICE_SDP_LINE_INDEX = "ice_sdp_line_index";
    public static final String EXTRA_RESULT_RECEIVER = "result_receiver";

    public static final String ACTION_INCOMING_CALL = "CALL_INCOMING";
    public static final String ACTION_OUTGOING_CALL = "CALL_OUTGOING";
    public static final String ACTION_ANSWER_CALL = "ANSWER_CALL";
    public static final String ACTION_DENY_CALL = "DENY_CALL";
    public static final String ACTION_LOCAL_HANGUP = "LOCAL_HANGUP";
    public static final String ACTION_SET_MUTE_AUDIO = "SET_MUTE_AUDIO";
    public static final String ACTION_SET_MUTE_VIDEO = "SET_MUTE_VIDEO";
    public static final String ACTION_FLIP_CAMERA = "FLIP_CAMERA";
    public static final String ACTION_BLUETOOTH_CHANGE = "BLUETOOTH_CHANGE";
    public static final String ACTION_WIRED_HEADSET_CHANGE = "WIRED_HEADSET_CHANGE";
    public static final String ACTION_SCREEN_OFF = "SCREEN_OFF";
    public static final String ACTION_CHECK_TIMEOUT = "CHECK_TIMEOUT";
    public static final String ACTION_IS_IN_CALL_QUERY = "IS_IN_CALL";

    public static final String ACTION_RESPONSE_MESSAGE = "RESPONSE_MESSAGE";
    public static final String ACTION_ICE_MESSAGE = "ICE_MESSAGE";
    public static final String ACTION_ICE_CANDIDATE = "ICE_CANDIDATE";
    public static final String ACTION_CALL_CONNECTED = "CALL_CONNECTED";
    public static final String ACTION_REMOTE_HANGUP = "REMOTE_HANGUP";
    public static final String ACTION_REMOTE_BUSY = "REMOTE_BUSY";
    public static final String ACTION_REMOTE_VIDEO_MUTE = "REMOTE_VIDEO_MUTE";
    public static final String ACTION_ICE_CONNECTED = "ICE_CONNECTED";

    public static final String ACTION_GRANTED_AUDIO = "GRANTED_AUDIO";
    public static final long FINISH_DELAY = 1000L;
    public static final long FINISH_BUSY_DELAY = 5000L;
    static final int NOTIFICATION_NONE = 0;
    static final int NOTIFICATION_SIMPLY = 1;
   
    static final int NOTIFICATION_IMPORTANT = 2;
    private static int sCurrentCallType = -1;
    public static void checkHasWebRtcCall() {
        if (sCurrentCallType != -1) {
            startCallActivity(sCurrentCallType);
        }
    }


    public static void clearWebRtcCallType() {
        sCurrentCallType = -1;
    }

    
    private static void startCallActivity(int callType) {
        sCurrentCallType = callType;
        ALog.i(TAG, "start call activity:" + callType);
        
        IChatModule provider = (IChatModule) BcmRouter.getInstance().get(ARouterConstants.Provider.PROVIDER_CONVERSATION_BASE).navigation();
        provider.startRtcCallActivity(AppContextHolder.APP_CONTEXT, callType);
    }

    private AtomicReference<CallState> callState = new AtomicReference<>(CallState.STATE_IDLE); 
   
    private AtomicBoolean mIncoming = new AtomicBoolean(false);

    private CameraState localCameraState = CameraState.UNKNOWN;
    private boolean microphoneEnabled = true;
    private boolean remoteVideoEnabled = false;
    private boolean bluetoothAvailable = false;
    private Gson formatter = new Gson();

    private PeerConnectionFactory peerConnectionFactory;
    @NonNull
    private SignalAudioManager audioManager;

    @Nullable
    private BluetoothStateManager bluetoothStateManager;
    @Nullable
    private WiredHeadsetStateReceiver wiredHeadsetStateReceiver;
    @Nullable
    private PowerButtonReceiver powerButtonReceiver;
    @NonNull
    private LockManager lockManager;
    @Nullable
    private UncaughtExceptionHandlerManager uncaughtExceptionHandlerManager;
    private long callId = -1L;
    @Nullable
    private Recipient recipient;
    @Nullable
    private PeerConnectionWrapper peerConnection;
    @Nullable
    private DataChannel dataChannel;
    @Nullable
    private List<IceUpdateMessage> pendingOutgoingIceUpdates;
    @Nullable
    private List<IceCandidate> pendingIncomingIceUpdates;

    @Nullable
    public static SurfaceViewRenderer localRenderer;
    @Nullable
    public static SurfaceViewRenderer remoteRenderer;
    @Nullable
    private static EglBase eglBase;

    private ExecutorService serviceExecutor = Executors.newSingleThreadExecutor();
    private ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    private ScheduledExecutorService timeoutExecutor = Executors.newScheduledThreadPool(1);

    /**
     * 
     */
    private ScheduledFuture timeoutFuture;
    /**
     * 
     */
    private long mBeginTimestamp = 0;

    private int mCurrentNotificationType = TYPE_CALL_DEFAULT;

    private String currentMetric = "";

    private AtomicBoolean mMsgInserted = new AtomicBoolean(false); //

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AmeLanguageUtilsKt.setLocale(base));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ALog.i(TAG, "onCreate");
        setCallNotification(TYPE_CALL_DEFAULT);

        initializeResources();

        registerUncaughtExceptionHandler();
        registerWiredHeadsetStateReceiver();

    }

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            ALog.d(TAG, "onStartCommand end");
            return START_NOT_STICKY;
        }

        ALog.i(TAG, "onStart action = " + intent.getAction() + ", callState: " + this.callState.get().name());
        serviceExecutor.execute(() -> {
            ALog.d(TAG, "onStartCommand: action: " + intent.getAction());
            try {
                switch (intent.getAction()) {
                    case ACTION_INCOMING_CALL:
                        if (isBusy()) {
                            handleBusyCall(intent);
                        } else if (isIncomingMessageExpired(intent)) {
                            handleExpiredIncoming(intent);
                        } else {
                            handleIncomingCall(intent);
                        }
                        break;
                    case ACTION_REMOTE_BUSY:
                        handleBusyMessage(intent);
                        break;
                    case ACTION_OUTGOING_CALL:
                        if (isIdle()) {
                            handleOutgoingCall(intent);
                        }
                        break;
                    case ACTION_ANSWER_CALL:
                        handleAnswerCall(intent);
                        break;
                    case ACTION_DENY_CALL:
                        handleDenyCall(intent);
                        break;
                    case ACTION_LOCAL_HANGUP:
                        handleLocalHangup(intent);
                        break;
                    case ACTION_REMOTE_HANGUP:
                        handleRemoteHangup(intent);
                        break;
                    case ACTION_SET_MUTE_AUDIO:
                        handleSetMuteAudio(intent);
                        break;
                    case ACTION_SET_MUTE_VIDEO:
                        handleSetMuteVideo(intent);
                        break;
                    case ACTION_FLIP_CAMERA:
                        handleSetCameraFlip(intent);
                        break;
                    case ACTION_BLUETOOTH_CHANGE:
                        handleBluetoothChange(intent);
                        break;
                    case ACTION_WIRED_HEADSET_CHANGE:
                        handleWiredHeadsetChange(intent);
                        break;
                    case (ACTION_SCREEN_OFF):
                        handleScreenOffChange(intent);
                        break;
                    case ACTION_REMOTE_VIDEO_MUTE:
                        handleRemoteVideoMute(intent);
                        break;
                    case ACTION_RESPONSE_MESSAGE:
                        handleResponseMessage(intent);
                        break;
                    case ACTION_ICE_MESSAGE:
                        handleRemoteIceCandidate(intent);
                        break;
                    case ACTION_ICE_CANDIDATE:
                        handleLocalIceCandidate(intent);
                        break;
                    case ACTION_ICE_CONNECTED:
                        handleIceConnected(intent);
                        break;
                    case ACTION_CALL_CONNECTED:
                        handleCallConnected(intent);
                        break;
                    case ACTION_CHECK_TIMEOUT:
                        handleCheckTimeout(intent);
                        break;
                    case ACTION_IS_IN_CALL_QUERY:
                        handleIsInCallQuery(intent);
                        break;
                    case ACTION_GRANTED_AUDIO:
                        handleGrantedAudioPermission(intent);
                        break;
                    default:
                        break;
                }

                if (this.callState.get() == CallState.STATE_IDLE) {
                    terminate();
                }

            } catch (Exception ex) {
                ALog.logForSecret(TAG, "onStartCommand error", ex);
                terminate();
            }
        });

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            if (uncaughtExceptionHandlerManager != null) {
                uncaughtExceptionHandlerManager.unregister();
                uncaughtExceptionHandlerManager = null;
            }

            if (bluetoothStateManager != null) {
                bluetoothStateManager.onDestroy();
                bluetoothStateManager = null;
            }

            if (wiredHeadsetStateReceiver != null) {
                unregisterReceiver(wiredHeadsetStateReceiver);
                wiredHeadsetStateReceiver = null;
            }

            if (powerButtonReceiver != null) {
                unregisterReceiver(powerButtonReceiver);
                powerButtonReceiver = null;
            }

        } catch (Exception ex) {
            ALog.e(TAG, "onDestroy error", ex);
        } finally {
            terminate();
        }
    }

    @Override
    public void onBluetoothStateChanged(boolean isAvailable) {
        ALog.w(TAG, "onBluetoothStateChanged: " + isAvailable);

        try {
            Intent intent = new Intent(this, WebRtcCallService.class);
            intent.setAction(ACTION_BLUETOOTH_CHANGE);
            intent.putExtra(EXTRA_AVAILABLE, isAvailable);
            AppUtilKotlinKt.startForegroundServiceCompat(this, intent);
        } catch (Exception ex) {
            ALog.e("WebRtcCallService", "onBluetoothStateChanged start service error", ex);
        }
    }

    private void initializeResources() {
        ALog.i(TAG, "initializeResources");

        this.callState.set(CallState.STATE_IDLE);
        this.lockManager = new LockManager(this);
        this.audioManager = new SignalAudioManager(this);
        this.bluetoothStateManager = new BluetoothStateManager(this, this);
    }

    private void registerUncaughtExceptionHandler() {
        uncaughtExceptionHandlerManager = new UncaughtExceptionHandlerManager();
        uncaughtExceptionHandlerManager.registerHandler(new ProximityLockRelease(lockManager));
    }

    private void registerWiredHeadsetStateReceiver() {
        wiredHeadsetStateReceiver = new WiredHeadsetStateReceiver();

        String action = AudioManager.ACTION_HEADSET_PLUG;

        registerReceiver(wiredHeadsetStateReceiver, new IntentFilter(action));
    }

    private void registerPowerButtonReceiver() {
        if (powerButtonReceiver == null) {
            powerButtonReceiver = new PowerButtonReceiver();
            registerReceiver(powerButtonReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        }
    }

    private void unregisterPowerButtonReceiver() {
        if (powerButtonReceiver != null) {
            unregisterReceiver(powerButtonReceiver);
            powerButtonReceiver = null;
        }
    }

    /**
     * 
     *
     * @param intent
     */
    private void handleExpiredIncoming(final Intent intent) throws Exception {
        if (!callState.compareAndSet(CallState.STATE_IDLE, CallState.STATE_ANSWERING)) {
            ALog.w(TAG, "handleExpiredIncoming fail, current state is not idle");
            return;
        }
        this.mIncoming.set(true);
        this.recipient = getRemoteRecipient(intent);
        insertMissedCall(this.recipient, NOTIFICATION_IMPORTANT);
        terminate();
    }

    private void handleIncomingCall(final Intent intent) throws Exception {
        ALog.w(TAG, "handleIncomingCall()");
        if (!callState.compareAndSet(CallState.STATE_IDLE, CallState.STATE_ANSWERING)) {
            ALog.w(TAG, "handleIncomingCall fail, current state is not idle");
            throw new IllegalStateException("Incoming on non-idle");
        }
        // Set current metric step is callee get turn server
        this.currentMetric = MetricsConstKt.COUNTER_CALLEE_GET_TURN_SERVER;
        this.mIncoming.set(true);
        final String offer = intent.getStringExtra(EXTRA_REMOTE_DESCRIPTION);
        this.callId = intent.getLongExtra(EXTRA_CALL_ID, -1L);
        this.pendingIncomingIceUpdates = new LinkedList<>();
        this.recipient = getRemoteRecipient(intent);

        setCallInProgressNotification(TYPE_INCOMING_RINGING, recipient);

        ALog.d(TAG, "handleIncomingCall:" + recipient.getAddress() + ", callId: " + this.callId);

        timeoutFuture = timeoutExecutor.schedule(new TimeoutRunnable(this.callId), 2, TimeUnit.MINUTES);

        initializeVideo();

        retrieveTurnServers().addListener(new SuccessOnlyListener<List<PeerConnection.IceServer>>(CallState.STATE_ANSWERING, this.callId) {
            @Override
            public void onSuccessContinue(List<PeerConnection.IceServer> result) {
                try {
                    ALog.logForSecret(TAG, "handleIncomingCall " + formatter.toJson(result));

                    if (result.isEmpty()) {
                        throw new EmptyTurnException("IceServer list is empty!");
                    }

                    // Callee get turn server success, report success, set next step
                    ReportUtil.INSTANCE.addCustomCounterReportData(currentMetric, MetricsConstKt.CALL_FAILED, false);
                    ReportUtil.INSTANCE.addCustomCounterReportData(currentMetric, MetricsConstKt.CALL_SUCCESS, true);
                    currentMetric = MetricsConstKt.COUNTER_CALLEE_SEND_ANSWER;

                    boolean isAlwaysTurn = TextSecurePreferences.isTurnOnly(WebRtcCallService.this);

                    WebRtcCallService.this.peerConnection = new PeerConnectionWrapper(WebRtcCallService.this, peerConnectionFactory, WebRtcCallService.this, localRenderer, result, WebRtcCallService.this, eglBase, isAlwaysTurn);
                    WebRtcCallService.this.peerConnection.setRemoteDescription(new SessionDescription(SessionDescription.Type.OFFER, offer));
                    WebRtcCallService.this.lockManager.updatePhoneState(LockManager.PhoneState.PROCESSING);

                    SessionDescription sdp = WebRtcCallService.this.peerConnection.createAnswer(new MediaConstraints());
                    ALog.logForSecret(TAG, "Answer SDP: " + sdp.description);
                    WebRtcCallService.this.peerConnection.setLocalDescription(sdp);

                    ListenableFutureTask<Boolean> listenableFutureTask = sendMessage(recipient, SignalServiceCallMessage.forAnswer(new AnswerMessage(WebRtcCallService.this.callId, sdp.description)));

                    for (IceCandidate candidate : pendingIncomingIceUpdates) {
                        WebRtcCallService.this.peerConnection.addIceCandidate(candidate);
                    }
                    WebRtcCallService.this.pendingIncomingIceUpdates = null;

                    if (listenableFutureTask != null) {
                        listenableFutureTask.addListener(new FailureListener<Boolean>(CallState.STATE_ANSWERING, callId) {
                            @Override
                            public void onSuccessContinue(Boolean result) {
                                ReportUtil.INSTANCE.addCustomCounterReportData(currentMetric, MetricsConstKt.CALL_FAILED, false);
                                ReportUtil.INSTANCE.addCustomCounterReportData(currentMetric, MetricsConstKt.CALL_SUCCESS, true);
                                currentMetric = MetricsConstKt.COUNTER_CALLEE_ICE_UPDATE;
                            }

                            @Override
                            public void onFailureContinue(Throwable throwable) {
                                insertMissedCall(recipient, NOTIFICATION_IMPORTANT);

                                ReportUtil.INSTANCE.addCustomCounterReportData(currentMetric, MetricsConstKt.CALL_FAILED, true);
                                ReportUtil.INSTANCE.addCustomCounterReportData(currentMetric, MetricsConstKt.CALL_SUCCESS, false);

                                sendMessage(WebRtcViewModel.State.NETWORK_FAILURE, recipient, localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
                                terminate();
                            }
                        });
                    }

                } catch (Exception e) {
                    ALog.logForSecret(TAG, "handleIncomingCall error", e);

                    ReportUtil.INSTANCE.addCustomCounterReportData(currentMetric, MetricsConstKt.CALL_FAILED, true);
                    ReportUtil.INSTANCE.addCustomCounterReportData(currentMetric, MetricsConstKt.CALL_SUCCESS, false);

                    sendMessage(WebRtcViewModel.State.NETWORK_FAILURE, recipient, localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
                    terminate();
                }
            }
        });
    }

    private void handleOutgoingCall(Intent intent) throws Exception {
        ALog.w(TAG, "handleOutgoingCall...");

        if (!callState.compareAndSet(CallState.STATE_IDLE, CallState.STATE_DIALING)) {
            ALog.w(TAG, "handleOutgoingCall fail, current call state is not idle");
            throw new IllegalStateException("Dialing from non-idle");
        }
        // Set current metric step is caller get turn server
        this.currentMetric = MetricsConstKt.COUNTER_CALLER_GET_TURN_SERVER;
        this.mIncoming.set(false);
        this.recipient = getRemoteRecipient(intent);
        this.callId = SecureRandom.getInstance("SHA1PRNG").nextLong();
        this.pendingOutgoingIceUpdates = new LinkedList<>();

        setCallInProgressNotification(TYPE_OUTGOING_RINGING, recipient);
        startCallActivity(intent.getIntExtra(ARouterConstants.PARAM.PRIVATE_CALL.PARAM_CALL_TYPE, CameraState.Direction.NONE.ordinal()));

        ALog.d(TAG, "handleOutgoingCall:" + recipient.getAddress());

        initializeVideo();

        sendMessage(WebRtcViewModel.State.CALL_OUTGOING, recipient, localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);

        lockManager.updatePhoneState(LockManager.PhoneState.IN_CALL);
        audioManager.initializeAudioForCall();
        audioManager.startOutgoingRinger(OutgoingRinger.Type.SONAR);
        if (bluetoothStateManager == null) {
            throw new Exception("bluetoothStateManager is null");
        } else {
            bluetoothStateManager.setWantsConnection(true);
        }

        timeoutFuture = timeoutExecutor.schedule(new TimeoutRunnable(this.callId), 2, TimeUnit.MINUTES);

        retrieveTurnServers().addListener(new SuccessOnlyListener<List<PeerConnection.IceServer>>(CallState.STATE_DIALING, this.callId) {
            @Override
            public void onSuccessContinue(List<PeerConnection.IceServer> result) {
                try {
                    ALog.logForSecret(TAG, "handleOutgoingCall " + formatter.toJson(result));

                    if (result.isEmpty()) {
                        throw new EmptyTurnException("IceServer list is empty!");
                    }

                    // Caller get turn server success
                    ReportUtil.INSTANCE.addCustomCounterReportData(currentMetric, MetricsConstKt.CALL_FAILED, false);
                    ReportUtil.INSTANCE.addCustomCounterReportData(currentMetric, MetricsConstKt.CALL_SUCCESS, true);
                    currentMetric = MetricsConstKt.COUNTER_CALLER_SEND_OFFER;

                    boolean isAlwaysTurn = TextSecurePreferences.isTurnOnly(WebRtcCallService.this);

                    WebRtcCallService.this.peerConnection = new PeerConnectionWrapper(WebRtcCallService.this, peerConnectionFactory, WebRtcCallService.this, localRenderer, result, WebRtcCallService.this, eglBase, isAlwaysTurn);
                    WebRtcCallService.this.dataChannel = WebRtcCallService.this.peerConnection.createDataChannel(DATA_CHANNEL_NAME);
                    WebRtcCallService.this.dataChannel.registerObserver(WebRtcCallService.this);

                    SessionDescription sdp = WebRtcCallService.this.peerConnection.createOffer(new MediaConstraints());
                    WebRtcCallService.this.peerConnection.setLocalDescription(sdp);

                    ALog.logForSecret(TAG, "Sending offer: " + sdp.description);

                    ListenableFutureTask<Boolean> listenableFutureTask = sendMessage(recipient, SignalServiceCallMessage.forOffer(new OfferMessage(WebRtcCallService.this.callId, sdp.description)));
                    if (listenableFutureTask != null) {
                        listenableFutureTask.addListener(new FailureListener<Boolean>(CallState.STATE_DIALING, callId) {
                            @Override
                            public void onSuccessContinue(Boolean result) {
                                ALog.i(TAG, "Send offer success");
                                ReportUtil.INSTANCE.addCustomCounterReportData(currentMetric, MetricsConstKt.CALL_FAILED, false);
                                ReportUtil.INSTANCE.addCustomCounterReportData(currentMetric, MetricsConstKt.CALL_SUCCESS, true);
                                currentMetric = MetricsConstKt.COUNTER_CALLER_RECEIVED_ANSWER;
                            }

                            @Override
                            public void onFailureContinue(Throwable error) {
                                ALog.e(TAG, "sendMessage", error);

                                if (error instanceof UntrustedIdentityException) {
                                    sendMessage(WebRtcViewModel.State.UNTRUSTED_IDENTITY, recipient, ((UntrustedIdentityException) error).getIdentityKey(), localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
                                } else if (error instanceof UnregisteredUserException) {
                                    sendMessage(WebRtcViewModel.State.NO_SUCH_USER, recipient, localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
                                } else {
                                    sendMessage(WebRtcViewModel.State.NETWORK_FAILURE, recipient, localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
                                }

                                ReportUtil.INSTANCE.addCustomCounterReportData(currentMetric, MetricsConstKt.CALL_FAILED, true);
                                ReportUtil.INSTANCE.addCustomCounterReportData(currentMetric, MetricsConstKt.CALL_SUCCESS, false);

                                terminate();
                            }
                        });
                    }

                } catch (Exception e) {
                    ALog.logForSecret(TAG, "handleOutgoingCall error", e);

                    ReportUtil.INSTANCE.addCustomCounterReportData(currentMetric, MetricsConstKt.CALL_FAILED, true);
                    ReportUtil.INSTANCE.addCustomCounterReportData(currentMetric, MetricsConstKt.CALL_SUCCESS, false);

                    sendMessage(WebRtcViewModel.State.NETWORK_FAILURE, recipient, localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
                    terminate();
                }
            }
        });

    }

    private void handleResponseMessage(Intent intent) {

        try {
            ALog.logForSecret(TAG, "Got response: " + intent.getStringExtra(EXTRA_REMOTE_DESCRIPTION));
            CallState currentState = this.callState.get();
            if (currentState != CallState.STATE_DIALING || !getRemoteRecipient(intent).equals(recipient) || !Util.isEquals(this.callId, getCallId(intent))) {
                ALog.logForSecret(TAG, "Got answer for recipient and call id we're not currently dialing: " + getCallId(intent) + ", " + getRemoteRecipient(intent));
                return;
            }

            ReportUtil.INSTANCE.addCustomCounterReportData(currentMetric, MetricsConstKt.CALL_FAILED, false);
            ReportUtil.INSTANCE.addCustomCounterReportData(currentMetric, MetricsConstKt.CALL_SUCCESS, true);
            currentMetric = MetricsConstKt.COUNTER_CALLER_ICE_UPDATE;

            if (peerConnection == null || pendingOutgoingIceUpdates == null) {
                throw new Exception("peerConnection is null or pendingOutgoingIceUpdate is null");
            }

            if (!pendingOutgoingIceUpdates.isEmpty()) {
                ListenableFutureTask<Boolean> listenableFutureTask = sendMessage(recipient, SignalServiceCallMessage.forIceUpdates(pendingOutgoingIceUpdates));
                if (listenableFutureTask != null) {
                    listenableFutureTask.addListener(new FailureListener<Boolean>(currentState, callId) {
                        @Override
                        public void onFailureContinue(Throwable error) {
                            ALog.e(TAG, "sendMessage", error);
                            sendMessage(WebRtcViewModel.State.NETWORK_FAILURE, recipient, localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);

                            ReportUtil.INSTANCE.addCustomCounterReportData(currentMetric, MetricsConstKt.CALL_FAILED, true);
                            ReportUtil.INSTANCE.addCustomCounterReportData(currentMetric, MetricsConstKt.CALL_SUCCESS, false);

                            terminate();
                        }
                    });
                }
            }

            this.peerConnection.setRemoteDescription(new SessionDescription(SessionDescription.Type.ANSWER, intent.getStringExtra(EXTRA_REMOTE_DESCRIPTION)));
            this.pendingOutgoingIceUpdates = null;

        } catch (Exception e) {
            ALog.e(TAG, "handleResponseMessage error", e);

            if (recipient != null) {
                sendMessage(WebRtcViewModel.State.NETWORK_FAILURE, recipient, localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
            }
            terminate();
        }
    }

    private void handleRemoteIceCandidate(Intent intent) {
        ALog.w(TAG, "remoteIce candidate...");

        if (Util.isEquals(this.callId, getCallId(intent))) {
            IceCandidate candidate = new IceCandidate(intent.getStringExtra(EXTRA_ICE_SDP_MID),
                    intent.getIntExtra(EXTRA_ICE_SDP_LINE_INDEX, 0),
                    intent.getStringExtra(EXTRA_ICE_SDP));

            ALog.d(TAG, "handleRemoteIceCandidate " + new Gson().toJson(candidate));

            if (peerConnection != null) {
                peerConnection.addIceCandidate(candidate);
            } else if (pendingIncomingIceUpdates != null) {
                pendingIncomingIceUpdates.add(candidate);
            }
        }
    }

    private void handleLocalIceCandidate(Intent intent) {
        CallState currentState = this.callState.get();
        if (currentState == CallState.STATE_IDLE || !Util.isEquals(this.callId, getCallId(intent))) {
            ALog.w(TAG, "State is now idle, ignoring ice candidate...");
            return;
        }

        if (recipient == null || callId == -1L) {
            ALog.i(TAG, "handleLocalIceCandidate fail, recipient is null or callId is null");
            terminate();
            return;
        }

        IceUpdateMessage iceUpdateMessage = new IceUpdateMessage(this.callId, intent.getStringExtra(EXTRA_ICE_SDP_MID),
                intent.getIntExtra(EXTRA_ICE_SDP_LINE_INDEX, 0),
                intent.getStringExtra(EXTRA_ICE_SDP));

        ALog.d(TAG, "handleLocalIceCandidate  " + new Gson().toJson(iceUpdateMessage));

        if (pendingOutgoingIceUpdates != null) {
            ALog.i(TAG, "Adding to pending ice candidates...");
            this.pendingOutgoingIceUpdates.add(iceUpdateMessage);
            return;
        }

        ListenableFutureTask<Boolean> listenableFutureTask = sendMessage(recipient, SignalServiceCallMessage.forIceUpdate(iceUpdateMessage));
        if (listenableFutureTask != null) {
            listenableFutureTask.addListener(new FailureListener<Boolean>(currentState, callId) {
                @Override
                public void onFailureContinue(Throwable error) {
                    ALog.e(TAG, "sendMessage", error);
                    sendMessage(WebRtcViewModel.State.NETWORK_FAILURE, recipient, localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);

                    ReportUtil.INSTANCE.addCustomCounterReportData(currentMetric, MetricsConstKt.CALL_FAILED, true);
                    ReportUtil.INSTANCE.addCustomCounterReportData(currentMetric, MetricsConstKt.CALL_SUCCESS, false);

                    terminate();
                }
            });
        }
    }

    private void handleIceConnected(Intent intent) {
        ALog.i(TAG, "handleIceConnected " + callState);

        CallState currentState = this.callState.get();
        if (currentState == CallState.STATE_ANSWERING) {
            if (this.recipient == null) {
                ALog.i(TAG, "handleIceConnected fail, recipient is null");
                terminate();
                return;
            }

            if (callState.compareAndSet(CallState.STATE_ANSWERING, CallState.STATE_LOCAL_RINGING)) {
                this.lockManager.updatePhoneState(LockManager.PhoneState.INTERACTIVE);

                sendMessage(WebRtcViewModel.State.CALL_INCOMING, recipient, localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
                startCallActivity(CameraState.Direction.NONE.ordinal());
                audioManager.initializeAudioForCall();
                audioManager.startIncomingRinger();

                registerPowerButtonReceiver();

                setCallInProgressNotification(TYPE_INCOMING_RINGING, recipient);
            }

        } else if (currentState == CallState.STATE_DIALING) {
            if (this.recipient == null) {
                ALog.i(TAG, "handleIceConnected fail, recipient is null");
                terminate();
                return;
            }

            if (callState.compareAndSet(CallState.STATE_DIALING, CallState.STATE_REMOTE_RINGING)) {
                this.audioManager.startOutgoingRinger(OutgoingRinger.Type.RINGING);
                sendMessage(WebRtcViewModel.State.CALL_RINGING, recipient, localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
            }
        }
    }

    private void handleCallConnected(Intent intent) {
        CallState currentState = callState.get();
        if (currentState != CallState.STATE_REMOTE_RINGING && currentState != CallState.STATE_LOCAL_RINGING) {
            ALog.w(TAG, "handleCallConnected fail, current state is not remote ringing or local ringing");
            return;
        }

        if (!Util.isEquals(this.callId, getCallId(intent))) {
            ALog.w(TAG, "Ignoring connected for unknown call id: " + getCallId(intent));
            return;
        }

        if (recipient == null || peerConnection == null || dataChannel == null) {
            ALog.i(TAG, "handleCallConnected fail, recipient or peerConnection or dataChannel is null");
            terminate();
            return;
        }

        if (!callState.compareAndSet(CallState.STATE_REMOTE_RINGING, CallState.STATE_CONNECTED) && !callState.compareAndSet(CallState.STATE_LOCAL_RINGING, CallState.STATE_CONNECTED)) {
            ALog.w(TAG, "handleCallConnected fail, current state is not remote ringing or local ringing");
            return;
        }

        
        startCallActivity(CameraState.Direction.NONE.ordinal());
        setCallInProgressNotification(TYPE_ESTABLISHED, recipient);

        audioManager.startCommunication(currentState == CallState.STATE_REMOTE_RINGING);

        if (bluetoothStateManager != null) {
            bluetoothStateManager.setWantsConnection(true);
        }

        if (localCameraState.isEnabled()) {
            lockManager.updatePhoneState(LockManager.PhoneState.IN_VIDEO);
        } else {
            lockManager.updatePhoneState(LockManager.PhoneState.IN_CALL);
        }

        sendMessage(WebRtcViewModel.State.CALL_CONNECTED, recipient, localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);

        unregisterPowerButtonReceiver();

        this.peerConnection.setCommunicationMode();
        this.peerConnection.setAudioEnabled(microphoneEnabled);
        this.peerConnection.setVideoEnabled(localCameraState.isEnabled());

        this.dataChannel.send(new DataChannel.Buffer(ByteBuffer.wrap(Data.newBuilder()
                .setVideoStreamingStatus(WebRtcDataProtos.VideoStreamingStatus.newBuilder()
                        .setId(this.callId)
                        .setEnabled(localCameraState.isEnabled()))
                .build().toByteArray()), false));
    }

    /**
     *
     *
     * @param intent
     */
    private void handleBusyCall(Intent intent) throws Exception {
        ALog.w(TAG, "handleBusyCall");
        this.mIncoming.set(true);
        Recipient recipient = getRemoteRecipient(intent);
        long callId = getCallId(intent);
        sendMessage(recipient, SignalServiceCallMessage.forBusy(new BusyMessage(callId)));
    }

    /**
     * 
     *
     * @param intent
     */
    private void handleBusyMessage(Intent intent) throws Exception {
        ALog.w(TAG, "handleRemoteBusy");

        final Recipient recipient = getRemoteRecipient(intent);
        final long callId = getCallId(intent);

        if (callState.get() != CallState.STATE_DIALING || !Util.isEquals(this.callId, callId) || !recipient.equals(this.recipient)) {
            ALog.w(TAG, "Got busy message for inactive session...");
            return;
        }

        sendMessage(WebRtcViewModel.State.CALL_BUSY, recipient, localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
        insertMissedCall(recipient, NOTIFICATION_IMPORTANT);

        audioManager.startOutgoingRinger(OutgoingRinger.Type.BUSY);

        AmeDispatcher.INSTANCE.getMainThread().dispatch(() -> {
            Intent intent1 = new Intent(WebRtcCallService.this, WebRtcCallService.class);
            intent1.setAction(ACTION_LOCAL_HANGUP);
            intent1.putExtra(EXTRA_CALL_ID, callId);
            intent1.putExtra(EXTRA_REMOTE_ADDRESS, recipient.getAddress());
            AppUtilKotlinKt.startForegroundServiceCompat(WebRtcCallService.this, intent1);
            return null;
        }, FINISH_BUSY_DELAY);

    }

    private void handleCheckTimeout(Intent intent) {

        CallState currentState = this.callState.get();
        if (this.callId != -1L && this.callId == intent.getLongExtra(EXTRA_CALL_ID, -1) && currentState != CallState.STATE_CONNECTED) {
            ALog.w(TAG, "Timing out call: " + this.callId);

            if (this.recipient != null) {
                sendMessage(WebRtcViewModel.State.CALL_DISCONNECTED, this.recipient, localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);

                if (currentState == CallState.STATE_ANSWERING || currentState == CallState.STATE_LOCAL_RINGING || currentState == CallState.STATE_REMOTE_RINGING) {
                    insertMissedCall(this.recipient, NOTIFICATION_IMPORTANT);
                }
            }

            if (!currentMetric.equals(MetricsConstKt.COUNTER_CALLER_ICE_CONNECTED) && !currentMetric.equals(MetricsConstKt.COUNTER_CALLEE_ICE_CONNECTED)) {
                ReportUtil.INSTANCE.addCustomCounterReportData(currentMetric, MetricsConstKt.CALL_FAILED, true);
                ReportUtil.INSTANCE.addCustomCounterReportData(currentMetric, MetricsConstKt.CALL_SUCCESS, false);
            }

            terminate();
        }
    }

    private void handleIsInCallQuery(Intent intent) {
        ResultReceiver resultReceiver = intent.getParcelableExtra(EXTRA_RESULT_RECEIVER);

        if (resultReceiver != null) {
            resultReceiver.send(callState.get() != CallState.STATE_IDLE ? 1 : 0, null);
        }
    }

    private void insertMissedCall(@NonNull Recipient recipient, int notifyType) {
        if (!mMsgInserted.getAndSet(true)) {
            ALog.logForSecret(TAG, "insertMissedCall address: " + recipient.getAddress() + ", incoming: " + mIncoming);

            Pair<Long, Long> result = null;
            if (mIncoming.get()) {
                result = Repository.getChatRepo().insertIncomingMissedCall(recipient.getAddress().serialize());
            } else {
                result = Repository.getChatRepo().insertOutgoingMissedCall(recipient.getAddress().serialize());
            }
            if (result.getSecond() > 0L) {
                EventBus.getDefault().post(new MessageReceiveNotifyEvent(recipient.getAddress().serialize(), result.getSecond()));
            }

        }else {
            ALog.logForSecret(TAG, "insertMissedCall fail, has inserted, address: " + recipient.getAddress() + ", incoming: " + mIncoming);

        }
    }

    private void insertMissedCallFromHangup(@NonNull Recipient recipient, boolean remoteHangup) {
        if (!mMsgInserted.getAndSet(true)) {
            ALog.logForSecret(TAG, "insertMissedCallFromHangup address: " + recipient.getAddress() + ", incoming: " + mIncoming + ", remoteHangup: " + remoteHangup);
            
            Pair<Long, Long> result = null;
            if (mIncoming.get()) {
                result = Repository.getChatRepo().insertIncomingMissedCall(recipient.getAddress().serialize());
            } else {
                result = Repository.getChatRepo().insertOutgoingMissedCall(recipient.getAddress().serialize());
            }
            if (result.getSecond() > 0L) {
                EventBus.getDefault().post(new MessageReceiveNotifyEvent(recipient.getAddress().serialize(), result.getSecond()));
            }
        }else {
            ALog.logForSecret(TAG, "insertMissedCallFromHangup fail, has inserted, address: " + recipient.getAddress() + ", incoming: " + mIncoming + ", remoteHangup: " + remoteHangup);
        }
    }

    private void handleAnswerCall(Intent intent) {
        ALog.d(TAG, "handleAnswerCall");
        if (callState.get() != CallState.STATE_LOCAL_RINGING) {
            ALog.w(TAG, "handleAnswerCall fail, current state is not local ringing");
            return;
        }

        if (peerConnection == null || dataChannel == null || recipient == null || callId == -1L) {
            ALog.i(TAG, "handleAnswerCall fail, peerConnection or dataChannel or recipient callId is null");
            terminate();
            return;
        }
        setCallInProgressNotification(TYPE_ESTABLISHED, recipient);

        this.peerConnection.setAudioEnabled(true);
        this.peerConnection.setVideoEnabled(localCameraState.isEnabled());
        this.dataChannel.send(new DataChannel.Buffer(ByteBuffer.wrap(Data.newBuilder().setConnected(Connected.newBuilder().setId(this.callId)).build().toByteArray()), false));

        intent.putExtra(EXTRA_CALL_ID, callId);
        intent.putExtra(EXTRA_REMOTE_ADDRESS, recipient.getAddress());
        handleCallConnected(intent);
    }

    private void handleDenyCall(Intent intent) {

        try {
            CallState currentState = this.callState.get();
            if (currentState != CallState.STATE_LOCAL_RINGING) {
                ALog.w(TAG, "handleDenyCall fail, current state is not local ringing");
                return;
            }

            if (recipient == null || callId == -1L || dataChannel == null) {
                ALog.i(TAG, "handleDenyCall fail, recipient or callId or dataChannel is null");

            } else {
                this.dataChannel.send(new DataChannel.Buffer(ByteBuffer.wrap(Data.newBuilder().setHangup(Hangup.newBuilder().setId(this.callId)).build().toByteArray()), false));
                sendMessage(recipient, SignalServiceCallMessage.forHangup(new HangupMessage(this.callId)));

                if (currentState != CallState.STATE_IDLE && currentState != CallState.STATE_CONNECTED) {
                    insertMissedCallFromHangup(this.recipient, true);
                }
            }
        } finally {
            this.terminate();
        }
    }

    private void handleLocalHangup(Intent intent) {

        CallState currentState = this.callState.get();
        ALog.i(TAG, "handleLocalHangup: " + currentState);
        if (this.dataChannel != null && this.recipient != null && this.callId != -1L) {

            this.dataChannel.send(new DataChannel.Buffer(ByteBuffer.wrap(Data.newBuilder().setHangup(Hangup.newBuilder().setId(this.callId)).build().toByteArray()), false));
            sendMessage(this.recipient, SignalServiceCallMessage.forHangup(new HangupMessage(this.callId)));

        }
        if (recipient != null) {
            sendMessage(WebRtcViewModel.State.CALL_DISCONNECTED, this.recipient, localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);

            if (currentState != CallState.STATE_IDLE && currentState != CallState.STATE_CONNECTED) {
                insertMissedCallFromHangup(this.recipient, false);
            }
        }
        terminate();
    }

    private void handleRemoteHangup(Intent intent) {
        CallState currentState = this.callState.get();
        ALog.i(TAG, "handleRemoteHangup: " + currentState);

        if (!Util.isEquals(this.callId, getCallId(intent))) {
            ALog.w(TAG, "hangup for non-active call...");
            return;
        }

        if (recipient != null && recipient.getAddress().equals(intent.getParcelableExtra(EXTRA_REMOTE_ADDRESS))) {
            ALog.w(TAG, "Not current user hang up");
            return;
        }

        try {
            if (this.recipient == null) {
                ALog.i(TAG, "handleRemoteHangup fail, recipient is null");
            } else {
                if (currentState == CallState.STATE_ANSWERING || currentState == CallState.STATE_LOCAL_RINGING || currentState == CallState.STATE_REMOTE_RINGING) {
                    sendMessage(WebRtcViewModel.State.RECIPIENT_UNAVAILABLE, this.recipient, localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
                    insertMissedCallFromHangup(this.recipient, true);
                } else {
                    sendMessage(WebRtcViewModel.State.CALL_DISCONNECTED, this.recipient, localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
                }
            }
        } finally {
            terminate();
        }
    }

    private void handleSetMuteAudio(Intent intent) {
        
        if (!PermissionUtil.INSTANCE.checkAudio(this)) {
            return;
        }

        boolean muted = intent.getBooleanExtra(EXTRA_MUTE, false);
        this.microphoneEnabled = !muted;

        if (this.peerConnection != null) {
            this.peerConnection.setAudioEnabled(this.microphoneEnabled);
        }
    }

    private void handleSetMuteVideo(Intent intent) {

        if (!PermissionUtil.INSTANCE.checkCamera(this)) {
            return;
        }

        AudioManager audioManager = AppUtil.INSTANCE.getAudioManager(this);
        boolean muted = intent.getBooleanExtra(EXTRA_MUTE, false);

        if (this.peerConnection != null) {
            this.peerConnection.setVideoEnabled(!muted);
        }

        if (this.callId != -1L && this.dataChannel != null) {
            this.dataChannel.send(new DataChannel.Buffer(ByteBuffer.wrap(Data.newBuilder()
                    .setVideoStreamingStatus(WebRtcDataProtos.VideoStreamingStatus.newBuilder()
                            .setId(this.callId)
                            .setEnabled(!muted))
                    .build().toByteArray()), false));
        }

        if (muted) {
            localCameraState = new CameraState(CameraState.Direction.NONE, localCameraState.getCameraCount());
        } else {
            localCameraState = new CameraState(CameraState.Direction.FRONT, localCameraState.getCameraCount());
        }

        if (this.callState.get() == CallState.STATE_CONNECTED) {
            if (localCameraState.isEnabled()) {
                this.lockManager.updatePhoneState(LockManager.PhoneState.IN_VIDEO);
            } else {
                this.lockManager.updatePhoneState(LockManager.PhoneState.IN_CALL);
            }
        }

        if (this.recipient != null) {
            sendMessage(viewModelStateFor(callState.get()), this.recipient, localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
        }
    }

    private void handleSetCameraFlip(Intent intent) {
        ALog.i(TAG, "handleSetCameraFlip...");
        
        if (!PermissionUtil.INSTANCE.checkCamera(this)) {
            return;
        }
        if (localCameraState.isEnabled() && peerConnection != null) {
            peerConnection.flipCamera();
            localCameraState = peerConnection.getCameraState();
            if (recipient != null) {
                sendMessage(viewModelStateFor(callState.get()), recipient, localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
            }
        }
    }

    private void handleBluetoothChange(Intent intent) {

        this.bluetoothAvailable = intent.getBooleanExtra(EXTRA_AVAILABLE, false);

        if (recipient != null) {
            sendMessage(viewModelStateFor(callState.get()), recipient, localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
        }
    }

    private void handleWiredHeadsetChange(Intent intent) {
        ALog.d(TAG, "handleWiredHeadsetChange...");

        CallState currentState = this.callState.get();
        if (currentState == CallState.STATE_CONNECTED ||
                currentState == CallState.STATE_DIALING ||
                currentState == CallState.STATE_REMOTE_RINGING) {
            AudioManager audioManager = AppUtil.INSTANCE.getAudioManager(this);
            boolean present = intent.getBooleanExtra(EXTRA_AVAILABLE, false);

            if (present && audioManager.isSpeakerphoneOn()) {
                audioManager.setSpeakerphoneOn(false);
                audioManager.setBluetoothScoOn(false);
            } else if (!present && !audioManager.isSpeakerphoneOn() && !audioManager.isBluetoothScoOn() && localCameraState.isEnabled()) {
                audioManager.setSpeakerphoneOn(true);
            }

            if (recipient != null) {
                sendMessage(viewModelStateFor(callState.get()), recipient, localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
            }
        }
    }

    private void handleScreenOffChange(Intent intent) {
        CallState currentState = this.callState.get();
        if (currentState == CallState.STATE_ANSWERING ||
                currentState == CallState.STATE_LOCAL_RINGING) {
            ALog.w(TAG, "Silencing incoming ringer...");
            audioManager.silenceIncomingRinger();
        }
    }

    private void handleRemoteVideoMute(Intent intent) {

        boolean muted = intent.getBooleanExtra(EXTRA_MUTE, false);
        long callId = intent.getLongExtra(EXTRA_CALL_ID, -1);

        if (this.recipient == null || this.callState.get() != CallState.STATE_CONNECTED || !Util.isEquals(this.callId, callId)) {
            ALog.w(TAG, "Got video toggle for inactive call, ignoring...");
            return;
        }

        this.remoteVideoEnabled = !muted;
        sendMessage(WebRtcViewModel.State.CALL_CONNECTED, this.recipient, localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
    }

    private void handleGrantedAudioPermission(Intent intent) {
        if (this.peerConnection != null && this.peerConnectionFactory != null) {
            ALog.i(TAG, "Handle grant audio");
            this.peerConnection.reInitAudioTrack(this.peerConnectionFactory);
        }
    }

    
    private boolean isBusy() {
        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        return callState.get() != CallState.STATE_IDLE || (telephonyManager != null && telephonyManager.getCallState() != TelephonyManager.CALL_STATE_IDLE);
    }

    private boolean isIdle() {
        return callState.get() == CallState.STATE_IDLE;
    }

    private boolean isIncomingMessageExpired(Intent intent) {
        return System.currentTimeMillis() - intent.getLongExtra(WebRtcCallService.EXTRA_TIMESTAMP, -1) > TimeUnit.MINUTES.toMillis(2);
    }

    private void initializeVideo() {
        Util.runOnMainSync(() -> {
            eglBase = EglBase.create();
            localRenderer = new SurfaceViewRenderer(WebRtcCallService.this);
            remoteRenderer = new SurfaceViewRenderer(WebRtcCallService.this);

            localRenderer.init(eglBase.getEglBaseContext(), null);
            remoteRenderer.init(eglBase.getEglBaseContext(), null);

            VideoEncoderFactory encoderFactory = new DefaultVideoEncoderFactory(eglBase.getEglBaseContext(), true, true);
            VideoDecoderFactory decoderFactory = new DefaultVideoDecoderFactory(eglBase.getEglBaseContext());

            peerConnectionFactory = PeerConnectionFactory.builder()
                    .setOptions(new PeerConnectionFactoryOptions())
                    .setVideoEncoderFactory(encoderFactory)
                    .setVideoDecoderFactory(decoderFactory)
                    .createPeerConnectionFactory();
        });

    }

   
    private void setCallNotification(int type) {
        ALog.d(TAG, "setCallNotification type: " + type);
        startForeground(CallNotificationBuilder.WEBRTC_NOTIFICATION, CallNotificationBuilder.getCallDefaultNotification(this));
    }

   
    private void setCallInProgressNotification(int type, @Nullable Recipient recipient) {
        if (mCurrentNotificationType != TYPE_CALL_DEFAULT && type == TYPE_CALL_DEFAULT) {
            return;
        }
        ALog.d(TAG, "setCallInProgressNotification current: " + mCurrentNotificationType + ", new: " + type);
        mCurrentNotificationType = type;
        if (recipient != null) {
            startForeground(CallNotificationBuilder.WEBRTC_NOTIFICATION, CallNotificationBuilder.getCallInProgressNotification(this, type, recipient));
        }
    }

    private synchronized void terminate() {
        ALog.i(TAG, "terminate");
        try {
            EventBus.getDefault().postSticky(new WebRtcViewModel(WebRtcViewModel.State.CALL_DISCONNECTED, recipient, localCameraState, localRenderer, remoteRenderer, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled));

            sCurrentCallType = -1;
            mMsgInserted.set(false);

            lockManager.updatePhoneState(LockManager.PhoneState.PROCESSING);

            CallState currentState = this.callState.get();
            audioManager.stop(currentState == CallState.STATE_DIALING || currentState == CallState.STATE_REMOTE_RINGING || currentState == CallState.STATE_CONNECTED);

            if (bluetoothStateManager != null) {
                bluetoothStateManager.terminate();
                bluetoothStateManager.setWantsConnection(false);
            }

            if (peerConnection != null) {
                peerConnection.dispose();
                peerConnection = null;
            }

            if (eglBase != null && localRenderer != null && remoteRenderer != null) {
                localRenderer.release();
                remoteRenderer.release();
                eglBase.release();

                localRenderer = null;
                remoteRenderer = null;
                eglBase = null;
            }

            if (timeoutFuture != null) {
                timeoutFuture.cancel(false);
                timeoutFuture = null;
            }

            this.callState.set(CallState.STATE_IDLE);
            this.recipient = null;
            this.callId = -1L;
            this.microphoneEnabled = true;
            this.localCameraState = CameraState.UNKNOWN;
            this.remoteVideoEnabled = false;
            this.pendingOutgoingIceUpdates = null;
            this.pendingIncomingIceUpdates = null;

            lockManager.updatePhoneState(LockManager.PhoneState.IDLE);

            EventBus.getDefault().removeStickyEvent(WebRtcViewModel.class);

        } catch (Exception ex) {
            ALog.e(TAG, "terminate error", ex);
        } finally {
            stopForeground(true);
            stopSelf();
        }
    }


    private void sendMessage(@NonNull WebRtcViewModel.State state,
                             @NonNull Recipient recipient,
                             @NonNull CameraState localCameraState,
                             boolean remoteVideoEnabled,
                             boolean bluetoothAvailable,
                             boolean microphoneEnabled) {

        handleForCallTime(state, recipient);
        EventBus.getDefault().postSticky(new WebRtcViewModel(state, recipient, localCameraState, localRenderer, remoteRenderer, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled));
    }

    private void sendMessage(@NonNull WebRtcViewModel.State state,
                             @NonNull Recipient recipient,
                             @NonNull IdentityKey identityKey,
                             @NonNull CameraState localCameraState,
                             boolean remoteVideoEnabled,
                             boolean bluetoothAvailable, boolean microphoneEnabled) {

        handleForCallTime(state, recipient);
        EventBus.getDefault().postSticky(new WebRtcViewModel(state, recipient, identityKey, localCameraState, localRenderer, remoteRenderer, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled));
    }

  
    private void handleForCallTime(@NonNull WebRtcViewModel.State state, @NonNull Recipient recipient) {
        
        if (state == WebRtcViewModel.State.CALL_CONNECTED && mBeginTimestamp == 0) {
            mBeginTimestamp = System.currentTimeMillis();
        } else if (state == WebRtcViewModel.State.CALL_DISCONNECTED && mBeginTimestamp > 0) {
            long duration = System.currentTimeMillis() - mBeginTimestamp;
            if (mIncoming.get()) {
                Repository.getChatRepo().insertReceivedCall(recipient.getAddress().serialize(), duration,
                        localCameraState.isEnabled() ? PrivateChatDbModel.CallType.VIDEO : PrivateChatDbModel.CallType.AUDIO);
            } else {
                Repository.getChatRepo().insertOutgoingCall(recipient.getAddress().serialize(), duration,
                        localCameraState.isEnabled() ? PrivateChatDbModel.CallType.VIDEO : PrivateChatDbModel.CallType.AUDIO);
            }
            mBeginTimestamp = 0;
        }
    }


    private ListenableFutureTask<Boolean> sendMessage(@NonNull final Recipient recipient,
                                                      @NonNull final SignalServiceCallMessage callMessage) {

        if (recipient.getAddress().toString().equals(AMESelfData.INSTANCE.getUid())) {
            // Don't send call message to self
            return null;
        }

        ListenableFutureTask<Boolean> listenableFutureTask = new ListenableFutureTask<>(() -> {
            BcmChatCore.INSTANCE.sendCallMessage(new SignalServiceAddress(recipient.getAddress().serialize()), callMessage);
            return true;
        }, null, serviceExecutor);

        Optional<BusyMessage> busyMessageOptional = callMessage.getBusyMessage();
        if (busyMessageOptional.isPresent()) {
            insertMissedCall(recipient, NOTIFICATION_SIMPLY);
        }

        Optional<HangupMessage> hangupMessageOptional = callMessage.getHangupMessage();
        if (hangupMessageOptional.isPresent()) {
            EventBus.getDefault().postSticky(new WebRtcViewModel(WebRtcViewModel.State.CALL_DISCONNECTED, recipient, localCameraState, localRenderer, remoteRenderer, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled));
        }

        networkExecutor.execute(listenableFutureTask);

        return listenableFutureTask;
    }

    private @NonNull
    Recipient getRemoteRecipient(Intent intent) throws Exception {
        Address remoteAddress = intent.getParcelableExtra(EXTRA_REMOTE_ADDRESS);
        if (remoteAddress == null) {
            throw new Exception("No recipient in intent!");
        }
        return Recipient.from(this, remoteAddress, true);
    }

    private long getCallId(Intent intent) {
        return intent.getLongExtra(EXTRA_CALL_ID, -1);
    }

    private void reportIceConnection(boolean isSuccess) {
        if (currentMetric.equals(MetricsConstKt.COUNTER_CALLEE_ICE_CONNECTED) || currentMetric.equals(MetricsConstKt.COUNTER_CALLER_ICE_CONNECTED)) {
            // Has been reported
            return;
        }

        if (mIncoming.get()) {
            currentMetric = MetricsConstKt.COUNTER_CALLEE_ICE_CONNECTED;

            ReportUtil.INSTANCE.addCustomCounterReportData(MetricsConstKt.COUNTER_CALLEE_ICE_UPDATE, MetricsConstKt.CALL_FAILED, false);
            ReportUtil.INSTANCE.addCustomCounterReportData(MetricsConstKt.COUNTER_CALLEE_ICE_UPDATE, MetricsConstKt.CALL_SUCCESS, true);

            ReportUtil.INSTANCE.addCustomCounterReportData(MetricsConstKt.COUNTER_CALLEE_ICE_CONNECTED, MetricsConstKt.CALL_FAILED, !isSuccess);
            ReportUtil.INSTANCE.addCustomCounterReportData(MetricsConstKt.COUNTER_CALLEE_ICE_CONNECTED, MetricsConstKt.CALL_SUCCESS, isSuccess);
        } else {
            currentMetric = MetricsConstKt.COUNTER_CALLER_ICE_CONNECTED;

            ReportUtil.INSTANCE.addCustomCounterReportData(MetricsConstKt.COUNTER_CALLER_ICE_UPDATE, MetricsConstKt.CALL_FAILED, false);
            ReportUtil.INSTANCE.addCustomCounterReportData(MetricsConstKt.COUNTER_CALLER_ICE_UPDATE, MetricsConstKt.CALL_SUCCESS, true);

            ReportUtil.INSTANCE.addCustomCounterReportData(MetricsConstKt.COUNTER_CALLER_ICE_CONNECTED, MetricsConstKt.CALL_FAILED, !isSuccess);
            ReportUtil.INSTANCE.addCustomCounterReportData(MetricsConstKt.COUNTER_CALLER_ICE_CONNECTED, MetricsConstKt.CALL_SUCCESS, isSuccess);
        }
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /// PeerConnection Observer
    @Override
    public void onSignalingChange(PeerConnection.SignalingState newState) {
        ALog.w(TAG, "onSignalingChange: " + newState);
    }

    @Override
    public void onIceConnectionChange(PeerConnection.IceConnectionState newState) {
        ALog.w(TAG, "onIceConnectionChange:" + newState);

        switch (newState) {
            case CONNECTED:
            case COMPLETED:
                reportIceConnection(true);

                Intent intent1 = new Intent(this, WebRtcCallService.class);
                intent1.setAction(ACTION_ICE_CONNECTED);
                AppUtilKotlinKt.startForegroundServiceCompat(this, intent1);
                break;
            case FAILED:
                reportIceConnection(false);

                Intent intent2 = new Intent(this, WebRtcCallService.class);
                intent2.setAction(ACTION_REMOTE_HANGUP);
                intent2.putExtra(EXTRA_CALL_ID, this.callId);
                AppUtilKotlinKt.startForegroundServiceCompat(this, intent2);

                break;
            default:
                break;
        }
    }

    @Override
    public void onIceConnectionReceivingChange(boolean receiving) {
        ALog.w(TAG, "onIceConnectionReceivingChange:" + receiving);
    }

    @Override
    public void onIceGatheringChange(PeerConnection.IceGatheringState newState) {
        ALog.w(TAG, "onIceGatheringChange:" + newState);

    }

    @Override
    public void onIceCandidate(IceCandidate candidate) {
        ALog.logForSecret(TAG, "onIceCandidate: " + candidate);
        Intent intent = new Intent(this, WebRtcCallService.class);

        intent.setAction(ACTION_ICE_CANDIDATE);
        intent.putExtra(EXTRA_ICE_SDP_MID, candidate.sdpMid);
        intent.putExtra(EXTRA_ICE_SDP_LINE_INDEX, candidate.sdpMLineIndex);
        intent.putExtra(EXTRA_ICE_SDP, candidate.sdp);
        intent.putExtra(EXTRA_CALL_ID, callId);

        AppUtilKotlinKt.startForegroundServiceCompat(this, intent);
    }

    @Override
    public void onIceCandidatesRemoved(IceCandidate[] candidates) {
        ALog.w(TAG, "onIceCandidatesRemoved:" + (candidates != null ? candidates.length : null));
    }

    @Override
    public void onAddStream(MediaStream stream) {
        ALog.logForSecret(TAG, "onAddStream:" + stream);

        for (AudioTrack audioTrack : stream.audioTracks) {
            audioTrack.setEnabled(true);
        }

        if (stream.videoTracks != null && stream.videoTracks.size() == 1) {
            VideoTrack videoTrack = stream.videoTracks.get(0);
            videoTrack.setEnabled(true);
            videoTrack.addSink(remoteRenderer);
        }
    }

    @Override
    public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
        ALog.logForSecret(TAG, "onAddTrack: " + mediaStreams);
    }

    @Override
    public void onRemoveStream(MediaStream stream) {
        ALog.logForSecret(TAG, "onRemoveStream:" + stream);
    }

    @Override
    public void onDataChannel(DataChannel dataChannel) {
        ALog.logForSecret(TAG, "onDataChannel:" + dataChannel.label());

        if (dataChannel.label().equals(DATA_CHANNEL_NAME)) {
            this.dataChannel = dataChannel;
            this.dataChannel.registerObserver(this);
        }
    }

    @Override
    public void onRenegotiationNeeded() {
        ALog.w(TAG, "onRenegotiationNeeded");
    }

    @Override
    public void onBufferedAmountChange(long l) {
        ALog.w(TAG, "onBufferedAmountChange: " + l);
    }

    @Override
    public void onStateChange() {
        ALog.w(TAG, "onStateChange");
    }

    @Override
    public void onMessage(DataChannel.Buffer buffer) {
        ALog.w(TAG, "onMessage...");

        try {
            byte[] data = new byte[buffer.data.remaining()];
            buffer.data.get(data);

            Data dataMessage = Data.parseFrom(data);
            if (dataMessage.hasConnected()) {
                ALog.w(TAG, "hasConnected...");
                Intent intent = new Intent(this, WebRtcCallService.class);
                intent.setAction(ACTION_CALL_CONNECTED);
                intent.putExtra(EXTRA_CALL_ID, dataMessage.getConnected().getId());
                AppUtilKotlinKt.startForegroundServiceCompat(this, intent);
            } else if (dataMessage.hasHangup()) {
                ALog.w(TAG, "hasHangup...");
                Intent intent = new Intent(this, WebRtcCallService.class);
                intent.setAction(ACTION_REMOTE_HANGUP);
                intent.putExtra(EXTRA_CALL_ID, dataMessage.getHangup().getId());
                AppUtilKotlinKt.startForegroundServiceCompat(this, intent);
            } else if (dataMessage.hasVideoStreamingStatus()) {
                ALog.w(TAG, "hasVideoStreamingStatus...");
                Intent intent = new Intent(this, WebRtcCallService.class);
                intent.setAction(ACTION_REMOTE_VIDEO_MUTE);
                intent.putExtra(EXTRA_CALL_ID, dataMessage.getVideoStreamingStatus().getId());
                intent.putExtra(EXTRA_MUTE, !dataMessage.getVideoStreamingStatus().getEnabled());
                AppUtilKotlinKt.startForegroundServiceCompat(this, intent);
            }
        } catch (Exception e) {
            ALog.logForSecret(TAG, "onMessage error", e);
        }
    }

    private ListenableFutureTask<List<PeerConnection.IceServer>> retrieveTurnServers() {
        Callable<List<PeerConnection.IceServer>> callable = () -> {
            LinkedList<PeerConnection.IceServer> results = new LinkedList<>();

            try {
                TurnServerInfo turnServerInfo = ChatHttp.INSTANCE.getTurnServerInfo();

                for (String url : turnServerInfo.getUrls()) {
                    if (url.startsWith("turn")) {
                        results.add(new PeerConnection.IceServer(url, turnServerInfo.getUsername(), turnServerInfo.getPassword()));
                    } else {
                        results.add(new PeerConnection.IceServer(url));
                    }
                }

            } catch (Exception e) {
                ALog.logForSecret(TAG, "retrieveTurnServers error", e);
            }

            return results;
        };

        ListenableFutureTask<List<PeerConnection.IceServer>> futureTask = new ListenableFutureTask<>(callable, null, serviceExecutor);
        networkExecutor.execute(futureTask);

        return futureTask;
    }

    private WebRtcViewModel.State viewModelStateFor(CallState state) {
        switch (state) {
            case STATE_CONNECTED:
                return WebRtcViewModel.State.CALL_CONNECTED;
            case STATE_DIALING:
                return WebRtcViewModel.State.CALL_OUTGOING;
            case STATE_REMOTE_RINGING:
                return WebRtcViewModel.State.CALL_RINGING;
            case STATE_LOCAL_RINGING:
            case STATE_ANSWERING:
                return WebRtcViewModel.State.CALL_INCOMING;
            case STATE_IDLE:
                return WebRtcViewModel.State.CALL_DISCONNECTED;
            default:
                break;
        }

        return WebRtcViewModel.State.CALL_DISCONNECTED;
    }

    private static class WiredHeadsetStateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                int state = intent.getIntExtra("state", -1);
                Intent serviceIntent = new Intent(context, WebRtcCallService.class);
                serviceIntent.setAction(WebRtcCallService.ACTION_WIRED_HEADSET_CHANGE);
                serviceIntent.putExtra(WebRtcCallService.EXTRA_AVAILABLE, state != 0);
                AppUtilKotlinKt.startForegroundServiceCompat(context, serviceIntent);
            } catch (Exception ex) {
                ALog.e(TAG, "WireHeadsetStateReceiver handle error", ex);
            }
        }
    }

    private static class PowerButtonReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                try {
                    Intent serviceIntent = new Intent(context, WebRtcCallService.class);
                    serviceIntent.setAction(WebRtcCallService.ACTION_SCREEN_OFF);
                    AppUtilKotlinKt.startForegroundServiceCompat(context, serviceIntent);
                } catch (Exception ex) {
                    ALog.e(TAG, "PowerButtonReceive handle error", ex);
                }
            }
        }
    }

    private class TimeoutRunnable implements Runnable {

        private final long callId;

        private TimeoutRunnable(long callId) {
            this.callId = callId;
        }

        @Override
        public void run() {
            try {
                Intent intent = new Intent(WebRtcCallService.this, WebRtcCallService.class);
                intent.setAction(WebRtcCallService.ACTION_CHECK_TIMEOUT);
                intent.putExtra(EXTRA_CALL_ID, callId);
                AppUtilKotlinKt.startForegroundServiceCompat(WebRtcCallService.this, intent);
            } catch (Exception ex) {
                ALog.e(TAG, "TimeoutRunnable run error", ex);
            }
        }
    }

    private static class ProximityLockRelease implements Thread.UncaughtExceptionHandler {
        private final LockManager lockManager;

        private ProximityLockRelease(LockManager lockManager) {
            this.lockManager = lockManager;
        }

        @Override
        public void uncaughtException(Thread thread, Throwable throwable) {
            ALog.e(TAG, "Uncaught exception - releasing proximity lock", throwable);
            lockManager.updatePhoneState(LockManager.PhoneState.IDLE);
        }
    }

    private abstract class StateAwareListener<V> implements FutureTaskListener<V> {

        private final CallState expectedState;
        private final long expectedCallId;

        StateAwareListener(CallState expectedState, long expectedCallId) {
            this.expectedState = expectedState;
            this.expectedCallId = expectedCallId;
        }


        @Override
        public void onSuccess(V result) {
            if (!isConsistentState()) {
                ALog.w(TAG, "State has changed since request, aborting success callback...");
            } else {
                onSuccessContinue(result);
            }
        }

        @Override
        public void onFailure(ExecutionException throwable) {
            if (!isConsistentState()) {
                ALog.e(TAG, "State has changed since request, aborting failure callback...", throwable);
            } else {
                onFailureContinue(throwable.getCause());
            }
        }

        private boolean isConsistentState() {
            return this.expectedState == callState.get() && Util.isEquals(callId, this.expectedCallId);
        }

        public abstract void onSuccessContinue(V result);

        public abstract void onFailureContinue(Throwable throwable);
    }

    private abstract class FailureListener<V> extends StateAwareListener<V> {
        FailureListener(CallState expectedState, long expectedCallId) {
            super(expectedState, expectedCallId);
        }

        @Override
        public void onSuccessContinue(V result) {
        }
    }

    private abstract class SuccessOnlyListener<V> extends StateAwareListener<V> {
        SuccessOnlyListener(CallState expectedState, long expectedCallId) {
            super(expectedState, expectedCallId);
        }

        @Override
        public void onFailureContinue(Throwable throwable) {
            ALog.e(TAG, "", throwable);
        }
    }

    @WorkerThread
    public static boolean isCallActive(Context context) {
        ALog.w(TAG, "isCallActive()");

        HandlerThread handlerThread = null;

        try {
            handlerThread = new HandlerThread("webrtc-callback");
            handlerThread.start();

            final SettableFuture<Boolean> future = new SettableFuture<>();

            ResultReceiver resultReceiver = new ResultReceiver(new Handler(handlerThread.getLooper())) {
                @Override
                protected void onReceiveResult(int resultCode, Bundle resultData) {
                    ALog.w(TAG, "Got result...");
                    future.set(resultCode == 1);
                }
            };

            Intent intent = new Intent(context, WebRtcCallService.class);
            intent.setAction(ACTION_IS_IN_CALL_QUERY);
            intent.putExtra(EXTRA_RESULT_RECEIVER, resultReceiver);
            AppUtilKotlinKt.startForegroundServiceCompat(context, intent);

            ALog.w(TAG, "Blocking on result...");
            return future.get();

        } catch (Exception e) {
            ALog.e(TAG, "isCallActive error", e);
            return false;
        } finally {
            if (handlerThread != null) {
                handlerThread.quit();
            }
        }
    }

    public static void isCallActive(Context context, ResultReceiver resultReceiver) {
        Intent intent = new Intent(context, WebRtcCallService.class);
        intent.setAction(ACTION_IS_IN_CALL_QUERY);
        intent.putExtra(EXTRA_RESULT_RECEIVER, resultReceiver);
        AppUtilKotlinKt.startForegroundServiceCompat(context, intent);
    }

    @Override
    public void onCameraSwitchCompleted(@NonNull CameraState newCameraState) {
        this.localCameraState = newCameraState;
        if (recipient != null) {
            sendMessage(viewModelStateFor(callState.get()), recipient, localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
        }
    }
}
