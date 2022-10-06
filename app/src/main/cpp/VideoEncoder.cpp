//
// Created by lynx.liu on 2022/9/28.
//

#include "VideoEncoder.h"

#define  LOG_TAG    "llx"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

int64_t systemnanotime() {
    timespec now;
    clock_gettime(CLOCK_MONOTONIC, &now);
    return now.tv_sec * 1000000000LL + now.tv_nsec;
}

VideoEncoder::VideoEncoder()
{
    nWidth = 0;
    nHeight = 0;
    thread = 0;
    mVideoTrack = -1;
    mIsRecording = false;
}

VideoEncoder::~VideoEncoder(void)
{
    release();
}

ANativeWindow* VideoEncoder::init(int width, int height, int framerate, int bitrate, int minFps) {
    if(width==0 || height==0)
        return NULL;

    nWidth = width; nHeight = height;
    timeoutUs = minFps>0? 1000000L/minFps : -1;
    const char *VIDEO_MIME = "video/avc";

    AMediaFormat *videoFormat = AMediaFormat_new();
    AMediaFormat_setString(videoFormat, AMEDIAFORMAT_KEY_MIME, VIDEO_MIME);
    AMediaFormat_setInt32(videoFormat, AMEDIAFORMAT_KEY_WIDTH, nWidth);
    AMediaFormat_setInt32(videoFormat, AMEDIAFORMAT_KEY_HEIGHT, nHeight);
    AMediaFormat_setInt32(videoFormat, AMEDIAFORMAT_KEY_BIT_RATE,bitrate);
    AMediaFormat_setInt32(videoFormat, AMEDIAFORMAT_KEY_FRAME_RATE, framerate);
    AMediaFormat_setInt32(videoFormat, AMEDIAFORMAT_KEY_I_FRAME_INTERVAL, 1);
    AMediaFormat_setInt32(videoFormat, AMEDIAFORMAT_KEY_REPEAT_PREVIOUS_FRAME_AFTER, REPEAT_FRAME_DELAY_US); // µs
    AMediaFormat_setInt32(videoFormat, AMEDIAFORMAT_KEY_COLOR_FORMAT, 0x7F000789); //COLOR_FormatSurface

    videoCodec = AMediaCodec_createEncoderByType(VIDEO_MIME);
    media_status_t videoConfigureStatus = AMediaCodec_configure(videoCodec,
                                                                videoFormat, NULL, NULL, AMEDIACODEC_CONFIGURE_FLAG_ENCODE);
    AMediaFormat_delete(videoFormat);
    if (AMEDIA_OK != videoConfigureStatus) {
        LOGE("set video configure failed status-->%d", videoConfigureStatus);
        release();
        return NULL;
    }

    media_status_t createInputSurfaceStatus = AMediaCodec_createInputSurface(videoCodec, &surface);
    if (AMEDIA_OK != createInputSurfaceStatus) {
        LOGE("create Input Surface failed status-->%d", createInputSurfaceStatus);
        release();
        return NULL;
    }

    LOGI("init videoCodec success");
    return surface;
}

bool VideoEncoder::start(const char *filename) {
    int fd = open(filename, O_CREAT | O_RDWR, 0666);
    if (!fd) {
        LOGE("open media file failed-->%d", fd);
        release();
        return false;
    }
    mMuxer = AMediaMuxer_new(fd, AMEDIAMUXER_OUTPUT_FORMAT_MPEG_4);
    AMediaMuxer_setOrientationHint(mMuxer, 0); //旋转角度

    ///////////////////////////////////////////////////////////////////////////////////////////////////
    media_status_t videoStatus = AMediaCodec_start(videoCodec);
    if (AMEDIA_OK != videoStatus) {
        LOGI("open videoCodec status-->%d", videoStatus);
        release();
        return false;
    }

    mIsRecording = true;
    if(pthread_create(&thread, NULL, encode_thread, this)!=0) {
        LOGI("encode_thread failed!");
        release();
        return false;
    }
    return true;
}

void VideoEncoder::dequeueOutput() {
    struct timeval tv;
    gettimeofday(&tv, NULL);
    long start = tv.tv_sec * 1000 + tv.tv_usec / 1000;

    AMediaCodecBufferInfo *info = (AMediaCodecBufferInfo *) malloc(sizeof(AMediaCodecBufferInfo));
    ssize_t outIndex = -1;
    do {
        outIndex = AMediaCodec_dequeueOutputBuffer(videoCodec, info, timeoutUs);
        LOGI("AMediaCodec_dequeueOutputBuffer %zd", outIndex);

        size_t out_size = 0;
        if (outIndex >= 0) {
            uint8_t *outputBuffer = AMediaCodec_getOutputBuffer(videoCodec,
                                                                outIndex, &out_size);
            if (mVideoTrack >= 0 && info->size > 0
                && info->presentationTimeUs > 0) {
                AMediaMuxer_writeSampleData(mMuxer, mVideoTrack, outputBuffer,
                                            info);
            }
            AMediaCodec_releaseOutputBuffer(videoCodec, outIndex,
                                            false);
            if (mIsRecording) {
                continue;
            }
        } else if (outIndex == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
            AMediaFormat *outFormat = AMediaCodec_getOutputFormat(videoCodec);
            ssize_t track = AMediaMuxer_addTrack(mMuxer, outFormat);
            const char *s = AMediaFormat_toString(outFormat);
            mVideoTrack = 0;
            LOGI("video out format %s", s);
            LOGE("add video track status-->%zd", track);
            if (mVideoTrack >= 0) {
                AMediaMuxer_start(mMuxer);
            }
        }
    } while (outIndex >= 0);

    gettimeofday(&tv, NULL);
    LOGI("dequeueOutput: %ld\r\n",
         tv.tv_sec * 1000 + tv.tv_usec / 1000 - start);
}

void* VideoEncoder::encode_thread(void *arg) {
    VideoEncoder *videoEncoder =(VideoEncoder *)arg;
    while(videoEncoder->mIsRecording) {
        videoEncoder->dequeueOutput();
    }
    LOGI("encode_thread exit");
    return 0;
}

bool VideoEncoder::isRecording() {
    return mIsRecording;
}

void VideoEncoder::release() {
    mIsRecording = false;

    if (videoCodec != NULL) {
        AMediaCodec_stop(videoCodec);
        AMediaCodec_delete(videoCodec);
        videoCodec = NULL;
    }

    if(surface != NULL) {
        ANativeWindow_release(surface);
        surface = NULL;
    }

    mVideoTrack = -1;

    if (mMuxer != NULL) {
        AMediaMuxer_stop(mMuxer);
        AMediaMuxer_delete(mMuxer);
        mMuxer = NULL;
    }
}
