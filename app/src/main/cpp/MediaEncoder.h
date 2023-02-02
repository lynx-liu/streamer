//
// Created by lynx.liu on 2022/9/28.
//

#ifndef STREAMER_MEDIAENCODER_H
#define STREAMER_MEDIAENCODER_H

#include <jni.h>
#include <android/log.h>
#include <android/native_window_jni.h>

#include "AudioEncoder.h"
#include "VideoEncoder.h"

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jobject JNICALL Java_com_vrviu_streamer_MediaEncoder_init(JNIEnv *env, jobject thiz, int width, int height, int maxFps, int bitrate, int minFps, jboolean h264, int profile, int iFrameInterval, int bitrateMode, int audioMimeType, int defaulQP, int maxQP, int minQP, jstring fileName);
JNIEXPORT jboolean JNICALL Java_com_vrviu_streamer_MediaEncoder_start(JNIEnv *env, jobject thiz, jstring ip, jint videoPort, jint audioPort);
JNIEXPORT jboolean JNICALL Java_com_vrviu_streamer_MediaEncoder_stop(JNIEnv *env, jobject thiz);
JNIEXPORT void JNICALL Java_com_vrviu_streamer_MediaEncoder_requestSyncFrame(JNIEnv *env, jobject thiz);
JNIEXPORT void JNICALL Java_com_vrviu_streamer_MediaEncoder_setVideoBitrate(JNIEnv *env, jobject thiz, jint bitrate);
#ifdef __cplusplus
}
#endif

#endif //STREAMER_MEDIAENCODER_H