package com.bcm.messenger.common.jobs.persistence;

import com.bcm.messenger.common.crypto.MasterCipher;
import com.bcm.messenger.common.crypto.MasterSecret;
import com.bcm.messenger.utility.ParcelUtil;
import org.whispersystems.jobqueue.EncryptionKeys;
import org.whispersystems.jobqueue.Job;
import org.whispersystems.jobqueue.persistence.JavaJobSerializer;
import org.whispersystems.jobqueue.persistence.JobSerializer;
import org.whispersystems.libsignal.InvalidMessageException;

import java.io.IOException;

public class EncryptingJobSerializer implements JobSerializer {

  private final JavaJobSerializer delegate;

  public EncryptingJobSerializer() {
    this.delegate = new JavaJobSerializer();
  }

  @Override
  public String serialize(Job job) throws IOException {
    String plaintext = delegate.serialize(job);

    if (job.getEncryptionKeys() != null) {
      MasterSecret masterSecret = ParcelUtil.deserialize(job.getEncryptionKeys().getEncoded(),
                                                         MasterSecret.CREATOR);
      MasterCipher masterCipher = new MasterCipher(masterSecret);

      return masterCipher.encryptBody(plaintext);
    } else {
      return plaintext;
    }
  }

  @Override
  public Job deserialize(EncryptionKeys keys, boolean encrypted, String serialized) throws IOException {
    try {
      String plaintext;

      if (encrypted) {
        MasterSecret masterSecret = ParcelUtil.deserialize(keys.getEncoded(), MasterSecret.CREATOR);
        MasterCipher masterCipher = new MasterCipher(masterSecret);
        plaintext = masterCipher.decryptBody(serialized);
      } else {
        plaintext = serialized;
      }

      return delegate.deserialize(keys, encrypted, plaintext);
    } catch (InvalidMessageException e) {
      throw new IOException(e);
    }
  }
}
