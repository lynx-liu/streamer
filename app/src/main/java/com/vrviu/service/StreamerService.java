package com.vrviu.service;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.media.MediaCodecInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import com.vrviu.streamer.BuildConfig;
import com.vrviu.net.ControlTcpClient;
import com.vrviu.net.VideoTcpServer;
import com.vrviu.streamer.MediaEncoder;
import com.vrviu.utils.SurfaceControl;
import com.vrviu.utils.SystemUtils;

public class StreamerService extends AccessibilityService {
    private static final int NOT_IN_GAME = 9998;
    private static final int MSG_UPDATE_VIEW = 0x01;
    private static final int MAX_DELAY = 500;
    private static int delayMillis = MAX_DELAY;

    private ControlTcpClient controlTcpClient = null;

    private static int color = 0;
    private View floatView = null;

    private static int videoWidth = 1920;
    private static int videoHeight = 1080;
    private IBinder iDisplay = null;
    private DisplayManager displayManager = null;
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

        videoTcpServer.start();
        createFloatWindow();
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
            Display display = displayManager.getDisplay(0);
            display.getRealSize(screenSize);

            if(iDisplay!=null) {
                Rect screenRect = new Rect(0, 0, screenSize.x, screenSize.y);
                SurfaceControl.setDisplayRotation(iDisplay, screenRect, new Rect(0,0,videoWidth,videoHeight), 0);
            }

            if(controlTcpClient!=null) {
                controlTcpClient.setDisplayRotation(screenSize);
            }
        }
    };

    private final Handler mhandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what){
                case MSG_UPDATE_VIEW:
                    floatView.setBackgroundColor(color++);
                    mhandler.sendEmptyMessageDelayed(MSG_UPDATE_VIEW,delayMillis);
                    break;
            }
        }
    };

    private void createFloatWindow() {
        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
        layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        layoutParams.format = PixelFormat.RGBA_8888;
        layoutParams.width = 1; layoutParams.height = 1;

        floatView = new View(getApplicationContext());
        windowManager.addView(floatView,layoutParams);
    }

    private int getProfile(String profile) {
        int ret = MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline;
        switch (profile) {
            case "baseline":
                break;
            case "main":
                ret = MediaCodecInfo.CodecProfileLevel.AVCProfileMain;
                break;
            case "high":
                ret = MediaCodecInfo.CodecProfileLevel.AVCProfileHigh;
                break;
        }
        return ret;
    }

    private final VideoTcpServer videoTcpServer = new VideoTcpServer(51896) {
        @Override
        public boolean startStreaming(String flowId, String lsIp, boolean tcp, int lsVideoPort, int lsAudioPort, int lsControlPort, boolean h264, String videoCodecProfile, int idrPeriod, int maxFps, int minFps, int width, int height, int bitrate, int orientationType, int enableSEI, int rateControlMode, int gameMode, String packageName, String downloadDir) {
            boolean isGameMode = gameMode!=NOT_IN_GAME;

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

                int profile = getProfile(videoCodecProfile);
                Surface surface = mediaEncoder.init(videoWidth, videoHeight, maxFps, bitrate * 1000, minFps, h264, profile, idrPeriod/maxFps, rateControlMode);

                iDisplay = SurfaceControl.createDisplay("streamer", true);
                SurfaceControl.setDisplaySurface(iDisplay, surface, screenRect, new Rect(0, 0, videoWidth, videoHeight), 0);

                mhandler.removeMessages(MSG_UPDATE_VIEW);
                if(minFps>0) delayMillis = 1000/minFps;
                if(delayMillis>MAX_DELAY) delayMillis = MAX_DELAY;
                else if(delayMillis<1000/maxFps) delayMillis = 1000/maxFps+1;
                mhandler.sendEmptyMessageDelayed(MSG_UPDATE_VIEW,delayMillis);
                return mediaEncoder.start(lsIp, lsVideoPort, lsAudioPort, null);
            }
            return true;
        }

        @Override
        public void stopStreaming(boolean stopVideo, boolean stopAudio, boolean stopControl) {
            if(controlTcpClient!=null)
                controlTcpClient.interrupt();
            mhandler.removeMessages(MSG_UPDATE_VIEW);

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
                if(width!=-1||height!=-1||fps!=-1||frameInterval!=-1||profile!=-1||codec!=-1) {
                    Log.d("llx","reconfigureEncode {"+(width!=-1?" width:"+width:"")+(height!=-1?" height:"+height:"")+(bitrate!=-1?" bitrate:"+bitrate:"")+(fps!=-1?" fps:"+fps:"")+(frameInterval!=-1?" frameInterval:"+frameInterval:"")+(profile!=-1?" profile:"+profile:"")+(orientation!=-1?" orientation:"+orientation:"")+(codec!=-1?" codec:"+codec:"")+" }");
                    if (iDisplay != null) {
                        mediaEncoder.stop();//停止编码，并断开串流后，LS会重新startStreaming，以此达到重置编码器参数的目的
                        SurfaceControl.destroyDisplay(iDisplay);
                        iDisplay = null;
                    }
                    return true;
                } if (bitrate!=-1) {
                    mediaEncoder.setVideoBitrate(bitrate * 1000);
                    return true;
                }
            }
            return false;
        }
    };
}
