package com.bcm.messenger.common.jobs;

import android.content.Context;
import android.text.TextUtils;

import com.bcm.messenger.common.database.repositories.PrivateChatRepo;
import com.bcm.messenger.common.database.repositories.Repository;

import com.bcm.messenger.common.crypto.AsymmetricMasterCipher;
import com.bcm.messenger.common.crypto.AsymmetricMasterSecret;
import com.bcm.messenger.common.crypto.MasterSecret;
import com.bcm.messenger.common.crypto.MasterSecretUtil;
import com.bcm.messenger.common.database.model.MessageRecord;
import com.bcm.messenger.common.database.model.SmsMessageRecord;
import com.bcm.messenger.common.jobs.requirements.MasterSecretRequirement;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.libsignal.InvalidMessageException;

import java.io.IOException;

public class MasterSecretDecryptJob extends MasterSecretJob {

  private static final long   serialVersionUID = 1L;
  private static final String TAG              = MasterSecretDecryptJob.class.getSimpleName();

  public MasterSecretDecryptJob(Context context) {
    super(context, JobParameters.newBuilder()
                                .withRequirement(new MasterSecretRequirement(context))
                                .create());
  }

  @Override
  public void onRun(MasterSecret masterSecret) {
//    EncryptingSmsDatabase smsDatabase = DatabaseFactory.getEncryptingSmsDatabase(context);
//    SmsDatabase.Reader    smsReader   = smsDatabase.getDecryptInProgressMessages(masterSecret);

    PrivateChatRepo chatRepo = Repository.getChatRepo();

    SmsMessageRecord smsRecord;

//    while ((smsRecord = smsReader.getNext()) != null) {
//      try {
//        String body = getAsymmetricDecryptedBody(masterSecret, smsRecord.getBody().getBody());
//        smsDatabase.updateMessageBody(new MasterSecretUnion(masterSecret), smsRecord.getId(), body);
//      } catch (InvalidMessageException e) {
//        Log.w(TAG, e);
//      }
//    }

//    MmsDatabase        mmsDatabase = DatabaseFactory.getMmsDatabase(context);
//    MmsDatabase.Reader mmsReader   = mmsDatabase.getDecryptInProgressMessages(masterSecret);

    MessageRecord mmsRecord;

//    while ((mmsRecord = mmsReader.getNext()) != null) {
//      try {
//        String body = getAsymmetricDecryptedBody(masterSecret, mmsRecord.getBody().getBody());
//        mmsDatabase.updateMessageBody(new MasterSecretUnion(masterSecret), mmsRecord.getId(), body);
//      } catch (InvalidMessageException e) {
//        Log.w(TAG, e);
//      }
//    }

//    smsReader.close();
//    mmsReader.close();
  }

  @Override
  public boolean onShouldRetryThrowable(Exception exception) {
    return false;
  }

  @Override
  public void onAdded() {

  }

  @Override
  public void onCanceled() {

  }

  private String getAsymmetricDecryptedBody(MasterSecret masterSecret, String body)
      throws InvalidMessageException
  {
    try {
      AsymmetricMasterSecret asymmetricMasterSecret = MasterSecretUtil.getAsymmetricMasterSecret(context, masterSecret);
      AsymmetricMasterCipher asymmetricMasterCipher = new AsymmetricMasterCipher(asymmetricMasterSecret);

      if (TextUtils.isEmpty(body)) return "";
      else                         return asymmetricMasterCipher.decryptBody(body);

    } catch (IOException e) {
      throw new InvalidMessageException(e);
    }
  }


}
