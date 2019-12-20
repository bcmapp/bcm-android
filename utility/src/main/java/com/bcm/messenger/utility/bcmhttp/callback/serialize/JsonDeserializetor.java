package com.bcm.messenger.utility.bcmhttp.callback.serialize;

import com.google.gson.Gson;

import java.lang.reflect.Type;

public class JsonDeserializetor implements IDeserializetor{
    Gson mGson = new Gson();

    @Override
    public <T> T transform(String entityString, Type classOfT) {
        return mGson.fromJson(entityString, classOfT);
    }

}
