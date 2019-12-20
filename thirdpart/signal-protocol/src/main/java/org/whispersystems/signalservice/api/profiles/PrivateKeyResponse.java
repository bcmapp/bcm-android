package org.whispersystems.signalservice.api.profiles;

public class PrivateKeyResponse {

    private boolean isExists;
    private String privateKey = "";

    public PrivateKeyResponse() {
    }


    public boolean isExists() {
        return isExists;
    }

    public void setExists(boolean exists) {
        isExists = exists;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }


}
