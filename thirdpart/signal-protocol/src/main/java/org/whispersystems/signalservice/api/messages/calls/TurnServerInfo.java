package org.whispersystems.signalservice.api.messages.calls;

import com.google.gson.annotations.SerializedName;

import java.util.List;


public class TurnServerInfo {

  @SerializedName("username")
  private String username;

  @SerializedName("password")
  private String password;

  @SerializedName("urls")
  private List<String> urls;

  public String getUsername() {
    return username;
  }

  public String getPassword() {
    return password;
  }

  public List<String> getUrls() {
    return urls;
  }
}
