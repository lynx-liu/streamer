//
// Created by lynx.liu on 2023/02/07.
//

#ifndef STREAMER_SCENEDETECT_H
#define STREAMER_SCENEDETECT_H

#include <jni.h>
#include <android/log.h>
#include <opencv2/opencv.hpp>

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jboolean JNICALL Java_com_vrviu_streamer_SceneDetect_init(JNIEnv *env, jobject thiz, jstring targetFile, jfloat threshold, jint roiX, jint roiY, jint roiW, jint roiH);
JNIEXPORT jboolean JNICALL Java_com_vrviu_streamer_SceneDetect_detect(JNIEnv *env, jobject thiz, jbyteArray pixel, jint width, jint height);
JNIEXPORT jboolean JNICALL Java_com_vrviu_streamer_SceneDetect_release(JNIEnv *env, jobject thiz);
#ifdef __cplusplus
}
#endif

#endif //STREAMER_SCENEDETECT_H