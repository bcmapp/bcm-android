package com.bcm.messenger.common.exception;

public class RetryLaterException extends Exception {
  public RetryLaterException(Exception e) {
    super(e);
  }
}
