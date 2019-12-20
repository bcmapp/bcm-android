package com.bcm.route.api;

import com.bcm.route.annotation.RouteModel;

import java.util.HashMap;

class RouteMap {
    private static HashMap<String, RouteModel> routeMap = new HashMap<>();

    static RouteModel getModel(String path) {
        return routeMap.get(path);
    }

    static HashMap<String, RouteModel> getMap() {
        return routeMap;
    }

    static void clear() {
        routeMap.clear();
    }
}
