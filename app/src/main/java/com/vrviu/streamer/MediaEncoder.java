package com.vrviu.streamer;

import android.view.Surface;

public class MediaEncoder {

     static {
         System.loadLibrary("MediaEncoder");
     }

     public native Surface init(int width, int height, int framerate, int bitrate, int minFps);
     public native boolean start(String ip, int videoPort, int audioPort, String filename);
     public native boolean stop();
     public native void requestSyncFrame();
     public native void setVideoBitrate(int bitrate);
}
