package com.bcm.messenger.utility;

import android.text.TextUtils;
import android.util.Base64;

import org.spongycastle.crypto.digests.RIPEMD160Digest;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.bcm.messenger.utility.logger.ALog;

/**
 * ling created in 2018/5/4
 **/
public class EncryptUtils {

    public static final String MODE_AES = "AES/CBC/PKCS7Padding";

    private EncryptUtils() {
        throw new UnsupportedOperationException("u can't instantiate me...");
    }

    ///////////////////////////////////////////////////////////////////////////
    // hash encryption
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Calculates RIPEMD160(SHA256(input)). This is used in Address calculations.
     */
    public static byte[] sha256hash160(byte[] input) {

        byte[] sha256 = computeSHA256(input);
        RIPEMD160Digest digest = new RIPEMD160Digest();
        digest.update(sha256, 0, sha256.length);
        byte[] out = new byte[20];
        digest.doFinal(out, 0);
        return out;
    }

    /**
     * Return the hex string of MD5 encryption.
     *
     * @param data The data.
     * @return the hex string of MD5 encryption
     */
    public static String encryptMD5ToString(final String data) {
        if (data == null || data.length() == 0) {
            return "";
        }

        return encryptMD5ToString(data.getBytes());
    }

    /**
     * Return the hex string of MD5 encryption.
     *
     * @param data The data.
     * @return the hex string of MD5 encryption
     */
    private static String encryptMD5ToString(final byte[] data) {
        return HexUtil.toString(encryptMD5(data)).toUpperCase();
    }


    /**
     * Return the bytes of MD5 encryption.
     *
     * @param data The data.
     * @return the bytes of MD5 encryption
     */
    public static byte[] encryptMD5(final byte[] data) {
        return hashTemplate(data, "MD5");
    }



