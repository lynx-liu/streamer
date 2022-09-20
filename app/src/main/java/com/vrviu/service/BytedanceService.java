package com.vrviu.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import com.vrviu.bytestreamer.R;
import com.vrviu.net.VideoTcpClient;
import com.vrviu.net.VideoTcpServer;
import com.vrviu.utils.SystemUtils;

public class BytedanceService extends Service {
    @Override
    public void onCreate() {
        super.onCreate();

		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
			NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			NotificationChannel channel = new NotificationChannel(getPackageName(), getString(R.string.app_name), NotificationManager.IMPORTANCE_HIGH);
			notificationManager.createNotificationChannel(channel);
			Notification notification = new Notification.Builder(getApplicationContext(), getPackageName()).build();
			startForeground(1, notification);
		}

        videoTcpClient.start();
        videoTcpServer.start();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        videoTcpServer.interrupt();
        super.onDestroy();
    }

    private VideoTcpClient videoTcpClient = new VideoTcpClient(SystemUtils.getProperty("lsIp","10.0.2.2"),51897) {
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

    private VideoTcpServer videoTcpServer = new VideoTcpServer(51896) {
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
