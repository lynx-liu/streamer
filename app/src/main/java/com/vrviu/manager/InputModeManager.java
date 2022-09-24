package com.vrviu.manager;

import android.app.IActivityController;
import android.app.IProcessObserver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;

import com.vrviu.utils.SystemUtils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public abstract class InputModeManager {
    private static final int NOT_INPUT = 0x00;
    public static final int START_INPUT = 0xFF;
    private static final int MAX_COUNT = 5;
    private static final long delayMillis = 50;
    private static final long period = 50;

    private static Handler mHandler = null;
    private static ContentResolver contentResolver = null;
    private static final List<String> target_activity_list = new ArrayList<>();
    private static final List<String> special_activity_list = new ArrayList<>();
    private static final List<String> disable_simpleInputMethod_activity_list = new ArrayList<>();
    private static final String configFile = "/data/.config/inputmode.config";

    private static int targetActivityIndex = 0;
    private static boolean isPayActivity = false;

    private int inputMode=-1;
    private long inputModeSetTs =0;
    private long inputTextSetTs =0;
    private int waitCount = MAX_COUNT;
    private final Timer timer = new Timer();

    public InputModeManager(Context context, Handler handler) {
        super();
        loadConfig();

        mHandler = handler;
        contentResolver = context.getContentResolver();

        SystemUtils.registerProcessObserver(iProcessObserver);
        SystemUtils.setActivityController(iActivityController,true);

        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                checkInputMode();
            }
        };
        timer.schedule(timerTask,1,period);
    }

    private static void loadConfig() {
        try {
            FileReader localFileReader = new FileReader(configFile);
            BufferedReader localBufferedReader = new BufferedReader(localFileReader);

            while(true) {
                String line = localBufferedReader.readLine();
                if(line==null) break;

                String[] arrayOfString = line.trim().split("\\s+");
                if (arrayOfString.length == 2) {
                    switch (arrayOfString[0]) {
                        case "activity":
                            target_activity_list.add(arrayOfString[1]);
                            break;

                        case "special_activity":
                            special_activity_list.add(arrayOfString[1]);
                            break;

                        case "disable_simpleInputMethod":
                            disable_simpleInputMethod_activity_list.add(arrayOfString[1]);
                            break;
                    }
                }
            }
            localBufferedReader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int getInputMode() {
        return inputMode;
    }

    public boolean isActivityIndex() {
        return inputMode!=NOT_INPUT && inputMode!=START_INPUT;
    }

    public abstract void onInputModeChange(int mode);

    private static int getTargetActivityIndex(String activity) {
        int index = 0;
        for(String target_activity : target_activity_list) {
            index++;

            if(activity.contains(target_activity)) {
                return index;
            }
        }
        return NOT_INPUT;
    }

    private static boolean isPayActivity(String activity) {
        if(activity.contains("com.alipay.sdk.app.H5PayActivity"))
            return true;
        for(String special_activity : special_activity_list) {
            if(activity.contains(special_activity)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSimpleInputNeedSwitch(String activity) {
        for(String disable_simpleInputMethod_activity : disable_simpleInputMethod_activity_list) {
            if(activity.contains(disable_simpleInputMethod_activity)) {
                return true;
            }
        }
        return false;
    }

    private static void switchSimpleInputMethod(boolean disableSimpleIME) {
        String defaultInputMethod = Settings.Secure.getString(contentResolver,Settings.Secure.DEFAULT_INPUT_METHOD);
        if(disableSimpleIME){
            if(defaultInputMethod.contains("com.simple.inputmethod/.SimpleInputMethodService")) {
                Settings.Secure.putString(contentResolver,Settings.Secure.DEFAULT_INPUT_METHOD, "disableSimpleInputMethodService");
            }
        }else{
            if(defaultInputMethod.contains("disableSimpleInputMethodService")){
                Settings.Secure.putString(contentResolver,Settings.Secure.DEFAULT_INPUT_METHOD, "com.simple.inputmethod/.SimpleInputMethodService");
            }
        }
    }

    public boolean isStartInput() {
        return "true".equalsIgnoreCase(SystemUtils.getProperty("vrviu.startInput", "false"));
    }

    public boolean isSimpleInputMethodEnable() {
        return "true".equalsIgnoreCase(SystemUtils.getProperty("vrviu.simpleInputMethod.enable", "false"));
    }

    private void checkInputMode(){
        int recentMode = targetActivityIndex;
        if (isStartInput()) {
            recentMode = START_INPUT;
        }

        if(recentMode != inputMode) {
            long startInputTime = Long.parseLong(SystemUtils.getProperty("vrviu.startInput.timeStamp", "0"));

            if (startInputTime != inputModeSetTs) {
                long inputTextTime = Long.parseLong(SystemUtils.getProperty("vrviu.inputtext.timeStamp", "0"));

                if (recentMode == START_INPUT) {
                    if (isPayActivity && "1".equals(SystemUtils.getProperty("vrviu.iswebpc", "0"))) {
                        inputMode = recentMode;
                        inputModeSetTs = startInputTime;
                        inputTextSetTs = inputTextTime;
                        waitCount = MAX_COUNT;
                        return;
                    }
                    if (inputTextTime != inputTextSetTs || waitCount <= 0) {
                        inputMode = recentMode;
                        inputModeSetTs = startInputTime;
                        inputTextSetTs = inputTextTime;
                        waitCount = MAX_COUNT;
                        onInputModeChange(inputMode);
                    } else {
                        waitCount--;
                    }
                } else {
                    inputMode = recentMode;
                    inputModeSetTs = startInputTime;
                    inputTextSetTs = inputTextTime;
                    waitCount = MAX_COUNT;
                    onInputModeChange(inputMode);
                }
            }
        }
    }

    private static final Runnable runnable = new Runnable() {
        @Override
        public void run() {
            String topActivity = SystemUtils.getTopActivity();
            if(topActivity==null) {
                mHandler.postDelayed(runnable,delayMillis);
            } else {
                Log.d("llx", "Runnable:" +topActivity);

                switchSimpleInputMethod(isSimpleInputNeedSwitch(topActivity));

                isPayActivity = isPayActivity(topActivity);
                targetActivityIndex = getTargetActivityIndex(topActivity);
            }
        }
    };

    IActivityController iActivityController = new IActivityController.Stub() {
        @Override
        public boolean activityStarting(Intent intent, String pkg) {
            mHandler.removeCallbacks(runnable);
            mHandler.postDelayed(runnable, delayMillis);
            return true;
        }

        @Override
        public boolean activityResuming(String pkg) {
            mHandler.removeCallbacks(runnable);
            mHandler.postDelayed(runnable, delayMillis);
            return true;
        }

        @Override
        public boolean appCrashed(String processName, int pid, String shortMsg, String longMsg, long timeMillis, String stackTrace) {
            return true;
        }

        @Override
        public int appEarlyNotResponding(String processName, int pid, String annotation) {
            return 0;
        }

        @Override
        public int appNotResponding(String processName, int pid, String processStats) {
            return 0;
        }

        @Override
        public int systemNotResponding(String msg) {
            return 0;
        }
    };

    IProcessObserver iProcessObserver = new IProcessObserver.Stub() {
        @Override
        public void onForegroundActivitiesChanged(int pid, int uid, boolean foregroundActivities) {
            mHandler.removeCallbacks(runnable);
            mHandler.postDelayed(runnable, delayMillis);
        }

        @Override
        public void onProcessStateChanged(int pid, int uid, int procState) {

        }

        @Override
        public void onProcessDied(int pid, int uid) {
            mHandler.removeCallbacks(runnable);
            mHandler.postDelayed(runnable, delayMillis);
        }
    };

    public void Release() {
        timer.cancel();
        mHandler.removeCallbacks(runnable);
        SystemUtils.unregisterProcessObserver(iProcessObserver);
        SystemUtils.setActivityController(null,false);
    }
}
