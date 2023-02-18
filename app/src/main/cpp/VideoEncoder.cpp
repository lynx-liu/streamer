//
// Created by lynx.liu on 2022/9/28.
//

#include "VideoEncoder.h"

#define  LOG_TAG    "llx"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

#define NDK_DEBUG   0
#define CALL_BACK   1

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
    videoType = AVC;
    mIsRecording = false;
    mIsSending = false;
    memset(&spspps,0,sizeof(spspps));
}

VideoEncoder::~VideoEncoder()
{
    release();
}

void VideoEncoder::requestSyncFrame() {
    AMediaFormat *videoFormat = AMediaFormat_new();
    AMediaFormat_setInt32(videoFormat, PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
    AMediaCodec_setParameters(videoCodec,videoFormat);
    AMediaFormat_delete(videoFormat);
}

void VideoEncoder::setVideoBitrate(int bitrate) {
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
    if(pthread_create(&send_tid, nullptr, send_thread, this)!=0) {
        LOGE("video send_thread failed!");
        release();
        return nullptr;
    }

    if(width==0 || height==0)
        return nullptr;

    mMuxer = muxer;
    trackTotal = tracktotal;
    if(mMuxer!= nullptr) {
        (*trackTotal)++;
    }

    timeoutUs = minFps>0? 1000000L/minFps : -1;
    videoType = static_cast<VideoType>(codec);

    const char *VIDEO_MIME = videoType==AVC?"video/avc":"video/hevc";
    videoFormat = AMediaFormat_new();
    AMediaFormat_setString(videoFormat, AMEDIAFORMAT_KEY_MIME, VIDEO_MIME);
    AMediaFormat_setInt32(videoFormat, AMEDIAFORMAT_KEY_WIDTH, width);
    AMediaFormat_setInt32(videoFormat, AMEDIAFORMAT_KEY_HEIGHT, height);
    AMediaFormat_setInt32(videoFormat, AMEDIAFORMAT_KEY_BIT_RATE,bitrate);
    AMediaFormat_setInt32(videoFormat, AMEDIAFORMAT_KEY_FRAME_RATE, maxFps);
    AMediaFormat_setInt32(videoFormat, AMEDIAFORMAT_KEY_I_FRAME_INTERVAL, frameInterval);
    AMediaFormat_setInt32(videoFormat, AMEDIAFORMAT_KEY_REPEAT_PREVIOUS_FRAME_AFTER, REPEAT_FRAME_DELAY_US); // µs
    AMediaFormat_setInt32(videoFormat, AMEDIAFORMAT_KEY_COLOR_FORMAT, 0x7F000789); //COLOR_FormatSurface
    AMediaFormat_setFloat(videoFormat, AMEDIAFORMAT_KEY_MAX_FPS_TO_ENCODER, maxFps);
    AMediaFormat_setInt32(videoFormat, "max-bframes", 0);//MediaFormat.KEY_MAX_B_FRAMES

    AMediaFormat_setInt32(videoFormat, "vendor.qti-ext-enc-initial-qp.qp-i",defaulQP);
    AMediaFormat_setInt32(videoFormat, "vendor.qti-ext-enc-initial-qp.qp-i-enable",1);
    AMediaFormat_setInt32(videoFormat, "vendor.qti-ext-enc-qp-range.qp-i-min",minQP);
    AMediaFormat_setInt32(videoFormat, "vendor.qti-ext-enc-qp-range.qp-i-max",maxQP);

    AMediaFormat_setInt32(videoFormat, "vendor.qti-ext-enc-initial-qp.qp-p",defaulQP);
    AMediaFormat_setInt32(videoFormat, "vendor.qti-ext-enc-initial-qp.qp-p-enable",1);
    AMediaFormat_setInt32(videoFormat, "vendor.qti-ext-enc-qp-range.qp-p-min",minQP);
    AMediaFormat_setInt32(videoFormat, "vendor.qti-ext-enc-qp-range.qp-p-max",maxQP);

    if(bitrateMode==0) {
        AMediaFormat_setInt32(videoFormat, AMEDIAFORMAT_KEY_BITRATE_MODE, 2);//MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
    } else {
        AMediaFormat_setInt32(videoFormat, AMEDIAFORMAT_KEY_BITRATE_MODE, 1);//MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR
    }

    if(videoType==AVC) {
        AMediaFormat_setInt32(videoFormat, AMEDIAFORMAT_KEY_PROFILE, profile);
    }else {
        AMediaFormat_setInt32(videoFormat, AMEDIAFORMAT_KEY_PROFILE, 0x01);//MediaCodecInfo.CodecProfileLevel.HEVCProfileMain
    }
    AMediaFormat_setInt32(videoFormat, AMEDIAFORMAT_KEY_LEVEL, 0x200);

    videoCodec = AMediaCodec_createEncoderByType(VIDEO_MIME);
    media_status_t videoConfigureStatus = AMediaCodec_configure(videoCodec,
                                                                videoFormat, nullptr, nullptr, AMEDIACODEC_CONFIGURE_FLAG_ENCODE);
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

ANativeWindow* VideoEncoder::reconfigure(int width, int height, int bitrate, int fps, int frameInterval, int profile, int codec) {
    if(codec!=-1) videoType = static_cast<VideoType>(codec);

    const char *VIDEO_MIME = videoType==AVC?"video/avc":"video/hevc";
    AMediaFormat_setString(videoFormat, AMEDIAFORMAT_KEY_MIME, VIDEO_MIME);
    if(width!=-1) AMediaFormat_setInt32(videoFormat, AMEDIAFORMAT_KEY_WIDTH, width);
    if(height!=-1) AMediaFormat_setInt32(videoFormat, AMEDIAFORMAT_KEY_HEIGHT, height);
    if(bitrate!=-1) AMediaFormat_setInt32(videoFormat, AMEDIAFORMAT_KEY_BIT_RATE,bitrate);
    if(fps!=-1) {
        AMediaFormat_setInt32(videoFormat, AMEDIAFORMAT_KEY_FRAME_RATE, fps);
        AMediaFormat_setFloat(videoFormat, AMEDIAFORMAT_KEY_MAX_FPS_TO_ENCODER, fps);
    }
    if(frameInterval!=-1) AMediaFormat_setInt32(videoFormat, AMEDIAFORMAT_KEY_I_FRAME_INTERVAL, frameInterval);

    if(videoType==AVC) {
        if(profile!=-1) AMediaFormat_setInt32(videoFormat, AMEDIAFORMAT_KEY_PROFILE, profile);
    }else {
        AMediaFormat_setInt32(videoFormat, AMEDIAFORMAT_KEY_PROFILE, 0x01);//MediaCodecInfo.CodecProfileLevel.HEVCProfileMain
    }
    AMediaFormat_setInt32(videoFormat, AMEDIAFORMAT_KEY_LEVEL, 0x200);

    stop();

    videoCodec = AMediaCodec_createEncoderByType(VIDEO_MIME);
    media_status_t videoConfigureStatus = AMediaCodec_configure(videoCodec,
                                                                videoFormat, nullptr, nullptr, AMEDIACODEC_CONFIGURE_FLAG_ENCODE);
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
    LOGI("reconfigure success");
    return surface;
}

bool VideoEncoder::start() {
    mIsRecording = true;

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
    AMediaInfo mediaInfo;
    memset(&mediaInfo,0,sizeof(mediaInfo));

    mediaInfo.outIndex = index;
    memcpy(&mediaInfo.bufferInfo,bufferInfo,sizeof(AMediaCodecBufferInfo));
    mediaInfoQueue.push(mediaInfo);
#if NDK_DEBUG
    LOGI("notify_all");
#endif
    cond.notify_all();
}

inline void VideoEncoder::onOutputAvailable(int32_t outIndex, AMediaCodecBufferInfo *info) {
#if NDK_DEBUG
    LOGI("index(%d), (%d, %d, %lld, 0x%x)", outIndex, info->offset, info->size, (long long)info->presentationTimeUs, info->flags);
#endif
    size_t out_size = 0;
    uint8_t *outputBuffer = AMediaCodec_getOutputBuffer(videoCodec, outIndex, &out_size);
    if (info->size > 0 && info->presentationTimeUs > 0) {
        if(videoType==AVC) {
            onH264Frame(outputBuffer+info->offset,info->size,info->presentationTimeUs);
        } else {
            onH265Frame(outputBuffer+info->offset,info->size,info->presentationTimeUs);
        }

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
        LOGI("videoTrack: %d", mVideoTrack);
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
            LOGI("AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM");
            break;
        }
    } while (mIsRecording);
}

void* VideoEncoder::encode_thread(void *arg) {
    auto *videoEncoder =(VideoEncoder *)arg;
    auto *info = (AMediaCodecBufferInfo *) malloc(sizeof(AMediaCodecBufferInfo));

    setpriority(PRIO_PROCESS, getpid(), PRIO_MIN);
    setpriority(PRIO_PROCESS, gettid(), PRIO_MIN);

    while(videoEncoder->mIsRecording) {
        videoEncoder->dequeueOutput(info);
    }
    free(info);
    LOGI("video encode_thread exit");
    return nullptr;
}

void* VideoEncoder::send_thread(void *arg) {
    auto *videoEncoder =(VideoEncoder *)arg;
    setpriority(PRIO_PROCESS, getpid(), PRIO_MIN);
    setpriority(PRIO_PROCESS, gettid(), PRIO_MIN);

    while(videoEncoder->mIsSending) {
        std::unique_lock<std::mutex> lock_u(videoEncoder->mtx);
        videoEncoder->cond.wait(lock_u);
        while(!videoEncoder->mediaInfoQueue.empty()) {
            AMediaInfo mediaInfo = videoEncoder->mediaInfoQueue.front();
            videoEncoder->onOutputAvailable(mediaInfo.outIndex, &mediaInfo.bufferInfo);
            videoEncoder->mediaInfoQueue.pop();
        }
    }
    LOGI("video send_thread exit");
    return nullptr;
}

inline void VideoEncoder::onEncodeFrame(uint8_t *bytes,size_t size,int frametype,bool keyframe,int64_t ts) const {
    VideoHeader header;
    header.type = ntohs(frametype);
    header.keyframe = ntohs(keyframe);
    header.timestamp = ntohq(ts);
    header.size = ntohl(size);

    send(m_sockfd, &header, sizeof(header), 0);
    send(m_sockfd, bytes, size, 0);
}

inline void VideoEncoder::onH265Frame(uint8_t* bytes, size_t size, int64_t ts) {
    int frametype=1;
    int nalutype=(bytes[4]&0x7e)>>1;

    if(nalutype==32){//vps sps pps
        delete []spspps.data;
        spspps.data = new uint8_t[size];
        spspps.header.size = size;
        memcpy(spspps.data,bytes,size);
    } else if(nalutype == 19 || nalutype == 20) {//idr
        auto* data = new uint8_t[spspps.header.size + size];
        memcpy(data,spspps.data,spspps.header.size);
        memcpy(data+spspps.header.size,bytes,size);
        onEncodeFrame(data,spspps.header.size+size,frametype,true,ts);
        delete [] data;
    } else {
        onEncodeFrame(bytes,size,frametype,false,ts);
    }
}

inline void VideoEncoder::onH264Frame(uint8_t* bytes, size_t size, int64_t ts) {
    int frametype=0;
    int nalutype=bytes[4]&0x1f;
    bool fixNalueType=false;
    if(bytes[4]!=0x67){
        //common-c just fit like 0x67,0x68,0x65,0x61
        bytes[4]=(bytes[4]|0x60);
        fixNalueType=true;
    }

    if(nalutype==NonIDR){
        onEncodeFrame(bytes,size,frametype,false,ts);
    } else if(nalutype==IDR){
        auto* data = new uint8_t[spspps.header.size + size];
        memcpy(data,spspps.data,spspps.header.size);
        memcpy(data+spspps.header.size,bytes,size);
        onEncodeFrame(data,spspps.header.size+size,frametype,true,ts);
        delete [] data;
    } else if(nalutype==SPS){
        if(fixNalueType){
            for (int i=5;i<size-5;++i){
                if(bytes[i]==0
                   &&bytes[i+1]==0
                   &&bytes[i+2]==0
                   &&bytes[i+3]==1
                   &&(bytes[i+4]&0x1f)==8){
                    bytes[i+4]=(bytes[i+4]|0x60);
                    break;
                }
            }
        }

        delete []spspps.data;
        spspps.data = new uint8_t[size];
        spspps.header.size = size;
        memcpy(spspps.data,bytes,size);
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

void VideoEncoder::stop() {
    if(!mIsRecording)
        return;
    mIsRecording = false;

#if CALL_BACK
    AMediaCodecOnAsyncNotifyCallback callback = {};
    AMediaCodec_setAsyncNotifyCallback(videoCodec, callback, NULL);
#else
    if(encode_tid!=0) {
        LOGI("video encode pthread_join!!!");
        pthread_join(encode_tid, nullptr);
        encode_tid = 0;
    }
#endif

    if (videoCodec) {
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
}

void VideoEncoder::release() {
    stop();

    mIsSending = false;
    cond.notify_all();
    if(send_tid!=0) {
        LOGI("video send pthread_join!!!");
        pthread_join(send_tid, nullptr);
        send_tid = 0;
    }

    if(videoFormat!= nullptr) {
        AMediaFormat_delete(videoFormat);
        videoFormat = nullptr;
    }

    while(!mediaInfoQueue.empty()) {
        mediaInfoQueue.pop();
    }

    if(m_sockfd>=0) {
        close(m_sockfd);
        m_sockfd = -1;
    }
    LOGI("video release!!!");
}
