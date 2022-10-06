//
// Created by lynx.liu on 2022/9/28.
//

#ifndef STREAMER_VIDEOENCODER_H
#define STREAMER_VIDEOENCODER_H

#include <queue>
#include <pthread.h>
#include <unistd.h>
#include <fcntl.h>
#include <android/log.h>
#include <android/native_window.h>
#include <media/NdkMediaMuxer.h>
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>

#define REPEAT_FRAME_DELAY_US           50000 // repeat after 50ms

inline int64_t systemnanotime();

class VideoEncoder
{
private:
    int nWidth;
    int nHeight;
    pthread_t thread;

    ANativeWindow *surface = NULL;
    AMediaCodec *videoCodec = NULL;
    AMediaMuxer *mMuxer = NULL;
    int mVideoTrack;
    bool mIsRecording;
    int64_t timeoutUs = -1;

private:
    void dequeueOutput();
    static void* encode_thread(void *arg);

public:
    VideoEncoder();
    ~VideoEncoder(void);
    ANativeWindow* init(int width, int height, int framerate, int bitrate, int minFps);
    bool start(const char *filename);
    bool isRecording();
    void release();
};

#endif //STREAMER_VIDEOENCODER_H
