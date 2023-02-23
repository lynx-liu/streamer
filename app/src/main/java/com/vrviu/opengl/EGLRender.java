package com.vrviu.opengl;

import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Surface;

public class EGLRender implements SurfaceTexture.OnFrameAvailableListener {
    private EglWindow eglWindow;
    private TextureRender mTextureRender;
    private SurfaceTexture mSurfaceTexture;
    private long mIntervalTime = -1;
    private long mLastRefreshTime = -1;
    private int fps = -1;
    private static final float radio = 0.8f;

    public EGLRender(Surface surface, int width, int height, float sharp, int maxFps, Handler handler) {
        fps = maxFps;
        if(maxFps>0) mIntervalTime = (long) (1000/maxFps*radio);
        eglWindow = new EglWindow(surface, width, height);
        surface.release();

        mTextureRender = new TextureRender(width, height, sharp);
        mSurfaceTexture = new SurfaceTexture(mTextureRender.getTextureId());
        mSurfaceTexture.setDefaultBufferSize(width, height);
        mSurfaceTexture.setOnFrameAvailableListener(this, handler);
    }

    public Surface getSurface() {
        return new Surface(mSurfaceTexture);
    }

    public float getSharp() {
        return mTextureRender.getSharp();
    }

    public void setMaxFps(int maxFps) {
        fps = maxFps;
        if(maxFps>0) {
            mIntervalTime = (long) (1000/maxFps*radio);
        } else {
            mIntervalTime = -1;
        }
    }

    public int getFps() {
        return fps;
    }

    public boolean isDynamicFps() {
        return mIntervalTime>0;
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        eglWindow.makeCurrent();
        mSurfaceTexture.updateTexImage();

        long currentTime = SystemClock.uptimeMillis();
        if(currentTime-mLastRefreshTime>=mIntervalTime) {
            mLastRefreshTime = currentTime;
            mTextureRender.drawFrame();
            eglWindow.setPresentationTime(SystemClock.elapsedRealtimeNanos());
            eglWindow.swapBuffers();
        }
    }

    public void stop() {
        mSurfaceTexture.setOnFrameAvailableListener(null);
    }

    public void Release() {
        eglWindow.Release();
    }
}
