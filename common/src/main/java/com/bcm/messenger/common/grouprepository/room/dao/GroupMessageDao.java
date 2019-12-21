package com.bcm.messenger.common.grouprepository.room.dao;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.bcm.messenger.common.grouprepository.room.entity.GroupMessage;

import java.util.List;

@Dao
public interface GroupMessageDao {


    /**
     * 
     *
     * @param gid
     * @return
     */
    @Query("SELECT * FROM " + GroupMessage.TABLE_NAME + " WHERE gid = :gid" + " AND is_confirm = 0 And read_state = " + GroupMessage.READ_STATE_UNREAD)
    List<GroupMessage> loadUnreadMessagesByGid(long gid);


    /**
     * 
     * index 
     *
     * @param gid
     * @param indexId  Id
     * @param count   
     * @return
     */
    @Query("SELECT * FROM " + GroupMessage.TABLE_NAME + " WHERE _id < :indexId AND gid = :gid AND is_confirm = " + GroupMessage.CONFIRM_MESSAGE + " ORDER BY _id DESC " + " LIMIT 0 ,:count ")
    List<GroupMessage> loadMessagesByGidAndIndexId(long gid, long indexId, int count);

    /**
     * 
     * index 
     *
     * @param gid
     * @param indexId  Id
     * @param count   
     * @return
     */
    @Query("SELECT * FROM " + GroupMessage.TABLE_NAME + " WHERE _id > :indexId AND gid = :gid AND is_confirm = " + GroupMessage.CONFIRM_MESSAGE + " ORDER BY _id DESC " + " LIMIT 0 ,:count ")
    List<GroupMessage> loadMessagesByGidAndIndexIdAfter(long gid, long indexId, int count);

    /**
     * index
     * index 
     *
     * @param gid
     * @param indexId  Id
     * @return
     */
    @Query("SELECT * FROM " + GroupMessage.TABLE_NAME + " WHERE _id > :indexId AND gid = :gid AND is_confirm = " + GroupMessage.CONFIRM_MESSAGE + " ORDER BY _id DESC ")
    List<GroupMessage> loadMessagesByGidAndIndexIdAfter(long gid, long indexId);

    /**
     * ,
     *
     * @param gid
     * @param indexId  Id
     * @param count   
     * @return
     */
    @Query("SELECT * FROM " + GroupMessage.TABLE_NAME + " WHERE _id < :indexId AND gid = :gid AND is_confirm = " + GroupMessage.CONFIRM_MESSAGE + " AND( content_type = 2 " + " OR content_type = 4)" + " ORDER BY _id DESC " + " LIMIT 0 ,:count ")
    List<GroupMessage> loadImageOrVideoMessagesBeforeIndexId(long gid, long indexId, int count);

    /**
     * ,
     *
     * @param gid
     * @param indexId  Id
     * @param count   
     * @return
     */
    @Query("SELECT * FROM " + GroupMessage.TABLE_NAME + " WHERE _id < :indexId AND gid = :gid AND is_confirm = " + GroupMessage.CONFIRM_MESSAGE + " AND( content_type = 1)" + " ORDER BY _id DESC " + " LIMIT 0 ,:count ")
    List<GroupMessage> loadTextMessagesBeforeIndexId(long gid, long indexId, int count);


    /**
     * ，
     *
     * @param gid
     * @param indexId  Id
     * @param count   
     * @return
     */
    @Query("SELECT * FROM " + GroupMessage.TABLE_NAME + " WHERE _id > :indexId AND gid = :gid AND is_confirm = " + GroupMessage.CONFIRM_MESSAGE + " AND( content_type = 2 " + " OR content_type = 4)" + " LIMIT 0 ,:count ")
    List<GroupMessage> loadImageOrVideoMessagesAfterIndexId(long gid, long indexId, int count);

    /**
     * ，
     *
     * @param gid
     * @param indexId  Id
     * @param count   
     * @return
     */
    @Query("SELECT * FROM " + GroupMessage.TABLE_NAME + " WHERE _id > :indexId AND gid = :gid AND is_confirm = " + GroupMessage.CONFIRM_MESSAGE + " AND( content_type = 1)" + " LIMIT 0 ,:count ")
    List<GroupMessage> loadTextMessagesAfterIndexId(long gid, long indexId, int count);

    /**
     * 
     *
     * @param gid ID
     * @return 
     * @author Kin
     */
    @Query("SELECT * FROM " + GroupMessage.TABLE_NAME + " WHERE gid = :gid AND is_confirm =" + GroupMessage.CONFIRM_MESSAGE + " AND (content_type = 2 OR content_type = 4) ORDER BY _id DESC")
    List<GroupMessage> loadAllImageOrVideoMessages(long gid);

