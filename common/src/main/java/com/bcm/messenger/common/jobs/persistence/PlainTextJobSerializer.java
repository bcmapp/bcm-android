package com.bcm.messenger.common.jobs.persistence;

import org.whispersystems.jobqueue.EncryptionKeys;
import org.whispersystems.jobqueue.Job;
import org.whispersystems.jobqueue.persistence.JavaJobSerializer;
import org.whispersystems.jobqueue.persistence.JobSerializer;

import java.io.IOException;

public class PlainTextJobSerializer implements JobSerializer {

  private final JavaJobSerializer delegate;

  public PlainTextJobSerializer() {
    this.delegate = new JavaJobSerializer();
  }

  @Override
  public String serialize(Job job) throws IOException {
    return delegate.serialize(job);
  }

  @Override
  public Job deserialize(EncryptionKeys keys, boolean encrypted, String serialized) throws IOException {
      return delegate.deserialize(keys, encrypted, serialized);
  }
}
