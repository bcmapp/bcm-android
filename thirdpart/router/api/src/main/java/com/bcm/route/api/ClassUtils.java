package com.bcm.route.api;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;
import dalvik.system.DexFile;

import java.io.File;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CountDownLatch;

class ClassUtils {
    private static final String TAG = ClassUtils.class.getSimpleName();

    /**
     * Find out all generated classes.
     */
    static Set<String> getFileNameByPackageName(Context context, final String packageName) throws PackageManager.NameNotFoundException {
        final Set<String> classNames = new HashSet<>();

        List<String> paths = getSourcePaths(context);

        final CountDownLatch latch = new CountDownLatch(paths.size());

        for (final String path : paths) {
            RoutePoolExecutor.getInstance().execute(new Runnable() {
                @Override
                public void run() {
                    DexFile dexFile = null;

                    try {
                        dexFile = new DexFile(path);
                        Enumeration<String> dexEntries = dexFile.entries();
                        while (dexEntries.hasMoreElements()) {
                            String className = dexEntries.nextElement();
//                            Log.i(TAG, className);
                            if (className.startsWith(packageName)) {
                                classNames.add(className);
                            }
                        }
                    } catch (Throwable t) {
                        Log.e(TAG, "Scan map file in dex files error!", t);
                    } finally {
                        if (dexFile != null) {
                            try {
                                dexFile.close();
                            } catch (Throwable ignore) {}
                        }
                        latch.countDown();
                    }
                }
            });
        }

        try {
            latch.await();
        } catch (Exception e) {}

        RoutePoolExecutor.getInstance().shutdown();
        return classNames;
    }

    /**
     * Get the main dex path and split dex paths
     */
    static List<String> getSourcePaths(Context context) throws PackageManager.NameNotFoundException {
        ApplicationInfo applicationInfo = context.getPackageManager().getApplicationInfo(context.getPackageName(), 0);

        List<String> sourcePaths = new ArrayList<>();
        sourcePaths.add(applicationInfo.sourceDir);

        if (BcmInnerRouter.isDebuggable()) {
            sourcePaths.addAll(tryLoadDexFile(applicationInfo));
        }

        return sourcePaths;
    }

    /**
     * Find out all split dex files
     */
    private static List<String> tryLoadDexFile(ApplicationInfo applicationInfo) {
        List<String> sourcePaths = new ArrayList<>();

        if (applicationInfo.splitSourceDirs != null) {
            sourcePaths.addAll(Arrays.asList(applicationInfo.splitSourceDirs));
            Log.i(TAG, "Found InstantRun support");
        } else {
            try {
                Class instantRun = Class.forName("com.android.tools.fd.runtime.Paths");
                Method getDexFileDirectory = instantRun.getMethod("getDexFileDirectory", String.class);
                String dexPath = (String) getDexFileDirectory.invoke(null, applicationInfo.packageName);

                File instantRunFilePath = new File(dexPath);
                if (instantRunFilePath.exists() && instantRunFilePath.isDirectory()) {
                    File[] dexFiles = instantRunFilePath.listFiles();
                    for (File file : dexFiles) {
                        if (file != null && file.exists() && file.isFile() && file.getName().endsWith(".dex")) {
                            sourcePaths.add(file.getAbsolutePath());
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "InstantRun support error, " + e.getMessage());
            }
        }

        return sourcePaths;
    }
}
