package com.vrviu.manager;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;

import com.vrviu.utils.SystemUtils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class InputModeManager {
    private static final int NOT_INPUT = 0x00;
    public static final int START_INPUT = 0xFF;

    private static ActivityMonitor activityMonitor = null;
    private static ContentResolver contentResolver = null;
    private static final List<String> target_activity_list = new ArrayList<>();
    private static final List<String> special_activity_list = new ArrayList<>();
    private static final List<String> disable_simpleInputMethod_activity_list = new ArrayList<>();
    private static final String configFile = "/data/.config/inputmode.config";

    private static int targetActivityIndex = 0;
    private static boolean isPayActivity = false;
    private int inputMode=-1;

    public InputModeManager(Context context, ActivityMonitor activityMonitor) {
        super();
        loadConfig();

        this.activityMonitor = activityMonitor;
        this.activityMonitor.addActivityChangeListener(activityChangeListener);
        contentResolver = context.getContentResolver();

        checkInputMode();
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

    public void checkInputMode(){
        int recentMode = targetActivityIndex;
        if (isStartInput()) {
            recentMode = START_INPUT;
        }

        if(recentMode != inputMode) {
            inputMode = recentMode;

            if (recentMode == START_INPUT && isPayActivity && "1".equals(SystemUtils.getProperty("vrviu.iswebpc", "0")))
                return;
            onInputModeChange(inputMode);
        }
    }

    private ActivityMonitor.ActivityChangeListener activityChangeListener = new ActivityMonitor.ActivityChangeListener() {
        @Override
        public void onActivityChanged(ComponentName componentName) {
            String topActivity = componentName.toShortString();
            switchSimpleInputMethod(isSimpleInputNeedSwitch(topActivity));

            isPayActivity = isPayActivity(topActivity);
            targetActivityIndex = getTargetActivityIndex(topActivity);
        }
    };

    public void Release() {
        if(activityMonitor != null) {
            activityMonitor.removeActivityChangeListener(activityChangeListener);
            activityMonitor = null;
        }
    }
}
