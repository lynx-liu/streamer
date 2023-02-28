//
// Created by lynx.liu on 2022/9/28.
//

#ifndef STREAMER_VIDEOENCODER_H
#define STREAMER_VIDEOENCODER_H

#include <queue>
#include <pthread.h>
#include <sys/prctl.h>
#include <unistd.h>
#include <fcntl.h>
#include <arpa/inet.h>
#include <sys/resource.h>
#include <android/log.h>
#include <android/native_window.h>
#include <media/NdkMediaMuxer.h>
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>
#include <mutex>

inline int64_t systemnanotime();
inline int32_t systemmilltime();

enum VideoType {
    AVC,
    HEVC
};

#pragma pack(push)
#pragma pack(1)
struct VideoHeader {
    uint16_t type = AVC;
    uint16_t keyframe = 0;
    int64_t timestamp = 0;
    uint32_t size = 0;
};
#pragma pack(pop)

typedef struct AMediaInfo {
    int32_t outIndex;
    AMediaCodecBufferInfo bufferInfo;
} AMediaInfo;

typedef struct VideoParam {
    VideoType videoType;
    int width;
    int height;
    int minFps;
    int maxFps;
    int bitrate;
    int profile;
    int frameInterval;
    int bitrateMode;
    int defaulQP;
    int maxQP;
    int minQP;
} VideoParam;

class VideoEncoder
{
private:
    pthread_t encode_tid = 0;
    int m_sockfd = -1;
    VideoHeader header;

    VideoParam videoParam;
    ANativeWindow *surface = NULL;
    AMediaCodec *videoCodec = NULL;
    AMediaMuxer *mMuxer = NULL;
    int mVideoTrack;
    bool mIsRecording;
    bool mIsSending;
    int64_t timeoutUs = -1;
    int8_t *trackTotal;

    pthread_t send_tid = 0;
    std::mutex mtx;
    std::condition_variable cond;
    std::queue<AMediaInfo> mediaInfoQueue;

private:
    ANativeWindow* createEncoder(AMediaMuxer *muxer);
    inline void dequeueOutput(AMediaCodecBufferInfo *info);
    static void* encode_thread(void *arg);

    static void OnInputAvailableCB(AMediaCodec *mediaCodec, void *userdata, int32_t index);
    static void OnOutputAvailableCB(AMediaCodec *mediaCodec, void *userdata, int32_t index, AMediaCodecBufferInfo *bufferInfo);
    static void OnFormatChangedCB(AMediaCodec *mediaCodec, void *userdata, AMediaFormat *format);
    static void OnErrorCB(AMediaCodec *mediaCodec, void *userdata, media_status_t err, int32_t actionCode, const char *detail);

    inline void notifyOutputAvailable(int32_t index, AMediaCodecBufferInfo *bufferInfo);
    inline void onOutputAvailable(int32_t outIndex, AMediaCodecBufferInfo *info);
    inline void onFormatChange(AMediaFormat *format);
    inline void onEncodeFrame(uint8_t *bytes,size_t size,int32_t flags,int64_t ts);

    static int connectSocket(const char *ip, int port);
    static void* send_video_thread(void *arg);

public:
    VideoEncoder();
    ~VideoEncoder();
    void requestSyncFrame();
    void setVideoBitrate(int bitrate);
    ANativeWindow* init(int width, int height, int framerate, int bitrate, int minFps, int codec, int profile, int frameInterval,
                        int bitrateMode, int defaulQP, int maxQP, int minQP, AMediaMuxer *muxer, int8_t *tracktotal,
                        const char *ip, int port);
    bool start();
    bool stop();
    ANativeWindow* reconfigure(int width, int height, int bitrate, int fps, int frameInterval, int profile, int codec, AMediaMuxer *muxer);
    bool release();
};

#endif //STREAMER_VIDEOENCODER_H
