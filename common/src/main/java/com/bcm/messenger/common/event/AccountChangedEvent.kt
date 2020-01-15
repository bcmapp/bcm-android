package com.bcm.messenger.common.event

class NewAccountAddedEvent(val newAccountUid: String)
class AccountLogoutEvent(val accountUid: String)
class AccountLoginStateChangedEvent