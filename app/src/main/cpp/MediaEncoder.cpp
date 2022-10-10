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

AudioEncoder *audioEncoder = new AudioEncoder();
VideoEncoder *videoEncoder = new VideoEncoder();

void _init(void) {
}

jint JNI_OnLoad(JavaVM* vm, void* reserved) //这个类似android的生命周期，加载jni的时候会自己调用
{
    return JNI_VERSION_1_6;
}

JNIEXPORT jobject JNICALL Java_com_vrviu_streamer_MediaEncoder_init(JNIEnv *env, jobject thiz, int width, int height, int framerate, int bitrate, int minFps) {
    ANativeWindow *nativeWindow = videoEncoder->init(width, height, framerate,bitrate, minFps);
    return ANativeWindow_toSurface(env,nativeWindow);
}

JNIEXPORT jboolean JNICALL Java_com_vrviu_streamer_MediaEncoder_start(JNIEnv *env, jobject thiz, jstring _ip, jint videoPort, jint audioPort, jstring _filename) {
    const char* ip = env->GetStringUTFChars(_ip,NULL);
    const char* filename = nullptr;
    if(_filename!= nullptr) {
        filename = env->GetStringUTFChars(_filename, NULL);
    }

    bool ret = videoEncoder->start(ip,videoPort,filename) & audioEncoder->start(env,ip,audioPort);

    if(_filename!= nullptr) {
        env->ReleaseStringUTFChars(_filename, filename);
    }
    env->ReleaseStringUTFChars(_ip, ip);
    return ret;
}

JNIEXPORT jboolean JNICALL Java_com_vrviu_streamer_MediaEncoder_stop(JNIEnv *env, jobject thiz) {
    audioEncoder->release();
    videoEncoder->release();
    return true;
}

#ifdef __cplusplus
}
#endif