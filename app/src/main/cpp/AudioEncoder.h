//
// Created by lynx.liu on 2022/10/9.
//

#ifndef STREAMER_AUDIOENCODER_H
#define STREAMER_AUDIOENCODER_H

#include <malloc.h>
#include <memory.h>
#include <pthread.h>
#include <unistd.h>
#include <arpa/inet.h>
#include <sys/resource.h>
#include <jni.h>
#include <android/log.h>

#include <media/NdkMediaMuxer.h>
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>

#define CHANNEL_IN_STEREO       0xC //双声道 (CHANNEL_IN_LEFT | CHANNEL_IN_RIGHT)
#define CHANNEL_COUNT           2
#define ENCODING_PCM_16BIT      2
#define REMOTE_SUBMIX           8
#define SAMPLE_RATE             48000

#define AUDIO_PCM               0
#define AUDIO_OPUS              1
#define AUDIO_AAC               2

#define BUF_SIZE                4096

#pragma pack(push)
#pragma pack(1)
struct AudioHeader {
    uint32_t size = 0;
    uint8_t type = 0;
    uint8_t channel = 0;
    int16_t sampleRate = 0;
};
#pragma pack(pop)

class AudioEncoder
{
private:
    pthread_t getpcm_tid = 0;
    pthread_t encode_tid = 0;
    int m_sockfd = -1;

    JavaVM* jvm = nullptr;
    AMediaCodec *audioCodec = NULL;
    AMediaMuxer *mMuxer = NULL;
    int mAudioTrack;
    bool mIsRecording;
    int8_t *trackTotal;
    int8_t audioType;

private:
    inline void dequeueOutput(AMediaCodecBufferInfo *info);
    static void* getpcm_thread(void *arg);
    static void* encode_thread(void *arg);
    inline void onPcmData(uint8_t *bytes,uint32_t size) const;
    inline void onEncodeFrame(uint8_t *bytes,uint32_t size,uint8_t type,uint8_t channel,uint16_t sampleRate) const;
    static int connectSocket(const char *ip, int port);

public:
    AudioEncoder();
    ~AudioEncoder();
    bool createEncoder(AMediaMuxer *muxer);
    bool init(JNIEnv *env, int mimeType, AMediaMuxer *muxer, int8_t *tracktotal, const char *ip, int port);
    bool start();
    void stop();
    void release();
};

#endif //STREAMER_AUDIOENCODER_H
