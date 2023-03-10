//
// Created by lynx.liu on 2022/10/9.
//

#include "AudioEncoder.h"

#define  LOG_TAG    "llx"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

#define NDK_DEBUG   0
#define CALL_BACK   1
#define dump_audio  0
#define NAME(variable) (#variable)

inline int64_t systemnanotime() {
    timespec now{};
    clock_gettime(CLOCK_MONOTONIC, &now);
    return now.tv_sec * 1000000000LL + now.tv_nsec;
}

inline int32_t systemmilltime() {
    return systemnanotime()/1000000;
}

AudioEncoder::AudioEncoder()
{
    getpcm_tid = 0;
    encode_tid = 0;
    m_sockfd = -1;
    send_tid = 0;
    mAudioTrack = -1;
    audioType = AUDIO_PCM;
    mIsRecording = false;
    mIsSending = false;
}

AudioEncoder::~AudioEncoder()
{
    release();
}

bool AudioEncoder::init(JNIEnv *env, int mimeType, AMediaMuxer *muxer, int8_t *tracktotal, const char *ip, int port) {
    m_sockfd = connectSocket(ip,port);
    if(m_sockfd<0) {
        LOGI("audio connectSocket failed!");
        release();
        return false;
    }

    env->GetJavaVM(&jvm);
    audioType = mimeType;
    if(audioType==AUDIO_PCM)
        return true;

    mIsSending = true;
    if(pthread_create(&send_tid, nullptr, send_audio_thread, this)!=0) {
        LOGE("audio send_thread failed!");
        release();
        return false;
    }

    if(audioType!=AUDIO_OPUS) {
        trackTotal = tracktotal;
    }

    return createEncoder(muxer);
}

bool AudioEncoder::createEncoder(AMediaMuxer *muxer) {
    if(audioType==AUDIO_PCM)
        return true;

    if(audioType!=AUDIO_OPUS) {
        mMuxer = muxer;
        if (mMuxer != nullptr) {
            (*trackTotal)++;
        }
    }

    const char *AUDIO_MIME = ((audioType==AUDIO_OPUS)?"audio/opus":"audio/mp4a-latm");
    AMediaFormat *audioFormat = AMediaFormat_new();
    AMediaFormat_setString(audioFormat, AMEDIAFORMAT_KEY_MIME, AUDIO_MIME);
    AMediaFormat_setInt32(audioFormat, AMEDIAFORMAT_KEY_SAMPLE_RATE, SAMPLE_RATE);
    AMediaFormat_setInt32(audioFormat, AMEDIAFORMAT_KEY_CHANNEL_COUNT, CHANNEL_COUNT);
    AMediaFormat_setInt32(audioFormat, AMEDIAFORMAT_KEY_BIT_RATE,32000);//32K
    AMediaFormat_setInt32(audioFormat, AMEDIAFORMAT_KEY_MAX_INPUT_SIZE, BUF_SIZE);

    audioCodec = AMediaCodec_createEncoderByType(AUDIO_MIME);
    if(audioCodec== nullptr) {
        LOGE("audio createEncoderByType failed");
        release();
        return false;
    }

    media_status_t audioConfigureStatus = AMediaCodec_configure(audioCodec,
                                                                audioFormat, nullptr, nullptr, AMEDIACODEC_CONFIGURE_FLAG_ENCODE);
    AMediaFormat_delete(audioFormat);
    if (AMEDIA_OK != audioConfigureStatus) {
        LOGE("set audio configure failed status-->%d", audioConfigureStatus);
        release();
        return false;
    }

    LOGI("init audioCodec success");
    return true;
}

bool AudioEncoder::start() {
    if(mIsRecording)
        return true;
    mIsRecording = true;

    if(audioCodec!=NULL) {
#if CALL_BACK
        AMediaCodecOnAsyncNotifyCallback audioCallBack = {
            OnInputAvailableCB,
            OnOutputAvailableCB,
            OnFormatChangedCB,
            OnErrorCB
        };
        AMediaCodec_setAsyncNotifyCallback(audioCodec,audioCallBack,this);
#endif
        media_status_t audioStatus = AMediaCodec_start(audioCodec);
        if (AMEDIA_OK != audioStatus) {
            LOGE("open audioCodec status-->%d", audioStatus);
            release();
            return false;
        }

#if !CALL_BACK
        if(pthread_create(&encode_tid, nullptr, encode_thread, this)!=0) {
            LOGI("audio encode_thread failed!");
            release();
            return false;
        }
#endif
    }

    if(pthread_create(&getpcm_tid, nullptr, getpcm_thread, this)!=0) {
        LOGI("audio getpcm_thread failed!");
        release();
        return false;
    }
    LOGI("audio start success");
    return true;
}

