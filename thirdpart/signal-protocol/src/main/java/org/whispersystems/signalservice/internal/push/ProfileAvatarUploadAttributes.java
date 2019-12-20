package org.whispersystems.signalservice.internal.push;

public class ProfileAvatarUploadAttributes {
  private String url;
  private String key;
  private String credential;
  private String acl;
  private String algorithm;
  private String date;
  private String policy;
  private String signature;

  public ProfileAvatarUploadAttributes() {}

  public String getUrl() {
    return url;
  }

  public String getKey() {
    return key;
  }

  public String getCredential() {
    return credential;
  }

  public String getAcl() {
    return acl;
  }

  public String getAlgorithm() {
    return algorithm;
  }

  public String getDate() {
    return date;
  }

  public String getPolicy() {
    return policy;
  }

  public String getSignature() {
    return signature;
  }
}
