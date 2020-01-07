package com.bcm.messenger.adhoc.logic

import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.grouprepository.room.dao.AdHocChannelDao
import com.bcm.messenger.common.grouprepository.room.entity.AdHocChannelInfo
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable

class AdHocChannelCache(ready:()->Unit) {
    private val channelList = HashMap<String, AdHocChannel>()

    init {
        ALog.i("AdHocChannelCache", "init begin")

        Observable.create<Map<String, AdHocChannel>> { em ->
            val channelList = HashMap<String, AdHocChannel>()
            getDao().loadAllChannel().forEach {
                val chanel = AdHocChannel(it.cid, it.channelName, it.passwd)
                channelList[chanel.cid] = chanel
            }
            em.onNext(channelList)
            em.onComplete()
        }.subscribeOn(AmeDispatcher.ioScheduler)
                .observeOn(AmeDispatcher.ioScheduler)
                .doOnError {}
                .subscribe {
                    ALog.i("AdHocChannelCache", "init end")
                    channelList.putAll(it)
                    ready()
                }
    }

    fun removeChannel(cid: String) {
        channelList.remove(cid)
        AmeDispatcher.io.dispatch {
            getDao().deleteChannel(cid)
        }
    }

    fun addChannel(channelName: String, passwd: String): Boolean {
        val cid = AdHocChannel.cid(channelName, passwd)
        if (null == getChannel(cid)) {
            channelList[cid] = AdHocChannel(cid, channelName, passwd)
            AmeDispatcher.io.dispatch {
                val dbChannel = AdHocChannelInfo(cid, channelName, passwd)
                getDao().saveChannel(dbChannel)
            }
            return true
        }

        return false
    }

    fun getChannel(cid: String): AdHocChannel? {
        return channelList[cid]
    }

    fun getChannelList(): List<AdHocChannel> {
        return channelList.values.toList()
    }

    @Throws(Exception::class)
    private fun getDao(): AdHocChannelDao {
        return Repository.getAdHocChannelRepo(AMELogin.majorContext) ?: throw Exception("getDao fail")
    }
}