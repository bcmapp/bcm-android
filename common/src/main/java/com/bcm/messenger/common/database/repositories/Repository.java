package com.bcm.messenger.common.database.repositories;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bcm.messenger.common.AccountContext;
import com.bcm.messenger.common.database.db.UserDatabase;
import com.bcm.messenger.common.grouprepository.room.dao.AdHocChannelDao;
import com.bcm.messenger.common.grouprepository.room.dao.AdHocMessageDao;
import com.bcm.messenger.common.grouprepository.room.dao.AdHocSessionDao;
import com.bcm.messenger.common.grouprepository.room.dao.BcmFriendDao;
import com.bcm.messenger.common.grouprepository.room.dao.ChatHideMessageDao;
import com.bcm.messenger.common.grouprepository.room.dao.FriendRequestDao;
import com.bcm.messenger.common.grouprepository.room.dao.GroupAvatarParamsDao;
import com.bcm.messenger.common.grouprepository.room.dao.GroupInfoDao;
import com.bcm.messenger.common.grouprepository.room.dao.GroupJoinInfoDao;
import com.bcm.messenger.common.grouprepository.room.dao.GroupKeyDao;
import com.bcm.messenger.common.grouprepository.room.dao.GroupLiveInfoDao;
import com.bcm.messenger.common.grouprepository.room.dao.GroupMemberDao;
import com.bcm.messenger.common.grouprepository.room.dao.GroupMessageDao;
import com.bcm.messenger.common.grouprepository.room.dao.NoteRecordDao;
import com.bcm.messenger.utility.logger.ALog;

import java.util.HashMap;
import java.util.concurrent.Callable;

public class Repository {
    private static final String TAG = Repository.class.getSimpleName();

    private static HashMap<AccountContext, Repository> repositoryHashMap = new HashMap<>(3);

    private AccountContext accountContext;
    private UserDatabase userDatabase;

    private PrivateChatRepo chatRepo;
    private AttachmentRepo attachmentRepo;
    private ThreadRepo threadRepo;
    private DraftRepo draftRepo;
    private RecipientRepo recipientRepo;
    private IdentityRepo identityRepo;
    private PushRepo pushRepo;

    @Nullable
    public static Repository getInstance(AccountContext accountContext) {
        synchronized (Repository.class) {
            if (!accountContext.isLogin()) {
                return null;
            }
            Repository repo = repositoryHashMap.get(accountContext);
            if (repo == null) {
                repo = new Repository(accountContext);
                repositoryHashMap.put(accountContext, repo);
            }
            return repo;
        }
    }

    @Nullable
    public static PrivateChatRepo getChatRepo(AccountContext accountContext) {
        Repository repo = getInstance(accountContext);
        if (repo != null) {
            return repo.chatRepo;
        }
        return null;
    }

    @Nullable
    public static AttachmentRepo getAttachmentRepo(AccountContext accountContext) {
        Repository repo = getInstance(accountContext);
        if (repo != null) {
            return repo.attachmentRepo;
        }
        return null;
    }

    @Nullable
    public static ThreadRepo getThreadRepo(AccountContext accountContext) {
        Repository repo = getInstance(accountContext);
        if (repo != null) {
            return repo.threadRepo;
        }
        return null;
    }

    @Nullable
    public static DraftRepo getDraftRepo(AccountContext accountContext) {
        Repository repo = getInstance(accountContext);
        if (repo != null) {
            return repo.draftRepo;
        }
        return null;
    }

    @Nullable
    public static RecipientRepo getRecipientRepo(AccountContext accountContext) {
        Repository repo = getInstance(accountContext);
        if (repo != null) {
            return repo.recipientRepo;
        }
        return null;
    }

    @Nullable
    public static IdentityRepo getIdentityRepo(AccountContext accountContext) {
        Repository repo = getInstance(accountContext);
        if (repo != null) {
            return repo.identityRepo;
        }
        return null;
    }

