//
// Created by lynx.liu on 2022/9/28.
//

#ifndef STREAMER_VIDEOENCODER_H
#define STREAMER_VIDEOENCODER_H

#include <queue>
#include <pthread.h>
#include <unistd.h>
#include <fcntl.h>
#include <arpa/inet.h>
#include <android/log.h>
#include <android/native_window.h>
#include <media/NdkMediaMuxer.h>
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>

#define REPEAT_FRAME_DELAY_US           50000 // repeat after 50ms

#define NonIDR                          1
#define IDR                             5
#define SPS                             7

inline int64_t systemnanotime();

#pragma pack(push)
#pragma pack(1)
struct Header {
    uint16_t type = 0; //0=h264 1=h265
    uint16_t keyframe = 0;
    int64_t timestamp = 0;
    uint32_t size = 0;
};
#pragma pack(pop)

typedef struct ABuffer {
    uint8_t*    data;
    Header      header;
} ABuffer;

class VideoEncoder
{
private:
    int nWidth;
    int nHeight;
    pthread_t encode_tid = 0;
    int m_sockfd = -1;
    ABuffer spspps;

    ANativeWindow *surface = NULL;
    AMediaCodec *videoCodec = NULL;
    AMediaMuxer *mMuxer = NULL;
    int mVideoTrack;
    bool mIsRecording;
    int64_t timeoutUs = -1;

private:
    inline void dequeueOutput(AMediaCodecBufferInfo *info);
    static void* encode_thread(void *arg);
    inline void onH264Frame(uint8_t* bytes, size_t size, int64_t ts);
    inline void onEncodeFrame(uint8_t *bytes,size_t size,int frametype,bool keyframe,int64_t ts);

    int connectSocket(const char *ip, int port);

public:
    VideoEncoder();
    ~VideoEncoder(void);
    ANativeWindow* init(int width, int height, int framerate, int bitrate, int minFps);
    bool start(const char *ip, int port, const char *filename);
    bool isRecording();
    void release();
};

#endif //STREAMER_VIDEOENCODER_H
