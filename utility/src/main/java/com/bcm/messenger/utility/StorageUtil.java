package com.bcm.messenger.utility;

import android.os.Environment;
import java.io.File;

public class StorageUtil {
    /**
     *
     * @return
     * @throws NoExternalStorageException
     */
    private static File getExternalStorageDir() throws NoExternalStorageException {
        final File storage = Environment.getExternalStorageDirectory();

        if (!storage.canWrite()) {
            throw new NoExternalStorageException();
        }

        return storage;
    }

    /**
     * @return
     */
    public static boolean canWriteInExternalStorageDir() {
        File storage;

        try {
            storage = getExternalStorageDir();
        } catch (NoExternalStorageException e) {
            return false;
        }

        return storage.canWrite();
    }

    public static File getBackupDir() throws NoExternalStorageException {
        return getExternalStorageDir();
    }

    public static File getVideoDir() throws NoExternalStorageException {
        return new File(getExternalStorageDir(), Environment.DIRECTORY_MOVIES);
    }

    public static File getAudioDir() throws NoExternalStorageException {
        return new File(getExternalStorageDir(), Environment.DIRECTORY_MUSIC);
    }

    public static File getImageDir() throws NoExternalStorageException {
        return new File(getExternalStorageDir(), Environment.DIRECTORY_PICTURES);
    }

    public static File getDownloadDir() throws NoExternalStorageException {
        return new File(getExternalStorageDir(), Environment.DIRECTORY_DOWNLOADS);
    }
}
