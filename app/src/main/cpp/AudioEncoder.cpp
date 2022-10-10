//
// Created by lynx.liu on 2022/10/9.
//

#include "AudioEncoder.h"

#define  LOG_TAG    "llx"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

#define dump_audio 0

AudioEncoder::AudioEncoder()
{
    encode_tid = 0;
    m_sockfd = -1;
    mIsRecording = false;
}

AudioEncoder::~AudioEncoder()
{
    release();
}

bool AudioEncoder::start(JNIEnv *env, const char *ip, int port) {
    env->GetJavaVM(&jvm);

    m_sockfd = connectSocket(ip,port);
    if(m_sockfd<0) {
        LOGI("audio connectSocket failed!");
        release();
        return false;
    }

    mIsRecording = true;
    if(pthread_create(&encode_tid, nullptr, encode_thread, this)!=0) {
        LOGI("audio encode_thread failed!");
        release();
        return false;
    }
    LOGI("init audioCodec success");
    return true;
}

void* AudioEncoder::encode_thread(void *arg) {
    auto *audioEncoder =(AudioEncoder *)arg;

    JNIEnv *jniEnv = nullptr;
    audioEncoder->jvm->AttachCurrentThread(&jniEnv, NULL);

    jclass audioRecordClass = jniEnv->FindClass("android/media/AudioRecord");

    jmethodID getMinBufferSize = jniEnv->GetStaticMethodID(audioRecordClass,"getMinBufferSize", "(III)I");
    jint bufferSize = jniEnv->CallStaticIntMethod(audioRecordClass, getMinBufferSize, SAMPLE_RATE, CHANNEL_IN_STEREO, ENCODING_PCM_16BIT);

    jmethodID init = jniEnv->GetMethodID(audioRecordClass, "<init>","(IIIII)V");
    jobject audioRecord = jniEnv->NewObject(audioRecordClass, init, REMOTE_SUBMIX, SAMPLE_RATE, CHANNEL_IN_STEREO, ENCODING_PCM_16BIT, bufferSize);

    jmethodID startRecording = jniEnv->GetMethodID(audioRecordClass, "startRecording","()V");
    jniEnv->CallVoidMethod(audioRecord, startRecording);//start recording
    jmethodID read = jniEnv->GetMethodID(audioRecordClass, "read", "([BII)I");

    int blockSize = 1024;
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
        audioEncoder->onEncodeFrame(reinterpret_cast<uint8_t *>(audio_bytes), nSize, PCM_DATA, CHANNEL_COUNT, SAMPLE_RATE);
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

inline void AudioEncoder::onEncodeFrame(uint8_t *bytes,uint32_t size,uint8_t type,uint8_t channel,uint16_t sampleRate) const {
    AudioHeader header;
    header.size = size;
    header.type = type;
    header.channel = channel;
    header.sampleRate = sampleRate;

    send(m_sockfd, &header, sizeof(header), 0);
    send(m_sockfd, bytes, size, 0);
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
    mIsRecording = false;
    if(encode_tid!=0) {
        LOGI("audio encode pthread_join!!!");
        pthread_join(encode_tid, nullptr);
        encode_tid = 0;
    }

    if(m_sockfd>=0) {
        close(m_sockfd);
        m_sockfd = -1;
    }
    LOGI("audio release!!!");
}
