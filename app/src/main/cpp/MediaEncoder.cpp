//
// Created by lynx.liu on 2022/9/28.
//

#include "MediaEncoder.h"

#define  LOG_TAG    "llx"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

#ifdef __cplusplus
extern "C" {
#endif

int fd = 0;
pthread_mutex_t mutex;
uint8_t trackTotal = 0;
AMediaMuxer *mMuxer = NULL;
OutputFormat format = AMEDIAMUXER_OUTPUT_FORMAT_MPEG_4;
AudioEncoder *audioEncoder = new AudioEncoder();
VideoEncoder *videoEncoder = new VideoEncoder();

void _init(void) {
}

inline int32_t currentTimeMillis() {
    timespec now{};
    clock_gettime(CLOCK_MONOTONIC, &now);
    return (now.tv_sec * 1000000000LL + now.tv_nsec)/1000000;
}

jint JNI_OnLoad(JavaVM* vm, void* reserved) //这个类似android的生命周期，加载jni的时候会自己调用
{
    pthread_mutex_init(&mutex, NULL);
    return JNI_VERSION_1_6;
}

JNIEXPORT jobject JNICALL Java_com_vrviu_streamer_MediaEncoder_init(JNIEnv *env, jobject thiz, int width, int height,
                                                                    int maxFps, int bitrate, int minFps, jint codec, int profile,
                                                                    int idrPeriod, int bitrateMode, int audioMimeType,
                                                                    int defaulQP, int maxQP, int minQP,
                                                                    jstring _ip, jint videoPort, jint audioPort, jboolean dump) {
    pthread_mutex_lock(&mutex);
    if(dump) {
        char filename[NAME_MAX] = {0};
        format = codec>HEVC?AMEDIAMUXER_OUTPUT_FORMAT_WEBM:AMEDIAMUXER_OUTPUT_FORMAT_MPEG_4;
        sprintf(filename,"/sdcard/DCIM/%d.%s",currentTimeMillis(),format==AMEDIAMUXER_OUTPUT_FORMAT_WEBM?"webm":"mp4");
        fd = open(filename, O_CREAT | O_LARGEFILE | O_TRUNC | O_RDWR, S_IRUSR | S_IWUSR);
        if (!fd) {
            LOGE("open media file failed-->%d", fd);
        } else {
            mMuxer = AMediaMuxer_new(fd, format);
            AMediaMuxer_setOrientationHint(mMuxer, 0); //旋转角度
        }
    }

    const char* ip = env->GetStringUTFChars(_ip,NULL);
    audioEncoder->init(env, audioMimeType, mMuxer, reinterpret_cast<int8_t *>(&trackTotal), ip, audioPort);
    ANativeWindow *nativeWindow = videoEncoder->init(width, height, maxFps, bitrate, minFps, codec, profile, idrPeriod, bitrateMode,
                                                     defaulQP, maxQP, minQP, mMuxer, reinterpret_cast<int8_t *>(&trackTotal),
                                                     ip, videoPort);
    env->ReleaseStringUTFChars(_ip, ip);
    if(mMuxer!= nullptr) {
        LOGI("trackTotal: %d", trackTotal);
    }

    pthread_mutex_unlock(&mutex);
    return ANativeWindow_toSurface(env,nativeWindow);
}

JNIEXPORT jobject JNICALL Java_com_vrviu_streamer_MediaEncoder_reconfigure(JNIEnv *env, jobject thiz, int width, int height,
                                                                           int bitrate, int fps, int idrPeriod, int profile, int codec,
                                                                           int defaulQP, int minQP, int maxQP, int rateControlMode) {
    pthread_mutex_lock(&mutex);
    videoEncoder->stop();

    if (mMuxer) {
        audioEncoder->stop();

        LOGI("trackTotal: %d", trackTotal);
        AMediaMuxer_stop(mMuxer);
        AMediaMuxer_delete(mMuxer);
        mMuxer = nullptr;

        if(fd) {
            close(fd);
            char filename[NAME_MAX] = {0};
            if(codec!=-1) format = codec>HEVC?AMEDIAMUXER_OUTPUT_FORMAT_WEBM:AMEDIAMUXER_OUTPUT_FORMAT_MPEG_4;
            sprintf(filename,"/sdcard/DCIM/%d.%s",currentTimeMillis(),format==AMEDIAMUXER_OUTPUT_FORMAT_WEBM?"webm":"mp4");
            fd = open(filename, O_CREAT | O_LARGEFILE | O_TRUNC | O_RDWR, S_IRUSR | S_IWUSR);

            mMuxer = AMediaMuxer_new(fd, format);
            AMediaMuxer_setOrientationHint(mMuxer, 0); //旋转角度
        }

        audioEncoder->createEncoder(mMuxer);
    }

    ANativeWindow *nativeWindow = videoEncoder->reconfigure(width, height, bitrate, fps, idrPeriod, profile, codec, defaulQP, minQP, maxQP, rateControlMode, mMuxer);
    pthread_mutex_unlock(&mutex);
    return ANativeWindow_toSurface(env,nativeWindow);
}

JNIEXPORT jboolean JNICALL Java_com_vrviu_streamer_MediaEncoder_start(JNIEnv *env, jobject thiz) {
    pthread_mutex_lock(&mutex);
    bool bRet = audioEncoder->start() && videoEncoder->start();
    pthread_mutex_unlock(&mutex);
    return bRet;
}

JNIEXPORT jboolean JNICALL Java_com_vrviu_streamer_MediaEncoder_release(JNIEnv *env, jobject thiz) {
    pthread_mutex_lock(&mutex);
    audioEncoder->release();
    videoEncoder->release();

    if (mMuxer) {
        LOGI("trackTotal: %d", trackTotal);
        AMediaMuxer_stop(mMuxer);
        AMediaMuxer_delete(mMuxer);
        mMuxer = nullptr;
    }

    if(fd) {
        close(fd);
        fd = 0;
    }
    pthread_mutex_unlock(&mutex);
    return true;
}

JNIEXPORT void JNICALL Java_com_vrviu_streamer_MediaEncoder_requestSyncFrame(JNIEnv *env, jobject thiz) {
    pthread_mutex_lock(&mutex);
    videoEncoder->requestSyncFrame();
    pthread_mutex_unlock(&mutex);
}

JNIEXPORT void JNICALL Java_com_vrviu_streamer_MediaEncoder_setVideoBitrate(JNIEnv *env, jobject thiz, jint bitrate) {
    pthread_mutex_lock(&mutex);
    videoEncoder->setVideoBitrate(bitrate);
    pthread_mutex_unlock(&mutex);
}

#ifdef __cplusplus
}
#endif