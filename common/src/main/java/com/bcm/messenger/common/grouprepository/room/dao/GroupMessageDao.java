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
     * 查一个群里面的所有未读消息
     *
     * @param gid
     * @return
     */
    @Query("SELECT * FROM " + GroupMessage.TABLE_NAME + " WHERE gid = :gid" + " AND is_confirm = 0 And read_state = " + GroupMessage.READ_STATE_UNREAD)
    List<GroupMessage> loadUnreadMessagesByGid(long gid);


    /**
     * 查询指定的一段范围内的消息
     * index 从大到小
     *
     * @param gid
     * @param indexId 初始位置 Id
     * @param count   一次取消息条数
     * @return
     */
    @Query("SELECT * FROM " + GroupMessage.TABLE_NAME + " WHERE _id < :indexId AND gid = :gid AND is_confirm = " + GroupMessage.CONFIRM_MESSAGE + " ORDER BY _id DESC " + " LIMIT 0 ,:count ")
    List<GroupMessage> loadMessagesByGidAndIndexId(long gid, long indexId, int count);

    /**
     * 查询指定的一段范围内的消息
     * index 从大到小
     *
     * @param gid
     * @param indexId 初始位置 Id
     * @param count   一次取消息条数
     * @return
     */
    @Query("SELECT * FROM " + GroupMessage.TABLE_NAME + " WHERE _id > :indexId AND gid = :gid AND is_confirm = " + GroupMessage.CONFIRM_MESSAGE + " ORDER BY _id DESC " + " LIMIT 0 ,:count ")
    List<GroupMessage> loadMessagesByGidAndIndexIdAfter(long gid, long indexId, int count);

    /**
     * 查询某index之后的所有消息
     * index 从大到小
     *
     * @param gid
     * @param indexId 初始位置 Id
     * @return
     */
    @Query("SELECT * FROM " + GroupMessage.TABLE_NAME + " WHERE _id > :indexId AND gid = :gid AND is_confirm = " + GroupMessage.CONFIRM_MESSAGE + " ORDER BY _id DESC ")
    List<GroupMessage> loadMessagesByGidAndIndexIdAfter(long gid, long indexId);

    /**
     * 查询指定的一段范围内的视频或图片消息消息,向前查
     *
     * @param gid
     * @param indexId 初始位置 Id
     * @param count   一次取消息条数
     * @return
     */
    @Query("SELECT * FROM " + GroupMessage.TABLE_NAME + " WHERE _id < :indexId AND gid = :gid AND is_confirm = " + GroupMessage.CONFIRM_MESSAGE + " AND( content_type = 2 " + " OR content_type = 4)" + " ORDER BY _id DESC " + " LIMIT 0 ,:count ")
    List<GroupMessage> loadImageOrVideoMessagesBeforeIndexId(long gid, long indexId, int count);

    /**
     * 查询指定的一段范围内的文字消息,向前查
     *
     * @param gid
     * @param indexId 初始位置 Id
     * @param count   一次取消息条数
     * @return
     */
    @Query("SELECT * FROM " + GroupMessage.TABLE_NAME + " WHERE _id < :indexId AND gid = :gid AND is_confirm = " + GroupMessage.CONFIRM_MESSAGE + " AND( content_type = 1)" + " ORDER BY _id DESC " + " LIMIT 0 ,:count ")
    List<GroupMessage> loadTextMessagesBeforeIndexId(long gid, long indexId, int count);


    /**
     * 查询指定的一段范围内的视频或图片消息，向后查
     *
     * @param gid
     * @param indexId 初始位置 Id
     * @param count   一次取消息条数
     * @return
     */
    @Query("SELECT * FROM " + GroupMessage.TABLE_NAME + " WHERE _id > :indexId AND gid = :gid AND is_confirm = " + GroupMessage.CONFIRM_MESSAGE + " AND( content_type = 2 " + " OR content_type = 4)" + " LIMIT 0 ,:count ")
    List<GroupMessage> loadImageOrVideoMessagesAfterIndexId(long gid, long indexId, int count);

    /**
     * 查询指定的一段范围内的文字信息，向后查
     *
     * @param gid
     * @param indexId 初始位置 Id
     * @param count   一次取消息条数
     * @return
     */
    @Query("SELECT * FROM " + GroupMessage.TABLE_NAME + " WHERE _id > :indexId AND gid = :gid AND is_confirm = " + GroupMessage.CONFIRM_MESSAGE + " AND( content_type = 1)" + " LIMIT 0 ,:count ")
    List<GroupMessage> loadTextMessagesAfterIndexId(long gid, long indexId, int count);

    /**
     * 查询指定群的所有图片和视频消息
     *
     * @param gid 群ID
     * @return 所有的图片和视频消息
     * @author Kin
     */
    @Query("SELECT * FROM " + GroupMessage.TABLE_NAME + " WHERE gid = :gid AND is_confirm =" + GroupMessage.CONFIRM_MESSAGE + " AND (content_type = 2 OR content_type = 4) ORDER BY _id DESC")
    List<GroupMessage> loadAllImageOrVideoMessages(long gid);

    /**
     * 查询指定群的所有文件消息
     *
     * @param gid 群ID
     * @return 所有的文件消息
     * @author Kin
     */
    @Query("SELECT * FROM " + GroupMessage.TABLE_NAME + " WHERE gid = :gid AND is_confirm =" + GroupMessage.CONFIRM_MESSAGE + " AND (content_type = 3) ORDER BY _id DESC")
    List<GroupMessage> loadAllFileMessages(long gid);


    /**
     * 查询指定群的多媒体消息
     *
     * @param gid 群ID
     * @return 所有的多媒体消息
     * @author Kin
     */
    @Query("SELECT * FROM " + GroupMessage.TABLE_NAME + " WHERE gid = :gid AND is_confirm =" + GroupMessage.CONFIRM_MESSAGE + " AND (content_type = 3 or content_type = 2 OR content_type = 4) ORDER BY _id DESC")
    List<GroupMessage> loadAllMediaMessages(long gid);


    /**
     * 查询指定群的所有链接消息
     *
     * @param gid 群ID
     * @return 所有的链接消息
     * @author Kin
     */
    @Query("SELECT * FROM " + GroupMessage.TABLE_NAME + " WHERE gid = :gid AND is_confirm =" + GroupMessage.CONFIRM_MESSAGE + " AND (content_type = 7) ORDER BY _id DESC")
    List<GroupMessage> loadAllLinkMessages(long gid);

    /**
     * 查询从fromMid到（包含）toMid的消息
     *
     * @param gid
     * @param fromMid
     * @param toMid
     * @return
     */
    @Query("SELECT * FROM " + GroupMessage.TABLE_NAME + " WHERE mid >= :toMid AND mid < :fromMid AND gid = :gid AND is_confirm = " + GroupMessage.CONFIRM_MESSAGE + " ORDER BY _id DESC ")
    List<GroupMessage> loadMessageFromTo(long gid, long fromMid, long toMid);

    /**
     * 查询指定类型的消息
     *
     * @param gid
     * @param type 1.chat 消息，不公开，订阅者不可见; 2.pub 公开，订阅者可见
     * @return
     */
    @Query("SELECT * FROM " + GroupMessage.TABLE_NAME + " WHERE gid =" + " :gid" + " AND type =" + " :type" + " AND is_confirm = " + GroupMessage.CONFIRM_MESSAGE)
    List<GroupMessage> loadAllSubMessageByGidAndType(long gid, int type);


    /**
     * 查询指定群的未读消息数量
     *
     * @param gid
     * @return
     */
    @Query("SELECT COUNT(*) FROM " + GroupMessage.TABLE_NAME + " WHERE gid =" + " :gid" + " AND read_state = " + GroupMessage.READ_STATE_UNREAD + " AND is_confirm = " + GroupMessage.CONFIRM_MESSAGE)
    long countUnread(long gid);


    /**
     * 查询指定群的未读消息数量
     *
     * @param gid
     * @return
     */
    @Query("SELECT COUNT(*) FROM " + GroupMessage.TABLE_NAME + " WHERE gid =" + " :gid" + " AND read_state = " + GroupMessage.READ_STATE_UNREAD + " AND is_confirm = " + GroupMessage.CONFIRM_MESSAGE + " AND create_time > " + " :lastSeen")
    long countUnreadFromLastSeen(long gid, long lastSeen);

    /**
     * 获取未读的消息列表数
     *
     * @return
     */
    @Query("SELECT COUNT(*) FROM (SELECT * FROM " + GroupMessage.TABLE_NAME + " WHERE read_state = " + GroupMessage.READ_STATE_UNREAD + " AND is_confirm = " + GroupMessage.CONFIRM_MESSAGE + " GROUP BY gid HAVING count(gid)>0)")
    int getAllUnreadThreadCount();

    /**
     * 查询指定群的消息数量
     *
     * @param gid
     * @return
     */
    @Query("SELECT COUNT(*) FROM " + GroupMessage.TABLE_NAME + " WHERE gid =" + " :gid" + " AND is_confirm = " + GroupMessage.CONFIRM_MESSAGE)
    long countMessagesByGid(long gid);



    /**
     * 查询指定群的消息数量
     *
     * @param gid
     * @return
     */
    @Query("SELECT COUNT(*) FROM " + GroupMessage.TABLE_NAME + " WHERE gid =" + " :gid" + " AND is_confirm = " + GroupMessage.UNCONFIRM_MESSAGE)
    long countUnconfirmedMessagesByGid(long gid);

    /**
     * 根据 mid 查询指定的一条消息
     *
     * @param gid
     * @param mid
     * @return
     */
    @Query("SELECT * FROM " + GroupMessage.TABLE_NAME + " WHERE gid =" + " :gid" + " AND mid = " + " :mid")
    GroupMessage queryOneMessageByMid(long gid, long mid);

    /**
     * 根据 mid 查询确认要显示的一条消息
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
     * 根据 index 查询指定的一条消息
     *
     * @param gid
     * @param index
     * @return
     */
    @Query("SELECT * FROM " + GroupMessage.TABLE_NAME + " WHERE gid =" + " :gid" + " AND _id = " + " :index")
    GroupMessage queryOneMessageByIndex(long gid, long index);


    /**
     * 根据 index 查询指定的一条可显示消息
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
     * 更新一条消息
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
