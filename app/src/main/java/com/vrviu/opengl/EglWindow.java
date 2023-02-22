package com.vrviu.opengl;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.util.Log;
import android.view.Surface;

public class EglWindow {
    private EGLDisplay mEGLDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLContext mEGLContextPb = EGL14.EGL_NO_CONTEXT;
    private EGLContext mEGLContext = EGL14.EGL_NO_CONTEXT;
    private EGLSurface mEGLSurfacePb = EGL14.EGL_NO_SURFACE;
    private EGLSurface mEGLSurface = EGL14.EGL_NO_SURFACE;

    public EglWindow(Surface surface, int width, int height) {
        mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (mEGLDisplay == EGL14.EGL_NO_DISPLAY) {
            Log.e("llx", "eglGetDisplay fail");
            return;
        }

        int[] version = new int[2];
        if (!EGL14.eglInitialize(mEGLDisplay, version, 0, version, 1)) {
            Log.e("llx", "eglInitialize fail");
            return;
        }
        Log.d("llx", "Initialized EGL v"+version[0]+"."+version[1]);

        int[] attrib_list = {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,EGL14.EGL_NONE};
        mEGLContextPb = eglCreatePbuffer(mEGLDisplay, attrib_list, width, height);
        if(mEGLContextPb==EGL14.EGL_NO_CONTEXT) {
            return;
        }
        eglCreateWindow(mEGLDisplay, attrib_list, mEGLContextPb, surface);
    }

    public void Release() {
        if(mEGLSurface!=null) {
            EGL14.eglDestroySurface(mEGLDisplay, mEGLSurface);
            mEGLSurface = EGL14.EGL_NO_SURFACE;
        }

        if(mEGLSurfacePb!=null) {
            EGL14.eglDestroySurface(mEGLDisplay, mEGLSurfacePb);
            mEGLSurfacePb = EGL14.EGL_NO_SURFACE;
        }

        if(mEGLContext!=null) {
            EGL14.eglDestroyContext(mEGLDisplay, mEGLContext);
            mEGLContext = EGL14.EGL_NO_CONTEXT;
        }

        if(mEGLContextPb!=null) {
            EGL14.eglDestroyContext(mEGLDisplay, mEGLContextPb);
            mEGLContextPb = EGL14.EGL_NO_CONTEXT;
        }
    }

    public void makeCurrent() {
        if (!EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext)) {
            Log.e("llx", "eglMakeCurrent failed");
        }
    }

    public void setPresentationTime(long nsecs) {
        EGLExt.eglPresentationTimeANDROID(mEGLDisplay, mEGLSurface, nsecs);
        checkEglError("eglPresentationTimeANDROID");
    }

    public boolean swapBuffers() {
        boolean result = EGL14.eglSwapBuffers(mEGLDisplay, mEGLSurface);
        checkEglError("eglSwapBuffers");
        return result;
    }

    private boolean eglCreateWindow(EGLDisplay eglDisplay, int[] attrib_list, EGLContext eglContext, Surface surface) {
        // 实际表面通常是RGBA或RGBX，因此省略alpha在读取GL_RGBA缓冲区时，会对glReadPixels（）造成巨大的性能影响
        int[] attribList = {
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE, 0,      // placeholder for recordable [@-3]
                EGL14.EGL_NONE
        };

        EGLConfig config = getConfig(attribList, eglDisplay);

        mEGLContext = EGL14.eglCreateContext(eglDisplay, config, eglContext, attrib_list, 0);
        if (mEGLContext == EGL14.EGL_NO_CONTEXT) {
            Log.e("llx", "EGL_NO_CONTEXT");
            return false;
        }

        int[] surfaceAttribs = {EGL14.EGL_NONE};
        mEGLSurface = EGL14.eglCreateWindowSurface(eglDisplay, config, surface, surfaceAttribs, 0);
        return mEGLSurface!=EGL14.EGL_NO_SURFACE;
    }

    private EGLContext eglCreatePbuffer(EGLDisplay eglDisplay, int[] attrib_list, int width, int height) {
        // Configure EGL for pbuffer and OpenGL ES 2.0, 24-bit RGB.
        int[] attribList = {
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE
        };
        EGLConfig config = getConfig(attribList, eglDisplay);

        EGLContext eglContext = EGL14.eglCreateContext(eglDisplay, config, EGL14.EGL_NO_CONTEXT, attrib_list, 0);
        if (eglContext == EGL14.EGL_NO_CONTEXT) {
            Log.e("llx", "EGL_NO_CONTEXT");
            return EGL14.EGL_NO_CONTEXT;
        }

        int[] surfaceAttribs = {EGL14.EGL_WIDTH, width, EGL14.EGL_HEIGHT, height, EGL14.EGL_NONE};
        mEGLSurfacePb = EGL14.eglCreatePbufferSurface(eglDisplay, config, surfaceAttribs, 0);
        checkEglError("eglCreatePbufferSurface");
        if (mEGLSurfacePb == EGL14.EGL_NO_SURFACE) {
            Log.e("llx", "EGL_NO_SURFACE");
            return EGL14.EGL_NO_CONTEXT;
        }

        if (!EGL14.eglMakeCurrent(eglDisplay, mEGLSurfacePb, mEGLSurfacePb, eglContext)) {
            Log.e("llx", "eglMakeCurrent failed");
            return EGL14.EGL_NO_CONTEXT;
        }
        return eglContext;
    }

    private EGLConfig getConfig(int[] attribList, EGLDisplay eglDisplay) {
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, configs.length, numConfigs, 0)) {
            return null;
        }
        return configs[0];
    }

    private void checkEglError(String msg) {
        int error = EGL14.eglGetError();
        if (error != EGL14.EGL_SUCCESS) {
            Log.e("llx", msg + ": EGL error: 0x" + Integer.toHexString(error));
        }
    }
}
