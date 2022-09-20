package com.vrviu.receive;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    String bootAction = "android.intent.action.BOOT_COMPLETED";
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();//监听开机广播时，Application会启动，在Application的onCreate中启动了Service
        if (bootAction.equals(action)) {
            Log.d("llx", "screenshot listener "+bootAction);
        }
    }
}
