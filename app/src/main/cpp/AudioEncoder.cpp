//
// Created by lynx.liu on 2022/10/9.
//

#include "AudioEncoder.h"

#define  LOG_TAG    "llx"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

#define dump_audio 0

inline int64_t systemnanotime() {
    timespec now{};
    clock_gettime(CLOCK_MONOTONIC, &now);
    return now.tv_sec * 1000000000LL + now.tv_nsec;
}

AudioEncoder::AudioEncoder()
{
    getpcm_tid = 0;
    encode_tid = 0;
    m_sockfd = -1;
    mAudioTrack = -1;
    audioType = AUDIO_PCM;
    mIsRecording = false;
}

AudioEncoder::~AudioEncoder()
{
    release();
}

bool AudioEncoder::init(JNIEnv *env, int mimeType, AMediaMuxer *muxer, int8_t *tracktotal) {
    env->GetJavaVM(&jvm);
    audioType = mimeType;
    if(audioType==AUDIO_PCM)
        return true;

    if(audioType!=AUDIO_OPUS) {
        mMuxer = muxer;
        trackTotal = tracktotal;
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
    media_status_t audioConfigureStatus = AMediaCodec_configure(audioCodec,
                                                                audioFormat, nullptr, nullptr, AMEDIACODEC_CONFIGURE_FLAG_ENCODE);
    AMediaFormat_delete(audioFormat);
    if (AMEDIA_OK != audioConfigureStatus) {
        LOGE("set video configure failed status-->%d", audioConfigureStatus);
        release();
        return false;
    }

    LOGI("init audioCodec success");
    return true;
}

bool AudioEncoder::start(const char *ip, int port) {
    mIsRecording = true;

    m_sockfd = connectSocket(ip,port);
    if(m_sockfd<0) {
        LOGI("audio connectSocket failed!");
        release();
        return false;
    }

    if(pthread_create(&getpcm_tid, nullptr, getpcm_thread, this)!=0) {
        LOGI("audio getpcm_thread failed!");
        release();
        return false;
    }

    if(audioCodec!=NULL) {
        media_status_t audioStatus = AMediaCodec_start(audioCodec);
        if (AMEDIA_OK != audioStatus) {
            LOGE("open audioCodec status-->%d", audioStatus);
            release();
            return false;
        }

        if(pthread_create(&encode_tid, nullptr, encode_thread, this)!=0) {
            LOGI("audio encode_thread failed!");
            release();
            return false;
        }
    }
    LOGI("audio start success");
    return true;
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
    while(audioEncoder->mIsRecording) {
        int nSize = jniEnv->CallIntMethod(audioRecord,read, jPcmBuffer, 0, blockSize);
        if(nSize<=0){
            usleep(0);
            continue;
        }

        jbyte* audio_bytes = (jbyte*)calloc(nSize,1);
        jniEnv->GetByteArrayRegion(jPcmBuffer, 0, nSize,audio_bytes);
#if dump_audio
        fwrite(audio_bytes, 1, nSize, fp);
#endif
        audioEncoder->onPcmData(reinterpret_cast<uint8_t *>(audio_bytes), nSize);
        free(audio_bytes);
    }
#if dump_audio
    fclose(fp);
#endif
    jniEnv->DeleteLocalRef(jPcmBuffer);
    audioEncoder->jvm->DetachCurrentThread();
    LOGI("audio encode_thread exit");
    return nullptr;
}

inline void AudioEncoder::onPcmData(uint8_t *bytes, uint32_t size) const {
    if(audioCodec) {
        int inBufferIndex = AMediaCodec_dequeueInputBuffer(audioCodec, -1);
        if (inBufferIndex >= 0) {
            size_t out_size = 0;
            uint8_t *inputBuffer = AMediaCodec_getInputBuffer(audioCodec, inBufferIndex, &out_size);
            if(out_size>0) {
                memcpy(inputBuffer, bytes, size);
                long pts = systemnanotime() / 1000;
                AMediaCodec_queueInputBuffer(audioCodec, inBufferIndex, 0, size, pts, 0);
            }
        }
    } else {
        onEncodeFrame(bytes, size, AUDIO_PCM, CHANNEL_COUNT, SAMPLE_RATE);
    }
}

void* AudioEncoder::encode_thread(void *arg) {
    auto *audioEncoder =(AudioEncoder *)arg;
    auto *info = (AMediaCodecBufferInfo *) malloc(sizeof(AMediaCodecBufferInfo));

    setpriority(PRIO_PROCESS, getpid(), PRIO_MIN);
    setpriority(PRIO_PROCESS, gettid(), PRIO_MIN);

    while(audioEncoder->mIsRecording) {
        audioEncoder->dequeueOutput(info);
    }
    free(info);
    LOGI("audio encode_thread exit");
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
        outIndex = AMediaCodec_dequeueOutputBuffer(audioCodec, info, 1000);
#if NDK_DEBUG
        LOGI("AMediaCodec_dequeueOutputBuffer: %zd, %d, %d ms\r\n", outIndex, info->offset, systemmilltime() - start);
#endif
        size_t out_size = 0;
        if (outIndex >= 0) {
            uint8_t *outputBuffer = AMediaCodec_getOutputBuffer(audioCodec, outIndex, &out_size);
            if (info->size > 0 && info->presentationTimeUs > 0) {
                onEncodeFrame(outputBuffer+info->offset,info->size,audioType,CHANNEL_COUNT,SAMPLE_RATE);

                if(mMuxer) {
                    AMediaMuxer_writeSampleData(mMuxer, mAudioTrack, outputBuffer, info);
                }
            }

            AMediaCodec_releaseOutputBuffer(audioCodec, outIndex, false);
        } else if (outIndex == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
            AMediaFormat *outFormat = AMediaCodec_getOutputFormat(audioCodec);
            const char *s = AMediaFormat_toString(outFormat);
            LOGI("audio out format %s", s);

            if(mMuxer) {
                mAudioTrack = AMediaMuxer_addTrack(mMuxer, outFormat);
                LOGI("audioTrack: %d", mAudioTrack);
                if(mAudioTrack>=(*trackTotal)-1) {
                    AMediaMuxer_start(mMuxer);
                    LOGI("MediaMuxer start");
                } else if(mAudioTrack<0) {
                    (*trackTotal)--;
                    mMuxer = nullptr;
                }
            }
        }

        if(info->flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) {
            LOGI("AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM");
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

void AudioEncoder::release() {
    if(!mIsRecording)
        return;

    mIsRecording = false;
    if(encode_tid!=0) {
        LOGI("audio encode pthread_join!!!");
        pthread_join(encode_tid, nullptr);
        encode_tid = 0;
    }

    if (audioCodec) {
        AMediaCodec_stop(audioCodec);
        AMediaCodec_delete(audioCodec);
        audioCodec = nullptr;
    }

    if(getpcm_tid!=0) {
        LOGI("audio getpcm pthread_join!!!");
        pthread_join(getpcm_tid, nullptr);
        getpcm_tid = 0;
    }

    if(mMuxer!= nullptr) {
        (*trackTotal)--;
        mMuxer = nullptr;
    }
    mAudioTrack = -1;

    if(m_sockfd>=0) {
        close(m_sockfd);
        m_sockfd = -1;
    }
    LOGI("audio release!!!");
}
