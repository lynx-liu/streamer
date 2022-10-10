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

#include <jni.h>
#include <android/log.h>

#define CHANNEL_IN_STEREO       0xC //双声道 (CHANNEL_IN_LEFT | CHANNEL_IN_RIGHT)
#define CHANNEL_COUNT           2
#define ENCODING_PCM_16BIT      2
#define REMOTE_SUBMIX           8
#define SAMPLE_RATE             48000
#define PCM_DATA                0

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
    pthread_t encode_tid = 0;
    int m_sockfd = -1;
    bool mIsRecording;

    JavaVM* jvm = nullptr;

private:
    static void* encode_thread(void *arg);
    inline void onEncodeFrame(uint8_t *bytes,uint32_t size,uint8_t type,uint8_t channel,uint16_t sampleRate) const;
    static int connectSocket(const char *ip, int port);

public:
    AudioEncoder();
    ~AudioEncoder();
    bool start(JNIEnv *env, const char *ip, int port);
    void release();
};

#endif //STREAMER_AUDIOENCODER_H
