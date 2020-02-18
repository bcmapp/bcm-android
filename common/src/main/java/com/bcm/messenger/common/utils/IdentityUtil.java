package com.bcm.messenger.common.utils;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;

import com.bcm.messenger.common.AccountContext;
import com.bcm.messenger.common.R;
import com.bcm.messenger.common.crypto.MasterSecretUnion;
import com.bcm.messenger.common.crypto.storage.TextSecureIdentityKeyStore;
import com.bcm.messenger.common.crypto.storage.TextSecureSessionStore;
import com.bcm.messenger.common.database.records.IdentityRecord;
import com.bcm.messenger.common.database.repositories.IdentityRepo;
import com.bcm.messenger.common.database.repositories.PrivateChatRepo;
import com.bcm.messenger.common.database.repositories.Repository;
import com.bcm.messenger.common.database.repositories.ThreadRepo;
import com.bcm.messenger.common.recipients.Recipient;
import com.bcm.messenger.common.sms.IncomingIdentityDefaultMessage;
import com.bcm.messenger.common.sms.IncomingIdentityVerifiedMessage;
import com.bcm.messenger.common.sms.IncomingTextMessage;
import com.bcm.messenger.common.sms.OutgoingIdentityDefaultMessage;
import com.bcm.messenger.common.sms.OutgoingIdentityVerifiedMessage;
import com.bcm.messenger.common.sms.OutgoingTextMessage;
import com.bcm.messenger.utility.AmeTimeUtil;
import com.bcm.messenger.utility.concurrent.ListenableFuture;
import com.bcm.messenger.utility.concurrent.SettableFuture;

import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.state.IdentityKeyStore;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.state.SessionStore;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.messages.multidevice.VerifiedMessage;

import java.util.List;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

import static org.whispersystems.libsignal.SessionCipher.SESSION_LOCK;

public class IdentityUtil {

    private static final String TAG = IdentityUtil.class.getSimpleName();

