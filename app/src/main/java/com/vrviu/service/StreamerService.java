package com.vrviu.service;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.IBinder;
import android.view.Surface;
import android.view.accessibility.AccessibilityEvent;

import com.vrviu.streamer.BuildConfig;
import com.vrviu.net.ControlTcpClient;
import com.vrviu.net.VideoTcpClient;
import com.vrviu.net.VideoTcpServer;
import com.vrviu.streamer.MediaEncoder;
import com.vrviu.utils.SurfaceControl;
import com.vrviu.utils.SystemUtils;

public class StreamerService extends AccessibilityService {
    private static final String lsIpField = "lsIp";
    private static final String lsControlPortField = "lsControlPort";
    private static final String isGameModeField = "isGameMode";
    private static final String downloadDirField = "downloadDir";
    private static final String packageNameField = "packageName";
    private static final String defaultIP = "10.0.2.2";
    private static final int NOT_IN_GAME = 9998;

    private SharedPreferences preferences = null;
    private ControlTcpClient controlTcpClient = null;

    private IBinder display = null;
    private MediaEncoder mediaEncoder = new MediaEncoder();

    @Override
    public void onCreate() {
        super.onCreate();
        SystemUtils.setProperty("vrviu.version.streamer", BuildConfig.VERSION_NAME);

        preferences = getSharedPreferences(getPackageName(),Context.MODE_PRIVATE);
        String ip = preferences.getString(lsIpField,defaultIP);
        int port = preferences.getInt(lsControlPortField,5000);
        boolean isGameMode = preferences.getBoolean(isGameModeField,true);
        String downloadDir = preferences.getString(downloadDirField, null);
        String packageName = preferences.getString(packageNameField, null);
        controlTcpClient = new ControlTcpClient(getApplicationContext(),ip,port,isGameMode,downloadDir,packageName,null);

        controlTcpClient.start();
        videoTcpClient.start();
        videoTcpServer.start();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if(controlTcpClient!=null)
            controlTcpClient.onAccessibility(event.getSource(),event.getEventType());
    }

    @Override
    public void onInterrupt() {

    }

    @Override
    public void onDestroy() {
        controlTcpClient.interrupt();
        videoTcpServer.interrupt();
        videoTcpClient.interrupt();
        super.onDestroy();
    }

    private final VideoTcpClient videoTcpClient = new VideoTcpClient(SystemUtils.getProperty("vrviu.ls.ip_addr",defaultIP),51897) {
        @Override
        public boolean startStreaming(String flowId, String lsIp, boolean tcp, int lsVideoPort, int lsAudioPort, int lsControlPort, boolean h264, String videoCodecProfile, int idrPeriod, int maxFps, int minFps, int width, int height, int bitrate, int orientationType, int enableSEI, int rateControlMode, int gameMode, String packageName, String downloadDir) {
            boolean isGameMode = gameMode!=NOT_IN_GAME;
            if(preferences!=null) {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString(lsIpField,lsIp);
                editor.putInt(lsControlPortField,lsControlPort);
                editor.putBoolean(isGameModeField,isGameMode);
                editor.putString(downloadDirField,downloadDir);
                editor.putString(packageNameField,packageName);
                editor.apply();
            }

            if(display!=null) {
                mediaEncoder.stop();
                SurfaceControl.destroyDisplay(display);
                display = null;
            }

            Rect rect = new Rect(0,0,width,height);
            Surface surface = mediaEncoder.init(rect.width(),rect.height(),maxFps,bitrate*1000,minFps);

            display = SurfaceControl.createDisplay("streamer", true);
            SurfaceControl.setDisplaySurface(display, surface, rect, rect,0);
            return mediaEncoder.start();
        }

        @Override
        public void stopStreaming(boolean stopVideo, boolean stopAudio, boolean stopControl) {
            if(display!=null) {
                mediaEncoder.stop();
                SurfaceControl.destroyDisplay(display);
                display = null;
            }
        }

        @Override
        public void requestIdrFrame() {

        }

        @Override
        public boolean reconfigureEncode(int width, int height, int bitrate, int fps, int frameInterval, int profile, int orientation, int codec) {
            return false;
        }
    };

    private final VideoTcpServer videoTcpServer = new VideoTcpServer(51896) {
        @Override
        public boolean startStreaming(String flowId, String lsIp, boolean tcp, int lsVideoPort, int lsAudioPort, int lsControlPort, boolean h264, String videoCodecProfile, int idrPeriod, int maxFps, int minFps, int width, int height, int bitrate, int orientationType, int enableSEI, int rateControlMode, int gameMode, String packageName, String downloadDir) {
            boolean isGameMode = gameMode!=NOT_IN_GAME;
            if(preferences!=null) {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString(lsIpField,lsIp);
                editor.putInt(lsControlPortField,lsControlPort);
                editor.putBoolean(isGameModeField,isGameMode);
                editor.putString(downloadDirField,downloadDir);
                editor.putString(packageNameField,packageName);
                editor.apply();
            }

            if(display!=null) {
                mediaEncoder.stop();
                SurfaceControl.destroyDisplay(display);
                display = null;
            }

            Rect rect = new Rect(0,0,width,height);
            Surface surface = mediaEncoder.init(rect.width(),rect.height(),maxFps,bitrate*1000,minFps);

            display = SurfaceControl.createDisplay("streamer", true);
            SurfaceControl.setDisplaySurface(display, surface, rect, rect,0);
            return mediaEncoder.start();
        }

        @Override
        public void stopStreaming(boolean stopVideo, boolean stopAudio, boolean stopControl) {
            if(display!=null) {
                mediaEncoder.stop();
                SurfaceControl.destroyDisplay(display);
                display = null;
            }
        }

        @Override
        public void requestIdrFrame() {

        }

        @Override
        public boolean reconfigureEncode(int width, int height, int bitrate, int fps, int frameInterval, int profile, int orientation, int codec) {
            return false;
        }
    };
}
