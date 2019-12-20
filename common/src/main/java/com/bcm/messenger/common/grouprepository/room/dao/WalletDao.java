package com.bcm.messenger.common.grouprepository.room.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.bcm.messenger.common.grouprepository.room.entity.WalletData;
import com.bcm.messenger.common.grouprepository.room.entity.WalletTransaction;

import java.util.List;

/**
 * BCM专属钱包表的操作dao
 * Created by wjh on 2018/8/3
 */
@Dao
public interface WalletDao {

    /**
     * 查询钱包信息（余额等）
     *
     * @return
     */
    @Query("SELECT * FROM " + WalletData.TABLE_NAME + " WHERE :owner = " + WalletData.COLUMN_OWNER)
    WalletData queryWalletData(String owner);

    /**
     * 查询钱包交易记录
     *
     * @return
     */
    @Query("SELECT * FROM " + WalletTransaction.TABLE_NAME + " WHERE :walletId = " + WalletTransaction.COLUMN_WALLET)
    List<WalletTransaction> queryTransactions(long walletId);


    /**
     * 插入钱包记录
     *
     * @param wallet
     * @return
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertWallet(WalletData wallet);

    /**
     * 插入交易记录
     *
     * @param transactions 交易记录
     * @return
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long[] insertTransactions(WalletTransaction... transactions);

}
