package org.whispersystems.signalservice.api.messages.calls;


import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

public class OfferMessage {

  private final long   id;
  private final String description;
  private final SignalServiceProtos.CallMessage.Offer.CallType type;

  public OfferMessage(long id, String description, SignalServiceProtos.CallMessage.Offer.CallType type) {
    this.id          = id;
    this.description = description;
    this.type = type;
  }

  public String getDescription() {
    return description;
  }

  public long getId() {
    return id;
  }

  public SignalServiceProtos.CallMessage.Offer.CallType callType() {
    return type;
  }
}
