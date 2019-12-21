package com.bcm.messenger.common.grouprepository.room.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.bcm.messenger.common.grouprepository.room.entity.WalletData;
import com.bcm.messenger.common.grouprepository.room.entity.WalletTransaction;

import java.util.List;

/**
 * BCMdao
 * Created by wjh on 2018/8/3
 */
@Dao
public interface WalletDao {

    /**
     * （）
     *
     * @return
     */
    @Query("SELECT * FROM " + WalletData.TABLE_NAME + " WHERE :owner = " + WalletData.COLUMN_OWNER)
    WalletData queryWalletData(String owner);

    /**
     * 
     *
     * @return
     */
    @Query("SELECT * FROM " + WalletTransaction.TABLE_NAME + " WHERE :walletId = " + WalletTransaction.COLUMN_WALLET)
    List<WalletTransaction> queryTransactions(long walletId);


    /**
     * 
     *
     * @param wallet
     * @return
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertWallet(WalletData wallet);

    /**
     * 
     *
     * @param transactions 
     * @return
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long[] insertTransactions(WalletTransaction... transactions);

}