    @Nullable
    public static PushRepo getPushRepo(AccountContext accountContext) {
        Repository repo = getInstance(accountContext);
        if (repo != null) {
            return repo.pushRepo;
        }
        return null;
    }

    @Nullable
    public static GroupInfoDao getGroupInfoRepo(AccountContext accountContext) {
        Repository repo = getInstance(accountContext);
        if (repo != null) {
            return repo.userDatabase.groupInfoDao();
        }
        return null;
    }

    @Nullable
    public static GroupMessageDao getGroupMessageRepo(AccountContext accountContext) {
        Repository repo = getInstance(accountContext);
        if (repo != null) {
            return repo.userDatabase.groupMessageDao();
        }
        return null;
    }

    @Nullable
    public static GroupMemberDao getGroupMemberRepo(AccountContext accountContext) {
        Repository repo = getInstance(accountContext);
        if (repo != null) {
            return repo.userDatabase.groupMemberDao();
        }
        return null;
    }

    @Nullable
    public static GroupLiveInfoDao getGroupLiveInfoRepo(AccountContext accountContext) {
        Repository repo = getInstance(accountContext);
        if (repo != null) {
            return repo.userDatabase.groupLiveInfoDao();
        }
        return null;
    }

    @Nullable
    public static BcmFriendDao getBcmFriendRepo(AccountContext accountContext) {
        Repository repo = getInstance(accountContext);
        if (repo != null) {
            return repo.userDatabase.bcmFriendDao();
        }
        return null;
    }

    @Nullable
    public static ChatHideMessageDao getChatHideMessageRepo(AccountContext accountContext) {
        Repository repo = getInstance(accountContext);
        if (repo != null) {
            return repo.userDatabase.chatControlMessageDao();
        }
        return null;
    }

    @Nullable
    public static FriendRequestDao getFriendRequestRepo(AccountContext accountContext) {
        Repository repo = getInstance(accountContext);
        if (repo != null) {
            return repo.userDatabase.friendRequestDao();
        }
        return null;
    }

    @Nullable
    public static GroupAvatarParamsDao getGroupAvatarParamsRepo(AccountContext accountContext) {
        Repository repo = getInstance(accountContext);
        if (repo != null) {
            return repo.userDatabase.groupAvatarParamsDao();
        }
        return null;
    }

    @Nullable
    public static NoteRecordDao getNoteRecordRepo(AccountContext accountContext) {
        Repository repo = getInstance(accountContext);
        if (repo != null) {
            return repo.userDatabase.noteRecordDao();
        }
        return null;
    }

    @Nullable
    public static AdHocChannelDao getAdHocChannelRepo(AccountContext accountContext) {
        Repository repo = getInstance(accountContext);
        if (repo != null) {
            return repo.userDatabase.adHocChannelDao();
        }
        return null;
    }

    @Nullable
    public static AdHocSessionDao getAdHocSessionRepo(AccountContext accountContext) {
        Repository repo = getInstance(accountContext);
        if (repo != null) {
            return repo.userDatabase.adHocSessionDao();
        }
        return null;
    }

    @Nullable
    public static AdHocMessageDao getAdHocMessageRepo(AccountContext accountContext) {
        Repository repo = getInstance(accountContext);
        if (repo != null) {
            return repo.userDatabase.adHocMessageDao();
        }
        return null;
    }

    @Nullable
    public static GroupJoinInfoDao getGroupJoinInfoRepo(AccountContext accountContext) {
        Repository repo = getInstance(accountContext);
        if (repo != null) {
            return repo.userDatabase.groupJoinInfoDao();
        }
        return null;
    }

    @Nullable
    public static GroupKeyDao getGroupKeyRepo(AccountContext accountContext) {
        Repository repo = getInstance(accountContext);
        if (repo != null) {
            return repo.userDatabase.groupKeyDao();
        }
        return null;
    }

