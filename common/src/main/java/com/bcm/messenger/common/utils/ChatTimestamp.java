package com.bcm.messenger.common.utils;

import com.bcm.messenger.common.AccountContext;
import com.bcm.messenger.common.database.repositories.PrivateChatRepo;
import com.bcm.messenger.common.database.repositories.Repository;
import com.bcm.messenger.utility.AmeTimeUtil;

public class ChatTimestamp {
    synchronized public static long getTime(AccountContext accountContext, long threadId) {
        Repository repo = Repository.getInstance(accountContext);
        if (null != repo) {
            PrivateChatRepo repoChat = repo.getChatRepo();
            return Math.max(repoChat.getLastMessageTime(threadId)+5, AmeTimeUtil.INSTANCE.serverTimeMillis());
        }

        return AmeTimeUtil.INSTANCE.serverTimeMillis();
    }
}
