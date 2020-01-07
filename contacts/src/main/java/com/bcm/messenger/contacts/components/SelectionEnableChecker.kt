package com.bcm.messenger.contacts.components

import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.recipients.Recipient
import io.reactivex.Observable
import java.lang.ref.WeakReference

object SelectionEnableChecker {
    enum class STATE {
        UNKNOWN,
        CHECKING,
        ENABLE,
        DISABLE
    }

    interface IChecker {
        fun canEnable(address:Address, syncFromNet:Boolean):Observable<STATE>
        fun checkState(address: Address): STATE
    }

    fun getChecker(checker:String): IChecker {
        return if (checker == ARouterConstants.PARAM.CONTACTS_SELECT.ENABLE_CHECKER.CHECKER_GROUP_V3) {
            GroupInviteChecker()
        } else {
            DefaultChecker()
        }
    }


    class DefaultChecker: IChecker {
        override fun canEnable(address: Address, syncFromNet: Boolean): Observable<STATE> {
            return Observable.just(STATE.ENABLE)
        }

        override fun checkState(address: Address): STATE {
            return STATE.ENABLE
        }
    }

    class GroupInviteChecker: IChecker {
        private val checkedList = mutableMapOf<Address, Boolean>()
        override fun checkState(address: Address): STATE {
            val recipient = Recipient.from(address, true)
            return when(checkedList[address]) {
                true -> {
                    if(recipient.featureSupport?.isSupportGroupSecureV3() == true) {
                        STATE.ENABLE
                    } else {
                        STATE.DISABLE
                    }
                }
                false -> {
                    STATE.CHECKING
                }
                else -> {
                    STATE.UNKNOWN
                }
            }
        }

        override fun canEnable(address: Address, syncFromNet: Boolean): Observable<STATE> {
            val recipient = Recipient.from(address, true)
            if(true == recipient.featureSupport?.isSupportGroupSecureV3()) {
                return Observable.just(STATE.ENABLE)
            } else if(checkedList[address] == true) {
                return Observable.just(STATE.DISABLE)
            } else if (checkedList[address] == false) {
                return Observable.just(STATE.CHECKING)
            } else if(!syncFromNet){
                return Observable.just(STATE.ENABLE)
            } else {
                checkedList[address] = false
                val weakThis = WeakReference<GroupInviteChecker>(this)
                return Observable.create<STATE> {
                    it.onNext(STATE.CHECKING)
                    AmeModuleCenter.contact(address.context())?.fetchProfile(recipient) { _ ->
                        weakThis.get()?.checkedList?.put(address, true)
                        val state = if(true == recipient.featureSupport?.isSupportGroupSecureV3()) {
                            STATE.ENABLE
                        } else {
                            STATE.DISABLE
                        }
                        it.onNext(state)
                        it.onComplete()
                    }
                }
            }
        }
    }

}