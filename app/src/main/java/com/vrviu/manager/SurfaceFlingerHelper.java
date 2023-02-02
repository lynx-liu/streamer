package com.vrviu.manager;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class SurfaceFlingerHelper extends Thread{
    private String latencyCmd = null;
    private onFpsListener mListener = null;

    public interface onFpsListener {
        boolean onFps(final byte fps);
    }

    public SurfaceFlingerHelper(String viewName, onFpsListener listener) {
        latencyCmd = "dumpsys SurfaceFlinger --latency \"" + viewName + "\"";
        Log.d("llx", latencyCmd);
        mListener = listener;
        start();
    }

    public void Release() {
        interrupt();
        mListener = null;
    }

    @Override
    public void run() {
        super.run();
        while(!isInterrupted()) {
            latencyClear();
            try {
                sleep(1000);
            } catch (InterruptedException e) {
                break;
            }
            byte fps = getFps();
            if(mListener!=null) {
                mListener.onFps(fps);
            }
        }
    }

    private void latencyClear() {
        try {
            Runtime.getRuntime().exec(new String[]{"sh","-c","dumpsys SurfaceFlinger --latency-clear"});
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private byte getFps() {
        byte fps = -1;
        byte frameCount = 0;
        byte zeroCount = 0;

        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[]{"sh","-c",latencyCmd});
            InputStream inputStream = process.getInputStream();
            if(inputStream!=null) {
                BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));
                try {
                    String line = in.readLine();
                    while (line != null) {
                        if ((line = in.readLine()) != null) {
                            int index = line.indexOf(9);
                            if(index>0) {
                                long time = Long.valueOf(line.substring(0,index));
                                if(time>0) {
                                    frameCount++;
                                } else {
                                    zeroCount++;
                                }
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    try {
                        in.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            Log.d("llx",e.toString());
        }

        if(frameCount>0) {
            fps = frameCount;
        } else if(zeroCount>0) {
            fps = 0;
        } else {
            fps = -1;
        }
        return fps;
    }
}
