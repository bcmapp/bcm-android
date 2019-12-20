#include "p.h"
#include <string.h>
#include <stdio.h>

const int GET_SIGNING_CERTIFICATES = 0x08000000;
const int GET_SIGNATURES          = 0x00000040;
const char* RELEASE_SIGN = "C9DF435FFC1C366083B984A1E780A531F31A89E4E2C5355A4108AF320180E9FF";
const char* DEV_SIGN = "D6C3EF17B995F567CC55381AD31E5B56D9B761FBED75C957E0E049337678B1EB";

static bool hasTest = false;
static bool hasRepackaged = false;


static jbyteArray signatureText(JNIEnv *env, jobjectArray signaturesArray);
static jstring hash(JNIEnv *env, jbyteArray byteArray);
static void notOfficialPackage(JNIEnv *env);

JNIEXPORT jstring JNICALL Java_com_bcm_messenger_common_p_a
        (JNIEnv *env, jclass jclazz, jobject contextObject){

    if(hasTest) {
        if(hasRepackaged) {
            notOfficialPackage(env);
        }
        return env->NewStringUTF("");
    }

    hasTest = true;

    jclass native_class = env->GetObjectClass(contextObject);
    jmethodID getPackageManagerFun = env->GetMethodID(native_class, "getPackageManager", "()Landroid/content/pm/PackageManager;");
    jobject packageMgr = env->CallObjectMethod(contextObject, getPackageManagerFun);
    jclass packageMgrClass = env->GetObjectClass(packageMgr);

    jmethodID getPackageInfoFun = env->GetMethodID(packageMgrClass, "getPackageInfo","(Ljava/lang/String;I)Landroid/content/pm/PackageInfo;");
    jclass contextClass = env->GetObjectClass(contextObject);
    jmethodID getPackageNameFun = env->GetMethodID(contextClass, "getPackageName", "()Ljava/lang/String;");
    jstring packageName = static_cast<jstring>(env->CallObjectMethod(contextObject, getPackageNameFun));

    jobject packageInfo = env->CallObjectMethod(packageMgr, getPackageInfoFun, packageName, GET_SIGNATURES);
    jclass packageInfoClass = env->GetObjectClass(packageInfo);

    jfieldID signatureField = env->GetFieldID(packageInfoClass, "signatures", "[Landroid/content/pm/Signature;");
    jobjectArray signaturesArray = (jobjectArray)(env->GetObjectField(packageInfo, signatureField));

    jbyteArray array = signatureText(env, signaturesArray);

    jstring h = hash(env, array);
    if(env->GetArrayLength(array) > 1) {
        const char *curHash = (char*)env->GetStringUTFChars(h, 0);

        int len = strlen(RELEASE_SIGN);

        if(strlen(curHash) == len) {
            if(strncmp(curHash, RELEASE_SIGN, len) != 0 && strncmp(curHash, DEV_SIGN, len) != 0) {
                notOfficialPackage(env);
            }
        }
        env->ReleaseStringUTFChars(h, curHash);
    }
     return h;
}


JNIEXPORT jstring JNICALL Java_com_bcm_messenger_common_p_b
        (JNIEnv *env, jclass jclazz, jobject contextObject){

    if(hasTest) {
        if(hasRepackaged) {
            notOfficialPackage(env);
        }
        return env->NewStringUTF("");
    }

    hasTest = true;

    jclass native_class = env->GetObjectClass(contextObject);
    jmethodID getPackageManagerFun = env->GetMethodID(native_class, "getPackageManager", "()Landroid/content/pm/PackageManager;");
    jobject packageMgr = env->CallObjectMethod(contextObject, getPackageManagerFun);
    jclass packageMgrClass = env->GetObjectClass(packageMgr);

    jmethodID getPackageInfoFun = env->GetMethodID(packageMgrClass, "getPackageInfo","(Ljava/lang/String;I)Landroid/content/pm/PackageInfo;");
    jclass contextClass = env->GetObjectClass(contextObject);
    jmethodID getPackageNameFun = env->GetMethodID(contextClass, "getPackageName", "()Ljava/lang/String;");
    jstring packageName = static_cast<jstring>(env->CallObjectMethod(contextObject, getPackageNameFun));

    jobject packageInfo = env->CallObjectMethod(packageMgr, getPackageInfoFun, packageName, GET_SIGNING_CERTIFICATES);
    jclass packageInfoClass = env->GetObjectClass(packageInfo);
    jfieldID signingInfoField = env->GetFieldID(packageInfoClass, "signingInfo", "Landroid/content/pm/SigningInfo;");
    jobject signingInfo = env->GetObjectField(packageInfo, signingInfoField);

    jclass signingInfoClass = env->GetObjectClass(signingInfo);

    jmethodID getApkContentsSignersFun = env->GetMethodID(signingInfoClass, "getApkContentsSigners", "()[Landroid/content/pm/Signature;");
    jobjectArray signaturesArray =  (jobjectArray)env->CallObjectMethod(signingInfo, getApkContentsSignersFun);

    jbyteArray array = signatureText(env, signaturesArray);

    jstring h = hash(env, array);
    if(env->GetArrayLength(array) > 1) {
        const char *curHash = (char*)env->GetStringUTFChars(h, 0);

        int len = strlen(RELEASE_SIGN);

        if(strlen(curHash) == len) {
            if(strncmp(curHash, RELEASE_SIGN, len) != 0 && strncmp(curHash, DEV_SIGN, len) != 0) {
                notOfficialPackage(env);
            }
        }
        env->ReleaseStringUTFChars(h, curHash);
    }
    return h;
}


static jbyteArray signatureText(JNIEnv *env, jobjectArray signaturesArray) {
    jsize size = env->GetArrayLength(signaturesArray);
    if(size > 0) {
        jobject signature = env->GetObjectArrayElement(signaturesArray, 0);
        jclass signatureClass = env->GetObjectClass(signature);
        jmethodID toCharsStringFun = env->GetMethodID(signatureClass, "toByteArray", "()[B");
        return static_cast<jbyteArray>(env->CallObjectMethod(signature, toCharsStringFun));
    }
    return env->NewByteArray(1);
}

static jstring hash(JNIEnv *env, jbyteArray byteArray) {
    if(env->GetArrayLength(byteArray) <= 1) {
        return env->NewStringUTF("");
    }
    jclass pClass = env->FindClass("com/bcm/messenger/common/p");
    jmethodID constructor = env->GetMethodID(pClass, "<init>", "()V");
    jobject p = env->NewObject(pClass, constructor);

    jmethodID hashFun = env->GetMethodID(pClass, "f", "([B)Ljava/lang/String;");
    return static_cast<jstring>(env->CallObjectMethod(p, hashFun, byteArray));
}

static void notOfficialPackage(JNIEnv *env) {
    hasRepackaged = true;

    jclass pClass = env->FindClass("com/bcm/messenger/common/p");
    jmethodID constructor = env->GetMethodID(pClass, "<init>", "()V");
    jobject p = env->NewObject(pClass, constructor);

    /*
    jmethodID setFlag = env->GetMethodID(pClass, "e", "(Ljava/lang/String;Ljava/lang/String;)V");

    jstring key = env->NewStringUTF("init");
    jstring value = env->NewStringUTF("unsupport");
    env->CallVoidMethod(p, setFlag, key, value);
    */

    jmethodID toast = env->GetMethodID(pClass, "j", "(Ljava/lang/String;)V");
    jstring text = env->NewStringUTF("Warning: The BCM installation package has been tampered.");
    env->CallVoidMethod(p, toast, text);
}