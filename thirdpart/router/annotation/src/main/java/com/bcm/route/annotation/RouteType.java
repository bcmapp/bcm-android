package com.bcm.route.annotation;

public enum RouteType {
    ACTIVITY(0),
    FRAGMENT(1),
    PROVIDER(2),
    SERVICE(3);

    int id;

    RouteType(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
}
