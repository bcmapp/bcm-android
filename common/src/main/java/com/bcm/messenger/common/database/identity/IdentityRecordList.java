package com.bcm.messenger.common.database.identity;


import com.bcm.messenger.common.AccountContext;
import com.bcm.messenger.common.database.records.IdentityRecord;
import com.bcm.messenger.common.database.repositories.IdentityRepo;
import com.bcm.messenger.common.recipients.Recipient;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class IdentityRecordList {

    private static final String TAG = IdentityRecordList.class.getSimpleName();

    private final List<IdentityRecord> identityRecords = new LinkedList<>();

    public void add(IdentityRecord identityRecord) {
        if (identityRecord != null) {
            identityRecords.add(identityRecord);
        }
    }

    public void replaceWith(IdentityRecordList identityRecordList) {
        identityRecords.clear();
        identityRecords.addAll(identityRecordList.identityRecords);
    }

    public boolean isVerified() {
        for (IdentityRecord identityRecord : identityRecords) {
            if (identityRecord.getVerifyStatus() != IdentityRepo.VerifiedStatus.VERIFIED) {
                return false;
            }
        }

        return identityRecords.size() > 0;
    }

    public boolean isUnverified() {
        for (IdentityRecord identityRecord : identityRecords) {
            if (identityRecord.getVerifyStatus() == IdentityRepo.VerifiedStatus.UNVERIFIED) {
                return true;
            }
        }

        return false;
    }

    public boolean isUntrusted() {
        for (IdentityRecord identityRecord : identityRecords) {
            if (isUntrusted(identityRecord)) {
                return true;
            }
        }

        return false;
    }

    public List<IdentityRecord> getUntrustedRecords() {
        List<IdentityRecord> results = new LinkedList<>();

        for (IdentityRecord identityRecord : identityRecords) {
            if (isUntrusted(identityRecord)) {
                results.add(identityRecord);
            }
        }

        return results;
    }

    public List<Recipient> getUntrustedRecipients(AccountContext context) {
        List<Recipient> untrusted = new LinkedList<>();

        for (IdentityRecord identityRecord : identityRecords) {
            if (isUntrusted(identityRecord)) {
                untrusted.add(Recipient.from(context, identityRecord.getUid(), false));
            }
        }

        return untrusted;
    }

    public List<IdentityRecord> getUnverifiedRecords() {
        List<IdentityRecord> results = new LinkedList<>();

        for (IdentityRecord identityRecord : identityRecords) {
            if (identityRecord.getVerifyStatus() == IdentityRepo.VerifiedStatus.UNVERIFIED) {
                results.add(identityRecord);
            }
        }

        return results;
    }

    public List<Recipient> getUnverifiedRecipients(AccountContext context) {
        List<Recipient> unverified = new LinkedList<>();

        for (IdentityRecord identityRecord : identityRecords) {
            if (identityRecord.getVerifyStatus() == IdentityRepo.VerifiedStatus.UNVERIFIED) {
                unverified.add(Recipient.from(context, identityRecord.getUid(), false));
            }
        }

        return unverified;
    }

    private boolean isUntrusted(IdentityRecord identityRecord) {
        return !identityRecord.isNonBlockingApproval() &&
                System.currentTimeMillis() - identityRecord.getTimestamp() < TimeUnit.SECONDS.toMillis(5);
    }

}
