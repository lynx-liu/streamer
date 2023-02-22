package com.vrviu.service;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.media.MediaCodecInfo;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import com.vrviu.manager.ActivityMonitor;
import com.vrviu.manager.CaptureHelper;
import com.vrviu.manager.GameHelper;
import com.vrviu.opengl.EGLRender;
import com.vrviu.streamer.BuildConfig;
import com.vrviu.net.ControlTcpClient;
import com.vrviu.net.VideoTcpServer;
import com.vrviu.streamer.MediaEncoder;
import com.vrviu.utils.SurfaceControl;
import com.vrviu.utils.SystemUtils;

import java.io.IOException;

public class StreamerService extends AccessibilityService {
    private static final int NOT_IN_GAME = 9998;
    private static final int MSG_UPDATE_VIEW = 0x01;
    private static final int MAX_DELAY = 500;
    private static int delayMillis = MAX_DELAY;

    private ControlTcpClient controlTcpClient = null;

    private static int color = 0;
    private View floatView = null;

    private static int orientation = 1;
    private static int videoWidth = 1920;
    private static int videoHeight = 1080;
    private IBinder iDisplay = null;
    private EGLRender eglRender = null;
    private DisplayManager displayManager = null;
    private static final Point screenSize = new Point();
    private static int refreshRate = 60;
    private final MediaEncoder mediaEncoder = new MediaEncoder();
    private MediaPlayer mMediaPlayer = null;

    private ActivityMonitor activityMonitor = null;
    private CaptureHelper captureHelper = null;
    private GameHelper gameHelper = null;

    @Override
    public void onCreate() {
        super.onCreate();
        SystemUtils.setProperty("vrviu.version.streamer", BuildConfig.VERSION_NAME);

        displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        Display display = displayManager.getDisplay(0);
        display.getRealSize(screenSize);
        refreshRate = (int) display.getRefreshRate();
        displayManager.registerDisplayListener(displayListener,null);
        Log.d("llx","width:"+screenSize.x+", height:"+screenSize.y+", orientation:"+display.getRotation()+", refreshRate:"+refreshRate);

        videoTcpServer.start();
        createFloatWindow();

        activityMonitor = new ActivityMonitor(getApplicationContext(), mhandler);
        activityMonitor.addActivityChangeListener(activityChangeListener);

        captureHelper = new CaptureHelper(screenSize);
    }

    ActivityMonitor.ActivityChangeListener activityChangeListener = new ActivityMonitor.ActivityChangeListener() {
        @Override
        public void onActivityChanged(ComponentName componentName) {
            if(gameHelper==null) {
                gameHelper = new GameHelper(captureHelper,componentName.getPackageName(), sceneChangeListener);
            } else if(!gameHelper.getPackageName().equals(componentName.getPackageName())) {
                gameHelper.interrupt();
                gameHelper = new GameHelper(captureHelper,componentName.getPackageName(), sceneChangeListener);
            }
        }
    };

