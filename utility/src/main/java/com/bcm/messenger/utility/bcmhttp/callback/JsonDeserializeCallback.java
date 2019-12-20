package com.bcm.messenger.utility.bcmhttp.callback;

import com.bcm.messenger.utility.bcmhttp.callback.serialize.JsonDeserializetor;

public abstract class JsonDeserializeCallback<T> extends DeserializeCallback<T> {

    public JsonDeserializeCallback() {
        super(new JsonDeserializetor());
    }

}
