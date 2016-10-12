/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package it.deeptelegram.messenger;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;

import net.hockeyapp.android.Constants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class NativeLoader {

    private final static int LIB_VERSION = 24;
    private final static String LIB_NAME = "tmessages." + LIB_VERSION;
    private final static String LIB_SO_NAME = "lib" + LIB_NAME + ".so";
    private final static String LOCALE_LIB_SO_NAME = "lib" + LIB_NAME + "loc.so";
    private String crashPath = "";

    private static volatile boolean nativeLoaded = false;

    private static File getNativeLibraryDir(Context context) {
        File f = null;
        if (context != null) {
            try {
                f = new File((String)ApplicationInfo.class.getField("nativeLibraryDir").get(context.getApplicationInfo()));
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
        if (f == null) {
            f = new File(context.getApplicationInfo().dataDir, "lib");
        }
        if (f.isDirectory()) {
            return f;
        }
        return null;
    }

    private static boolean loadFromZip(Context context, File destDir, File destLocalFile, String folder) {
        try {
            for (File file : destDir.listFiles()) {
                file.delete();
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }

        ZipFile zipFile = null;
        InputStream stream = null;
        try {
            zipFile = new ZipFile(context.getApplicationInfo().sourceDir);
            ZipEntry entry = zipFile.getEntry("lib/" + folder + "/" + LIB_SO_NAME);
            if (entry == null) {
                throw new Exception("Unable to find file in apk:" + "lib/" + folder + "/" + LIB_NAME);
            }
            stream = zipFile.getInputStream(entry);

            OutputStream out = new FileOutputStream(destLocalFile);
            byte[] buf = new byte[4096];
            int len;
            while ((len = stream.read(buf)) > 0) {
                Thread.yield();
                out.write(buf, 0, len);
            }
            out.close();

            destLocalFile.setReadable(true, false);
            destLocalFile.setExecutable(true, false);
            destLocalFile.setWritable(true);

            try {
                System.load(destLocalFile.getAbsolutePath());
                init(Constants.FILES_PATH, BuildVars.DEBUG_VERSION);
                nativeLoaded = true;
            } catch (Error e) {
                FileLog.e("tmessages", e);
            }
            return true;
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
            if (zipFile != null) {
                try {
                    zipFile.close();
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
        }
        return false;
    }

    public static synchronized void initNativeLibs(Context context) {
        if (nativeLoaded) {
            return;
        }

        Constants.loadFromContext(context);

        try {
            String folder;
            try {
                if (Build.CPU_ABI.equalsIgnoreCase("armeabi-v7a")) {
                    folder = "armeabi-v7a";
                } else if (Build.CPU_ABI.equalsIgnoreCase("armeabi")) {
                    folder = "armeabi";
                } else if (Build.CPU_ABI.equalsIgnoreCase("x86")) {
                    folder = "x86";
                } else if (Build.CPU_ABI.equalsIgnoreCase("mips")) {
                    folder = "mips";
                } else {
                    folder = "armeabi";
                    FileLog.e("tmessages", "Unsupported arch: " + Build.CPU_ABI);
                }
            } catch (Exception e) {
                FileLog.e("tmessages", e);
                folder = "armeabi";
            }

            String javaArch = System.getProperty("os.arch");
            if (javaArch != null && javaArch.contains("686")) {
                folder = "x86";
            }


            File destFile = getNativeLibraryDir(context);
            if (destFile != null) {
                destFile = new File(destFile, LIB_SO_NAME);
                if (destFile.exists()) {
                    FileLog.d("tmessages", "load normal lib");
                    try {
                        System.loadLibrary(LIB_NAME);
                        init(Constants.FILES_PATH, BuildVars.DEBUG_VERSION);
                        nativeLoaded = true;
                        return;
                    } catch (Error e) {
                        FileLog.e("tmessages", e);
                    }
                }
            }

            File destDir = new File(context.getFilesDir(), "lib");
            destDir.mkdirs();

            File destLocalFile = new File(destDir, LOCALE_LIB_SO_NAME);
            if (destLocalFile.exists()) {
                try {
                    FileLog.d("tmessages", "Load local lib");
                    System.load(destLocalFile.getAbsolutePath());
                    init(Constants.FILES_PATH, BuildVars.DEBUG_VERSION);
                    nativeLoaded = true;
                    return;
                } catch (Error e) {
                    FileLog.e("tmessages", e);
                }
                destLocalFile.delete();
            }

            FileLog.e("tmessages", "Library not found, arch = " + folder);

            if (loadFromZip(context, destDir, destLocalFile, folder)) {
                return;
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }

        try {
            System.loadLibrary(LIB_NAME);
            init(Constants.FILES_PATH, BuildVars.DEBUG_VERSION);
            nativeLoaded = true;
        } catch (Error e) {
            FileLog.e("tmessages", e);
        }
    }

    private static native void init(String path, boolean enable);
    //public static native void crash();
}
