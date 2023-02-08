//
// Created by lynx.liu on 2023/02/07.
//

#include "SceneDetect.h"

#define  LOG_TAG    "llx"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

#define NDK_DEBUG   0

#ifdef __cplusplus
extern "C" {
#endif

cv::Mat targetMat;
float degree = 0.8;

void _init(void) {
}

jint JNI_OnLoad(JavaVM* vm, void* reserved) //这个类似android的生命周期，加载jni的时候会自己调用
{
    return JNI_VERSION_1_6;
}

long systemnanotime() {
    timespec now;
    clock_gettime(CLOCK_MONOTONIC, &now);
    return now.tv_sec * 1000 + now.tv_nsec/1000000;
}

double compare(cv::Mat gray, cv::Mat traget) {
    /*
    ** 相似度计算方法
    ** 0：cv::TM_SQDIFF        平方差匹配法，最好的匹配值为0；匹配越差，匹配值越大
    ** 1：cv::TM_SQDIFF_NORMED 归一化平方差匹配法, 0表示完美匹配，1表示最差匹配
    ** 2：cv::TM_CCORR         相关匹配法：该方法采用乘法操作；数值越大表明匹配程度越好
    ** 3：cv::TM_CCORR_NORMED  归一化相关匹配法
    ** 4：cv::TM_CCOEFF        相关系数匹配法：1表示完美的匹配；-1表示最差的匹配。
    ** 5：cv::TM_CCOEFF_NORMED 归一化相关系数匹配法
    */
    cv::Mat result;
    matchTemplate(gray, traget, result, cv::TM_SQDIFF_NORMED);

    double minValue = 1.0;
    minMaxLoc(result, &minValue);

    double ret = 1.0-minValue;
    LOGI("compare: %lf", ret);
    return ret;
}

JNIEXPORT jboolean JNICALL Java_com_vrviu_streamer_SceneDetect_init(JNIEnv *env, jobject thiz, jstring targetFile, jfloat threshold) {
    if(targetFile!= nullptr) {
        const char* filename = env->GetStringUTFChars(targetFile, NULL);
        if(filename) {
            targetMat = cv::imread(filename,cv::IMREAD_REDUCED_GRAYSCALE_4);
            equalizeHist(targetMat, targetMat);//直方图均衡化
            degree = threshold;
#if NDEBUG
            char filename[MAX_INPUT] = {0};
            sprintf(filename,"/sdcard/Capture/%ld.jpg",systemnanotime());
            cv::imwrite(filename,targetMat);
#endif
        }
        env->ReleaseStringUTFChars(targetFile, filename);
    }
    return !targetMat.empty();
}

JNIEXPORT jboolean JNICALL Java_com_vrviu_streamer_SceneDetect_detect(JNIEnv *env, jobject thiz, jbyteArray pixel, jint width, jint height) {
    jbyte *buf = env->GetByteArrayElements(pixel, NULL);
    if (buf == NULL)
        return 0;

    cv::Mat imgData(height, width, CV_8UC4, buf);
    env->ReleaseByteArrayElements(pixel, buf, 0);

#if NDK_DEBUG
    char filename[MAX_INPUT] = {0};
    cv::cvtColor(imgData, imgData, cv::COLOR_BGRA2RGBA);
    sprintf(filename,"/sdcard/Capture/%ld.jpg",systemnanotime());
    cv::imwrite(filename,imgData);
#endif
    cv::cvtColor(imgData, imgData, cv::COLOR_BGRA2GRAY);
    cv::resize(imgData,imgData,targetMat.size());
    equalizeHist(imgData, imgData);//直方图均衡化
    return compare(imgData,targetMat)>degree;
}

JNIEXPORT jboolean JNICALL Java_com_vrviu_streamer_SceneDetect_release(JNIEnv *env, jobject thiz) {
    targetMat.release();
    return true;
}

#ifdef __cplusplus
}
#endif