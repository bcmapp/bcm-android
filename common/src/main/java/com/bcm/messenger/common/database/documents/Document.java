package com.bcm.messenger.common.database.documents;

import com.bcm.messenger.utility.proguard.NotGuard;
import java.util.List;

public interface Document<T> extends NotGuard {

  public int size();
  public List<T> list();

}
