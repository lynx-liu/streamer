package com.vrviu.opengl;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Surface;

import com.vrviu.opengl.gltext.TextRenderer;

public class EGLRender implements SurfaceTexture.OnFrameAvailableListener {
    private EglWindow eglWindow;
    private TextureRender mTextureRender;
    private HSBRender hsbRender;
    private TextRenderer mTextRenderer;
    private SurfaceTexture mSurfaceTexture;
    private long mIntervalTime = -1;
    private long mLastRefreshTime = -1;
    private int fps = -1;
    private static final float radio = 0.8f;

    public EGLRender(Context context, Surface surface, int width, int height, float sharp, int maxFps, boolean showText, Handler handler) {
        fps = maxFps;
        if(maxFps>0) mIntervalTime = (long) (1000/maxFps*radio);
        eglWindow = new EglWindow(surface, width, height);
        surface.release();

        mTextureRender = new TextureRender(width, height, sharp);
        hsbRender = new HSBRender(mTextureRender.getTextureId());
        mSurfaceTexture = new SurfaceTexture(mTextureRender.getTextureId());
        mSurfaceTexture.setDefaultBufferSize(width, height);
        mSurfaceTexture.setOnFrameAvailableListener(this, handler);

        if(showText) {
            mTextRenderer = new TextRenderer(context, width, height);
        }
    }

    public Surface getSurface() {
        return new Surface(mSurfaceTexture);
    }

    public boolean isShowText() {
        return mTextRenderer!=null;
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
        if(mSurfaceTexture==null)
            return;

        eglWindow.makeCurrent();
        mSurfaceTexture.updateTexImage();

        long currentTime = System.currentTimeMillis();
        if(currentTime-mLastRefreshTime>=mIntervalTime) {
            mLastRefreshTime = currentTime;
            mTextureRender.drawFrame();
            if(hsbRender!=null) hsbRender.drawFrame();
            if(mTextRenderer!=null) {
                mTextRenderer.drawText(String.valueOf(currentTime));
            }
            eglWindow.setPresentationTime(SystemClock.elapsedRealtimeNanos());
            eglWindow.swapBuffers();
        }
    }

    public void stop() {
        if(mSurfaceTexture!=null) {
            mSurfaceTexture.setOnFrameAvailableListener(null);
            mSurfaceTexture = null;
        }
    }

    public void Release() {
        eglWindow.Release();
    }
}
