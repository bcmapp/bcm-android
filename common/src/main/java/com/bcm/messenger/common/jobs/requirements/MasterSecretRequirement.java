package com.bcm.messenger.common.jobs.requirements;

import android.content.Context;

import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils;

import org.whispersystems.jobqueue.dependencies.ContextDependent;
import org.whispersystems.jobqueue.requirements.Requirement;

public class MasterSecretRequirement implements Requirement, ContextDependent {

  private transient Context context;

  public MasterSecretRequirement(Context context) {
    this.context = context;
  }

  @Override
  public boolean isPresent() {
    return BCMEncryptUtils.INSTANCE.getMasterSecret(context) != null;
  }

  @Override
  public void setContext(Context context) {
    this.context = context;
  }
}