    @UiThread
    public static ListenableFuture<IdentityRecord> getRemoteIdentityKey(final Context context, final Recipient recipient) {
        final SettableFuture<IdentityRecord> future = new SettableFuture<>();

        Observable.create(new ObservableOnSubscribe<IdentityRecord>() {
            @Override
            public void subscribe(ObservableEmitter<IdentityRecord> emitter) throws Exception {
                emitter.onNext(Repository.getIdentityRepo(recipient.getAddress().context()).getIdentityRecord(recipient.getAddress().serialize()));
                emitter.onComplete();
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> future.set(result), throwable -> future.set(null));

        return future;
    }

    public static void markIdentityVerified(Recipient recipient, boolean verified, boolean remote) {

        PrivateChatRepo repo = Repository.getChatRepo(recipient.getAddress().context());
        ThreadRepo threadRepo = Repository.getThreadRepo(recipient.getAddress().context());
        if (repo == null || threadRepo == null) {
            return;
        }

        long threadId = threadRepo.getThreadIdFor(recipient);
        long time = ChatTimestamp.getTime(recipient.getAddress().context(), threadId);
        if (remote) {
            IncomingTextMessage incoming = new IncomingTextMessage(recipient.getAddress(), 1, time, null, Optional.<SignalServiceGroup>absent(), 0);

            if (verified)
                incoming = new IncomingIdentityVerifiedMessage(incoming);
            else
                incoming = new IncomingIdentityDefaultMessage(incoming);

            repo.insertIncomingTextMessage(incoming);
        } else {
            OutgoingTextMessage outgoing;

            if (verified)
                outgoing = new OutgoingIdentityVerifiedMessage(recipient);
            else
                outgoing = new OutgoingIdentityDefaultMessage(recipient);

            Log.w(TAG, "Inserting verified outbox...");
            repo.insertOutgoingTextMessage(threadId, outgoing, time, null);
        }
    }

    public static void saveIdentity(Context context, AccountContext accountContext, String number, IdentityKey identityKey, boolean nonBlockingApproval) {
        synchronized (SESSION_LOCK) {
            TextSecureIdentityKeyStore identityKeyStore = new TextSecureIdentityKeyStore(context, accountContext);
            SessionStore sessionStore = new TextSecureSessionStore(context, accountContext);
            SignalProtocolAddress address = new SignalProtocolAddress(number, 1);

            if (identityKeyStore.saveIdentity(address, identityKey, nonBlockingApproval)) {
                if (sessionStore.containsSession(address)) {
                    SessionRecord sessionRecord = sessionStore.loadSession(address);
                    sessionRecord.archiveCurrentState();

                    sessionStore.storeSession(address, sessionRecord);
                }
            }
        }
    }

    public static void saveIdentity(Context context, AccountContext accountContext, String number, IdentityKey identityKey) {
        synchronized (SESSION_LOCK) {
            IdentityKeyStore identityKeyStore = new TextSecureIdentityKeyStore(context, accountContext);
            SessionStore sessionStore = new TextSecureSessionStore(context, accountContext);
            SignalProtocolAddress address = new SignalProtocolAddress(number, 1);

            if (identityKeyStore.saveIdentity(address, identityKey)) {
                if (sessionStore.containsSession(address)) {
                    SessionRecord sessionRecord = sessionStore.loadSession(address);
                    sessionRecord.archiveCurrentState();

                    sessionStore.storeSession(address, sessionRecord);
                }
            }
        }
    }

    public static void processVerifiedMessage(Context context, AccountContext accountContext, MasterSecretUnion masterSecret, VerifiedMessage verifiedMessage) {
        synchronized (SESSION_LOCK) {
            IdentityRepo identityRepo = Repository.getIdentityRepo(accountContext);
            Recipient recipient = Recipient.from(accountContext, verifiedMessage.getDestination(), true);
            IdentityRecord identityRecord = identityRepo.getIdentityRecord(recipient.getAddress().serialize());

            if (identityRecord== null && verifiedMessage.getVerified() == VerifiedMessage.VerifiedState.DEFAULT) {
                Log.w(TAG, "No existing record for default status");
                return;
            }

            if (verifiedMessage.getVerified() == VerifiedMessage.VerifiedState.DEFAULT &&
                    identityRecord != null &&
                    identityRecord.getIdentityKey().equals(verifiedMessage.getIdentityKey()) &&
                    identityRecord.getVerifyStatus() != IdentityRepo.VerifiedStatus.DEFAULT) {
                identityRepo.setVerified(recipient.getAddress().serialize(), identityRecord.getIdentityKey(), IdentityRepo.VerifiedStatus.DEFAULT);
                markIdentityVerified(recipient, false, true);
            }

            if (verifiedMessage.getVerified() == VerifiedMessage.VerifiedState.VERIFIED &&
                    (identityRecord == null ||
                            identityRecord.getIdentityKey().equals(verifiedMessage.getIdentityKey()) ||
                            identityRecord.getVerifyStatus() != IdentityRepo.VerifiedStatus.VERIFIED)) {
                saveIdentity(context, accountContext, verifiedMessage.getDestination(), verifiedMessage.getIdentityKey(), true);
                identityRepo.setVerified(recipient.getAddress().serialize(), verifiedMessage.getIdentityKey(), IdentityRepo.VerifiedStatus.DEFAULT);
                markIdentityVerified(recipient, true, true);
            }
        }
    }


    public static @Nullable
    String getUnverifiedBannerDescription(@NonNull Context context,
                                          @NonNull List<Recipient> unverified) {
        return getPluralizedIdentityDescription(context, unverified,
                R.string.IdentityUtil_unverified_banner_one,
                R.string.IdentityUtil_unverified_banner_two,
                R.string.IdentityUtil_unverified_banner_many);
    }

    public static @Nullable
    String getUnverifiedSendDialogDescription(@NonNull Context context,
                                              @NonNull List<Recipient> unverified) {
        return getPluralizedIdentityDescription(context, unverified,
                R.string.IdentityUtil_unverified_dialog_one,
                R.string.IdentityUtil_unverified_dialog_two,
                R.string.IdentityUtil_unverified_dialog_many);
    }

    public static @Nullable
    String getUntrustedSendDialogDescription(@NonNull Context context,
                                             @NonNull List<Recipient> untrusted) {
        return getPluralizedIdentityDescription(context, untrusted,
                R.string.IdentityUtil_untrusted_dialog_one,
                R.string.IdentityUtil_untrusted_dialog_two,
                R.string.IdentityUtil_untrusted_dialog_many);
    }

    private static @Nullable
    String getPluralizedIdentityDescription(@NonNull Context context,
                                            @NonNull List<Recipient> recipients,
                                            @StringRes int resourceOne,
                                            @StringRes int resourceTwo,
                                            @StringRes int resourceMany) {
        if (recipients.isEmpty())
            return null;

        if (recipients.size() == 1) {
            String name = recipients.get(0).toShortString();
            return context.getString(resourceOne, name);
        } else {
            String firstName = recipients.get(0).toShortString();
            String secondName = recipients.get(1).toShortString();

            if (recipients.size() == 2) {
                return context.getString(resourceTwo, firstName, secondName);
            } else {
                String nMore;

                if (recipients.size() == 3) {
                    nMore = context.getResources().getQuantityString(R.plurals.identity_others, 1);
                } else {
                    nMore = context.getResources().getQuantityString(R.plurals.identity_others, recipients.size() - 2);
                }

                return context.getString(resourceMany, firstName, secondName, nMore);
            }
        }
    }
}
