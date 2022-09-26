package com.vrviu.service;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.vrviu.streamer.BuildConfig;
import com.vrviu.streamer.R;
import com.vrviu.net.ControlTcpClient;
import com.vrviu.net.VideoTcpClient;
import com.vrviu.net.VideoTcpServer;
import com.vrviu.utils.SystemUtils;

public class StreamerService extends AccessibilityService {
    private static final String lsIpField = "lsIp";
    private static final String lsControlPortField = "lsControlPort";
    private static final String isGameModeField = "isGameMode";
    private static final String defaultIP = "10.0.2.2";
    private static final int NOT_IN_GAME = 9998;

    private SharedPreferences preferences = null;
    private ControlTcpClient controlTcpClient = null;

    @Override
    public void onCreate() {
        super.onCreate();
        SystemUtils.setProperty("vrviu.version.streamer", BuildConfig.VERSION_NAME);

		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
			NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			NotificationChannel channel = new NotificationChannel(getPackageName(), getString(R.string.app_name), NotificationManager.IMPORTANCE_HIGH);
			notificationManager.createNotificationChannel(channel);
			Notification notification = new Notification.Builder(getApplicationContext(), getPackageName()).build();
			startForeground(1, notification);
		}

        preferences = getSharedPreferences(getPackageName(),Context.MODE_PRIVATE);
        String ip = preferences.getString(lsIpField,defaultIP);
        int port = preferences.getInt(lsControlPortField,5000);
        boolean isGameMode = preferences.getBoolean(isGameModeField,true);
        controlTcpClient = new ControlTcpClient(getApplicationContext(),ip,port,isGameMode,null);

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
                editor.apply();
            }
            return false;
        }

        @Override
        public void stopStreaming(boolean stopVideo, boolean stopAudio, boolean stopControl) {

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
            return false;
        }

        @Override
        public void stopStreaming(boolean stopVideo, boolean stopAudio, boolean stopControl) {

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
