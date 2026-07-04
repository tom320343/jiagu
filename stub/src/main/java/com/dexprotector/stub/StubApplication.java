package com.dexprotector.stub;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.util.Log;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import dalvik.system.DexClassLoader;
import dalvik.system.InMemoryDexClassLoader;

public class StubApplication extends Application {

    private static final String TAG = "DexProtectorStub";
    private static final String META_ANTI_DEBUG = "DEXPROT_ANTI_DEBUG";
    private static final String META_INTEGRITY = "DEXPROT_INTEGRITY";

    private Application realApplication;

    static {
        try {
            System.loadLibrary("dexprotector");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load dexprotector native library", e);
        }
    }

    private native byte[] getDexData();
    private native void releaseDexData();

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        try {
            Bundle metaData = getMetaData();
            if (metaData != null) {
                if (metaData.getBoolean(META_ANTI_DEBUG, false)) {
                    startAntiDebug();
                }
                if (metaData.getBoolean(META_INTEGRITY, false)) {
                    checkIntegrity();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in security checks", e);
        }

        try {
            byte[] dexBytes = getDexData();
            if (dexBytes != null && dexBytes.length > 0) {
                Log.d(TAG, "Got decrypted DEX: " + dexBytes.length + " bytes");
                installDex(base, dexBytes);
                releaseDexData();
            } else {
                Log.w(TAG, "No DEX data available, app may not function correctly");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to install DEX", e);
        }
    }

    private void installDex(Context base, byte[] dexBytes) {
        try {
            ClassLoader parentLoader = base.getClassLoader();
            if (parentLoader == null) {
                parentLoader = ClassLoader.getSystemClassLoader();
            }

            ClassLoader dexLoader;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ByteBuffer dexBuffer = ByteBuffer.wrap(dexBytes);
                dexLoader = new InMemoryDexClassLoader(dexBuffer, parentLoader);
            } else {
                File dexFile = writeDexToDisk(dexBytes);
                File optDir = new File(base.getCacheDir(), "dexopt");
                optDir.mkdirs();
                dexLoader = new DexClassLoader(
                    dexFile.getAbsolutePath(),
                    optDir.getAbsolutePath(),
                    null,
                    parentLoader
                );
                dexFile.deleteOnExit();
            }

            String appClassName = findRealApplicationClass(dexLoader);
            if (appClassName != null && !appClassName.equals(StubApplication.class.getName())) {
                Log.d(TAG, "Real application class: " + appClassName);
                installClassLoader(dexLoader);
                realApplication = createRealApplication(dexLoader, appClassName, base);
            }
        } catch (Exception e) {
            Log.e(TAG, "DEX installation failed", e);
        }
    }

    private File writeDexToDisk(byte[] dexBytes) {
        try {
            File dexFile = File.createTempFile("dexprotector_", ".dex", getCacheDir());
            java.io.FileOutputStream fos = new java.io.FileOutputStream(dexFile);
            fos.write(dexBytes);
            fos.close();
            return dexFile;
        } catch (Exception e) {
            throw new RuntimeException("Failed to write DEX to disk", e);
        }
    }

    private String findRealApplicationClass(ClassLoader dexLoader) {
        try {
            String pkg = getPackageName();
            ApplicationInfo ai = getPackageManager().getApplicationInfo(pkg, PackageManager.GET_META_DATA);
            return ai.className;
        } catch (Exception e) {
            return null;
        }
    }

    private void installClassLoader(ClassLoader dexLoader) {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Method currentActivityThread = activityThreadClass.getDeclaredMethod("currentActivityThread");
            currentActivityThread.setAccessible(true);
            Object activityThread = currentActivityThread.invoke(null);

            Field mPackagesField = activityThreadClass.getDeclaredField("mPackages");
            mPackagesField.setAccessible(true);
            Object mPackages = mPackagesField.get(activityThread);

            if (mPackages instanceof java.util.Map) {
                java.util.Map<?, ?> map = (java.util.Map<?, ?>) mPackages;
                for (Object wp : map.values()) {
                    try {
                        Class<?> loadedApkClass = Class.forName("android.app.LoadedApk");
                        Field mClassLoaderField = loadedApkClass.getDeclaredField("mClassLoader");
                        mClassLoaderField.setAccessible(true);
                        ClassLoader currentLoader = (ClassLoader) mClassLoaderField.get(wp);

                        if (currentLoader != null &&
                            currentLoader.loadClass(StubApplication.class.getName()) != null &&
                            currentLoader != dexLoader) {
                            mClassLoaderField.set(wp, dexLoader);
                            Log.d(TAG, "ClassLoader replaced successfully");
                            break;
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to install ClassLoader", e);
        }
    }

    private Application createRealApplication(ClassLoader dexLoader, String appClassName, Context base) {
        try {
            Class<?> appClass = dexLoader.loadClass(appClassName);
            Application app = (Application) appClass.newInstance();
            Method attachMethod = Application.class.getDeclaredMethod("attach", Context.class);
            attachMethod.setAccessible(true);
            attachMethod.invoke(app, base);
            return app;
        } catch (Exception e) {
            Log.e(TAG, "Failed to create real application: " + appClassName, e);
            return null;
        }
    }

    private Bundle getMetaData() {
        try {
            ApplicationInfo ai = getPackageManager()
                    .getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
            return ai.metaData;
        } catch (Exception e) {
            return null;
        }
    }

    private void startAntiDebug() {
        new Thread(() -> {
            while (true) {
                try {
                    if (Debug.isDebuggerConnected()) {
                        Log.w(TAG, "Debugger detected, exiting process");
                        android.os.Process.killProcess(android.os.Process.myPid());
                    }
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "anti-debug-thread").start();
    }

    private void checkIntegrity() {
        try {
            android.content.pm.PackageInfo info = getPackageManager()
                    .getPackageInfo(getPackageName(), PackageManager.GET_SIGNATURES);

            if (info.signatures != null && info.signatures.length > 0) {
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
                byte[] digest = md.digest(info.signatures[0].toByteArray());

                StringBuilder sb = new StringBuilder();
                for (byte b : digest) {
                    sb.append(String.format("%02x", b));
                }
                Log.d(TAG, "App signature: " + sb.toString());
            }
        } catch (Exception e) {
            Log.e(TAG, "Integrity check failed", e);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (realApplication != null) {
            try {
                realApplication.onCreate();
            } catch (Exception e) {
                Log.e(TAG, "Error in real application onCreate", e);
            }
        }
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        if (realApplication != null) {
            realApplication.onTerminate();
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (realApplication != null) {
            realApplication.onLowMemory();
        }
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (realApplication != null) {
            realApplication.onTrimMemory(level);
        }
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (realApplication != null) {
            realApplication.onConfigurationChanged(newConfig);
        }
    }
}
