package com.bcm.messenger.common.crypto.encrypt;

import android.text.TextUtils;

import com.bcm.messenger.common.core.corebean.EncryptMessageBean;
import com.bcm.messenger.common.core.corebean.EncryptMessageBody;
import com.bcm.messenger.common.core.corebean.GroupKeyParam;
import com.bcm.messenger.common.utils.BCMPrivateKeyUtils;
import com.bcm.messenger.utility.Base64;
import com.bcm.messenger.utility.EncryptUtils;
import com.bcm.messenger.utility.logger.ALog;
import com.google.gson.Gson;

import org.whispersystems.libsignal.kdf.HKDF;

import java.io.IOException;

import kotlin.Pair;
import kotlin.Triple;

/**
 *
 */
public class GroupMessageEncryptUtils {

    public static final String CBC_MODE = "AES/CBC/PKCS7Padding";
    public static final int MESSAGE_ENCRYPT_VERSION = 1;

    public static final int ENCRYPT_OR_DECRYPT_ERROR_CODE = 500;
    public static final int PASSWORD_NOT_FOUND_CODE = 404;
    public static final int SUCCESS_PLAIN_LEVEL_CODE = 200;
    public static final int SUCCESS_SUB_LEVEL_CODE = 201;
    public static final int SUCCESS_CHAT_LEVEL_CODE = 202;

    /**
     * 
     *
     * @param plainMessageBody
     * @param encryptKeySpec
     * @return
     */
    public static Triple<Boolean, String, Integer> encryptMessageProcess(String plainMessageBody, GroupKeyParam encryptKeySpec) {
        if (encryptKeySpec == null) {
            // ，
            return new Triple<>(false, plainMessageBody, PASSWORD_NOT_FOUND_CODE);
        }
        try {

            // password 
            Pair<String, String> encryptMessageBody = encryptMessage(plainMessageBody, encryptKeySpec.getKey());
            String encryptMessage = encapsulateEncryptMessage(encryptMessageBody, encryptKeySpec );
            return new Triple<>(true, encryptMessage, SUCCESS_CHAT_LEVEL_CODE);
        } catch (Exception e) {
            //
        }
        return new Triple<>(false, plainMessageBody, ENCRYPT_OR_DECRYPT_ERROR_CODE);

    }

    /**
     * 
     *
     * @return
     */
    public static String decryptMessageProcess(GroupKeyParam keyParam, EncryptMessageBean bean) {
        try {
            if (bean.getHeader() == null || bean.getBody() == null || bean.getVersion() < 1) {
                ALog.e("GroupMessageEncryptUtils", "decryptMessageProcess failed 1");
                return null;
            }

            if (bean.getVersion() != MESSAGE_ENCRYPT_VERSION) {
                ALog.e("GroupMessageEncryptUtils", "decryptMessageProcess failed 2");
                return null;
            }

            if (keyParam == null || keyParam.getKey() == null) {
                ALog.e("GroupMessageEncryptUtils", "decryptMessageProcess failed 3");
                return null;
            }
           return decryptMessage(bean.getHeader().getHash_data(), bean.getBody(), keyParam.getKey());
        } catch (Throwable e) {
            ALog.e("GroupMessageEncryptUtils", "decrypt failed", e);
        }

        return null;
    }


    /**
     * ，，header
     *
     * @param encryptMessageBody
     * @return
     */
    private static String encapsulateEncryptMessage(Pair<String, String> encryptMessageBody, GroupKeyParam keyParam) {
        EncryptMessageBean encryptMessageBean = new EncryptMessageBean();
        encryptMessageBean.setVersion(MESSAGE_ENCRYPT_VERSION);
        encryptMessageBean.setKeyVersion(keyParam.getKeyVersion());
        encryptMessageBean.setHeader(new EncryptMessageBean.HeaderBean());
        encryptMessageBean.getHeader().setHash_data(encryptMessageBody.getSecond());
        encryptMessageBean.getHeader().setEncryption_level(EncryptMessageBean.ENCRYPT_CHAT_LEVEL);
        encryptMessageBean.setBody(encryptMessageBody.getFirst());
        return new Gson().toJson(encryptMessageBean);
    }


