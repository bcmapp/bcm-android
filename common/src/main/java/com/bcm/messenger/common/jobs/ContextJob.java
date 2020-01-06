package com.bcm.messenger.common.jobs;

import android.content.Context;

import com.bcm.messenger.common.AccountContext;

import org.whispersystems.jobqueue.Job;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.jobqueue.dependencies.ContextDependent;

public abstract class ContextJob extends Job implements ContextDependent {

  protected transient Context context;
  protected final AccountContext accountContext;

  protected ContextJob(Context context, AccountContext accountContext, JobParameters parameters) {
    super(parameters);
    this.context = context;
    this.accountContext = accountContext;
  }

  public void setContext(Context context) {
    this.context = context;
  }

  protected Context getContext() {
    return context;
  }
}
