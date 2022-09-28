package com.vrviu.utils;

import android.app.IActivityController;
import android.app.IProcessObserver;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

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
            Object activityManagerNative = cls.getMethod("getDefault").invoke(null);
            if (activityManagerNative != null) {
                Method method = cls.getMethod("setActivityController", IActivityController.class, boolean.class);
                Object[] args = new Object[]{watcher, imAMonkey};
                method.invoke(activityManagerNative, args);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void registerProcessObserver(IProcessObserver observer) {
        try{
            Class<?> cls = Class.forName("android.app.ActivityManagerNative");
            Object activityManagerNative = cls.getMethod("getDefault").invoke(null);
            if (activityManagerNative != null) {
                Method method = cls.getMethod("registerProcessObserver", IProcessObserver.class);
                method.invoke(activityManagerNative, observer);
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public static void unregisterProcessObserver(IProcessObserver observer) {
        try {
            Class<?> cls = Class.forName("android.app.ActivityManagerNative");
            Object activityManagerNative = cls.getMethod("getDefault").invoke(null);
            if (activityManagerNative != null) {
                Method method = cls.getMethod("unregisterProcessObserver", IProcessObserver.class);
                method.invoke(activityManagerNative, observer);
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

    /**
     * 5.0以上版本
     * 需要使用系统签名,同时需要<uses-permission android:name="android.permission.DUMP"/>或声明系统用户
     *
     */
    public static String getTopActivity() {
        String cmd = "dumpsys activity activities | grep mResumedActivity";//"dumpsys activity";
        String info = null;
        Process p = null;
        try {
            p = Runtime.getRuntime().exec(cmd);
            BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = null;
            while ((line = in.readLine()) != null) {
                if (line.contains("mResumedActivity")) {
                    break;
                }
            }
            if (line!=null && !line.isEmpty()) {
                info = line.substring(line.indexOf("u0 ") + 3, line.lastIndexOf(" "));
            }

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally {
            try {
                if(p!=null) {
                    p.getInputStream().close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return info;
    }

    public static boolean isTopPackage(String packageName) {
        if(packageName!=null && !packageName.isEmpty()) {
            String topActivity = SystemUtils.getTopActivity();
            if (topActivity == null) {
                Log.d("llx", "topActivity is null");
            } else {
                Log.d("llx", "topActivity: " + topActivity);
                return topActivity.contains(packageName);
            }
        }
        return false;
    }
}