    /**
     * Return the bytes of file's MD5 encryption.
     *
     * @param file The file.
     * @return the bytes of file's MD5 encryption
     */
    public static byte[] encryptMD5File(final File file) {
        if (file == null) {
            return null;
        }
        FileInputStream fis = null;
        DigestInputStream digestInputStream;
        try {
            fis = new FileInputStream(file);
            MessageDigest md = MessageDigest.getInstance("MD5");
            digestInputStream = new DigestInputStream(fis, md);
            byte[] buffer = new byte[256 * 1024];
            while (true) {
                if (!(digestInputStream.read(buffer) > 0)) {
                    break;
                }
            }
            md = digestInputStream.getMessageDigest();
            return md.digest();
        } catch (NoSuchAlgorithmException | IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            try {
                if (fis != null) {
                    fis.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Return the hex string of SHA1 encryption.
     *
     * @param data The data.
     * @return the hex string of SHA1 encryption
     */
    public static String encryptSHA1ToString(final String data) {
        if (data == null || data.length() == 0) {
            return "";
        }

        return encryptSHA1ToString(data.getBytes());
    }

    /**
     * Return the bytes of SHA1 encryption.
     *
     * @param data The data.
     * @return the bytes of SHA1 encryption
     */
    public static Long byteArrayToLong(final byte[] data) {
         byte[] bytes = hashTemplate(data, "SHA1");
         if (bytes.length >= 8) {
             int offset = 0;
             return ((bytes[offset]     & 0xffL) << 56) |
                     ((bytes[offset + 1] & 0xffL) << 48) |
                     ((bytes[offset + 2] & 0xffL) << 40) |
                     ((bytes[offset + 3] & 0xffL) << 32) |
                     ((bytes[offset + 4] & 0xffL) << 24) |
                     ((bytes[offset + 5] & 0xffL) << 16) |
                     ((bytes[offset + 6] & 0xffL) << 8)  |
                     ((bytes[offset + 7] & 0xffL));
         }
         return 0L;

    }

    /**
     * Return the hex string of SHA1 encryption.
     *
     * @param data The data.
     * @return the hex string of SHA1 encryption
     */
    private static String encryptSHA1ToString(final byte[] data) {
        return HexUtil.toString(encryptSHA1(data));
    }

    /**
     * Return the bytes of SHA1 encryption.
     *
     * @param data The data.
     * @return the bytes of SHA1 encryption
     */
    private static byte[] encryptSHA1(final byte[] data) {
        return hashTemplate(data, "SHA1");
    }

    /**
     * Return the bytes of SHA256 encryption.
     *
     * @param data The data.
     * @return the bytes of SHA256 encryption
     */
    public static byte[] computeSHA256(final byte[] data) {
        return hashTemplate(data, "SHA256");
    }



    /**
     * Return the bytes of SHA512 encryption.
     *
     * @param data The data.
     * @return the bytes of SHA512 encryption
     */
    public static byte[] encryptSHA512(final byte[] data) {
        return hashTemplate(data, "SHA512");
    }

    /**
     * Return the bytes of SHA512 encryption.
     *
     * @param fis The data.
     * @return the bytes of SHA512 encryption
     */
    public static byte[] encryptSHA512(InputStream fis) {
        return hashFile(fis, "SHA512");
    }

    /**
     * Return the bytes of hash encryption.
     *
     * @param data      The data.
     * @param algorithm The name of hash encryption.
     * @return the bytes of hash encryption
     */
    private static byte[] hashTemplate(final byte[] data, final String algorithm) {
        if (data == null || data.length <= 0) {
            return null;
        }

        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            md.update(data);
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static byte[] hashFile(InputStream fis, String hashType) {
        try {
            //拿到一个MD5转换器,如果想使用SHA-1或SHA-256，则传入SHA-1,SHA-256
            MessageDigest md = MessageDigest.getInstance(hashType);

            //分多次将一个文件读入，对于大型文件而言，比较推荐这种方式，占用内存比较少。
            byte[] buffer = new byte[1024];
            int length = -1;
            while ((length = fis.read(buffer, 0, 1024)) != -1) {
                md.update(buffer, 0, length);
            }
            fis.close();
            //转换并返回包含16个元素字节数组,返回数值范围为-128到127
            return md.digest();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Return the bytes of AES encryption.
     *
     * @param data           The data.
     * @param key            The key.
     * @param transformation The name of the transformation, e.g., <i>DES/CBC/PKCS5Padding</i>.
     * @param iv             The buffer with the IV. The contents of the
     *                       buffer are copied to protect against subsequent modification.
     * @return the bytes of AES encryption
     */
    public static byte[] encryptAES(final byte[] data,
                                    final byte[] key,
                                    final String transformation,
                                    final byte[] iv) {
        return symmetricTemplate(data, key, "AES", transformation, iv, true);
    }


    /**
     * Return the bytes of AES decryption.
     *
     * @param data           The data.
     * @param key            The key.
     * @param transformation The name of the transformation, e.g., <i>DES/CBC/PKCS5Padding</i>.
     * @param iv             The buffer with the IV. The contents of the
     *                       buffer are copied to protect against subsequent modification.
     * @return the bytes of AES decryption
     */
    public static byte[] decryptAES(final byte[] data,
                                    final byte[] key,
                                    final String transformation,
                                    final byte[] iv) {
        return symmetricTemplate(data, key, "AES", transformation, iv, false);
    }

    /**
     * Return the bytes of symmetric encryption or decryption.
     *
     * @param data           The data.
     * @param key            The key.
     * @param algorithm      The name of algorithm.
     * @param transformation The name of the transformation, e.g., <i>DES/CBC/PKCS5Padding</i>.
     * @param isEncrypt      True to encrypt, false otherwise.
     * @return the bytes of symmetric encryption or decryption
     */
    private static byte[] symmetricTemplate(final byte[] data,
                                            final byte[] key,
                                            final String algorithm,
                                            final String transformation,
                                            final byte[] iv,
                                            final boolean isEncrypt) {
        if (data == null || data.length == 0 || key == null || key.length == 0) {
            return null;
        }

        try {
            SecretKeySpec keySpec = new SecretKeySpec(key, algorithm);
            Cipher cipher = Cipher.getInstance(transformation);
            if (iv == null || iv.length == 0) {
                cipher.init(isEncrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, keySpec);
            } else {
                AlgorithmParameterSpec params = new IvParameterSpec(iv);
                cipher.init(isEncrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, keySpec, params);
            }
            return cipher.doFinal(data);
        } catch (Throwable e) {
            ALog.e("EncryptUtils", "symmetricTemplate", e);
            return null;
        }
    }


    public static byte[] base64Encode(final byte[] input) {
        return Base64.encode(input, Base64.NO_WRAP);
    }

    public static byte[] base64Decode(final byte[] input) {
        try {
            return Base64.decode(input, Base64.NO_WRAP);
        } catch (Exception e) {
            ALog.e("base64Decode", "decode failed with no_wrap", e);
            return com.bcm.messenger.utility.Base64.decode(input);
        }
    }

    private static boolean isSpace(final String s) {
        if (s == null) {
            return true;
        }

        for (int i = 0, len = s.length(); i < len; ++i) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static String getSecret(int size) {
        byte[] secret = getSecretBytes(size);
        return com.bcm.messenger.utility.Base64.encodeBytes(secret);
    }

    public static String getSecretHex(int size){
        byte[] secret = getSecretBytes(size);
        return HexUtil.toString(secret);
    }

    public static byte[] getSecretBytes(int size) {
        byte[] secret = new byte[size];
        getSecureRandom().nextBytes(secret);
        return secret;
    }

    public static SecureRandom getSecureRandom() {
        try {
            return SecureRandom.getInstance("SHA1PRNG");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }


    public static String aes256EncryptAndBase64(String body, byte[] key) {
        if (null == key || key.length == 0 || TextUtils.isEmpty(body)) {
            ALog.e("aes256EncryptAndBase64",  "invalid param");
            return "";
        }

        byte[] rootKey = EncryptUtils.encryptSHA512(key);

        byte[] result = aes256Encrypt(body.getBytes(), rootKey);

        if (null == result) {
            return "";
        } else  {
            return new String(base64Encode(result), StandardCharsets.UTF_8);
        }
    }


    public static String aes256DecryptAndBase64(String body, byte[] key) {
        if (null == key || key.length == 0 || TextUtils.isEmpty(body)) {
            ALog.e("aes256DecryptAndBase64",  "invalid param");
            return "";
        }

        byte[] rootKey = EncryptUtils.encryptSHA512(key);
        byte[] bytes = base64Decode(body.getBytes());
        byte[] result = aes256Decrypt(bytes, rootKey);

        if (null == result) {
            return "";
        } else  {
            return new String(result, StandardCharsets.UTF_8);
        }
    }

    public static byte[] aes256Encrypt(byte[] body, byte[] key) {
        if (null == key || key.length < 48 || null == body || body.length == 0) {
            ALog.e("aes256Encrypt",  "invalid param");
            return null;
        }

        byte[] aesKey256 = new byte[32];
        byte[] iv = new byte[16];
        System.arraycopy(key, 0, aesKey256, 0, 32);
        System.arraycopy(key, key.length-16, iv, 0, 16);

        try {
           return EncryptUtils.encryptAES(body, aesKey256, "AES/CBC/PKCS7Padding", iv);
        } catch (Exception e) {
            ALog.e("aes256Encrypt", e);
        }

        return null;
    }


    public static byte[] aes256Decrypt(byte[] body, byte[] key) {
        if (null == key || key.length < 48 || null == body || body.length == 0) {
            ALog.e("aes256Decrypt",  "invalid param");
            return null;
        }

        byte[] aesKey256 = new byte[32];
        byte[] iv = new byte[16];
        System.arraycopy(key, 0, aesKey256, 0, 32);
        System.arraycopy(key, key.length-16, iv, 0, 16);

        try {
            return decryptAES(body, aesKey256, "AES/CBC/PKCS7Padding", iv);
        } catch (Exception e) {
            ALog.e("aes256Decrypt", e);
        }

        return null;
    }
}
