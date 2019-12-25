package com.bcm.messenger.login.jobs;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bcm.messenger.common.contacts.ContactAccessor;
import com.bcm.messenger.common.contacts.ContactAccessor.ContactData;
import com.bcm.messenger.common.core.Address;
import com.bcm.messenger.common.crypto.MasterSecret;
import com.bcm.messenger.common.crypto.ProfileKeyUtil;
import com.bcm.messenger.common.database.records.IdentityRecord;
import com.bcm.messenger.common.database.repositories.Repository;
import com.bcm.messenger.common.jobs.MasterSecretJob;
import com.bcm.messenger.common.jobs.requirements.MasterSecretRequirement;
import com.bcm.messenger.common.preferences.TextSecurePreferences;
import com.bcm.messenger.common.provider.AMELogin;
import com.bcm.messenger.common.recipients.Recipient;

import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.jobqueue.requirements.NetworkRequirement;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContact;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContactsOutputStream;
import org.whispersystems.signalservice.api.messages.multidevice.VerifiedMessage;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;


public class MultiDeviceContactUpdateJob extends MasterSecretJob {

    private static final long serialVersionUID = 2L;

    private static final String TAG = MultiDeviceContactUpdateJob.class.getSimpleName();

    private final @Nullable String address;

    public MultiDeviceContactUpdateJob(@NonNull Context context) {
        this(context, null);
    }

    public MultiDeviceContactUpdateJob(@NonNull Context context, @Nullable Address address) {
        super(context, JobParameters.newBuilder()
                .withRequirement(new NetworkRequirement(context))
                .withRequirement(new MasterSecretRequirement(context))
                .withGroupId(MultiDeviceContactUpdateJob.class.getSimpleName())
                .withPersistence()
                .create());

        if (address != null) this.address = address.serialize();
        else this.address = null;
    }

    @Override
    public void onRun(MasterSecret masterSecret)
            throws IOException, UntrustedIdentityException, NetworkException {
        if (!TextSecurePreferences.isMultiDevice(context)) {
            Log.w(TAG, "Not multi device, aborting...");
            return;
        }

        if (address == null) generateFullContactUpdate();
        else generateSingleContactUpdate(Address.fromSerialized(address));
    }

    private void generateSingleContactUpdate(@NonNull Address address)
            throws IOException, UntrustedIdentityException, NetworkException {
        File contactDataFile = createTempFile("multidevice-contact-update");

        try {
            DeviceContactsOutputStream out = new DeviceContactsOutputStream(new FileOutputStream(contactDataFile));
            Recipient recipient = Recipient.from(context, address, false);
            IdentityRecord identityRecord = Repository.getIdentityRepo().getIdentityRecord(address.serialize());
            Optional<VerifiedMessage> verifiedMessage = getVerifiedMessage(recipient, identityRecord);

            out.write(new DeviceContact(address.toString(),
                    Optional.fromNullable(recipient.getName()),
                    getAvatar(recipient.getContactUri()),
                    Optional.fromNullable(recipient.getColor().serialize()),
                    verifiedMessage,
                    Optional.fromNullable(recipient.getProfileKey())));

            out.close();


        } catch (InvalidNumberException e) {
            Log.w(TAG, e);
        } finally {
            if (contactDataFile != null) contactDataFile.delete();
        }
    }