    /**
     * 
     *
     * @param encapsulatedMessage
     * @return
     */
    public static EncryptMessageBean decapsulateMessage(String encapsulatedMessage) {
        try {
            EncryptMessageBean bean = new Gson().fromJson(encapsulatedMessage, EncryptMessageBean.class);
            if (bean != null && bean.getBody() == null) {
                ALog.e("GroupMessageEncryptUtils", "EncryptMessageBean body is null");
                return null;
            }
            return bean;
        } catch (Throwable e) {
            ALog.e("GroupMessageEncryptUtils", "EncryptMessageBean bean parse failed");
        }
        return  null;
    }

    /**
     * 
     *
     * @param plainText
     * @param groupPassword
     * @return
     */
    private static Pair<String, String> encryptMessage(String plainText, byte[] groupPassword) {
        byte[] random = BCMPrivateKeyUtils.INSTANCE.getSecretBytes(64);
        byte[] oneTimePassword = EncryptUtils.encryptSHA512(byteMerger(groupPassword, random));
        byte[] aesKey256 = new byte[32];
        byte[] iv = new byte[16];
        System.arraycopy(oneTimePassword, 0, aesKey256, 0, 32);
        System.arraycopy(oneTimePassword, 48, iv, 0, 16);
        Gson gson = new Gson();
        EncryptMessageBody encryptMessageBody = new EncryptMessageBody();
        encryptMessageBody.setPlainText(plainText);
        encryptMessageBody.setSign(Base64.encodeBytes(EncryptUtils.encryptSHA512(plainText.getBytes())));
        String bodyString = gson.toJson(encryptMessageBody);

        return new Pair<>(Base64.encodeBytes(EncryptUtils.encryptAES(bodyString.getBytes(), aesKey256, CBC_MODE, iv)), Base64.encodeBytes(random));
    }


    /**
     * @param digest
     * @param encryptMessageBody
     * @param groupPassword
     * @return
     * @throws IOException
     */
    private static String decryptMessage(String digest, String encryptMessageBody, byte[] groupPassword) throws IOException {
        // onetimePassword
        byte[] random = Base64.decode(digest);
        byte[] oneTimePassword = EncryptUtils.encryptSHA512(byteMerger(groupPassword, random));
        byte[] aesKey256 = new byte[32];
        byte[] iv = new byte[16];
        System.arraycopy(oneTimePassword, 0, aesKey256, 0, 32);
        System.arraycopy(oneTimePassword, 48, iv, 0, 16);

        byte[] messageBytes =  Base64.decode(encryptMessageBody);
        byte[] messageDecrypt = EncryptUtils.decryptAES(messageBytes, aesKey256, CBC_MODE, iv);
        if (null == messageDecrypt) {
            return null;
        }
        String bodyString = new String(messageDecrypt);
        Gson gson = new Gson();
        EncryptMessageBody messageBody = gson.fromJson(bodyString, EncryptMessageBody.class);
        return messageBody.getPlainText();
    }

    /**
     *  ByteArray
     *
     * @param byte_1
     * @param byte_2
     * @return
     */
    public static byte[] byteMerger(byte[] byte_1, byte[] byte_2) {
        byte[] byte_3 = new byte[byte_1.length + byte_2.length];
        System.arraycopy(byte_1, 0, byte_3, 0, byte_1.length);
        System.arraycopy(byte_2, 0, byte_3, byte_1.length, byte_2.length);
        return byte_3;
    }


    public static byte[] decodeGroupPassword(String password) throws IOException {
        return Base64.decode(password);
    }


    /**
     * chat key  channelKey
     *
     * @param password
     * @return
     */
    public static String generateChannelKey(String password) {
        try {
            if (TextUtils.isEmpty(password)) {
                return "";
            }
            return Base64.encodeBytes(HKDF.createFor(3).deriveSecrets(Base64.decode(password), "BCMCHANNEL".getBytes(), 16));
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return "";
    }


}
