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
        startService(intent);
    }
}