void AudioEncoder::OnInputAvailableCB(AMediaCodec *mediaCodec, void *userdata, int32_t index) {
    auto *audioEncoder =(AudioEncoder *)userdata;
    audioEncoder->indexQueue.push(index);
    audioEncoder->condIn.notify_all();
}

void AudioEncoder::OnOutputAvailableCB(AMediaCodec *mediaCodec, void *userdata, int32_t index, AMediaCodecBufferInfo *bufferInfo) {
    auto *audioEncoder =(AudioEncoder *)userdata;
    audioEncoder->notifyOutputAvailable(index,bufferInfo);
}

void AudioEncoder::OnFormatChangedCB(AMediaCodec *mediaCodec, void *userdata, AMediaFormat *format) {
    auto *audioEncoder =(AudioEncoder *)userdata;
    audioEncoder->onFormatChange(format);
}

void AudioEncoder::OnErrorCB(AMediaCodec *mediaCodec, void *userdata, media_status_t err, int32_t actionCode, const char *detail) {
    LOGE("OnErrorCB: err(%d), actionCode(%d), detail(%s)", err, actionCode, detail);
}

inline void AudioEncoder::notifyOutputAvailable(int32_t index, AMediaCodecBufferInfo *bufferInfo) {
    AudioInfo mediaInfo;
    memset(&mediaInfo,0,sizeof(mediaInfo));

    mediaInfo.outIndex = index;
    memcpy(&mediaInfo.bufferInfo,bufferInfo,sizeof(AMediaCodecBufferInfo));
    mediaInfoQueue.push(mediaInfo);
#if NDK_DEBUG
    LOGI("notify_all");
#endif
    condOut.notify_all();
}

inline void AudioEncoder::onOutputAvailable(int32_t outIndex, AMediaCodecBufferInfo *info) {
    if(!mIsRecording) return;

#if NDK_DEBUG
    LOGI("index(%d), (%d, %d, %lld, 0x%x)", outIndex, info->offset, info->size, (long long)info->presentationTimeUs, info->flags);
#endif
    size_t out_size = 0;
    uint8_t *outputBuffer = AMediaCodec_getOutputBuffer(audioCodec, outIndex, &out_size);
    if (info->size > 0 && info->presentationTimeUs > 0) {
        onEncodeFrame(outputBuffer+info->offset, info->size, audioType,CHANNEL_COUNT,SAMPLE_RATE);

        if(mMuxer) {
            AMediaMuxer_writeSampleData(mMuxer, mAudioTrack, outputBuffer, info);
        }
    }

    AMediaCodec_releaseOutputBuffer(audioCodec, outIndex, false);
}

inline void AudioEncoder::onFormatChange(AMediaFormat *format) {
    const char *s = AMediaFormat_toString(format);
    LOGI("audio out format %s", s);

    if(mMuxer) {
        mAudioTrack = AMediaMuxer_addTrack(mMuxer, format);
        LOGI("trackTotal: %d, audioTrack: %d", (*trackTotal), mAudioTrack);
        if(mAudioTrack>=(*trackTotal)-1) {
            AMediaMuxer_start(mMuxer);
            LOGI("MediaMuxer start");
        } else if(mAudioTrack<0) {
            (*trackTotal)--;
            mMuxer = nullptr;
        }
    }
}

