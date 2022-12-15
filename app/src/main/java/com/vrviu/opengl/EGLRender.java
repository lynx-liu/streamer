package com.vrviu.opengl;

import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Surface;

public class EGLRender extends Thread implements SurfaceTexture.OnFrameAvailableListener {
    private EglWindow eglWindow;
    private TextureRender mTextureRender;
    private SurfaceTexture mSurfaceTexture;

    private int interval = 0;
    private boolean mFrameAvailable = true;

    public EGLRender(Surface surface, int width, int height, Handler handler) {
        eglWindow = new EglWindow(surface, width, height);

        mTextureRender = new TextureRender();
        mSurfaceTexture = new SurfaceTexture(mTextureRender.getTextureId());
        mSurfaceTexture.setDefaultBufferSize(width, height);
        mSurfaceTexture.setOnFrameAvailableListener(this, handler);
    }

    public Surface getSurface() {
        return new Surface(mSurfaceTexture);
    }

    public void setFps(byte fps) {
        if(fps>0) {
            interval = 1000 / fps;
        } else {
            interval = -1;
        }
    }

    public void awaitNewImage() {
        if (mFrameAvailable) {
            mFrameAvailable = false;
            mSurfaceTexture.updateTexImage();
        }
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        mFrameAvailable = true;
    }

    @Override
    public void run() {
        super.run();
        eglWindow.makeCurrent();
        while (!isInterrupted()) {
            awaitNewImage();
            mTextureRender.drawFrame();
            eglWindow.setPresentationTime(SystemClock.elapsedRealtimeNanos());
            eglWindow.swapBuffers();

            if(interval>0) {
                try {
                    sleep(interval);
                } catch (InterruptedException ex) {
                    break;
                }
            }
        }
    }
}