    private void generateFullContactUpdate()
            throws IOException, UntrustedIdentityException, NetworkException {
        File contactDataFile = createTempFile("multidevice-contact-update");

        try {
            DeviceContactsOutputStream out = new DeviceContactsOutputStream(new FileOutputStream(contactDataFile));
            Collection<ContactData> contacts = ContactAccessor.getInstance().getContactsWithPush(context);

            for (ContactData contactData : contacts) {
                Uri contactUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, String.valueOf(contactData.id));
                Address address = Address.from(context, contactData.numbers.get(0).number);
                Recipient recipient = Recipient.from(context, address, false);
                IdentityRecord identity = Repository.getIdentityRepo().getIdentityRecord(address.serialize());
                Optional<VerifiedMessage> verified = getVerifiedMessage(recipient, identity);
                Optional<String> name = Optional.fromNullable(contactData.name);
                Optional<String> color = Optional.of(recipient.getColor().serialize());
                Optional<byte[]> profileKey = Optional.fromNullable(recipient.getProfileKey());

                out.write(new DeviceContact(address.toString(), name, getAvatar(contactUri), color, verified, profileKey));
            }

            if (ProfileKeyUtil.hasProfileKey(context)) {
                out.write(new DeviceContact(AMELogin.INSTANCE.getUid(),
                        Optional.absent(), Optional.absent(),
                        Optional.absent(), Optional.absent(),
                        Optional.of(ProfileKeyUtil.getProfileKey(context))));
            }

            out.close();
        } catch (InvalidNumberException e) {
            Log.w(TAG, e);
        } finally {
            if (contactDataFile != null) contactDataFile.delete();
        }
    }

    @Override
    public boolean onShouldRetryThrowable(Exception exception) {
        if (exception instanceof PushNetworkException) return true;
        return false;
    }

    @Override
    public void onAdded() {

    }

    @Override
    public void onCanceled() {

    }

    private Optional<SignalServiceAttachmentStream> getAvatar(@Nullable Uri uri) throws IOException {
        if (uri == null) {
            return Optional.absent();
        }

        try {
            Uri displayPhotoUri = Uri.withAppendedPath(uri, ContactsContract.Contacts.Photo.DISPLAY_PHOTO);
            AssetFileDescriptor fd = context.getContentResolver().openAssetFileDescriptor(displayPhotoUri, "r");

            return Optional.of(SignalServiceAttachment.newStreamBuilder()
                    .withStream(fd.createInputStream())
                    .withContentType("image/*")
                    .withLength(fd.getLength())
                    .build());
        } catch (IOException e) {
            Log.w(TAG, e);
        }

        Uri photoUri = Uri.withAppendedPath(uri, ContactsContract.Contacts.Photo.CONTENT_DIRECTORY);

        if (photoUri == null) {
            return Optional.absent();
        }

        Cursor cursor = context.getContentResolver().query(photoUri,
                new String[]{
                        ContactsContract.CommonDataKinds.Photo.PHOTO,
                        ContactsContract.CommonDataKinds.Phone.MIMETYPE
                }, null, null, null);

        try {
            if (cursor != null && cursor.moveToNext()) {
                byte[] data = cursor.getBlob(0);

                if (data != null) {
                    return Optional.of(SignalServiceAttachment.newStreamBuilder()
                            .withStream(new ByteArrayInputStream(data))
                            .withContentType("image/*")
                            .withLength(data.length)
                            .build());
                }
            }

            return Optional.absent();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private Optional<VerifiedMessage> getVerifiedMessage(Recipient recipient, IdentityRecord identity) throws InvalidNumberException {
        if (identity == null) return Optional.absent();

        String destination = recipient.getAddress().serialize();
        IdentityKey identityKey = identity.getIdentityKey();

        VerifiedMessage.VerifiedState state;

        switch (identity.getVerifyStatus()) {
            case VERIFIED:
                state = VerifiedMessage.VerifiedState.VERIFIED;
                break;
            case UNVERIFIED:
                state = VerifiedMessage.VerifiedState.UNVERIFIED;
                break;
            case DEFAULT:
                state = VerifiedMessage.VerifiedState.DEFAULT;
                break;
            default:
                throw new AssertionError("Unknown state: " + identity.getVerifyStatus());
        }

        return Optional.of(new VerifiedMessage(destination, identityKey, state, System.currentTimeMillis()));
    }

    private File createTempFile(String prefix) throws IOException {
        File file = File.createTempFile(prefix, "tmp", context.getCacheDir());
        file.deleteOnExit();

        return file;
    }

    private static class NetworkException extends Exception {

        public NetworkException(Exception ioe) {
            super(ioe);
        }
    }

}
