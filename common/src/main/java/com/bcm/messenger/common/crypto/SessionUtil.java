package com.bcm.messenger.common.crypto;

import android.content.Context;

import androidx.annotation.NonNull;

import com.bcm.messenger.common.AccountContext;
import com.bcm.messenger.common.core.Address;
import com.bcm.messenger.common.crypto.storage.TextSecureSessionStore;
import com.bcm.messenger.common.recipients.Recipient;

import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.state.SessionStore;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.util.List;

public class SessionUtil {

    public static boolean hasSession(Context context, MasterSecret masterSecret, Recipient recipient) {
        return hasSession(context, masterSecret, recipient.getAddress());
    }

    public static boolean hasSession(Context context, MasterSecret masterSecret, @NonNull Address address) {
        SessionStore sessionStore = new TextSecureSessionStore(context, address.context(), masterSecret);
        SignalProtocolAddress axolotlAddress = new SignalProtocolAddress(address.serialize(), SignalServiceAddress.DEFAULT_DEVICE_ID);

        return sessionStore.containsSession(axolotlAddress);
    }

    public static void archiveSiblingSessions(Context context, AccountContext accountContext, SignalProtocolAddress address) {
        SessionStore sessionStore = new TextSecureSessionStore(context, accountContext);
        List<Integer> devices = sessionStore.getSubDeviceSessions(address.getName());
        devices.add(1);

        for (int device : devices) {
            if (device != address.getDeviceId()) {
                SignalProtocolAddress sibling = new SignalProtocolAddress(address.getName(), device);

                if (sessionStore.containsSession(sibling)) {
                    SessionRecord sessionRecord = sessionStore.loadSession(sibling);
                    sessionRecord.archiveCurrentState();
                    sessionStore.storeSession(sibling, sessionRecord);
                }
            }
        }
    }

    public static void archiveAllSessions(Context context, AccountContext accountContext) {
        new TextSecureSessionStore(context, accountContext).archiveAllSessions();
    }
}
