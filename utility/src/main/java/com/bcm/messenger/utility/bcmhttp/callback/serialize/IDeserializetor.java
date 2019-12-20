package com.bcm.messenger.utility.bcmhttp.callback.serialize;


import java.lang.reflect.Type;

public interface IDeserializetor {
    <T> T transform(String entity, Type classOfT);
}
