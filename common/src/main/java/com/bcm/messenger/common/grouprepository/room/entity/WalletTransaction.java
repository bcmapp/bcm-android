package com.bcm.messenger.common.grouprepository.room.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;

import static androidx.room.ForeignKey.CASCADE;

/**
 * BCM
 * Created by wjh on 2018/8/3
 */
@Entity(tableName = WalletTransaction.TABLE_NAME, foreignKeys = @ForeignKey(entity = WalletData.class, parentColumns = WalletData.COLUMN_ID, childColumns = WalletTransaction.COLUMN_WALLET,
        onDelete = CASCADE))
public class WalletTransaction {

    public static final String TABLE_NAME = "wallet_transaction";

    public static final String COLUMN_ID = "_t_id";
    public static final String COLUMN_WALLET = WalletData.COLUMN_ID;
    public static final String COLUMN_TX = "tx_id";
    public static final String COLUMN_TYPE = "op_type";
    public static final String COLUMN_AMOUNT = "amount";
    public static final String COLUMN_FROM = "from";
    public static final String COLUMN_TO = "to";
    public static final String COLUMN_MEMO = "memo";
    public static final String COLUMN_TIME = "timestamp";

    /**
     * The unique ID of the groupUser.
     */
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(index = true, name = COLUMN_ID)
    private long id;

    /**
     * id
     */
    @ColumnInfo(index = true, name = COLUMN_WALLET)
    private long walletId;

    /**
     * id
     */
    @ColumnInfo(index = true, name = COLUMN_TX)
    private String tx;

    /**
     * 
     */
    @ColumnInfo(name = COLUMN_TYPE)
    private String type;

    /**
     * 
     */
    @ColumnInfo(name = COLUMN_AMOUNT)
    private String amount;

    /**
     * 
     */
    @ColumnInfo(name = COLUMN_FROM)
    private String from;

    /**
     * 
     */
    @ColumnInfo(name = COLUMN_TO)
    private String to;

    /**
     * 
     */
    @ColumnInfo(name = COLUMN_TIME)
    private long time;

    @ColumnInfo(name = COLUMN_MEMO)
    private String memo;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getWalletId() {
        return walletId;
    }

    public void setWalletId(long walletId) {
        this.walletId = walletId;
    }

    public String getTx() {
        return tx;
    }

    public void setTx(String tx) {
        this.tx = tx;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public String getMemo() {
        return memo;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }
}
