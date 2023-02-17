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

    public EGLRender(Surface surface, int width, int height, float sharp, int maxFps, Handler handler) {
        if(maxFps>0) mIntervalTime = 1000/maxFps;
        eglWindow = new EglWindow(surface, width, height);

        mTextureRender = new TextureRender(width, height, sharp);
        mSurfaceTexture = new SurfaceTexture(mTextureRender.getTextureId());
        mSurfaceTexture.setDefaultBufferSize(width, height);
        mSurfaceTexture.setOnFrameAvailableListener(this, handler);
    }

    public Surface getSurface() {
        return new Surface(mSurfaceTexture);
    }

    public void setMaxFps(int maxFps) {
        if(maxFps>0) {
            mIntervalTime = 1000/maxFps;
        } else {
            mIntervalTime = -1;
        }
    }

    public boolean isDynamicFps() {
        return mIntervalTime>0;
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        eglWindow.makeCurrent();
        mSurfaceTexture.updateTexImage();
        if(SystemClock.uptimeMillis()-mLastRefreshTime>=mIntervalTime) {
            mLastRefreshTime = SystemClock.uptimeMillis();
            mTextureRender.drawFrame();
            eglWindow.setPresentationTime(SystemClock.elapsedRealtimeNanos());
            eglWindow.swapBuffers();
        }
    }

    public void Release() {
        mSurfaceTexture.setOnFrameAvailableListener(null);
    }
}