    GameHelper.onSceneChangeListener sceneChangeListener = new GameHelper.onSceneChangeListener() {
        @Override
        public void onSceneChanged(int report) {
            videoTcpServer.reportScene(report);
        }
    };

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if(controlTcpClient!=null)
            controlTcpClient.onAccessibility(event.getSource(),event.getEventType());
    }

    @Override
    public void onInterrupt() {

    }

    synchronized void releaseStreaming() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if(mMediaPlayer!=null) {
                mMediaPlayer.release();
                mMediaPlayer = null;
            }

            if(eglRender!=null) {
                eglRender.Release();
                eglRender = null;
            }

            if (iDisplay != null) {
                SurfaceControl.destroyDisplay(iDisplay);
                iDisplay = null;
            }
            mediaEncoder.release();
        }
    }

    @Override
    public void onDestroy() {
        releaseStreaming();
        if(gameHelper != null) {
            gameHelper.interrupt();
            gameHelper = null;
        }

        if (captureHelper != null) {
            captureHelper.Release();
            captureHelper = null;
        }

        if(activityMonitor != null) {
            activityMonitor.Release();
            activityMonitor = null;
        }

        if(controlTcpClient!=null) {
            controlTcpClient.interrupt();
            try {
                controlTcpClient.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            controlTcpClient = null;
        }

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
            Point lastSize = new Point(screenSize);
            Display display = displayManager.getDisplay(0);
            display.getRealSize(screenSize);

            if(screenSize.equals(lastSize) && refreshRate==(int)display.getRefreshRate()) {
                Log.d("llx", "display is same");
                return;
            }

            refreshRate = (int) display.getRefreshRate();

            if(captureHelper!=null) {
                captureHelper.Release();
                captureHelper = null;
            }
            captureHelper = new CaptureHelper(screenSize);

            if(gameHelper != null) {
                gameHelper.setCaptureHelper(captureHelper);
            }

            if(controlTcpClient!=null) {
                controlTcpClient.setDisplayRotation(screenSize);
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                if (iDisplay != null) {
                    SurfaceControl.destroyDisplay(iDisplay);
                    iDisplay = null;

                    int width = videoWidth;
                    int height = videoHeight;

                    if (screenSize.x < screenSize.y && orientation % 2 != 0) {
                        videoWidth = Math.min(width, height);
                        videoHeight = Math.max(width, height);
                    } else {
                        videoWidth = Math.max(width, height);
                        videoHeight = Math.min(width, height);
                    }

                    Surface surface = mediaEncoder.reconfigure(videoWidth, videoHeight, -1, -1, -1, -1, -1);

                    if (eglRender != null) {
                        int fps = eglRender.getFps();
                        float sharp = eglRender.getSharp();
                        eglRender.Release();

                        eglRender = new EGLRender(surface, videoWidth, videoHeight, sharp, fps, mhandler);
                        surface = eglRender.getSurface();
                    }

                    if (mMediaPlayer != null) {
                        mMediaPlayer.setSurface(surface);
                    } else {
                        Rect screenRect = new Rect(0, 0, screenSize.x, screenSize.y);
                        iDisplay = SurfaceControl.createDisplay("streamer", true);
                        if (screenSize.x < screenSize.y && orientation == 0) {
                            SurfaceControl.setDisplaySurface(iDisplay, surface, screenRect, new Rect(0, 0, videoHeight, videoWidth), 3);
                        } else {
                            SurfaceControl.setDisplaySurface(iDisplay, surface, screenRect, new Rect(0, 0, videoWidth, videoHeight), 0);
                        }
                    }
                    surface.release();
                    mediaEncoder.start();
                }
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
        public boolean startStreaming(String flowId, String lsIp, boolean tcp, int lsVideoPort, int lsAudioPort, int lsControlPort,
                                      int codec, String videoCodecProfile, int idrPeriod, int maxFps, int minFps, boolean dynamicFps,
                                      int width, int height, int bitrate, int orientationType, int enableSEI, int rateControlMode,
                                      int gameMode, String packageName, String downloadDir, float sharp, int audioType,
                                      int defaulQP, int maxQP, int minQP, String fakeVideoPath) {
            boolean isGameMode = gameMode!=NOT_IN_GAME;

            if(controlTcpClient!=null) {
                controlTcpClient.interrupt();
                try {
                    controlTcpClient.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                controlTcpClient = null;
            }

            controlTcpClient = new ControlTcpClient(getApplicationContext(),lsIp,lsControlPort,isGameMode,downloadDir,packageName,activityMonitor,null);
            controlTcpClient.start();
            controlTcpClient.setDisplayRotation(screenSize);
            releaseStreaming();

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                if(width<height) {
                    width+=height;
                    height=width-height;
                    width-=height;
                }
                width = Math.max(screenSize.x,screenSize.y)*height/Math.min(screenSize.x,screenSize.y);

                orientation = orientationType;
                if(screenSize.x< screenSize.y && orientation%2!=0) {
                    videoWidth = Math.min(width, height);
                    videoHeight = Math.max(width, height);
                } else {
                    videoWidth = Math.max(width, height);
                    videoHeight = Math.min(width, height);
                }

                if(fakeVideoPath!=null) {
                    mMediaPlayer = new MediaPlayer();
                    try {
                        mMediaPlayer.setDataSource(fakeVideoPath);
                        mMediaPlayer.prepare();
                        videoWidth = mMediaPlayer.getVideoWidth();
                        videoHeight = mMediaPlayer.getVideoHeight();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                videoWidth &= 0xFFFC;
                videoHeight &= 0xFFFC;

                int profile = getProfile(videoCodecProfile);
                int framerate = dynamicFps?refreshRate:maxFps;
                Surface surface = mediaEncoder.init(videoWidth, videoHeight, framerate, bitrate * 1000, minFps, codec, profile,
                        idrPeriod/maxFps, rateControlMode, audioType, defaulQP, maxQP, minQP, lsIp, lsVideoPort, lsAudioPort,
                        null);

                if(eglRender != null) {
                    eglRender.Release();
                    eglRender = null;
                }

                if(sharp>0 || dynamicFps) {
                    eglRender = new EGLRender(surface, videoWidth, videoHeight, sharp, maxFps, mhandler);
                    surface = eglRender.getSurface();
                }

                if(mMediaPlayer!=null) {
                    mMediaPlayer.setSurface(surface);
                    mMediaPlayer.setScreenOnWhilePlaying(true);
                    mMediaPlayer.setLooping(true);
                    mMediaPlayer.start();
                } else {
                    Rect screenRect = new Rect(0, 0, screenSize.x, screenSize.y);
                    iDisplay = SurfaceControl.createDisplay("streamer", true);
                    if (screenSize.x < screenSize.y && orientation == 0) {
                        SurfaceControl.setDisplaySurface(iDisplay, surface, screenRect, new Rect(0, 0, videoHeight, videoWidth), 3);
                    } else {
                        SurfaceControl.setDisplaySurface(iDisplay, surface, screenRect, new Rect(0, 0, videoWidth, videoHeight), 0);
                    }
                }
                surface.release();

                mhandler.removeMessages(MSG_UPDATE_VIEW);
                if(minFps>0) delayMillis = 1000/minFps;
                if(delayMillis>MAX_DELAY) delayMillis = MAX_DELAY;
                else if(delayMillis<1000/maxFps) delayMillis = 1000/maxFps+1;
                mhandler.sendEmptyMessageDelayed(MSG_UPDATE_VIEW,delayMillis);
                return mediaEncoder.start();
            }
            return true;
        }

        @Override
        public void stopStreaming(boolean stopVideo, boolean stopAudio, boolean stopControl) {
            if(controlTcpClient!=null) {
                controlTcpClient.interrupt();
                try {
                    controlTcpClient.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                controlTcpClient = null;
            }

            mhandler.removeMessages(MSG_UPDATE_VIEW);
            releaseStreaming();
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
                if(width!=-1||height!=-1||(fps!=-1&&(eglRender==null||!eglRender.isDynamicFps()))||frameInterval!=-1||profile!=-1||codec!=-1||orientation!=-1) {
                    Log.d("llx","reconfigureEncode {"+(width!=-1?" width:"+width:"")+(height!=-1?" height:"+height:"")+(bitrate!=-1?" bitrate:"+bitrate:"")+(fps!=-1?" fps:"+fps:"")+(frameInterval!=-1?" frameInterval:"+frameInterval:"")+(profile!=-1?" profile:"+profile:"")+(orientation!=-1?" orientation:"+orientation:"")+(codec!=-1?" codec:"+codec:"")+" }");
                    if(width!=-1 && height!=-1) {
                        if(width<height) {
                            width+=height;
                            height=width-height;
                            width-=height;
                        }
                        width = Math.max(screenSize.x,screenSize.y)*height/Math.min(screenSize.x,screenSize.y);

                        if (screenSize.x < screenSize.y && orientation % 2 != 0) {
                            videoWidth = Math.min(width, height);
                            videoHeight = Math.max(width, height);
                        } else {
                            videoWidth = Math.max(width, height);
                            videoHeight = Math.min(width, height);
                        }
                    }

                    if(mMediaPlayer!=null) {
                        videoWidth = mMediaPlayer.getVideoWidth();
                        videoHeight = mMediaPlayer.getVideoHeight();
                    }

                    videoWidth &= 0xFFFC;
                    videoHeight &= 0xFFFC;

                    int maxFps = (fps!=-1 && eglRender!=null && eglRender.isDynamicFps())? refreshRate:fps;
                    Surface surface = mediaEncoder.reconfigure(videoWidth,videoHeight,bitrate,maxFps,frameInterval,profile,codec);

                    if(eglRender!=null) {
                        if(fps==-1) fps = eglRender.getFps();
                        float sharp = eglRender.getSharp();
                        eglRender.Release();

                        eglRender = new EGLRender(surface, videoWidth, videoHeight, sharp, fps, mhandler);
                        surface = eglRender.getSurface();
                    }

                    if(mMediaPlayer!=null) {
                        mMediaPlayer.setSurface(surface);
                    } else {
                        if (iDisplay != null) {
                            SurfaceControl.destroyDisplay(iDisplay);
                            iDisplay = null;
                        }

                        Rect screenRect = new Rect(0, 0, screenSize.x, screenSize.y);
                        iDisplay = SurfaceControl.createDisplay("streamer", true);
                        if (screenSize.x < screenSize.y && orientation == 0) {
                            SurfaceControl.setDisplaySurface(iDisplay, surface, screenRect, new Rect(0, 0, videoHeight, videoWidth), 3);
                        } else {
                            SurfaceControl.setDisplaySurface(iDisplay, surface, screenRect, new Rect(0, 0, videoWidth, videoHeight), 0);
                        }
                    }
                    surface.release();
                    return mediaEncoder.start();
                } if (bitrate!=-1||(fps!=-1&&eglRender!=null&&eglRender.isDynamicFps())) {
                    Log.d("llx","reconfigureEncode {"+(bitrate!=-1?" bitrate:"+bitrate:"")+(fps!=-1?" fps:"+fps:"")+" }");
                    if(bitrate!=-1) mediaEncoder.setVideoBitrate(bitrate * 1000);
                    if(fps!=-1&&eglRender!=null&&eglRender.isDynamicFps()) {
                        eglRender.setMaxFps(fps);
                    }
                    return true;
                }
            }
            return false;
        }
    };
}
