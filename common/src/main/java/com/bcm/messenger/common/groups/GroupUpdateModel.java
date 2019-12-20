package com.bcm.messenger.common.groups;

import com.bcm.messenger.utility.proguard.NotGuard;

import java.util.List;

public class GroupUpdateModel implements NotGuard {
    //创建群
    public final static int GROUP_CREATE = 10;
    //更新群
    public final static int GROUP_UPDATE = 20;
    public final static int GROUP_REMOVE = 30;
    public final static int GROUP_ADD = 40;
    //加入新成员
    public final static int GROUP_MEMBER_JOINED = 50;
    //成员退群
    public final static int GROUP_MEMBER_LEFT = 60;
    //群昵称改变
    public final static int GROUP_TITLE_CHANGED = 70;
    //群头像改变
    public final static int GROUP_AVATAR_CHANGED = 80;


    /**
     * sender : nikename
     * numbers : ["+8615810898012","+8613200000000"]
     * action : 80
     * target : ["zhangsan","lisi"]
     * info : groupName
     */

    private String sender;
    private int action;
    private String info;
    private List<String> numbers;
    private List<String> target;

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public int getAction() {
        return action;
    }

    public void setAction(int action) {
        this.action = action;
    }

    public String getInfo() {
        return info;
    }

    public void setInfo(String info) {
        this.info = info;
    }

    public List<String> getNumbers() {
        return numbers;
    }

    public void setNumbers(List<String> numbers) {
        this.numbers = numbers;
    }

    public List<String> getTarget() {
        return target;
    }

    public void setTarget(List<String> target) {
        this.target = target;
    }
}
