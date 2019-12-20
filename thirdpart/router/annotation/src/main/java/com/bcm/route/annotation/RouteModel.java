package com.bcm.route.annotation;

public class RouteModel {

    private RouteType type;
    private String moduleName;
    private String path;
    private Class routeClass;

    public RouteModel(RouteType type, String moduleName, String path, Class routeClass) {
        this.type = type;
        this.moduleName = moduleName;
        this.path = path;
        this.routeClass = routeClass;
    }

    public RouteType getType() {
        return type;
    }

    public void setType(RouteType type) {
        this.type = type;
    }

    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Class getRouteClass() {
        return routeClass;
    }

    public void setRouteClass(Class routeClass) {
        this.routeClass = routeClass;
    }
}
