package com.bcm.messenger.common.profiles;


import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bcm.messenger.common.core.Address;
import com.bcm.messenger.common.provider.AMESelfData;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class AvatarHelper {

  private static final String AVATAR_DIRECTORY = "avatars";

  public static InputStream getInputStreamFor(@NonNull Context context, @NonNull Address address)
      throws IOException
  {
    return new FileInputStream(getAvatarFile(context, address));
  }

  public static void delete(@NonNull Context context, @NonNull Address address) {
    getAvatarFile(context, address).delete();
  }

  public static @NonNull File getAvatarFile(@NonNull Context context, @NonNull Address address) {
    File avatarDirectory = new File(AMESelfData.INSTANCE.getAccountDir(), AVATAR_DIRECTORY);
    avatarDirectory.mkdirs();

    return new File(avatarDirectory, new File(address.serialize()).getName());
  }

  public static void setAvatar(@NonNull Context context, @NonNull Address address, @Nullable byte[] data)
    throws IOException
  {
    if (data == null)  {
      delete(context, address);
    } else {
      FileOutputStream out = new FileOutputStream(getAvatarFile(context, address));
      out.write(data);
      out.close();
    }
  }

}
