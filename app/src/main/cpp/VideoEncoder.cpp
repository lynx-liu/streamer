//
// Created by lynx.liu on 2022/9/28.
//

#include "VideoEncoder.h"

#define  LOG_TAG    "llx"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

#define NDK_DEBUG   0
#define CALL_BACK   1

#define NAME(variable) (#variable)
#define COLOR_FormatSurface                 0x7F000789
#define REPEAT_FRAME_DELAY_US               50000 // repeat after 50ms
#define AMEDIACODEC_BUFFER_FLAG_KEY_FRAME   1

const char PARAMETER_KEY_REQUEST_SYNC_FRAME[] = "request-sync";
const char PARAMETER_KEY_VIDEO_BITRATE[] = "video-bitrate";
const char KEY_MAX_B_FRAMES[] = "max-bframes";
const char KEY_PREPEND_HEADER_TO_SYNC_FRAMES[] = "prepend-sps-pps-to-idr-frames";

inline int64_t systemnanotime() {
    timespec now{};
    clock_gettime(CLOCK_MONOTONIC, &now);
    return now.tv_sec * 1000000000LL + now.tv_nsec;
}

inline int32_t systemmilltime() {
    return systemnanotime()/1000000;
}

VideoEncoder::VideoEncoder()
{
    encode_tid = 0;
    m_sockfd = -1;
    send_tid = 0;
    mVideoTrack = -1;
    videoParam.videoType = AVC;
    mIsRecording = false;
    mIsSending = false;
    memset(&spspps,0,sizeof(spspps));
}

VideoEncoder::~VideoEncoder()
{
    release();
}

