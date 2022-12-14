#ifndef _EGL_WINDOW_H
#define _EGL_WINDOW_H

#include <android/log.h>
#include <android/native_window.h>

#define EGL_EGLEXT_PROTOTYPES//for eglPresentationTimeANDROID

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES/gl.h>
#include <GLES/glext.h>
#include <GLES3/gl3.h>
#include <GLES3/gl3ext.h>

class EglWindow {
public:
    EglWindow() :
        mEglDisplay(EGL_NO_DISPLAY),
        mEglContext(EGL_NO_CONTEXT),
        mEglSurface(EGL_NO_SURFACE),
        mEglConfig(NULL),
        mWidth(0),
        mHeight(0)
        {}
    ~EglWindow() { eglRelease(); }

    // Creates an EGL window for the supplied surface.
    bool createWindow(ANativeWindow *nativeWindow);

    // Creates an EGL pbuffer surface.
    bool createPbuffer(int width, int height);

    // Return width and height values (obtained from IGBP).
    int getWidth() const { return mWidth; }
    int getHeight() const { return mHeight; }

    // Release anything we created.
    void release() { eglRelease(); }

    // Make this context current.
    bool makeCurrent() const;

    // Sets the presentation time on the current EGL buffer.
    void presentationTime(EGLnsecsANDROID whenNsec) const;

    // Swaps the EGL buffer.
    void swapBuffers() const;

    void drawFrame();
    void eglReleqaseWindow();
    static void* gl_thread(void *arg);

private:
    EglWindow(const EglWindow&);
    EglWindow& operator=(const EglWindow&);

    // Init display, create config and context.
    bool eglSetupContext(bool forPbuffer);
    void eglRelease();

    // Basic EGL goodies.
    EGLDisplay mEglDisplay;
    EGLContext mEglContext;
    EGLSurface mEglSurface;
    EGLConfig mEglConfig;

    // Surface dimensions.
    int mWidth;
    int mHeight;
};
#endif /*_EGL_WINDOW_H*/
