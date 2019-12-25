package com.bcm.messenger.common.core;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bcm.messenger.common.bcmhttp.FileHttp;
import com.bcm.messenger.common.bcmhttp.IMHttp;
import com.bcm.messenger.common.bcmhttp.interceptor.RedirectInterceptorHelper;
import com.bcm.messenger.common.provider.AMELogin;
import com.bcm.messenger.utility.AppContextHolder;
import com.bcm.messenger.utility.GsonUtils;
import com.bcm.messenger.utility.bcmhttp.call.callbuilder.individualbodybuilder.FileCallBuilder;
import com.bcm.messenger.utility.bcmhttp.callback.Callback;
import com.bcm.messenger.utility.bcmhttp.callback.FileDownCallback;
import com.bcm.messenger.utility.bcmhttp.callback.JsonDeserializeCallback;
import com.bcm.messenger.utility.bcmhttp.facade.BaseHttp;
import com.bcm.messenger.utility.bcmhttp.utils.config.ProgressRequestTag;
import com.bcm.messenger.utility.bcmhttp.utils.streams.StreamRequestBody;
import com.bcm.messenger.utility.bcmhttp.utils.streams.StreamUploadData;
import com.bcm.messenger.utility.logger.ALog;
import com.bcm.messenger.utility.proguard.NotGuard;
import com.google.gson.annotations.SerializedName;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Call;

public class AmeFileUploader {

    private static final String TAG = "AmeFileUploader";

    public static final String ATTACHMENT_URL = "http://ameim.bs2dl.yy.com/attachments/";
    public static final String AWS_UPLOAD_INFO_PATH = "/v1/attachments/s3/upload_certification"; // Path to get AWS S3 certification data in IM server

    public static String DEFAULT_PATH = getDefaultPath();
    public static String DOWNLOAD_PATH = "";
    public static String AUDIO_DIRECTORY;
    public static String THUMBNAIL_DIRECTORY;
    public static String DOCUMENT_DIRECTORY;
    public static String VIDEO_DIRECTORY;
    public static String MAP_DIRECTORY;
    public static String ENCRYPT_DIRECTORY;
    public static String DECRYPT_DIRECTORY;//（）
    public static String TEMP_DIRECTORY;//
    public static String CHAT_FILE_DIRECTORY; // Directory of encrypted chat files with MasterSecret
    public static final String DCIM_DIRECTORY = Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/bcm/";

    public enum AttachmentType {
        PRIVATE_MESSAGE("pmsg"),
        GROUP_MESSAGE("gmsg"),
        PROFILE("profile");

        private String type;

        AttachmentType(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }
    }

    private static String getDefaultPath() {
        String path = AppContextHolder.APP_CONTEXT.getFilesDir().getAbsolutePath() + "/BCM";
        File dir = new File(path);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return path;
    }

    /**
     * ，APP
     *
     * @param context Application Context
     */
    public static void initDownloadPath(Context context) {

        //todo wangshuhe
        DOWNLOAD_PATH = "";//AMELogin.INSTANCE.getAccountDir();
        AUDIO_DIRECTORY = DOWNLOAD_PATH + "/audio";
        THUMBNAIL_DIRECTORY = DOWNLOAD_PATH + "/thumbnail";
        DOCUMENT_DIRECTORY = DOWNLOAD_PATH + "/document";
        VIDEO_DIRECTORY = DOWNLOAD_PATH + "/video";
        MAP_DIRECTORY = DOWNLOAD_PATH + "/map";
        ENCRYPT_DIRECTORY = DOWNLOAD_PATH + "/encrypt";
        DECRYPT_DIRECTORY = DOWNLOAD_PATH + "/decrypt";
        TEMP_DIRECTORY = DOWNLOAD_PATH + "/temp";
        CHAT_FILE_DIRECTORY = DOWNLOAD_PATH + "/chat-files";

        File f = new File(AUDIO_DIRECTORY);
        if (!f.exists()) {
            f.mkdirs();
        }

        f = new File(DOCUMENT_DIRECTORY);
        if (!f.exists()) {
            f.mkdirs();
        }

        f = new File(THUMBNAIL_DIRECTORY);
        if (!f.exists()) {
            f.mkdirs();
        }

        f = new File(VIDEO_DIRECTORY);
        if (!f.exists()) {
            f.mkdirs();
        }

        f = new File(MAP_DIRECTORY);
        if (!f.exists()) {
            f.mkdirs();
        }

        f = new File(ENCRYPT_DIRECTORY);
        if (!f.exists()) {
            f.mkdirs();
        }

        f = new File(DECRYPT_DIRECTORY);
        if (!f.exists()) {
            f.mkdirs();
        }

        f = new File(TEMP_DIRECTORY);
        if (!f.exists()) {
            f.mkdirs();
        }
    }