    /**
     * 
     *
     * @param gid ID
     * @return 
     * @author Kin
     */
    @Query("SELECT * FROM " + GroupMessage.TABLE_NAME + " WHERE gid = :gid AND is_confirm =" + GroupMessage.CONFIRM_MESSAGE + " AND (content_type = 3) ORDER BY _id DESC")
    List<GroupMessage> loadAllFileMessages(long gid);


    /**
     * 
     *
     * @param gid ID
     * @return 
     * @author Kin
     */
    @Query("SELECT * FROM " + GroupMessage.TABLE_NAME + " WHERE gid = :gid AND is_confirm =" + GroupMessage.CONFIRM_MESSAGE + " AND (content_type = 3 or content_type = 2 OR content_type = 4) ORDER BY _id DESC")
    List<GroupMessage> loadAllMediaMessages(long gid);


    /**
     * 
     *
     * @param gid ID
     * @return 
     * @author Kin
     */
    @Query("SELECT * FROM " + GroupMessage.TABLE_NAME + " WHERE gid = :gid AND is_confirm =" + GroupMessage.CONFIRM_MESSAGE + " AND (content_type = 7) ORDER BY _id DESC")
    List<GroupMessage> loadAllLinkMessages(long gid);

    /**
     * fromMid（）toMid
     *
     * @param gid
     * @param fromMid
     * @param toMid
     * @return
     */
    @Query("SELECT * FROM " + GroupMessage.TABLE_NAME + " WHERE mid >= :toMid AND mid < :fromMid AND gid = :gid AND is_confirm = " + GroupMessage.CONFIRM_MESSAGE + " ORDER BY _id DESC ")
    List<GroupMessage> loadMessageFromTo(long gid, long fromMid, long toMid);

    /**
     * 
     *
     * @param gid
     * @param type 1.chat ，，; 2.pub ，
     * @return
     */
    @Query("SELECT * FROM " + GroupMessage.TABLE_NAME + " WHERE gid =" + " :gid" + " AND type =" + " :type" + " AND is_confirm = " + GroupMessage.CONFIRM_MESSAGE)
    List<GroupMessage> loadAllSubMessageByGidAndType(long gid, int type);


    /**
     * 
     *
     * @param gid
     * @return
     */
    @Query("SELECT COUNT(*) FROM " + GroupMessage.TABLE_NAME + " WHERE gid =" + " :gid" + " AND read_state = " + GroupMessage.READ_STATE_UNREAD + " AND is_confirm = " + GroupMessage.CONFIRM_MESSAGE)
    long countUnread(long gid);


    /**
     * 
     *
     * @param gid
     * @return
     */
    @Query("SELECT COUNT(*) FROM " + GroupMessage.TABLE_NAME + " WHERE gid =" + " :gid" + " AND read_state = " + GroupMessage.READ_STATE_UNREAD + " AND is_confirm = " + GroupMessage.CONFIRM_MESSAGE + " AND create_time > " + " :lastSeen")
    long countUnreadFromLastSeen(long gid, long lastSeen);

    /**
     * 
     *
     * @return
     */
    @Query("SELECT COUNT(*) FROM (SELECT * FROM " + GroupMessage.TABLE_NAME + " WHERE read_state = " + GroupMessage.READ_STATE_UNREAD + " AND is_confirm = " + GroupMessage.CONFIRM_MESSAGE + " GROUP BY gid HAVING count(gid)>0)")
    int getAllUnreadThreadCount();

    /**
     * 
     *
     * @param gid
     * @return
     */
    @Query("SELECT COUNT(*) FROM " + GroupMessage.TABLE_NAME + " WHERE gid =" + " :gid" + " AND is_confirm = " + GroupMessage.CONFIRM_MESSAGE)
    long countMessagesByGid(long gid);



    /**
     * 
     *
     * @param gid
     * @return
     */
    @Query("SELECT COUNT(*) FROM " + GroupMessage.TABLE_NAME + " WHERE gid =" + " :gid" + " AND is_confirm = " + GroupMessage.UNCONFIRM_MESSAGE)
    long countUnconfirmedMessagesByGid(long gid);

    /**
     *  mid 
     *
     * @param gid
     * @param mid
     * @return
     */
    @Query("SELECT * FROM " + GroupMessage.TABLE_NAME + " WHERE gid =" + " :gid" + " AND mid = " + " :mid")
    GroupMessage queryOneMessageByMid(long gid, long mid);