void* AudioEncoder::getpcm_thread(void *arg) {
    auto *audioEncoder =(AudioEncoder *)arg;
    JNIEnv *jniEnv = nullptr;
    audioEncoder->jvm->AttachCurrentThread(&jniEnv, nullptr);

    jclass audioRecordClass = jniEnv->FindClass("android/media/AudioRecord");

    jmethodID getMinBufferSize = jniEnv->GetStaticMethodID(audioRecordClass,"getMinBufferSize", "(III)I");
    jint bufferSize = jniEnv->CallStaticIntMethod(audioRecordClass, getMinBufferSize, SAMPLE_RATE, CHANNEL_IN_STEREO, ENCODING_PCM_16BIT);

    jmethodID init = jniEnv->GetMethodID(audioRecordClass, "<init>","(IIIII)V");
    jobject audioRecord = jniEnv->NewObject(audioRecordClass, init, REMOTE_SUBMIX, SAMPLE_RATE, CHANNEL_IN_STEREO, ENCODING_PCM_16BIT, bufferSize);

    jmethodID startRecording = jniEnv->GetMethodID(audioRecordClass, "startRecording","()V");
    jniEnv->CallVoidMethod(audioRecord, startRecording);//start recording

    jmethodID read = jniEnv->GetMethodID(audioRecordClass, "read", "([BII)I");

    int blockSize = BUF_SIZE;
    jbyteArray jPcmBuffer = jniEnv->NewByteArray(blockSize);
#if dump_audio
    FILE* fp = fopen("/sdcard/test.pcm","ab");
#endif
    prctl(PR_SET_NAME,__func__);
    while(audioEncoder->mIsRecording) {
        int nSize = jniEnv->CallIntMethod(audioRecord,read, jPcmBuffer, 0, blockSize);
        if(nSize<=0){
            usleep(0);
            continue;
        }

        jbyte* audio_bytes = jniEnv->GetByteArrayElements(jPcmBuffer, NULL);
#if dump_audio
        fwrite(audio_bytes, 1, nSize, fp);
#endif
        audioEncoder->onPcmData(reinterpret_cast<uint8_t *>(audio_bytes), nSize);
        jniEnv->ReleaseByteArrayElements(jPcmBuffer,audio_bytes,0);
    }
#if dump_audio
    fclose(fp);
#endif
    jniEnv->DeleteLocalRef(jPcmBuffer);

    jmethodID stop = jniEnv->GetMethodID(audioRecordClass, "stop","()V");
    jniEnv->CallVoidMethod(audioRecord, stop);//stop recording

    jmethodID release = jniEnv->GetMethodID(audioRecordClass, "release","()V");
    jniEnv->CallVoidMethod(audioRecord, release);//release recording

    jniEnv->DeleteLocalRef(audioRecord);
    jniEnv->DeleteLocalRef(audioRecordClass);
    audioEncoder->jvm->DetachCurrentThread();
    LOGI("audio getpcm_thread exit");
    return nullptr;
}

inline void AudioEncoder::onPcmData(uint8_t *bytes, uint32_t size) {
    if(audioCodec) {
#if CALL_BACK
        if(indexQueue.empty()) {
            std::unique_lock<std::mutex> lock_u(mtxIn);
            condIn.wait(lock_u);
        }

        int inBufferIndex = indexQueue.front();
        indexQueue.pop();
#else
        int inBufferIndex = AMediaCodec_dequeueInputBuffer(audioCodec, 1000000L);
#endif
        if (inBufferIndex >= 0) {
            size_t out_size = 0;
            uint8_t *inputBuffer = AMediaCodec_getInputBuffer(audioCodec, inBufferIndex, &out_size);
            if(out_size>0) {
                memcpy(inputBuffer, bytes, size);
                long pts = systemnanotime() / 1000;
                media_status_t audioQueueInputBufferStatus = AMediaCodec_queueInputBuffer(audioCodec, inBufferIndex, 0, size, pts, 0);
                if(AMEDIA_OK != audioQueueInputBufferStatus) {
                    LOGE("audio queueInputBuffer failed status-->%d", audioQueueInputBufferStatus);
                }
            }
        }
    } else {
        onEncodeFrame(bytes, size, AUDIO_PCM, CHANNEL_COUNT, SAMPLE_RATE);
    }
}

