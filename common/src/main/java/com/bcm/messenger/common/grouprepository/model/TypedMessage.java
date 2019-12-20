package com.bcm.messenger.common.grouprepository.model;

public class TypedMessage {

    /**
     * type : 1
     * content : http://www.content.com/img.jpg
     */

    private int type;
    private String content;

    public TypedMessage(int type, String content) {
        this.type = type;
        this.content = content;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
