package com.vrviu.streamer;

import android.view.Surface;

public class MediaEncoder {

     static {
          if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
               System.loadLibrary("MediaEncoder");
          }
     }

     public native Surface init(int width, int height, int idrPeriod, int bitrate, int minFps, int codec, int profile, int frameInterval,
                                int bitrateMode, int audioMimeType, int defaulQP, int maxQP, int minQP,
                                String ip, int videoPort, int audioPort, boolean dump);
     public native Surface reconfigure(int width, int height, int bitrate, int fps, int idrPeriod, int profile, int codec,
                                       int defaulQP, int minQP, int maxQP, int rateControlMode);

     public native boolean start();
     public native boolean release();
     public native void requestSyncFrame();
     public native void setVideoBitrate(int bitrate);
}
