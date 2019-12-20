/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.messages;

import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.internal.util.Util;

import java.io.InputStream;

/**
 * Represents a local SignalServiceAttachment to be sent.
 */
public class SignalServiceAttachmentStream extends SignalServiceAttachment {

  private final InputStream      inputStream;
  private final long             length;
  private final Optional<String> fileName;
  private final ProgressListener listener;
  private final Optional<byte[]> preview;
  private final boolean          voiceNote;
  private byte[] attachmentKey;
  private StreamUploadResult uploadResult;

  public SignalServiceAttachmentStream(InputStream inputStream, String contentType, long length, Optional<String> fileName, boolean voiceNote, String index, ProgressListener listener) {
    this(inputStream, contentType, length, fileName, voiceNote, Optional.<byte[]>absent(), index, listener);
  }

  public SignalServiceAttachmentStream(InputStream inputStream, String contentType, long length, Optional<String> fileName, boolean voiceNote, Optional<byte[]> preview, String index,ProgressListener listener) {
    super(contentType, index);
    this.inputStream = inputStream;
    this.length      = length;
    this.fileName    = fileName;
    this.listener    = listener;
    this.voiceNote   = voiceNote;
    this.preview     = preview;
    this.attachmentKey    = Util.getSecretBytes(64);
  }

  @Override
  public boolean isStream() {
    return true;
  }

  @Override
  public boolean isPointer() {
    return false;
  }

  public InputStream getInputStream() {
    return inputStream;
  }

  public long getLength() {
    return length;
  }

  public Optional<String> getFileName() {
    return fileName;
  }

  public ProgressListener getListener() {
    return listener;
  }

  public Optional<byte[]> getPreview() {
    return preview;
  }

  public boolean getVoiceNote() {
    return voiceNote;
  }

  public byte[] getAttachmentKey() {
    return attachmentKey;
  }

  public StreamUploadResult getUploadResult() {
    return uploadResult;
  }

  public Boolean isUploaded() {
    return null != uploadResult;
  }

  public void setUploadResult(Long attachmentId, String url, byte[] digest){
    if (digest != null){
      uploadResult = new StreamUploadResult();
      uploadResult.attachmentId = attachmentId;
      uploadResult.digest = digest;
      uploadResult.url = url;
    }
  }


  public class StreamUploadResult {
    public String url;
    public Long attachmentId;
    public byte[] digest;
  }
}
