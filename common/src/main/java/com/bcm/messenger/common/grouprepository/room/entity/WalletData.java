package com.bcm.messenger.common.grouprepository.room.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * BCM专属钱包记录表
 * Created by wjh on 2018/8/3
 */
@Entity(tableName = WalletData.TABLE_NAME)
public class WalletData {

    public static final String TABLE_NAME = "wallet_data";
    /**
     * The name of the ID column.
     */
    public static final String COLUMN_ID = "_w_id";
    public static final String COLUMN_OWNER = "owner";
    public static final String COLUMN_BALANCE = "balance";
    public static final String COLUMN_EXTRA = "extra";

    /**
     * 自增ID
     */
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(index = true, name = COLUMN_ID)
    private long id;

    /**
     * 钱包所有者
     */
    @ColumnInfo(index = true, name = COLUMN_OWNER)
    private String owner;

    /**
     * 余额
     */
    @ColumnInfo(name = COLUMN_BALANCE)
    private String balance;

    /**
     * 附加信息
     */
    @ColumnInfo(name = COLUMN_EXTRA)
    private String extra;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getBalance() {
        return balance;
    }

    public void setBalance(String balance) {
        this.balance = balance;
    }

    public String getExtra() {
        return extra;
    }

    public void setExtra(String extra) {
        this.extra = extra;
    }
}
