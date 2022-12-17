#ifndef _OVERLAY_H
#define _OVERLAY_H

#include "Program.h"
#include "EglWindow.h"

#include <EGL/egl.h>
#include <pthread.h>

class Overlay {
public:
    Overlay() :
        mRunning(false),
        mFrameAvailable(false),
        mExtTextureName(0),
        glThreadTid(-1)
        {}

    bool start(ANativeWindow *outputSurface);
    void stop();
    void set_sharp_alpha(float alpha);

    Overlay(const Overlay&);
    Overlay& operator=(const Overlay&);

    // Destruction via RefBase.
    virtual ~Overlay() {}

private:
    virtual void onFrameAvailable();
    static void* threadLoop(void *arg);

    // One-time setup (essentially object construction on the overlay thread).
    bool setup_l();

    // Release all resources held.
    void release_l();

    // Process a frame received from the virtual display.
    void processFrame_l();

    bool mRunning;
    // Set by the FrameAvailableListener callback.
    bool mFrameAvailable;

    // Producer side of queue, passed into the virtual display.
    // The consumer end feeds into our GLConsumer.
    ANativeWindow *mProducer;

    // This receives frames from the virtual display and makes them available
    // as an external texture.
//TODO    sp<GLConsumer> mGlConsumer;

    EglWindow mEglWindow;

    // GL rendering support.
    Program mExtTexSharpProgram;

    // External texture, updated by GLConsumer.
    GLuint mExtTextureName;

    float mSharpAlpha;

    pthread_t glThreadTid;
};
#endif /*_OVERLAY_H*/
