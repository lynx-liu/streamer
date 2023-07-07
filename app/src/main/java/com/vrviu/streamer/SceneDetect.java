package com.vrviu.streamer;

import android.view.Surface;

public class SceneDetect {

     static {
          System.loadLibrary("SceneDetect");
     }

     public native boolean init(String fileName, float matchDegree, int threshold, int roiX, int roiY, int roiW, int roiH);
     public native boolean detect(int[] pixel, int width, int height);
     public native boolean release();
}
