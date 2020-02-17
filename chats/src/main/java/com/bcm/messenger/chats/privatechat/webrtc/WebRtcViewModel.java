package com.bcm.messenger.chats.privatechat.webrtc;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bcm.messenger.common.recipients.Recipient;

import org.webrtc.SurfaceViewRenderer;
import org.whispersystems.libsignal.IdentityKey;

public class WebRtcViewModel {

    public enum State {
        // Normal states
        CALL_INCOMING,
        CALL_OUTGOING,
        CALL_CONNECTED,
        CALL_RINGING,
        CALL_BUSY,
        CALL_DISCONNECTED,

        // Error states
        NETWORK_FAILURE,
        RECIPIENT_UNAVAILABLE,
        NO_SUCH_USER,
        UNTRUSTED_IDENTITY,
    }


    private final @NonNull  State       state;
    private final @Nullable  Recipient   recipient;
    private final @Nullable IdentityKey identityKey;

    private final boolean remoteVideoEnabled;

    private final boolean isBluetoothAvailable;
    private final boolean isMicrophoneEnabled;

    private final CameraState         localCameraState;
    private final SurfaceViewRenderer localRenderer;
    private final SurfaceViewRenderer remoteRenderer;

    public WebRtcViewModel(@NonNull State               state,
                           @Nullable Recipient           recipient,
                           @NonNull CameraState         localCameraState,
                           @Nullable SurfaceViewRenderer localRenderer,
                           @Nullable SurfaceViewRenderer remoteRenderer,
                           boolean             remoteVideoEnabled,
                           boolean             isBluetoothAvailable,
                           boolean             isMicrophoneEnabled)
    {
        this(state,
                recipient,
                null,
                localCameraState,
                localRenderer,
                remoteRenderer,
                remoteVideoEnabled,
                isBluetoothAvailable,
                isMicrophoneEnabled);
    }

    public WebRtcViewModel(@NonNull  State               state,
                           @Nullable  Recipient           recipient,
                           @Nullable IdentityKey         identityKey,
                           @NonNull  CameraState         localCameraState,
                           @Nullable  SurfaceViewRenderer localRenderer,
                           @Nullable  SurfaceViewRenderer remoteRenderer,
                           boolean             remoteVideoEnabled,
                           boolean             isBluetoothAvailable,
                           boolean             isMicrophoneEnabled)
    {
        this.state                = state;
        this.recipient            = recipient;
        this.localCameraState     = localCameraState;
        this.localRenderer        = localRenderer;
        this.remoteRenderer       = remoteRenderer;
        this.identityKey          = identityKey;
        this.remoteVideoEnabled   = remoteVideoEnabled;
        this.isBluetoothAvailable = isBluetoothAvailable;
        this.isMicrophoneEnabled  = isMicrophoneEnabled;
    }

    public @NonNull State getState() {
        return state;
    }

    public @Nullable Recipient getRecipient() {
        return recipient;
    }

    public @NonNull CameraState getLocalCameraState() {
        return localCameraState;
    }

    public @Nullable IdentityKey getIdentityKey() {
        return identityKey;
    }

    public boolean isRemoteVideoEnabled() {
        return remoteVideoEnabled;
    }

    public boolean isBluetoothAvailable() {
        return isBluetoothAvailable;
    }

    public boolean isMicrophoneEnabled() {
        return isMicrophoneEnabled;
    }

    public @Nullable SurfaceViewRenderer getLocalRenderer() {
        return localRenderer;
    }

    public @Nullable SurfaceViewRenderer getRemoteRenderer() {
        return remoteRenderer;
    }

    public @NonNull String toString() {
        String address = "";
        if (recipient != null) {
            address = recipient.getAddress().toString();
        }
        return "[State: " + state + ", recipient: " + address + ", identity: " + identityKey + ", remoteVideo: " + remoteVideoEnabled + ", localVideo: " + localCameraState.isEnabled() + " lcamera:"+ localCameraState.isEnabled() +"]";
    }
}
