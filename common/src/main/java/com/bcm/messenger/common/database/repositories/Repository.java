package com.bcm.messenger.common.database.repositories;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bcm.messenger.common.AccountContext;
import com.bcm.messenger.common.provider.AMELogin;

public class Repository {
    private static Repository sInstance = null;

    private AccountContext accountContext;
    private PrivateChatRepo chatRepo;
    private AttachmentRepo attachmentRepo;
    private ThreadRepo threadRepo;
    private DraftRepo draftRepo;
    private RecipientRepo recipientRepo;
    private IdentityRepo identityRepo;
    private PushRepo pushRepo;

    @NonNull
    public static Repository getInstance(AccountContext accountContext) {
        synchronized (Repository.class) {
            if (sInstance == null) {
                sInstance = new Repository(accountContext);
            }
            return sInstance;
        }
    }

    @NonNull
    public static PrivateChatRepo getChatRepo(AccountContext accountContext) {
        return getInstance(accountContext).chatRepo;
    }

    @NonNull
    public static AttachmentRepo getAttachmentRepo(AccountContext accountContext) {
        return getInstance(accountContext).attachmentRepo;
    }

    @NonNull
    public static ThreadRepo getThreadRepo(AccountContext accountContext) {
        return getInstance(accountContext).threadRepo;
    }

    @NonNull
    public static DraftRepo getDraftRepo(AccountContext accountContext) {
        return getInstance(accountContext).draftRepo;
    }

    @Nullable
    public static RecipientRepo getRecipientRepo(AccountContext accountContext) {
        if (!accountContext.isLogin()) {
            return null;
        }
        return getInstance(accountContext).recipientRepo;
    }

    @NonNull
    public static IdentityRepo getIdentityRepo(AccountContext accountContext) {
        return getInstance(accountContext).identityRepo;
    }

    @NonNull
    public static PushRepo getPushRepo(AccountContext accountContext) {
        return getInstance(accountContext).pushRepo;
    }

    private Repository(AccountContext context) {
        accountContext = context;
        reset();
    }

    public void reset() {
        attachmentRepo = new AttachmentRepo(accountContext);
        chatRepo = new PrivateChatRepo(accountContext);
        threadRepo = new ThreadRepo(accountContext);
        draftRepo = new DraftRepo(accountContext);
        recipientRepo = new RecipientRepo(accountContext);
        identityRepo = new IdentityRepo(accountContext);
        pushRepo = new PushRepo(accountContext);
    }
}
