package com.bcm.messenger.chats.group.logic;

import java.util.List;

public class GroupEncryptSpec {

    String ownerEncryptSpec;
    List<String> memberEncryptSpecs;

    public GroupEncryptSpec(String ownerEncryptSpec, List<String> memberEncryptSpecs) {
        this.ownerEncryptSpec = ownerEncryptSpec;
        this.memberEncryptSpecs = memberEncryptSpecs;
    }

    public String getOwnerEncryptSpec() {
        return ownerEncryptSpec;
    }

    public void setOwnerEncryptSpec(String ownerEncryptSpec) {
        this.ownerEncryptSpec = ownerEncryptSpec;
    }

    public List<String> getMemberEncryptSpecs() {
        return memberEncryptSpecs;
    }

    public void setMemberEncryptSpecs(List<String> memberEncryptSpecs) {
        this.memberEncryptSpecs = memberEncryptSpecs;
    }

}
