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

uint8_t trackTotal = 0;
AMediaMuxer *mMuxer = NULL;
AudioEncoder *audioEncoder = new AudioEncoder();
VideoEncoder *videoEncoder = new VideoEncoder();

void _init(void) {
}

jint JNI_OnLoad(JavaVM* vm, void* reserved) //这个类似android的生命周期，加载jni的时候会自己调用
{
    return JNI_VERSION_1_6;
}

JNIEXPORT jobject JNICALL Java_com_vrviu_streamer_MediaEncoder_init(JNIEnv *env, jobject thiz, int width, int height,
                                                                    int maxFps, int bitrate, int minFps, jint codec, int profile,
                                                                    int frameInterval, int bitrateMode, int audioMimeType,
                                                                    int defaulQP, int maxQP, int minQP,
                                                                    jstring _ip, jint videoPort, jint audioPort, jstring fileName) {
    if(fileName!= nullptr) {
        const char* filename = env->GetStringUTFChars(fileName, NULL);

        if(filename) {
            int fd = open(filename, O_CREAT | O_LARGEFILE | O_TRUNC | O_RDWR, S_IRUSR | S_IWUSR);
            if (!fd) {
                LOGE("open media file failed-->%d", fd);
            } else {
                mMuxer = AMediaMuxer_new(fd, AMEDIAMUXER_OUTPUT_FORMAT_MPEG_4);
                AMediaMuxer_setOrientationHint(mMuxer, 0); //旋转角度
                close(fd);
            }
        }
        env->ReleaseStringUTFChars(fileName, filename);
    }

    const char* ip = env->GetStringUTFChars(_ip,NULL);
    audioEncoder->init(env, audioMimeType, mMuxer, reinterpret_cast<int8_t *>(&trackTotal), ip, audioPort);
    ANativeWindow *nativeWindow = videoEncoder->init(width, height, maxFps, bitrate, minFps, codec, profile, frameInterval, bitrateMode,
                                                     defaulQP, maxQP, minQP, mMuxer, reinterpret_cast<int8_t *>(&trackTotal),
                                                     ip, videoPort);
    env->ReleaseStringUTFChars(_ip, ip);

    if(mMuxer!= nullptr) {
        LOGI("trackTotal: %d", trackTotal);
    }
    return ANativeWindow_toSurface(env,nativeWindow);
}

JNIEXPORT jobject JNICALL Java_com_vrviu_streamer_MediaEncoder_reconfigure(JNIEnv *env, jobject thiz, int width, int height,
                                                                           int bitrate, int fps, int frameInterval, int profile, int codec) {
    ANativeWindow *nativeWindow = videoEncoder->reconfigure(width, height, bitrate, fps, frameInterval, profile, codec);
    return ANativeWindow_toSurface(env,nativeWindow);
}

JNIEXPORT jboolean JNICALL Java_com_vrviu_streamer_MediaEncoder_start(JNIEnv *env, jobject thiz, jboolean onlyVideo) {
    if(onlyVideo) return videoEncoder->start();
    return videoEncoder->start() & audioEncoder->start();
}

JNIEXPORT jboolean JNICALL Java_com_vrviu_streamer_MediaEncoder_stop(JNIEnv *env, jobject thiz) {
    audioEncoder->release();
    videoEncoder->release();

    if (mMuxer) {
        LOGI("trackTotal: %d", trackTotal);
        AMediaMuxer_stop(mMuxer);
        AMediaMuxer_delete(mMuxer);
        mMuxer = nullptr;
    }
    return true;
}

JNIEXPORT void JNICALL Java_com_vrviu_streamer_MediaEncoder_requestSyncFrame(JNIEnv *env, jobject thiz) {
    videoEncoder->requestSyncFrame();
}

JNIEXPORT void JNICALL Java_com_vrviu_streamer_MediaEncoder_setVideoBitrate(JNIEnv *env, jobject thiz, jint bitrate) {
    videoEncoder->setVideoBitrate(bitrate);
}

#ifdef __cplusplus
}
#endif