    /**
     *  mid 
     *
     * @param gid
     * @param mid
     * @return
     */
    @Query("SELECT * FROM " + GroupMessage.TABLE_NAME + " WHERE gid =" + " :gid" + " AND mid = " + " :mid AND is_confirm = " + GroupMessage.CONFIRM_MESSAGE)
    GroupMessage queryOneMessageByMidConfirm(long gid, long mid);

    /**
     * @param gid
     * @return
     */
    @Query("SELECT * FROM " + GroupMessage.TABLE_NAME + " WHERE gid = " + " :gid" + " AND read_state =" + GroupMessage.READ_STATE_UNREAD + " ORDER BY _id ASC " + " LIMIT 0 , :count")
    List<GroupMessage> queryMinReadMessage(long gid, int count);

    /**
     *  index 
     *
     * @param gid
     * @param index
     * @return
     */
    @Query("SELECT * FROM " + GroupMessage.TABLE_NAME + " WHERE gid =" + " :gid" + " AND _id = " + " :index")
    GroupMessage queryOneMessageByIndex(long gid, long index);


    /**
     *  index 
     *
     * @param gid
     * @param index
     * @return
     */
    @Query("SELECT * FROM " + GroupMessage.TABLE_NAME + " WHERE gid =" + " :gid" + " AND _id = " + " :index" + " AND is_confirm = " + GroupMessage.CONFIRM_MESSAGE)
    GroupMessage queryOneVisiableMessageByIndex(long gid, long index);

    @Query("SELECT * FROM " + GroupMessage.TABLE_NAME + " WHERE gid =" + " :gid" + " AND is_confirm = " + GroupMessage.CONFIRM_MESSAGE + " ORDER BY _id DESC " + " LIMIT 0 ,1 ")
    GroupMessage queryLastMessageByGid(long gid);

    @Query("SELECT * FROM " + GroupMessage.TABLE_NAME + " WHERE gid =" + " :gid" + " AND mid IN (:midList)")
    List<GroupMessage> queryMessageByMidList(long gid, long[] midList);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertMessage(GroupMessage message);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertMessages(List<GroupMessage> messages);

    @Query("SELECT * FROM " + GroupMessage.TABLE_NAME + " WHERE gid =" + " :gid ")
    List<GroupMessage> loadGroupMessageByGid(long gid);

    @Delete
    void delete(GroupMessage message);

    @Delete
    void delete(List<GroupMessage> messages);

    @Query("SELECT MAX(mid) FROM " + GroupMessage.TABLE_NAME + " WHERE gid = " + " :gid")
    long queryMaxMid(long gid);

    @Query("SELECT MAX(_id) FROM " + GroupMessage.TABLE_NAME + " WHERE gid = " + " :gid")
    long queryMaxIndexId(long gid);

    @Query("SELECT * FROM " + GroupMessage.TABLE_NAME + " WHERE gid = " + " :gid" + " AND from_uid = :from_uid" + " AND content_type = 1" + " AND create_time >= :startTime" + " AND create_time <= :endTime")
    List<GroupMessage> fetchMessagesBySenderAndPeriod(long gid, String from_uid, long startTime, long endTime);

    /**
     * 
     *
     * @param message
     */
    @Update(onConflict = OnConflictStrategy.REPLACE)
    void updateMessage(GroupMessage message);

    @Update(onConflict = OnConflictStrategy.REPLACE)
    void updateMessages(List<GroupMessage> groupMessages);

    @Query("UPDATE " + GroupMessage.TABLE_NAME + " set read_state = " + GroupMessage.READ_STATE_READ + " WHERE gid = :gid AND read_state = " + GroupMessage.READ_STATE_UNREAD)
    void setMessageRead(long gid);

    @Query("SELECT * FROM " + GroupMessage.TABLE_NAME + " LIMIT 500 OFFSET :page")
    @NonNull List<GroupMessage> loadMessageByPage(int page);

    @Query("SELECT * FROM " + GroupMessage.TABLE_NAME + " WHERE data_hash = :hash LIMIT 1")
    @Nullable GroupMessage getExistMessageAttachment(String hash);

    @Query("SELECT * FROM " + GroupMessage.TABLE_NAME + " WHERE _id != :indexId AND data_hash = :hash LIMIT 1")
    @Nullable GroupMessage getExistMessageAttachment(long indexId, String hash);

    @Query("SELECT * FROM " + GroupMessage.TABLE_NAME + " WHERE thumb_hash = :hash LIMIT 1")
    @Nullable GroupMessage getExistMessageThumbnail(String hash);

    @Query("SELECT * FROM " + GroupMessage.TABLE_NAME + " WHERE _id != :indexId AND thumb_hash = :hash LIMIT 1")
    @Nullable GroupMessage getExistMessageThumbnail(long indexId, String hash);
}