    private static String getMimeType(Context context, String path) {
        try {
            Uri uri = Uri.fromFile(new File(path));
            String mimeType = getMimeType(context, uri);
            if (mimeType != null) {
                return mimeType;
            }

        } catch (Exception ex) {
            ALog.e(TAG, "getMimeType fail", ex);
        }
        return "application/octet-stream";
    }

    private static @Nullable
    String getMimeType(Context context, Uri uri) {
        if (uri == null) {
            return null;
        }
        String type = context.getContentResolver().getType(uri);
        if (type == null) {
            final String extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
        }

        return getCorrectedMimeType(type);
    }

    private static @Nullable
    String getCorrectedMimeType(@Nullable String mimeType) {
        if (mimeType == null) {
            return null;
        }

        switch (mimeType) {
            case "image/jpg":
                return MimeTypeMap.getSingleton().hasMimeType("image/jpeg")
                        ? "image/jpeg"
                        : mimeType;
            default:
                return mimeType;
        }
    }

    //，，
    public static class FileUploadResult {

        public FileUploadResult(boolean isSuccess, String location, String id) {
            this.isSuccess = isSuccess;
            this.location = location;
            this.id = id;
        }

        public final boolean isSuccess;
        public final String location;
        public final String id;
    }

    //
    public  interface MultiFileUploadCallback {

        void onFailed(Map<String, FileUploadResult> resultMap);

        void onSuccess(Map<String, FileUploadResult> resultMap);
    }

    public interface MultiStreamUploadCallback {
        void onFailed(Map<StreamUploadData, FileUploadResult> resultMap);

        void onSuccess(Map<StreamUploadData, FileUploadResult> resultMap);
    }


    /**
     * ，, map ，， url
     *  onSuccess ， onFailed 
     * 
     * @param context Context.
     * @param type Attachment type
     * @param filePaths Will be uploaded files paths.
     * @param callback Upload callback.
     */
    public static void uploadMultiFileToAws(@NonNull Context context, @NonNull AttachmentType type, final List<String> filePaths, final MultiFileUploadCallback callback) {
        final Map<String, FileUploadResult> failedResultMap = new HashMap<>();
        final Map<String, FileUploadResult> successResultMap = new HashMap<>();
        for (final String filePath : filePaths) {
            uploadAttachmentToAws(context, type, new File(filePath), new FileUploadCallback() {
                @Override
                public void onUploadSuccess(String url, String id) {
                    synchronized (TAG) {
                        successResultMap.put(filePath, new FileUploadResult(true, url, id));
                        if ((successResultMap.size() + failedResultMap.size()) == filePaths.size()) {
                            if (failedResultMap.size() == 0) {
                                callback.onSuccess(successResultMap);
                            } else {
                                failedResultMap.putAll(successResultMap);
                                callback.onFailed(failedResultMap);
                            }

                        }
                    }
                }

                @Override
                public void onUploadFailed(String filepath, String msg) {
                    ALog.w(TAG, msg);
                    synchronized (TAG) {
                        failedResultMap.put(filePath, new FileUploadResult(false, "", ""));
                        if ((failedResultMap.size() + successResultMap.size()) == filePaths.size()) {
                            failedResultMap.putAll(successResultMap);
                            callback.onFailed(failedResultMap);
                        }
                    }
                }
            });
        }
    }

    public static void uploadMultiStreamToAws(@NonNull AttachmentType type, final List<StreamUploadData> uploadDataList, final MultiStreamUploadCallback callback) {
        final Map<StreamUploadData, FileUploadResult> failedResultMap = new HashMap<>();
        final Map<StreamUploadData, FileUploadResult> successResultMap = new HashMap<>();
        for (final StreamUploadData data : uploadDataList) {
            uploadStreamToAws(type, data, new StreamUploadCallback() {
                @Override
                public void onUploadSuccess(String url, String id) {
                    synchronized (TAG) {
                        successResultMap.put(data, new FileUploadResult(true, url, id));
                        if ((successResultMap.size() + failedResultMap.size()) == uploadDataList.size()) {
                            if (failedResultMap.size() == 0) {
                                callback.onSuccess(successResultMap);
                            } else {
                                failedResultMap.putAll(successResultMap);
                                callback.onFailed(failedResultMap);
                            }

                        }
                    }
                }

                @Override
                public void onUploadFailed(StreamUploadData data, String msg) {
                    ALog.w(TAG, msg);
                    synchronized (TAG) {
                        failedResultMap.put(data, new FileUploadResult(false, "", ""));
                        if ((failedResultMap.size() + successResultMap.size()) == uploadDataList.size()) {
                            failedResultMap.putAll(successResultMap);
                            callback.onFailed(failedResultMap);
                        }
                    }
                }
            }, Callback.THREAD_CURRENT);
        }
    }

