package com.bcm.messenger.chats.privatechat.webrtc;

import androidx.annotation.NonNull;

public class CameraState {
    public static final CameraState UNKNOWN = new CameraState(Direction.NONE, 0);

    private final Direction activeDirection;
    private final int       cameraCount;

    public CameraState(@NonNull Direction activeDirection, int cameraCount) {
        this.activeDirection = activeDirection;
        this.cameraCount = cameraCount;
    }

    public int getCameraCount() {
        return cameraCount;
    }

    public Direction getActiveDirection() {
        return activeDirection;
    }

    public boolean isEnabled() {
        return this.activeDirection != Direction.NONE;
    }

    public enum Direction {
        FRONT, BACK, NONE, PENDING
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof CameraState)) {
            return false;
        }
        return ((CameraState) obj).activeDirection == activeDirection && ((CameraState) obj).cameraCount == cameraCount;
    }
}