void VideoEncoder::requestSyncFrame() {
    if(videoCodec== nullptr) return;
    AMediaFormat *videoFormat = AMediaFormat_new();
    AMediaFormat_setInt32(videoFormat, PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
    AMediaCodec_setParameters(videoCodec,videoFormat);
    AMediaFormat_delete(videoFormat);
}

void VideoEncoder::setVideoBitrate(int bitrate) {
    if(videoCodec== nullptr) return;
    AMediaFormat *videoFormat = AMediaFormat_new();
    AMediaFormat_setInt32(videoFormat, PARAMETER_KEY_VIDEO_BITRATE, bitrate);
    AMediaCodec_setParameters(videoCodec,videoFormat);
    AMediaFormat_delete(videoFormat);
}

ANativeWindow* VideoEncoder::init(int width, int height, int maxFps, int bitrate, int minFps, int codec, int profile, int frameInterval,
                                  int bitrateMode, int defaulQP, int maxQP, int minQP, AMediaMuxer *muxer, int8_t *tracktotal,
                                  const char *ip, int port) {
    m_sockfd = connectSocket(ip,port);
    if(m_sockfd<0) {
        LOGE("video connectSocket failed!");
        release();
        return nullptr;
    }

    mIsSending = true;
    if(pthread_create(&send_tid, nullptr, send_video_thread, this)!=0) {
        LOGE("video send_thread failed!");
        release();
        return nullptr;
    }

    trackTotal = tracktotal;

    videoParam.width = width;
    videoParam.height = height;
    videoParam.bitrate = bitrate;
    videoParam.minFps = minFps;
    videoParam.maxFps = maxFps;
    videoParam.frameInterval = frameInterval;
    videoParam.bitrateMode = bitrateMode;
    videoParam.profile = profile;
    videoParam.videoType = static_cast<VideoType>(codec);
    videoParam.defaulQP = defaulQP;
    videoParam.minQP = minQP;
    videoParam.maxQP = maxQP;
    return createEncoder(muxer);
}

ANativeWindow* VideoEncoder::createEncoder(AMediaMuxer *muxer) {
    if(videoParam.width==0 || videoParam.height==0)
        return nullptr;

    mMuxer = muxer;
    if(mMuxer!= nullptr) {
        (*trackTotal)++;
    }

    timeoutUs = videoParam.minFps>0? 1000000L/videoParam.minFps : -1;
    const char *VIDEO_MIME = videoParam.videoType==AVC?"video/avc":"video/hevc";

    AMediaFormat *videoFormat = AMediaFormat_new();
    AMediaFormat_setString(videoFormat, AMEDIAFORMAT_KEY_MIME, VIDEO_MIME);
    AMediaFormat_setInt32(videoFormat, AMEDIAFORMAT_KEY_WIDTH, videoParam.width);
    AMediaFormat_setInt32(videoFormat, AMEDIAFORMAT_KEY_HEIGHT, videoParam.height);
    AMediaFormat_setInt32(videoFormat, AMEDIAFORMAT_KEY_BIT_RATE,videoParam.bitrate);
    AMediaFormat_setInt32(videoFormat, AMEDIAFORMAT_KEY_FRAME_RATE, videoParam.maxFps);
    AMediaFormat_setInt32(videoFormat, AMEDIAFORMAT_KEY_I_FRAME_INTERVAL, videoParam.frameInterval);
    AMediaFormat_setInt64(videoFormat, AMEDIAFORMAT_KEY_REPEAT_PREVIOUS_FRAME_AFTER, (long) (1.5*(1000000L/videoParam.maxFps+1))); // µs
    AMediaFormat_setInt32(videoFormat, AMEDIAFORMAT_KEY_COLOR_FORMAT, COLOR_FormatSurface);
    AMediaFormat_setFloat(videoFormat, AMEDIAFORMAT_KEY_MAX_FPS_TO_ENCODER, videoParam.maxFps);
    AMediaFormat_setInt32(videoFormat, KEY_MAX_B_FRAMES, 0);
    AMediaFormat_setInt32(videoFormat, KEY_PREPEND_HEADER_TO_SYNC_FRAMES, 0);//disable sync frames, otherwise the mp4 will not be played

    if(videoParam.defaulQP!=0 || videoParam.minQP!=0 || videoParam.maxQP!=0) {
        AMediaFormat_setInt32(videoFormat, "vendor.qti-ext-enc-initial-qp.qp-i", videoParam.defaulQP);
        AMediaFormat_setInt32(videoFormat, "vendor.qti-ext-enc-initial-qp.qp-i-enable", 1);
        AMediaFormat_setInt32(videoFormat, "vendor.qti-ext-enc-qp-range.qp-i-min", videoParam.minQP);
        AMediaFormat_setInt32(videoFormat, "vendor.qti-ext-enc-qp-range.qp-i-max", videoParam.maxQP);

        AMediaFormat_setInt32(videoFormat, "vendor.qti-ext-enc-initial-qp.qp-p", videoParam.defaulQP);
        AMediaFormat_setInt32(videoFormat, "vendor.qti-ext-enc-initial-qp.qp-p-enable", 1);
        AMediaFormat_setInt32(videoFormat, "vendor.qti-ext-enc-qp-range.qp-p-min", videoParam.minQP);
        AMediaFormat_setInt32(videoFormat, "vendor.qti-ext-enc-qp-range.qp-p-max", videoParam.maxQP);
    }

    if(videoParam.bitrateMode==0) {
        AMediaFormat_setInt32(videoFormat, AMEDIAFORMAT_KEY_BITRATE_MODE, 2);//MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
    } else {
        AMediaFormat_setInt32(videoFormat, AMEDIAFORMAT_KEY_BITRATE_MODE, 1);//MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR
    }

    if(videoParam.videoType==AVC) {
        AMediaFormat_setInt32(videoFormat, AMEDIAFORMAT_KEY_PROFILE, videoParam.profile);
    }else {
        AMediaFormat_setInt32(videoFormat, AMEDIAFORMAT_KEY_PROFILE, 0x01);//MediaCodecInfo.CodecProfileLevel.HEVCProfileMain
    }
    AMediaFormat_setInt32(videoFormat, AMEDIAFORMAT_KEY_LEVEL, 0x200);

    videoCodec = AMediaCodec_createEncoderByType(VIDEO_MIME);
    if(videoCodec== nullptr) {
        LOGE("video createEncoderByType failed");
        release();
        return nullptr;
    }

    media_status_t videoConfigureStatus = AMediaCodec_configure(videoCodec,
                                                                videoFormat, nullptr, nullptr, AMEDIACODEC_CONFIGURE_FLAG_ENCODE);
    AMediaFormat_delete(videoFormat);

    if (AMEDIA_OK != videoConfigureStatus) {
        LOGE("set video configure failed status-->%d", videoConfigureStatus);
        release();
        return nullptr;
    }

    media_status_t createInputSurfaceStatus = AMediaCodec_createInputSurface(videoCodec, &surface);
    if (AMEDIA_OK != createInputSurfaceStatus) {
        LOGE("create Input Surface failed status-->%d", createInputSurfaceStatus);
        release();
        return nullptr;
    }

    LOGI("init videoCodec success");
    return surface;
}

ANativeWindow* VideoEncoder::reconfigure(int width, int height, int bitrate, int fps, int frameInterval, int profile, int codec, AMediaMuxer *muxer) {
    if(codec!=-1) videoParam.videoType = static_cast<VideoType>(codec);
    if(width!=-1) videoParam.width = width;
    if(height!=-1) videoParam.height = height;
    if(bitrate!=-1) videoParam.bitrate = bitrate;
    if(fps!=-1) videoParam.maxFps = fps;
    if(frameInterval!=-1) videoParam.frameInterval = frameInterval;
    if(profile!=-1) videoParam.profile = profile;
    return createEncoder(muxer);
}

bool VideoEncoder::start() {
    if(mIsRecording)
        return true;
    mIsRecording = true;

    if(!videoCodec) {
        release();
        return false;
    }

#if CALL_BACK
    AMediaCodecOnAsyncNotifyCallback videoCallBack = {
            OnInputAvailableCB,
            OnOutputAvailableCB,
            OnFormatChangedCB,
            OnErrorCB
    };
    AMediaCodec_setAsyncNotifyCallback(videoCodec,videoCallBack,this);
#endif

    media_status_t videoStatus = AMediaCodec_start(videoCodec);
    if (AMEDIA_OK != videoStatus) {
        LOGE("open videoCodec status-->%d", videoStatus);
        release();
        return false;
    }

#if !CALL_BACK
    if(pthread_create(&encode_tid, nullptr, encode_thread, this)!=0) {
        LOGE("video encode_thread failed!");
        release();
        return false;
    }
#endif
    LOGI("video start success");
    return true;
}

void VideoEncoder::OnInputAvailableCB(AMediaCodec *mediaCodec, void *userdata, int32_t index) {
    LOGI("OnInputAvailableCB: index(%d)", index);
}

void VideoEncoder::OnOutputAvailableCB(AMediaCodec *mediaCodec, void *userdata, int32_t index, AMediaCodecBufferInfo *bufferInfo) {
    auto *videoEncoder =(VideoEncoder *)userdata;
    videoEncoder->notifyOutputAvailable(index,bufferInfo);
}

void VideoEncoder::OnFormatChangedCB(AMediaCodec *mediaCodec, void *userdata, AMediaFormat *format) {
    auto *videoEncoder =(VideoEncoder *)userdata;
    videoEncoder->onFormatChange(format);
}

void VideoEncoder::OnErrorCB(AMediaCodec *mediaCodec, void *userdata, media_status_t err, int32_t actionCode, const char *detail) {
    LOGE("OnErrorCB: err(%d), actionCode(%d), detail(%s)", err, actionCode, detail);
}

inline void VideoEncoder::notifyOutputAvailable(int32_t index, AMediaCodecBufferInfo *bufferInfo) {
    VideoInfo mediaInfo;
    memset(&mediaInfo,0,sizeof(mediaInfo));

    mediaInfo.outIndex = index;
    memcpy(&mediaInfo.bufferInfo,bufferInfo,sizeof(AMediaCodecBufferInfo));
    mediaInfoQueue.push(mediaInfo);
#if NDK_DEBUG
    LOGI("notify_all");
#endif
    condOut.notify_all();
}

inline void VideoEncoder::onOutputAvailable(int32_t outIndex, AMediaCodecBufferInfo *info) {
    if(!mIsRecording) return;

#if NDK_DEBUG
    LOGI("index(%d), (%d, %d, %lld, 0x%x)", outIndex, info->offset, info->size, (long long)info->presentationTimeUs, info->flags);
#endif
    size_t out_size = 0;
    uint8_t *outputBuffer = AMediaCodec_getOutputBuffer(videoCodec, outIndex, &out_size);
    if (info->size > 0 && info->presentationTimeUs > 0) {
        onEncodeFrame(outputBuffer+info->offset, info->size, info->flags, info->presentationTimeUs);

        if(mMuxer) {
            AMediaMuxer_writeSampleData(mMuxer, mVideoTrack, outputBuffer, info);
        }
    }

    AMediaCodec_releaseOutputBuffer(videoCodec, outIndex, false);
}

inline void VideoEncoder::onFormatChange(AMediaFormat *format) {
    const char *s = AMediaFormat_toString(format);
    LOGI("video out format %s", s);

    if(mMuxer) {
        mVideoTrack = AMediaMuxer_addTrack(mMuxer, format);
        LOGI("trackTotal: %d, videoTrack: %d", (*trackTotal), mVideoTrack);
        if(mVideoTrack>=(*trackTotal)-1) {
            AMediaMuxer_start(mMuxer);
            LOGI("MediaMuxer start");
        } else if(mVideoTrack<0) {
            (*trackTotal)--;
            mMuxer = nullptr;
        }
    }
}

inline void VideoEncoder::dequeueOutput(AMediaCodecBufferInfo *info) {
    ssize_t outIndex;
    do {
#if NDK_DEBUG
        int32_t start = systemmilltime();
#endif
        outIndex = AMediaCodec_dequeueOutputBuffer(videoCodec, info, timeoutUs);
#if NDK_DEBUG
        LOGI("AMediaCodec_dequeueOutputBuffer: %zd, %d, %d ms\r\n", outIndex, info->offset, systemmilltime() - start);
#endif
        if (outIndex >= 0) {
            notifyOutputAvailable(outIndex, info);
        } else if (outIndex == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
            AMediaFormat *outFormat = AMediaCodec_getOutputFormat(videoCodec);
            onFormatChange(outFormat);
        }

        if(info->flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) {
            LOGI("video AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM");
            break;
        }
    } while (mIsRecording);
}

void* VideoEncoder::encode_thread(void *arg) {
    auto *videoEncoder =(VideoEncoder *)arg;
    auto *info = (AMediaCodecBufferInfo *) malloc(sizeof(AMediaCodecBufferInfo));

    prctl(PR_SET_NAME,NAME(videoEncoder));
    setpriority(PRIO_PROCESS, getpid(), PRIO_MIN);
    setpriority(PRIO_PROCESS, gettid(), PRIO_MIN);

    while(videoEncoder->mIsRecording) {
        videoEncoder->dequeueOutput(info);
    }
    free(info);
    LOGI("video encode_thread exit");
    return nullptr;
}

void* VideoEncoder::send_video_thread(void *arg) {
    auto *videoEncoder =(VideoEncoder *)arg;

    prctl(PR_SET_NAME,__func__);
    setpriority(PRIO_PROCESS, getpid(), PRIO_MIN);
    setpriority(PRIO_PROCESS, gettid(), PRIO_MIN);

    while(videoEncoder->mIsSending) {
        std::unique_lock<std::mutex> lock_u(videoEncoder->mtxOut);
        videoEncoder->condOut.wait(lock_u);
        while(!videoEncoder->mediaInfoQueue.empty()) {
            VideoInfo mediaInfo = videoEncoder->mediaInfoQueue.front();
            videoEncoder->onOutputAvailable(mediaInfo.outIndex, &mediaInfo.bufferInfo);
            videoEncoder->mediaInfoQueue.pop();
        }
    }
    LOGI("video send_thread exit");
    return nullptr;
}

inline void VideoEncoder::onEncodeFrame(uint8_t *bytes,size_t size,int32_t flags, int64_t ts) {
    if(flags>0) LOGI("flags: %d",flags);
    switch (flags) {
        case AMEDIACODEC_BUFFER_FLAG_CODEC_CONFIG:
            delete[]spspps.data;
            spspps.data = new uint8_t[size];
            spspps.size = size;
            memcpy(spspps.data, bytes, size);
            break;

        case AMEDIACODEC_BUFFER_FLAG_KEY_FRAME:
            header.type = ntohs(videoParam.videoType);
            header.keyframe = ntohs(flags);
            header.timestamp = ntohq(ts);
            header.size = ntohl(size+spspps.size);

            send(m_sockfd, &header, sizeof(header), 0);
            send(m_sockfd, spspps.data, spspps.size, 0);
            send(m_sockfd, bytes, size, 0);
            break;

        default:
            header.type = ntohs(videoParam.videoType);
            header.keyframe = ntohs(flags);
            header.timestamp = ntohq(ts);
            header.size = ntohl(size);

            send(m_sockfd, &header, sizeof(header), 0);
            send(m_sockfd, bytes, size, 0);
            break;
    }
}

int VideoEncoder::connectSocket(const char *ip, int port) {
    int fd = socket(AF_INET, SOCK_STREAM, 0);
    if(fd<0) return -1;

    struct sockaddr_in servaddr{};
    memset(&servaddr, 0, sizeof(servaddr));
    servaddr.sin_family = AF_INET;
    servaddr.sin_port = htons(port);
    if(inet_pton(AF_INET, ip, &servaddr.sin_addr) <= 0){
        LOGE("inet_pton error for %s\n",ip);
        close(fd);
        return -1;
    }

    int opt = 1;//端口复用
    setsockopt(fd, SOL_SOCKET,SO_REUSEADDR, (const void *) &opt, sizeof(opt));

    if( connect(fd, (struct sockaddr*)&servaddr, sizeof(servaddr)) < 0){
        LOGE("connect error: %s:%d\n",ip,port);
        close(fd);
        return -1;
    }
    return fd;
}

bool VideoEncoder::stop() {
    if(!mIsRecording)
        return false;
    mIsRecording = false;

#if !CALL_BACK
    if(encode_tid!=0) {
        LOGI("video encode pthread_join!!!");
        pthread_join(encode_tid, nullptr);
        encode_tid = 0;
    }
#endif

    if (videoCodec) {
#if CALL_BACK
        AMediaCodecOnAsyncNotifyCallback callback = {};
        AMediaCodec_setAsyncNotifyCallback(videoCodec, callback, NULL);
#endif
        AMediaCodec_signalEndOfInputStream(videoCodec);
        AMediaCodec_stop(videoCodec);
        AMediaCodec_delete(videoCodec);
        videoCodec = nullptr;
    }

    if(surface) {
        ANativeWindow_release(surface);
        surface = nullptr;
    }

    if(mMuxer!= nullptr) {
        (*trackTotal)--;
        mMuxer = nullptr;
    }
    mVideoTrack = -1;
    LOGI("video stop!!!");
    return true;
}

bool VideoEncoder::release() {
    if(!stop())
        return false;

    mIsSending = false;
    condOut.notify_all();
    if(send_tid!=0) {
        LOGI("video send pthread_join!!!");
        pthread_join(send_tid, nullptr);
        send_tid = 0;
    }

    while(!mediaInfoQueue.empty()) {
        mediaInfoQueue.pop();
    }

    if(m_sockfd>=0) {
        close(m_sockfd);
        m_sockfd = -1;
    }
    LOGI("video release!!!");
    return true;
}
