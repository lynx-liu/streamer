//
// Created by lynx.liu on 2023/02/07.
//

#include "SceneDetect.h"

#define  LOG_TAG    "llx"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

#define NDK_DEBUG   0

cv::Rect getROI(cv::Mat gray)
{
    GaussianBlur(gray, gray, cv::Size(5, 5), 0);
    std::vector<std::vector<cv::Point> > contours;
    std::vector<cv::Vec4i> hierarchy;
    contours.clear();
    hierarchy.clear();
    cv::findContours(gray, contours, hierarchy, cv::RETR_EXTERNAL, cv::CHAIN_APPROX_SIMPLE);

    cv::Rect rect, temp;
    unsigned int N = contours.size();
    if (N <= 0) return rect;
    int rarea = -1;
    for (unsigned int i = 0; i < N; ++i)
    {
        temp = boundingRect(contours[i]);
        if (temp.area() > rarea)
        {
            rarea = temp.area();
            rect = temp;
        }
    }
    return rect;
}

#ifdef __cplusplus
extern "C" {
#endif

cv::Mat targetMat;
float degree = 0.8;
int thresh = -1;
int dilateValue = -1;
cv::Rect targetROI;
bool autoROI = false;

void _init(void) {
}

jint JNI_OnLoad(JavaVM* vm, void* reserved) //这个类似android的生命周期，加载jni的时候会自己调用
{
    return JNI_VERSION_1_6;
}

long systemnanotime() {
    timespec now = {0};
    clock_gettime(CLOCK_MONOTONIC, &now);
    return now.tv_sec * 1000 + now.tv_nsec/1000000;
}

double compare(cv::InputArray gray, cv::InputArray traget) {
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

JNIEXPORT jboolean JNICALL Java_com_vrviu_streamer_SceneDetect_init(JNIEnv *env, jobject thiz, jstring targetFile, jfloat matchDegree, jint threshold,
                                                                    jint dilate, jint roiX, jint roiY, jint roiW, jint roiH) {
    if(targetFile!= nullptr) {
        const char* filename = env->GetStringUTFChars(targetFile, nullptr);
        if(filename) {
            int scale = 1;
            int flags = cv::IMREAD_REDUCED_GRAYSCALE_4;
            switch(flags) {
                case cv::IMREAD_REDUCED_GRAYSCALE_2:
                case cv::IMREAD_REDUCED_COLOR_2:
                case cv::IMREAD_REDUCED_GRAYSCALE_4:
                case cv::IMREAD_REDUCED_COLOR_4:
                case cv::IMREAD_REDUCED_GRAYSCALE_8:
                case cv::IMREAD_REDUCED_COLOR_8:
                    scale = flags/8;
                    break;
            }

            targetMat = cv::imread(filename,flags);
            equalizeHist(targetMat, targetMat);//直方图均衡化

            degree = matchDegree;
            thresh = threshold;
            if(thresh>0) cv::threshold(targetMat, targetMat, thresh&0xFF, 255, (thresh>>8)&0xFF);

            dilateValue = dilate;
            if(dilateValue>0) cv::dilate(targetMat, targetMat, cv::Mat(dilateValue,dilateValue,CV_8UC1));

            autoROI = roiX==-1 && roiY==-1 && roiW==-1 && roiH==-1;
            if(autoROI) {
                targetROI = getROI(targetMat);
            } else {
                targetROI = cv::Rect(roiX/scale,roiY/scale,roiW/scale,roiH/scale);
            }
#if NDEBUG
            char picFile[MAX_INPUT] = {0};
            sprintf(picFile,"/sdcard/Capture/%ld.jpg",systemnanotime());
            cv::imwrite(picFile,targetMat);
#endif
        }
        env->ReleaseStringUTFChars(targetFile, filename);
    }
    return !targetMat.empty();
}

JNIEXPORT jboolean JNICALL Java_com_vrviu_streamer_SceneDetect_detect(JNIEnv *env, jobject thiz, jintArray pixel, jint width, jint height) {
    jint *buf = env->GetIntArrayElements(pixel, nullptr);
    if (buf == nullptr)
        return false;

    cv::Mat imgData(height, width, CV_8UC4, buf);
    env->ReleaseIntArrayElements(pixel, buf, 0);

    cv::cvtColor(imgData, imgData, cv::COLOR_BGRA2GRAY);
    cv::resize(imgData,imgData,targetMat.size());
    equalizeHist(imgData, imgData);//直方图均衡化
    if(thresh>0) cv::threshold(imgData, imgData, thresh&0xFF, 255, (thresh>>8)&0xFF);
    if(dilateValue>0) cv::dilate(imgData, imgData, cv::Mat(dilateValue,dilateValue,CV_8UC1));

    if(targetROI.empty())
        return compare(imgData,targetMat)>degree;

    if(autoROI) {
        cv::Rect imgROI = getROI(imgData);
        if(imgROI.empty()) return false;
        cv::resize(imgData(imgROI), imgData, targetROI.size());

#if NDK_DEBUG
        char filename[MAX_INPUT] = {0};
        sprintf(filename,"/sdcard/Capture/%ld.jpg",systemnanotime());
        cv::imwrite(filename,imgData);
#endif
        return compare(imgData,targetMat(targetROI))>degree;
    }
    return compare(imgData(targetROI), targetMat(targetROI))>degree;
}

JNIEXPORT jboolean JNICALL Java_com_vrviu_streamer_SceneDetect_release(JNIEnv *env, jobject thiz) {
    targetMat.release();
    return true;
}

#ifdef __cplusplus
}
#endif