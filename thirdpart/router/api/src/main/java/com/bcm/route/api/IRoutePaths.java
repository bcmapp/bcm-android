package com.bcm.route.api;


import com.bcm.route.annotation.RouteModel;

import java.util.HashMap;

public interface IRoutePaths {
    void loadInto(HashMap<String, RouteModel> routes);
}
