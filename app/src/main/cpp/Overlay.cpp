#include <assert.h>
#include <inttypes.h>
#include <stdlib.h>

#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>

#include "Overlay.h"

inline int64_t systemnanotime(int clock) {
    timespec now{};
    clock_gettime(clock, &now);
    return now.tv_sec * 1000000000LL + now.tv_nsec;
}

void Overlay::set_sharp_alpha(float alpha) {
    mSharpAlpha = alpha;
}

bool Overlay::start(ANativeWindow *nativeWindow) {
    if (!mEglWindow.createWindow(nativeWindow)) {
        return false;
    }

    if(pthread_create(&glThreadTid, nullptr, threadLoop, this)!=0) {
        LOGE("Overlay threadLoop failed!");
        return false;
    }
    return true;
}

void Overlay::stop() {
    LOGI("Overlay::stop");
    mRunning = false;
}

void* Overlay::threadLoop(void *arg) {
    auto *overlay =(Overlay *)arg;

    if (overlay->setup_l()) {
        overlay->mRunning = true;
        while (overlay->mRunning) {
            if (true/*overlay->mFrameAvailable*/) {
                overlay->processFrame_l();
                overlay->mFrameAvailable = false;
            } else {
                LOGI("Awake, frame not available");
            }
        }
    }

    LOGI("Overlay thread stopping");
    overlay->release_l();
    overlay->mRunning = false;
}

bool Overlay::setup_l() {
    mEglWindow.makeCurrent();

    int width = mEglWindow.getWidth();
    int height = mEglWindow.getHeight();

    glViewport(0, 0, width, height);
    glDisable(GL_DEPTH_TEST);
    glDisable(GL_CULL_FACE);

    // Shaders for rendering from different types of textures.
    if (!mTexProgram.setup(Program::PROGRAM_TEXTURE_2D)) {
        return false;
    }

    if (!mExtTexProgram.setup(Program::PROGRAM_EXTERNAL_TEXTURE)) {
        return false;
    }

    if (!mExtTexSharpProgram.setup(Program::PROGRAM_EXTERNAL_TEXTURE_SHARP)) {
        return false;
    }

    // Input side (buffers from virtual display).
    glGenTextures(1, &mExtTextureName);
    if (mExtTextureName == 0) {
        LOGE("glGenTextures failed: %#x", glGetError());
        return false;
    }
    LOGI("glGenTextures: %u", mExtTextureName);

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES,mExtTextureName);
/* TODO
    sp<IGraphicBufferConsumer> consumer;
    BufferQueue::createBufferQueue(&mProducer, &consumer);
    mGlConsumer = new GLConsumer(consumer, mExtTextureName,
                GL_TEXTURE_EXTERNAL_OES, true, false);
    mGlConsumer->setName(String8("virtual display"));
    mGlConsumer->setDefaultBufferSize(width, height);
    mProducer->setMaxDequeuedBufferCount(4);
    mGlConsumer->setConsumerUsageBits(GRALLOC_USAGE_HW_TEXTURE);

    mGlConsumer->setFrameAvailableListener(this);
*/
    return true;
}


void Overlay::release_l() {
    LOGI("Overlay::release_l");
//TODO    mGlConsumer.clear();

    mTexProgram.release();
    mExtTexProgram.release();
    mExtTexSharpProgram.release();
    mEglWindow.release();
}

void Overlay::processFrame_l() {
    float texMatrix[16];
/* TODO
    mGlConsumer->updateTexImage();
    mGlConsumer->getTransformMatrix(texMatrix);
    int64_t monotonicNsec = mGlConsumer->getTimestamp();
    int64_t frameNumber = mGlConsumer->getFrameNumber();

    if (mLastFrameNumber > 0) {
        mTotalDroppedFrames += size_t(frameNumber - mLastFrameNumber) - 1;
    }
    mLastFrameNumber = frameNumber;
*/

    if (true) {  // DEBUG - full blue background
        glClearColor(0.0f, 0.0f, 1.0f, 1.0f);
        glClear(GL_DEPTH_BUFFER_BIT | GL_COLOR_BUFFER_BIT);
    }

    int width = mEglWindow.getWidth();
    int height = mEglWindow.getHeight();
    if (true) {  // DEBUG - draw inset
        mExtTexProgram.blit(mExtTextureName, texMatrix,
                100, 100, width-200, height-200);
    } else {
        mExtTexProgram.blit(mExtTextureName, texMatrix,
                0, 0, width, height);
    }

    glEnable(GL_BLEND);
    glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);

    mExtTexSharpProgram.setSharpAlpha(mSharpAlpha);
    mExtTexSharpProgram.drawSharp(mExtTextureName, texMatrix, 0 ,0 ,width, height);

    glDisable(GL_BLEND);

    if (true) {  // DEBUG - add red rectangle in lower-left corner
        glEnable(GL_SCISSOR_TEST);
        glScissor(0, 0, 200, 200);
        glClearColor(1.0f, 0.0f, 0.0f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);
        glDisable(GL_SCISSOR_TEST);
    }

    mEglWindow.presentationTime(systemnanotime(CLOCK_MONOTONIC));
    mEglWindow.swapBuffers();
}

void Overlay::onFrameAvailable() {
    LOGI("Overlay::onFrameAvailable");
    mFrameAvailable = true;
}