void* AudioEncoder::encode_thread(void *arg) {
    auto *audioEncoder =(AudioEncoder *)arg;
    auto *info = (AMediaCodecBufferInfo *) malloc(sizeof(AMediaCodecBufferInfo));

    prctl(PR_SET_NAME,NAME(audioEncoder));
    setpriority(PRIO_PROCESS, getpid(), PRIO_MIN);
    setpriority(PRIO_PROCESS, gettid(), PRIO_MIN);

    while(audioEncoder->mIsRecording) {
        audioEncoder->dequeueOutput(info);
    }
    free(info);
    LOGI("audio encode_thread exit");
    return nullptr;
}

void* AudioEncoder::send_audio_thread(void *arg) {
    auto *audioEncoder =(AudioEncoder *)arg;

    prctl(PR_SET_NAME,__func__);
    setpriority(PRIO_PROCESS, getpid(), PRIO_MIN);
    setpriority(PRIO_PROCESS, gettid(), PRIO_MIN);

    while(audioEncoder->mIsSending) {
        std::unique_lock<std::mutex> lock_u(audioEncoder->mtxOut);
        audioEncoder->condOut.wait(lock_u);
        while(!audioEncoder->mediaInfoQueue.empty()) {
            AudioInfo mediaInfo = audioEncoder->mediaInfoQueue.front();
            audioEncoder->onOutputAvailable(mediaInfo.outIndex, &mediaInfo.bufferInfo);
            audioEncoder->mediaInfoQueue.pop();
        }
    }
    LOGI("audio send_thread exit");
    return nullptr;
}

inline void AudioEncoder::onEncodeFrame(uint8_t *bytes,uint32_t size,uint8_t type,uint8_t channel,uint16_t sampleRate) const {
    AudioHeader header;
    header.size = size;
    header.type = type;
    header.channel = channel;
    header.sampleRate = sampleRate;

    send(m_sockfd, &header, sizeof(header), 0);
    send(m_sockfd, bytes, size, 0);
}

inline void AudioEncoder::dequeueOutput(AMediaCodecBufferInfo *info) {
    ssize_t outIndex;
    do {
#if NDK_DEBUG
        int32_t start = systemmilltime();
#endif
        outIndex = AMediaCodec_dequeueOutputBuffer(audioCodec, info, 1000000L);
#if NDK_DEBUG
        LOGI("AMediaCodec_dequeueOutputBuffer: %zd, %d, %d ms\r\n", outIndex, info->offset, systemmilltime() - start);
#endif
        size_t out_size = 0;
        if (outIndex >= 0) {
            notifyOutputAvailable(outIndex, info);
        } else if (outIndex == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
            AMediaFormat *outFormat = AMediaCodec_getOutputFormat(audioCodec);
            onFormatChange(outFormat);
        }

        if(info->flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) {
            LOGI("audio AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM");
            break;
        }
    } while (mIsRecording);
}

int AudioEncoder::connectSocket(const char *ip, int port) {
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

bool AudioEncoder::stop() {
    if(!mIsRecording)
        return false;

    mIsRecording = false;
    condIn.notify_all();
    if(getpcm_tid!=0) {
        LOGI("audio getpcm pthread_join!!!");
        pthread_join(getpcm_tid, nullptr);
        getpcm_tid = 0;
    }

    while(!indexQueue.empty()) {
        indexQueue.pop();
    }

#if !CALL_BACK
    if(encode_tid!=0) {
        LOGI("audio encode pthread_join!!!");
        pthread_join(encode_tid, nullptr);
        encode_tid = 0;
    }
#endif

    if (audioCodec) {
#if CALL_BACK
        AMediaCodecOnAsyncNotifyCallback callback = {};
        AMediaCodec_setAsyncNotifyCallback(audioCodec, callback, NULL);
#endif
        AMediaCodec_signalEndOfInputStream(audioCodec);
        AMediaCodec_stop(audioCodec);
        AMediaCodec_delete(audioCodec);
        audioCodec = nullptr;
    }

    if(mMuxer!= nullptr) {
        (*trackTotal)--;
        mMuxer = nullptr;
    }
    mAudioTrack = -1;
    LOGI("audio stop!!!");
    return true;
}

bool AudioEncoder::release() {
    if(!stop())
        return false;

    mIsSending = false;
    condOut.notify_all();
    if(send_tid!=0) {
        LOGI("audio send pthread_join!!!");
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
    LOGI("audio release!!!");
    return true;
}
