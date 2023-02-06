package com.vrviu.opengl;

import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Surface;

public class EGLRender implements SurfaceTexture.OnFrameAvailableListener {
    private EglWindow eglWindow;
    private TextureRender mTextureRender;
    private SurfaceTexture mSurfaceTexture;

    public EGLRender(Surface surface, int width, int height, float sharp, Handler handler) {
        eglWindow = new EglWindow(surface, width, height);

        mTextureRender = new TextureRender(width, height, sharp);
        mSurfaceTexture = new SurfaceTexture(mTextureRender.getTextureId());
        mSurfaceTexture.setDefaultBufferSize(width, height);
        mSurfaceTexture.setOnFrameAvailableListener(this, handler);
    }

    public Surface getSurface() {
        return new Surface(mSurfaceTexture);
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        eglWindow.makeCurrent();
        mSurfaceTexture.updateTexImage();
        mTextureRender.drawFrame();
        eglWindow.setPresentationTime(SystemClock.elapsedRealtimeNanos());
        eglWindow.swapBuffers();
    }

    public void Release() {
        mSurfaceTexture.setOnFrameAvailableListener(null);
    }
}
