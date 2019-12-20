package com.bcm.messenger.utility.bcmhttp.callback;
import com.bcm.messenger.utility.bcmhttp.callback.serialize.IDeserializetor;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import okhttp3.Response;

/**
 * @param <T>
 */
public abstract class DeserializeCallback<T> extends Callback<T> {
    IDeserializetor deserializetor;

    public DeserializeCallback(IDeserializetor deserializetor) {
        this.deserializetor = deserializetor;
    }


    @Override
    public T parseNetworkResponse(Response response, long id) throws IOException {
        String payloadString = response.body().string();
        Type beanType = ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        if (beanType == String.class) {
            return (T) payloadString;
        }
        T bean = deserializetor.transform(payloadString, beanType);
        return bean;
    }

}
