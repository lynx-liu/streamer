package com.vrviu.application;

import android.app.Application;
import android.content.Intent;
import android.os.Build;

import com.vrviu.service.StreamerService;

public class AppBoot extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Intent intent = new Intent(Intent.ACTION_RUN);
        intent.setClass(this, StreamerService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);//Android 8.0 不再允许后台service直接通过startService方式去启动
        } else {
            startService(intent);
        }
    }
}
