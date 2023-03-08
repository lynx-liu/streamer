package com.vrviu.utils;

import android.app.ActivityManager;
import android.app.IActivityController;
import android.app.IProcessObserver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.IInterface;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.List;

public final class SystemUtils {
    public static String getProperty(final String key, final String defaultValue) {
        String value = defaultValue;
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method get = c.getMethod("get", String.class, String.class);
            value = (String)(get.invoke(c, key, defaultValue ));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return value;
    }

    public static void setProperty(final String key, final String value) {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method set = c.getMethod("set", String.class, String.class);
            set.invoke(c, key, value );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void setActivityController(IActivityController watcher, boolean imAMonkey) {
        try {
            Class<?> cls = Class.forName("android.app.ActivityManagerNative");
            IInterface iInterface = (IInterface) cls.getMethod("getDefault").invoke(null);
            if (iInterface != null) {
                Method method = iInterface.getClass().getMethod("setActivityController", IActivityController.class, boolean.class);
                Object[] args = new Object[]{watcher, imAMonkey};
                method.invoke(iInterface, args);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void registerProcessObserver(IProcessObserver observer) {
        try{
            Class<?> cls = Class.forName("android.app.ActivityManagerNative");
            IInterface iInterface = (IInterface) cls.getMethod("getDefault").invoke(null);
            if (iInterface != null) {
                Method method = iInterface.getClass().getMethod("registerProcessObserver", IProcessObserver.class);
                method.invoke(iInterface, observer);
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public static void unregisterProcessObserver(IProcessObserver observer) {
        try {
            Class<?> cls = Class.forName("android.app.ActivityManagerNative");
            IInterface iInterface = (IInterface) cls.getMethod("getDefault").invoke(null);
            if (iInterface != null) {
                Method method = iInterface.getClass().getMethod("unregisterProcessObserver", IProcessObserver.class);
                method.invoke(iInterface, observer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static byte[] byteMerger(byte[] bt1, byte[] bt2){
        byte[] bytes = new byte[bt1.length+bt2.length];
        System.arraycopy(bt1, 0, bytes, 0, bt1.length);
        System.arraycopy(bt2, 0, bytes, bt1.length, bt2.length);
        return bytes;
    }

    public static boolean isAppRunning(Context context, String packageName) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> list = am.getRunningTasks(5);
        for (ActivityManager.RunningTaskInfo info : list) {
            if (info.baseActivity.getPackageName().equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    public static ComponentName getTopActivity(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> list = am.getRunningTasks(1);
        if (list != null && list.size() > 0) {
            return list.get(0).topActivity;
        }
        return null;
    }

    public static boolean isTopPackage(Context context, String packageName) {
        if(packageName!=null && !packageName.isEmpty()) {
            ComponentName componentName = SystemUtils.getTopActivity(context);
            if (componentName == null) {
                Log.d("llx", "topActivity is null");
            } else {
                String topActivity = componentName.toShortString();
                Log.d("llx", "topActivity: " + topActivity);
                return topActivity.contains(packageName);
            }
        }
        return false;
    }

    public static void clearImage(Context context, String path) {
        if (path == null || path.isEmpty())
            return;

        File dir = new File(path);
        if(!dir.exists() || !dir.isDirectory())
            return;

        Log.d("llx","clearImage:"+dir.getPath());
        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        for (File file : dir.listFiles()) {
            if (isImageFile(file.getName())) {
                file.delete();

                intent.setData(Uri.fromFile(file));
                context.sendBroadcast(intent);
                Log.d("llx", "remove picture:" + file.getAbsolutePath());
            }/* else if (file.isDirectory()) {
                clearImage(context, file);
            }*/
        }

        try {
            Runtime.getRuntime().exec(new String[]{"sh", "-c", "pm clear com.android.providers.media"});
            Runtime.getRuntime().exec(new String[]{"sh", "-c", "pm clear com.android.gallery3d"});
            Runtime.getRuntime().exec(new String[]{"sh", "-c", "pm grant com.android.gallery3d android.permission.READ_EXTERNAL_STORAGE"});
            Runtime.getRuntime().exec(new String[]{"sh", "-c", "pm grant com.android.gallery3d android.permission.WRITE_EXTERNAL_STORAGE"});
        }catch (Exception e) {
            Log.d("llx",e.toString());
        }
    }

    public static boolean grantPermission(String packageName, String permission){
        String cmd="pm grant "+packageName+" "+permission;
        Log.d("llx", cmd);

        try {
            Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
        } catch (Exception e) {
            Log.d("llx",e.toString());
            return false;
        }
        return true;
    }

    public static boolean isImageFile(String filename) {
        String[] extensions = new String[] {"jpg", "png", "gif","jpeg","bmp"};
        for (String extenstion:extensions) {
            if(filename.toLowerCase().endsWith(extenstion)) {
                return true;
            }
        }
        return false;
    }

    public static String read(String fileName){
        try {
            File file = new File(fileName);
            if(!file.exists()) return null;

            InputStream inputStream= new FileInputStream(file);
            byte[] buffer=new byte[inputStream.available()];
            inputStream.read(buffer);
            return new String(buffer,"GB2312");
        } catch (Exception e) {
            Log.d("llx",e.toString());
        }
        return null;
    }
}
