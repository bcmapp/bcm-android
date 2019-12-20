package com.bcm.messenger.utility.bcmhttp.callback;
import okhttp3.Call;
import okhttp3.Response;

public abstract class Callback<T>
{
    public static final int THREAD_MAIN = 0;
    public static final int THREAD_SYNC = 1;
    public static final int THREAD_CURRENT = 2;

    public int callInThreadMode() {
        return THREAD_CURRENT;
    }

    public void inProgress(int progress, long total , long id)
    {

    }

    /**
     * if you parse reponse code in parseNetworkResponse, you should make this method return true.
     *
     * @param response
     * @return
     */
    public boolean validateResponse(Response response, long id)
    {
        return response.isSuccessful();
    }

    /**
     * Thread Pool Thread
     */
    public abstract T parseNetworkResponse(Response response, long id) throws Exception;

    public abstract void onError(Call call, Exception e, long id);

    public abstract void onResponse(T response, long id);

    public static Callback CALLBACK_DEFAULT = new Callback()
    {

        @Override
        public Object parseNetworkResponse(Response response, long id) throws Exception
        {
            return null;
        }

        @Override
        public void onError(Call call, Exception e, long id)
        {

        }

        @Override
        public void onResponse(Object response, long id)
        {

        }
    };

}