    /**
     * Upload attachment to AWS S3
     *
     * @param context Context
     * @param type Attachment type, one of private message(pmsg), group message(gmsg), profile(profile)
     * @param file File to upload
     * @param callback Upload callback
     */
    public static void uploadAttachmentToAws(@NonNull final Context context, @NonNull AttachmentType type,
                                             @NonNull final File file, @NonNull final FileUploadCallback callback) {
        uploadAttachmentToAws(context, type, file, callback, Callback.THREAD_CURRENT);
    }


    public static void uploadAttachmentToAws(@NonNull final Context context, @NonNull AttachmentType type,
                                             @NonNull final File file, @NonNull final FileUploadCallback callback, final int threadMode) {

        getAwsUploadInfo(type, new JsonDeserializeCallback<AwsUploadResEntity>() {
            @Override
            public void onError(Call call, Exception e, long id) {
                callback.onUploadFailed(file.getAbsolutePath(), e.getMessage());
            }

            @Override
            public void onResponse(final AwsUploadResEntity response, long id) {
                realUploadFileToAws(context, response.getPostUrl(), response.getFields(), file, new com.bcm.messenger.utility.bcmhttp.callback.FileUploadCallback<Void>() {
                    @Override
                    public int callInThreadMode() {
                        return threadMode;
                    }

                    @Override
                    public void onError(Call call, Exception e, long id) {
                        callback.onUploadFailed(file.getAbsolutePath(), e.getMessage());
                    }

                    @Override
                    public void onResponse(Void uploadResponse, long id) {
                        callback.onUploadSuccess(response.getDownloadUrl(), String.valueOf(id));
                    }

                    @Override
                    public void inProgress(int progress, long total, long id) {
                        callback.onProgressChange(progress);
                    }
                });
            }
        });
    }

    public static void uploadStreamToAws(@NonNull AttachmentType type, @NonNull final StreamUploadData data, @NonNull final StreamUploadCallback callback, final int threadMode) {

        getAwsUploadInfo(type, new JsonDeserializeCallback<AwsUploadResEntity>() {
            @Override
            public void onError(Call call, Exception e, long id) {
                callback.onUploadFailed(data, e.getMessage());
            }

            @Override
            public void onResponse(final AwsUploadResEntity response, long id) {
                realUploadFileToAws(response.getPostUrl(), response.getFields(), data, new com.bcm.messenger.utility.bcmhttp.callback.FileUploadCallback<Void>() {
                    @Override
                    public int callInThreadMode() {
                        return threadMode;
                    }

                    @Override
                    public void onError(Call call, Exception e, long id) {
                        callback.onUploadFailed(data, e.getMessage());
                    }

                    @Override
                    public void onResponse(Void uploadResponse, long id) {
                        callback.onUploadSuccess(response.downloadUrl, String.valueOf(id));
                    }

                    @Override
                    public void inProgress(int progress, long total, long id) {
                        callback.onProgressChange(progress);
                    }
                });
            }
        });
    }

    /**
     * （）
     * @param context
     * @param file
     * @param callback
     */
    public static void uploadGroupAvatar(@NonNull Context context, @NonNull final File file, @NonNull final FileUploadCallback callback) {
        uploadAttachmentToAws(context, AttachmentType.PROFILE, file, callback, Callback.THREAD_MAIN);
    }

    private static void getAwsUploadInfo(AttachmentType type, JsonDeserializeCallback<AwsUploadResEntity> callback) {
        int region = RedirectInterceptorHelper.INSTANCE.getImServerInterceptor().getCurrentServer().getArea();
        String body = new AwsUploadReqEntity(type.getType(), Integer.toString(region)).toJson();

        IMHttp.INSTANCE.postString()
                .url(BcmHttpApiHelper.INSTANCE.getApi(AWS_UPLOAD_INFO_PATH))
                .addHeader("content-type", "application/json")
                .content(body)
                .build()
                .writeTimeOut(BaseHttp.DEFAULT_MILLISECONDS)
                .readTimeOut(BaseHttp.DEFAULT_MILLISECONDS)
                .connTimeOut(BaseHttp.DEFAULT_MILLISECONDS)
                .enqueue(callback);
    }

