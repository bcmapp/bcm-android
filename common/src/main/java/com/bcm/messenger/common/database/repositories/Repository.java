package com.bcm.messenger.common.database.repositories;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bcm.messenger.common.provider.AMESelfData;

public class Repository {
    private static Repository sInstance = null;

    private PrivateChatRepo chatRepo;
    private AttachmentRepo attachmentRepo;
    private ThreadRepo threadRepo;
    private DraftRepo draftRepo;
    private RecipientRepo recipientRepo;
    private IdentityRepo identityRepo;
    private PushRepo pushRepo;

    @NonNull
    public static Repository getInstance() {
        synchronized (Repository.class) {
            if (sInstance == null) {
                sInstance = new Repository();
            }
            return sInstance;
        }
    }

    @NonNull
    public static PrivateChatRepo getChatRepo() {
        return getInstance().chatRepo;
    }

    @NonNull
    public static AttachmentRepo getAttachmentRepo() {
        return getInstance().attachmentRepo;
    }

    @NonNull
    public static ThreadRepo getThreadRepo() {
        return getInstance().threadRepo;
    }

    @NonNull
    public static DraftRepo getDraftRepo() {
        return getInstance().draftRepo;
    }

    @Nullable
    public static RecipientRepo getRecipientRepo() {
        if (!AMESelfData.INSTANCE.isLogin()) {
            return null;
        }
        return getInstance().recipientRepo;
    }

    @NonNull
    public static IdentityRepo getIdentityRepo() {
        return getInstance().identityRepo;
    }

    @NonNull
    public static PushRepo getPushRepo() {
        return getInstance().pushRepo;
    }

    private Repository() {
        reset();
    }

    public void reset() {
        attachmentRepo = new AttachmentRepo();
        chatRepo = new PrivateChatRepo();
        threadRepo = new ThreadRepo();
        draftRepo = new DraftRepo();
        recipientRepo = new RecipientRepo();
        identityRepo = new IdentityRepo();
        pushRepo = new PushRepo();
    }
}
