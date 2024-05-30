package com.vrviu.opengl;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;

import com.vrviu.opengl.gltext.TextRenderer;

public class EGLRender implements SurfaceTexture.OnFrameAvailableListener {
    private EglWindow eglWindow;
    private TextureRender mTextureRender;
    private int mTextureID = -1;
    private TextRenderer mTextRenderer;
    private SurfaceTexture mSurfaceTexture;
    private long mIntervalTime = -1;
    private long mLastRefreshTime = -1;
    private int fps = -1;
    private RenderConfig config;
    private static final float radio = 0.8f;

    public EGLRender(Context context, Surface surface, int width, int height, RenderConfig config, int maxFps, Handler handler) {
        this.config = config;
        if(config.dynamicFps)
            fps = maxFps;
        else fps = -1;

        if(fps>0) mIntervalTime = (long) (1000/fps*radio);
        eglWindow = new EglWindow(surface, width, height);
        surface.release();

        mTextureID = genTextures();
        mSurfaceTexture = new SurfaceTexture(mTextureID);
        mSurfaceTexture.setDefaultBufferSize(width, height);
        mSurfaceTexture.setOnFrameAvailableListener(this, handler);

        if(config.sharp>0 || config.contrast!=0 || config.brightness!=0 || config.saturation!=0 || config.showText) {
            float brightness = config.brightness/100.0f;
            float contrast = config.contrast/100.0f+1.0f;
            float saturation = config.saturation/100.0f+1.0f;
            Log.d("llx","B: "+brightness+", C: "+contrast+", S: "+saturation);
            mTextureRender = new TextureRender(mTextureID, width, height, config.sharp, brightness, contrast, saturation);

            if(config.showText) {
                mTextRenderer = new TextRenderer(context, width, height);
            }
        }
    }

    private int genTextures() {
        int[] texture = new int[] {0};
        GLES20.glGenTextures(1, texture, 0);
        return texture[0];
    }

    public Surface getSurface() {
        return new Surface(mSurfaceTexture);
    }

    public RenderConfig getConfig() {
        return config;
    }

    public boolean setMaxFps(int maxFps) {
        if(!config.dynamicFps)
            return false;

        fps = maxFps;
        if(maxFps>0) {
            mIntervalTime = (long) (1000/maxFps*radio);
        } else {
            mIntervalTime = -1;
        }
        return true;
    }

    public int getFps() {
        return fps;
    }

    public boolean isDynamicFps() {
        return config.dynamicFps;
    }

    @Override
    public synchronized void onFrameAvailable(SurfaceTexture surfaceTexture) {
        if(mSurfaceTexture==null)
            return;

        eglWindow.makeCurrent();
        mSurfaceTexture.updateTexImage();

        long currentTime = System.currentTimeMillis();
        if(currentTime-mLastRefreshTime>=mIntervalTime) {
            mLastRefreshTime = currentTime;

            if(mTextureRender!=null) mTextureRender.drawFrame();
            if(mTextRenderer!=null) {
                mTextRenderer.drawText(String.valueOf(currentTime));
            }

            eglWindow.setPresentationTime(mSurfaceTexture.getTimestamp());
            eglWindow.swapBuffers();
        }
    }

    public synchronized void stop() {
        if(mSurfaceTexture!=null) {
            mSurfaceTexture.setOnFrameAvailableListener(null);
            mSurfaceTexture = null;
        }

        if(mTextureID!=-1) {
            GLES20.glDeleteTextures(1, new int[]{mTextureID}, 0);
            mTextureID = -1;
        }

        if(mTextureRender!=null) {
            mTextureRender.release();
            mTextureRender = null;
        }
    }

    public synchronized void Release() {
        stop();
        eglWindow.Release();
    }
}
