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
#include <sys/resource.h>
#include <android/log.h>
#include <android/native_window.h>
#include <media/NdkMediaMuxer.h>
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES/gl.h>
#include <GLES/glext.h>
#include <GLES3/gl3.h>
#include <GLES3/gl3ext.h>

#define REPEAT_FRAME_DELAY_US           50000 // repeat after 50ms

#define NonIDR                          1
#define IDR                             5
#define SPS                             7

const char PARAMETER_KEY_REQUEST_SYNC_FRAME[] = "request-sync";
const char PARAMETER_KEY_VIDEO_BITRATE[] = "video-bitrate";

inline int64_t systemnanotime();
inline int32_t systemmilltime();

#pragma pack(push)
#pragma pack(1)
struct VideoHeader {
    uint16_t type = 0; //0=h264 1=h265
    uint16_t keyframe = 0;
    int64_t timestamp = 0;
    uint32_t size = 0;
};
#pragma pack(pop)

typedef struct ABuffer {
    uint8_t*    data;
    VideoHeader header;
} ABuffer;

class VideoEncoder
{
private:
    int nWidth;
    int nHeight;
    pthread_t encode_tid = 0;
    int m_sockfd = -1;
    ABuffer spspps;

    EGLDisplay gDisplay = NULL;
    EGLContext gContext = NULL;
    EGLSurface gSurface = NULL;
    GLuint texture = 0;
    pthread_t gl_tid = 0;

    ANativeWindow *surface = NULL;
    AMediaCodec *videoCodec = NULL;
    AMediaMuxer *mMuxer = NULL;
    int mVideoTrack;
    bool mIsRecording;
    int64_t timeoutUs = -1;
    bool avc = false;

private:
    inline void dequeueOutput(AMediaCodecBufferInfo *info);
    static void* encode_thread(void *arg);
    inline void onH264Frame(uint8_t* bytes, size_t size, int64_t ts);
    inline void onH265Frame(uint8_t* bytes, size_t size, int64_t ts);
    inline void onEncodeFrame(uint8_t *bytes,size_t size,int maxFps,bool keyframe,int64_t ts) const;

    static int connectSocket(const char *ip, int port);

    bool eglCreateWindow(ANativeWindow *nativeWindow);
    void drawFrame();
    void eglReleqaseWindow();
    static void* gl_thread(void *arg);

public:
    VideoEncoder();
    ~VideoEncoder();
    void requestSyncFrame();
    void setVideoBitrate(int bitrate);
    ANativeWindow* init(int width, int height, int framerate, int bitrate, int minFps, bool h264, int profile, int iFrameInterval, int bitrateMode);
    bool start(const char *ip, int port, const char *filename);
    void release();
};

#endif //STREAMER_VIDEOENCODER_H
