#include "EglWindow.h"

#define  LOG_TAG    "llx"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

bool EglWindow::createWindow(ANativeWindow *nativeWindow) {
    if (mEglSurface != EGL_NO_SURFACE) {
        LOGE("surface already created");
        return false;
    }

    if (!eglSetupContext(false)) {
        return false;
    }

    // Cache the current dimensions.  We're not expecting these to change.
    mWidth = ANativeWindow_getWidth(nativeWindow);
    mHeight = ANativeWindow_getHeight(nativeWindow);

    // Output side (EGL surface to draw on).
    mEglSurface = eglCreateWindowSurface(mEglDisplay, mEglConfig, nativeWindow, NULL);
    if (mEglSurface == EGL_NO_SURFACE) {
        LOGE("eglCreateWindowSurface error: %#x", eglGetError());
        eglRelease();
        return false;
    }
    return true;
}

bool EglWindow::createPbuffer(int width, int height) {
    if (mEglSurface != EGL_NO_SURFACE) {
        LOGE("surface already created");
        return false;
    }

    if (!eglSetupContext(true)) {
        return false;
    }

    mWidth = width;
    mHeight = height;

    EGLint pbufferAttribs[] = {
            EGL_WIDTH, width,
            EGL_HEIGHT, height,
            EGL_NONE
    };
    mEglSurface = eglCreatePbufferSurface(mEglDisplay, mEglConfig, pbufferAttribs);
    if (mEglSurface == EGL_NO_SURFACE) {
        LOGE("eglCreatePbufferSurface error: %#x", eglGetError());
        eglRelease();
        return false;
    }
    return true;
}

bool EglWindow::makeCurrent() const {
    if (!eglMakeCurrent(mEglDisplay, mEglSurface, mEglSurface, mEglContext)) {
        LOGE("eglMakeCurrent failed: %#x", eglGetError());
        return false;
    }
    return true;
}

bool EglWindow::eglSetupContext(bool forPbuffer) {
    mEglDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (mEglDisplay == EGL_NO_DISPLAY) {
        LOGE("eglGetDisplay failed: %#x", eglGetError());
        return false;
    }

    EGLint majorVersion, minorVersion;
    EGLBoolean result = eglInitialize(mEglDisplay, &majorVersion, &minorVersion);
    if (result != EGL_TRUE) {
        LOGE("eglInitialize failed: %#x", eglGetError());
        return false;
    }
    LOGI("Initialized EGL v%d.%d", majorVersion, minorVersion);

    EGLint numConfigs = 0;
    EGLint windowConfigAttribs[] = {
            EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
            EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL_RED_SIZE, 8,
            EGL_GREEN_SIZE, 8,
            EGL_BLUE_SIZE, 8,
            // no alpha
            EGL_NONE
    };
    EGLint pbufferConfigAttribs[] = {
            EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
            EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
            EGL_RED_SIZE, 8,
            EGL_GREEN_SIZE, 8,
            EGL_BLUE_SIZE, 8,
            EGL_ALPHA_SIZE, 8,
            EGL_NONE
    };
    result = eglChooseConfig(mEglDisplay,
            forPbuffer ? pbufferConfigAttribs : windowConfigAttribs,
            &mEglConfig, 1, &numConfigs);
    if (result != EGL_TRUE) {
        LOGE("eglChooseConfig error: %#x", eglGetError());
        return false;
    }

    EGLint contextAttribs[] = {
        EGL_CONTEXT_CLIENT_VERSION, 2,
        EGL_NONE
    };
    mEglContext = eglCreateContext(mEglDisplay, mEglConfig, EGL_NO_CONTEXT,
            contextAttribs);
    if (mEglContext == EGL_NO_CONTEXT) {
        LOGE("eglCreateContext error: %#x", eglGetError());
        return false;
    }
    return true;
}

void EglWindow::eglRelease() {
    LOGI("EglWindow::eglRelease");
    if (mEglDisplay != EGL_NO_DISPLAY) {
        eglMakeCurrent(mEglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE,
                EGL_NO_CONTEXT);

        if (mEglContext != EGL_NO_CONTEXT) {
            eglDestroyContext(mEglDisplay, mEglContext);
        }

        if (mEglSurface != EGL_NO_SURFACE) {
            eglDestroySurface(mEglDisplay, mEglSurface);
        }
    }

    mEglDisplay = EGL_NO_DISPLAY;
    mEglContext = EGL_NO_CONTEXT;
    mEglSurface = EGL_NO_SURFACE;
    mEglConfig = NULL;

    eglReleaseThread();
}

// Sets the presentation time on the current EGL buffer.
void EglWindow::presentationTime(EGLnsecsANDROID whenNsec) const {
    eglPresentationTimeANDROID(mEglDisplay, mEglSurface, whenNsec);
}

// Swaps the EGL buffer.
void EglWindow::swapBuffers() const {
    eglSwapBuffers(mEglDisplay, mEglSurface);
}
