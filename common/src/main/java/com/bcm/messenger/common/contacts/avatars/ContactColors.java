package com.bcm.messenger.common.contacts.avatars;

import androidx.annotation.NonNull;

import com.bcm.messenger.common.color.MaterialColor;
import com.bcm.messenger.common.color.MaterialColors;

public class ContactColors {

  public static final MaterialColor UNKNOWN_COLOR = MaterialColor.GREY;

  public static MaterialColor generateFor(@NonNull String name) {
    return MaterialColors.CONVERSATION_PALETTE.get(Math.abs(name.hashCode()) % MaterialColors.CONVERSATION_PALETTE.size());
  }

}
