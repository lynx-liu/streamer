//
// Created by lynx.liu on 2022/9/28.
//

#include "VideoEncoder.h"

#define  LOG_TAG    "llx"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

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
    nWidth = 0;
    nHeight = 0;
    encode_tid = 0;
    m_sockfd = -1;
    mVideoTrack = -1;
    mIsRecording = false;
    memset(&spspps,0,sizeof(spspps));
}

VideoEncoder::~VideoEncoder()
{
    release();
}

ANativeWindow* VideoEncoder::init(int width, int height, int framerate, int bitrate, int minFps) {
    if(width==0 || height==0)
        return nullptr;

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

bool VideoEncoder::start(const char *ip, int port, const char *filename) {
    if(filename) {
        int fd = open(filename, O_CREAT | O_RDWR, 0666);
        if (!fd) {
            LOGE("open media file failed-->%d", fd);
            release();
            return false;
        }
        mMuxer = AMediaMuxer_new(fd, AMEDIAMUXER_OUTPUT_FORMAT_MPEG_4);
        AMediaMuxer_setOrientationHint(mMuxer, 0); //旋转角度
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////
    media_status_t videoStatus = AMediaCodec_start(videoCodec);
    if (AMEDIA_OK != videoStatus) {
        LOGI("open videoCodec status-->%d", videoStatus);
        release();
        return false;
    }

    m_sockfd = connectSocket(ip,port);
    if(m_sockfd<0) {
        LOGI("connectSocket failed!");
        release();
        return false;
    }

    mIsRecording = true;
    if(pthread_create(&encode_tid, nullptr, encode_thread, this)!=0) {
        LOGI("encode_thread failed!");
        release();
        return false;
    }
    return true;
}

inline void VideoEncoder::dequeueOutput(AMediaCodecBufferInfo *info) {
    ssize_t outIndex;
    do {
        int32_t start = systemmilltime();
        outIndex = AMediaCodec_dequeueOutputBuffer(videoCodec, info, timeoutUs);
        LOGI("AMediaCodec_dequeueOutputBuffer: %zd, %d, %d ms\r\n", outIndex, info->offset, systemmilltime() - start);

        size_t out_size = 0;
        if (outIndex >= 0) {
            uint8_t *outputBuffer = AMediaCodec_getOutputBuffer(videoCodec, outIndex, &out_size);
            if (mVideoTrack >= 0 && info->size > 0 && info->presentationTimeUs > 0) {
                onH264Frame(outputBuffer+info->offset, info->size, info->presentationTimeUs);
                if(mMuxer) {
                    AMediaMuxer_writeSampleData(mMuxer, mVideoTrack, outputBuffer, info);
                }
            }

            AMediaCodec_releaseOutputBuffer(videoCodec, outIndex, false);
        } else if (outIndex == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
            AMediaFormat *outFormat = AMediaCodec_getOutputFormat(videoCodec);
            const char *s = AMediaFormat_toString(outFormat);
            LOGI("video out format %s", s);
            mVideoTrack = 0;

            if(mMuxer) {
                AMediaMuxer_addTrack(mMuxer, outFormat);
                AMediaMuxer_start(mMuxer);
            }
        }

        if(info->flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) {
            LOGI("AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM");
            break;
        }
    } while (outIndex >= 0);
}

void* VideoEncoder::encode_thread(void *arg) {
    auto *videoEncoder =(VideoEncoder *)arg;
    auto *info = (AMediaCodecBufferInfo *) malloc(sizeof(AMediaCodecBufferInfo));
    while(videoEncoder->mIsRecording) {
        videoEncoder->dequeueOutput(info);
    }
    free(info);
    LOGI("encode_thread exit");
    return nullptr;
}

inline void VideoEncoder::onEncodeFrame(uint8_t *bytes,size_t size,int frametype,bool keyframe,int64_t ts) const {
    Header header;
    header.type = ntohs(frametype);
    header.keyframe = ntohs(keyframe);
    header.timestamp = ntohq(ts);
    header.size = ntohl(size);

    send(m_sockfd, &header, sizeof(header), 0);
    send(m_sockfd, bytes, size, 0);
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

void VideoEncoder::release() {
    mIsRecording = false;
    if(encode_tid!=0) {
        LOGI("encode pthread_join!!!");
        pthread_join(encode_tid, nullptr);
        encode_tid = 0;
    }

    if (videoCodec) {
        AMediaCodec_stop(videoCodec);
        AMediaCodec_delete(videoCodec);
        videoCodec = nullptr;
    }

    if(surface) {
        ANativeWindow_release(surface);
        surface = nullptr;
    }

    mVideoTrack = -1;

    if (mMuxer) {
        AMediaMuxer_stop(mMuxer);
        AMediaMuxer_delete(mMuxer);
        mMuxer = nullptr;
    }

    if(m_sockfd>=0) {
        close(m_sockfd);
        m_sockfd = -1;
    }
    LOGI("release!!!");
}
