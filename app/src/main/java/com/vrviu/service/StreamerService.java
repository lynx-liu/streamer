package com.vrviu.service;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.IBinder;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.accessibility.AccessibilityEvent;

import com.vrviu.streamer.BuildConfig;
import com.vrviu.net.ControlTcpClient;
import com.vrviu.net.VideoTcpServer;
import com.vrviu.streamer.MediaEncoder;
import com.vrviu.utils.SurfaceControl;
import com.vrviu.utils.SystemUtils;

public class StreamerService extends AccessibilityService {
    private static final String lsIpField = "lsIp";
    private static final String startStreamingField = "startStreaming";
    private static final String lsControlPortField = "lsControlPort";
    private static final String isGameModeField = "isGameMode";
    private static final String downloadDirField = "downloadDir";
    private static final String packageNameField = "packageName";
    private static final String defaultIP = "10.0.2.2";
    private static final int NOT_IN_GAME = 9998;

    private SharedPreferences preferences = null;
    private ControlTcpClient controlTcpClient = null;

    private static int videoWidth = 1920;
    private static int videoHeight = 1080;
    private IBinder iDisplay = null;
    private DisplayManager displayManager;
    private static final Point screenSize = new Point();
    private final MediaEncoder mediaEncoder = new MediaEncoder();

    @Override
    public void onCreate() {
        super.onCreate();
        SystemUtils.setProperty("vrviu.version.streamer", BuildConfig.VERSION_NAME);

        displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        Display display = displayManager.getDisplay(0);
        display.getRealSize(screenSize);
        displayManager.registerDisplayListener(displayListener,null);
        Log.d("llx","width:"+screenSize.x+", height:"+screenSize.y+", orientation:"+display.getRotation());

        preferences = getSharedPreferences(getPackageName(),Context.MODE_PRIVATE);
        if(preferences.getBoolean(startStreamingField,false)) {
            String ip = preferences.getString(lsIpField, defaultIP);
            int port = preferences.getInt(lsControlPortField, 5000);
            boolean isGameMode = preferences.getBoolean(isGameModeField, true);
            String downloadDir = preferences.getString(downloadDirField, null);
            String packageName = preferences.getString(packageNameField, null);

            controlTcpClient = new ControlTcpClient(getApplicationContext(), ip, port, isGameMode, downloadDir, packageName, null);
            controlTcpClient.start();
            controlTcpClient.setDisplayRotation(screenSize);
        }
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
        if(controlTcpClient!=null)
            controlTcpClient.interrupt();
        videoTcpServer.interrupt();
        displayManager.unregisterDisplayListener(displayListener);
        super.onDestroy();
    }

    DisplayManager.DisplayListener displayListener = new DisplayManager.DisplayListener() {
        @Override
        public void onDisplayAdded(int displayId) {

        }

        @Override
        public void onDisplayRemoved(int displayId) {

        }

        @Override
        public void onDisplayChanged(int displayId) {
            Display display = displayManager.getDisplay(displayId);
            display.getRealSize(screenSize);

            if(iDisplay!=null) {
                Rect screenRect = new Rect(0, 0, screenSize.x, screenSize.y);
                if(screenSize.x >= screenSize.y) {
                    SurfaceControl.setDisplayRotation(iDisplay, screenRect, new Rect(0,0,videoWidth,videoHeight), 0);
                } else {
                    SurfaceControl.setDisplayRotation(iDisplay, screenRect, new Rect(0,0,videoHeight,videoWidth),3);
                }
            }

            if(controlTcpClient!=null) {
                controlTcpClient.setDisplayRotation(screenSize);
            }
        }
    };

    private final VideoTcpServer videoTcpServer = new VideoTcpServer(51896) {
        @Override
        public boolean startStreaming(String flowId, String lsIp, boolean tcp, int lsVideoPort, int lsAudioPort, int lsControlPort, boolean h264, String videoCodecProfile, int idrPeriod, int maxFps, int minFps, int width, int height, int bitrate, int orientationType, int enableSEI, int rateControlMode, int gameMode, String packageName, String downloadDir) {
            boolean isGameMode = gameMode!=NOT_IN_GAME;
            if(preferences!=null) {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean(startStreamingField,true);
                editor.putString(lsIpField,lsIp);
                editor.putInt(lsControlPortField,lsControlPort);
                editor.putBoolean(isGameModeField,isGameMode);
                editor.putString(downloadDirField,downloadDir);
                editor.putString(packageNameField,packageName);
                editor.apply();
            }

            if(controlTcpClient!=null) controlTcpClient.interrupt();
            controlTcpClient = new ControlTcpClient(getApplicationContext(),lsIp,lsControlPort,isGameMode,downloadDir,packageName,null);
            controlTcpClient.start();
            controlTcpClient.setDisplayRotation(screenSize);

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                if(iDisplay!=null) {
                    mediaEncoder.stop();
                    SurfaceControl.destroyDisplay(iDisplay);
                    iDisplay = null;
                }

                videoWidth = Math.max(width, height);
                videoHeight = Math.min(width, height);
                Rect screenRect = new Rect(0, 0, screenSize.x, screenSize.y);
                Surface surface = mediaEncoder.init(videoWidth, videoHeight, maxFps, bitrate * 1000, minFps);

                iDisplay = SurfaceControl.createDisplay("streamer", true);
                if (screenSize.x >= screenSize.y) {
                    SurfaceControl.setDisplaySurface(iDisplay, surface, screenRect, new Rect(0, 0, videoWidth, videoHeight), 0);
                } else {
                    SurfaceControl.setDisplaySurface(iDisplay, surface, screenRect, new Rect(0, 0, videoHeight, videoWidth), 3);
                }
                return mediaEncoder.start(lsIp, lsVideoPort, lsAudioPort, null);
            }
            return true;
        }

        @Override
        public void stopStreaming(boolean stopVideo, boolean stopAudio, boolean stopControl) {
            if(preferences!=null) {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean(startStreamingField,false);
                editor.apply();
            }

            if(controlTcpClient!=null)
                controlTcpClient.interrupt();

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                if (iDisplay != null) {
                    mediaEncoder.stop();
                    SurfaceControl.destroyDisplay(iDisplay);
                    iDisplay = null;
                }
            }
        }

        @Override
        public void requestIdrFrame() {
            Log.d("llx","requestIdrFrame");
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                mediaEncoder.requestSyncFrame();
            }
        }

        @Override
        public boolean reconfigureEncode(int width, int height, int bitrate, int fps, int frameInterval, int profile, int orientation, int codec) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                if (bitrate > 0) {
                    mediaEncoder.setVideoBitrate(bitrate * 1000);
                    return true;
                }
            }
            return false;
        }
    };
}