    private static void realUploadFileToAws(Context context, String url, final List<AwsUploadResEntity.AwsUploadField> formFields, final File file, com.bcm.messenger.utility.bcmhttp.callback.FileUploadCallback<Void> callback) {
        FileCallBuilder.PostFormBuilder builder = FileHttp.INSTANCE.postForm();
        builder.url(url);
        for (AwsUploadResEntity.AwsUploadField field : formFields) {
            builder.addFormData(field.getKey(), field.getValue());
        }
        // AWS says file must be the last field
        builder.addFormFile("file", file.getName(), file, AmeFileUploader.getMimeType(context, file.getAbsolutePath()))
                .tag(new ProgressRequestTag(true, false))
                .build()
                .writeTimeOut(BaseHttp.DEFAULT_FILE_MILLISECONDS)
                .readTimeOut(BaseHttp.DEFAULT_FILE_MILLISECONDS)
                .connTimeOut(BaseHttp.DEFAULT_FILE_MILLISECONDS)
                .enqueue(callback);
    }

    private static void realUploadFileToAws(String url, final List<AwsUploadResEntity.AwsUploadField> formFields, final StreamUploadData data, com.bcm.messenger.utility.bcmhttp.callback.FileUploadCallback<Void> callback) {
        FileCallBuilder.PostFormBuilder builder = FileHttp.INSTANCE.postForm();
        builder.url(url);
        for (AwsUploadResEntity.AwsUploadField field : formFields) {
            builder.addFormData(field.getKey(), field.getValue());
        }
        // AWS says file must be the last field
        builder.addFormPart("file", data.getName(), new StreamRequestBody(data.getInputStream(), data.getMimeType(), data.getDataSize()))
                .tag(new ProgressRequestTag(true, false))
                .build()
                .writeTimeOut(BaseHttp.DEFAULT_FILE_MILLISECONDS)
                .readTimeOut(BaseHttp.DEFAULT_FILE_MILLISECONDS)
                .connTimeOut(BaseHttp.DEFAULT_FILE_MILLISECONDS)
                .enqueue(callback);
    }

    /**
     * （）
     * @param context
     * @param url
     * @param callback
     */
    public static void downloadFile(Context context, String url, FileDownCallback callback) {
        FileHttp.INSTANCE.get()
                .url(url)
                .tag(new ProgressRequestTag(false,true))
                .build()
                .writeTimeOut(BaseHttp.DEFAULT_FILE_MILLISECONDS)
                .readTimeOut(BaseHttp.DEFAULT_FILE_MILLISECONDS)
                .connTimeOut(BaseHttp.DEFAULT_FILE_MILLISECONDS)
                .enqueue(callback);
    }


    //，，，
    public static class FileUploadCallback {

        public void onUploadSuccess(String url, String id) {
        }

        public void onUploadFailed(String filepath, String msg) {

        }

        public void onProgressChange(float currentProgress) {
            //
        }
    }

    public static class StreamUploadCallback {
        public void onUploadSuccess(String url, String id) {
        }

        public void onUploadFailed(StreamUploadData data, String msg) {
        }

        public void onProgressChange(float currentProgress) {
        }
    }

    public class FileUploadEntity implements NotGuard {

        @SerializedName("location")
        public String location;

        @SerializedName("id")
        public String id;

    }

    public static class AwsUploadReqEntity implements NotGuard {
        private String type;
        private String region;

        public AwsUploadReqEntity(String type, String region) {
            this.type = type;
            this.region = region;
        }

        public String toJson() {
            return GsonUtils.INSTANCE.toJson(this);
        }
    }

    public static class AwsUploadResEntity implements NotGuard {
        private String postUrl;
        private String downloadUrl;
        private List<AwsUploadField> fields;

        public String getPostUrl() {
            return postUrl;
        }

        public String getDownloadUrl() {
            return downloadUrl;
        }

        public List<AwsUploadField> getFields() {
            return fields;
        }

        public class AwsUploadField implements NotGuard {
            private String key;
            private String value;

            public String getKey() {
                return key;
            }

            public String getValue() {
                return value;
            }
        }
    }
}
