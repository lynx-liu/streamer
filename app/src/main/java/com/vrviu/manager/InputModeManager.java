package com.vrviu.manager;

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
    private static final String configFile = "/data/.config/inputmode.config";

    private static int targetActivityIndex = 0;
    private static boolean bIsSpecialActivity = false;
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

    private static boolean isSpecialActivity(String activity) {
        for(String special_activity : special_activity_list) {
            if(activity.contains(special_activity)) {
                return true;
            }
        }
        return false;
    }

    public boolean isStartInput() {
        return "true".equalsIgnoreCase(SystemUtils.getProperty("vrviu.startInput", "false"));
    }

    public boolean isSimpleInputMethodEnable() {
        return "true".equalsIgnoreCase(SystemUtils.getProperty("vrviu.simpleInputMethod.enable", "false"));
    }

    public boolean isCloudIME() {
        return 0==Integer.parseInt(SystemUtils.getProperty("vrviu.localInput.type", "1"));
    }

    public void checkInputMode() {
        int recentMode = targetActivityIndex;
        if (isStartInput()) {
            recentMode = START_INPUT;
        }

        if(recentMode != inputMode) {
            inputMode = recentMode;

            if (recentMode == START_INPUT && bIsSpecialActivity && "1".equals(SystemUtils.getProperty("vrviu.iswebpc", "0")))
                return;
            onInputModeChange(inputMode);
        }
    }

    private ActivityMonitor.ActivityChangeListener activityChangeListener = componentName -> {
        String topActivity = componentName.toShortString();
        bIsSpecialActivity = isSpecialActivity(topActivity);
        targetActivityIndex = getTargetActivityIndex(topActivity);
    };

    public void Release() {
        if(activityMonitor != null) {
            activityMonitor.removeActivityChangeListener(activityChangeListener);
            activityMonitor = null;
        }
    }
}