    public static void accountLogOut(AccountContext accountContext) {
        Repository repo = repositoryHashMap.get(accountContext);
        if (repo != null) {
            try {
                repo.userDatabase.close();
            } catch (Throwable tr) {
                ALog.e(TAG, "Close database failed!", tr);
            }
        }
    }

    public void runInTransaction(@NonNull Runnable runnable) {
        if (runnable == null) {
            return;
        }

        userDatabase.runInTransaction(runnable);
    }

    public <V> V runInTransaction(@NonNull Callable<V> callable) {
        if (callable == null) {
            return null;
        }

        return userDatabase.runInTransaction(callable);
    }

    @NonNull
    public UserDatabase getUserDatabase() {
        return userDatabase;
    }

    @NonNull
    public PrivateChatRepo getChatRepo() {
        return chatRepo;
    }

    @NonNull
    public AttachmentRepo getAttachmentRepo() {
        return attachmentRepo;
    }

    @NonNull
    public ThreadRepo getThreadRepo() {
        return threadRepo;
    }

    @NonNull
    public DraftRepo getDraftRepo() {
        return draftRepo;
    }

    @NonNull
    public RecipientRepo getRecipientRepo() {
        return recipientRepo;
    }

    @NonNull
    public IdentityRepo getIdentityRepo() {
        return identityRepo;
    }

    @NonNull
    public PushRepo getPushRepo() {
        return pushRepo;
    }

    @NonNull
    public GroupMessageDao getGroupMessageRepo() {
        return userDatabase.groupMessageDao();
    }

    @NonNull
    public GroupMemberDao getGroupMemberRepo() {
        return userDatabase.groupMemberDao();
    }

    @NonNull
    public GroupLiveInfoDao getGroupLiveInfoRepo() {
        return userDatabase.groupLiveInfoDao();
    }

    @NonNull
    public BcmFriendDao getBcmFriendRepo() {
        return userDatabase.bcmFriendDao();
    }

    @NonNull
    public ChatHideMessageDao getChatHideMessageRepo() {
        return userDatabase.chatControlMessageDao();
    }

    @NonNull
    public FriendRequestDao getFriendRequestRepo() {
        return userDatabase.friendRequestDao();
    }

    @NonNull
    public GroupAvatarParamsDao getGroupAvatarParamsRepo() {
        return userDatabase.groupAvatarParamsDao();
    }

    @NonNull
    public NoteRecordDao getNoteRecordRepo() {
        return userDatabase.noteRecordDao();
    }

    @NonNull
    public AdHocChannelDao getAdHocChannelRepo() {
        return userDatabase.adHocChannelDao();
    }

    @NonNull
    public AdHocSessionDao getAdHocSessionRepo() {
        return userDatabase.adHocSessionDao();
    }

    @NonNull
    public AdHocMessageDao getAdHocMessageRepo() {
        return userDatabase.adHocMessageDao();
    }

    @NonNull
    public GroupJoinInfoDao getGroupJoinInfoRepo() {
        return userDatabase.groupJoinInfoDao();
    }

    @NonNull
    public GroupKeyDao getGroupKeyRepo() {
        return userDatabase.groupKeyDao();
    }

    private Repository(AccountContext context) {
        accountContext = context;
        userDatabase = UserDatabase.openDatabase(accountContext, true);

        attachmentRepo = new AttachmentRepo(accountContext, userDatabase.getAttachmentDao());
        chatRepo = new PrivateChatRepo(accountContext, userDatabase.getPrivateChatDao());
        threadRepo = new ThreadRepo(accountContext, userDatabase.getThreadDao(), userDatabase.getPrivateChatDao());
        draftRepo = new DraftRepo(accountContext, userDatabase.getDraftDao());
        recipientRepo = new RecipientRepo(accountContext, userDatabase.getRecipientDao());
        identityRepo = new IdentityRepo(accountContext, userDatabase.getIdentityDao());
        pushRepo = new PushRepo(accountContext, userDatabase.getPushDao());
    }
}
