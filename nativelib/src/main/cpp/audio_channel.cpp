//
// Created by Administrator on 2022/1/25.
//

#include "audio_channel.h"

int AudioChannel::init(JNIEnv *env, int sampleRateInHz, int channelConfig, int audioFormat) {
//    this->sampleRateInHz = sampleRateInHz;
//    this->channelConfig = channelConfig;
//    this->audioFormat = audioFormat;
    this->sampleRateInHz = sampleRateInHz;
    this->audioFormat = audioFormat;
    if (channelConfig == 1) {
        this->channelConfig = SL_SPEAKER_FRONT_LEFT;
    } else if (channelConfig == 2) {
        this->channelConfig = SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT;
    } else {
        this->channelConfig = SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT;
    }
    return 1;
}

int AudioChannel::create() {
    //创建引擎
    int code = CreateEngine();
    //设置输出混响器
    if (code)
        code = createOutputMix();
    //获取混响接口并设置混响
    if (code)
        code = setOutputMix();
    //配置音源输入
    //配置音源输出
    //创建并实现播放器
    if (code)
        code = createAudioPlayer();
    //获取播放器接口 和 缓冲队列接口
    //注册回调函数
    if (code)
        code = getOther1();
    //获取效果器接口 和 音量控制接口
    if (code)
        code = getOther2();
    if (!code)
        release();
    return code;
}

int AudioChannel::play() {
    result = (*bqPlayerPlay)->SetPlayState(bqPlayerPlay, SL_PLAYSTATE_PLAYING);
    if (result != SL_RESULT_SUCCESS)return 0;
    return 1;
}

int AudioChannel::write(uint8_t *src, int srcStride) {
    (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, src, srcStride);
    delete src;
    return 0;
}

int AudioChannel::release() {
    if (bqPlayerObject) {// AudioPlayer
        (*bqPlayerObject)->Destroy(bqPlayerObject);
    }
    if (outputMixObject) {// 混响
        (*outputMixObject)->Destroy(outputMixObject);
    }
    if (engineObject) {// 引擎
        (*engineObject)->Destroy(engineObject);
    }
    return 0;
}

///-------------------------------------------------------------------------------------------------
int AudioChannel::CreateEngine() {
    // 创建引擎
    result = slCreateEngine(&engineObject, 0, nullptr, 0, nullptr, nullptr);
    if (result != SL_RESULT_SUCCESS)return 0;
    // 实现引擎
    result = (*engineObject)->Realize(engineObject, SL_BOOLEAN_FALSE);
    if (result != SL_RESULT_SUCCESS)return 0;
    // 获取引擎接口
    result = (*engineObject)->GetInterface(engineObject, SL_IID_ENGINE, &engineEngine);
    if (result != SL_RESULT_SUCCESS)return 0;
    return 1;
}

int AudioChannel::createOutputMix() {
    const SLInterfaceID ids_engine[1] = {SL_IID_ENVIRONMENTALREVERB};
    const SLboolean req_engine[1] = {SL_BOOLEAN_FALSE};
    result = (*engineEngine)->CreateOutputMix(engineEngine, &outputMixObject, 1, ids_engine,
                                              req_engine);
    if (result != SL_RESULT_SUCCESS)return 0;

    // 实现输出混音器
    result = (*outputMixObject)->Realize(outputMixObject, SL_BOOLEAN_FALSE);
    if (result != SL_RESULT_SUCCESS)return 0;
    return 1;
}

int AudioChannel::setOutputMix() {
    result = (*outputMixObject)->GetInterface(outputMixObject, SL_IID_ENVIRONMENTALREVERB,
                                              &outputMixEnvironmentalReverb);
    if (result != SL_RESULT_SUCCESS)return 0;
    result = (*outputMixEnvironmentalReverb)->SetEnvironmentalReverbProperties(
            outputMixEnvironmentalReverb, &reverbSettings);
    if (result != SL_RESULT_SUCCESS)return 0;
    return 1;
}

int AudioChannel::createAudioPlayer() {
    SLuint32 sampleRateInHz_ = this->sampleRateInHz * 1000;
    SLuint32 channelConfig_ = channelConfig;
    SLuint32 audioFormat_ = SL_PCMSAMPLEFORMAT_FIXED_16;
    SLuint32 channel_ = 2;

    bqPlayerSampleRate = sampleRateInHz_;
    SLDataLocator_AndroidSimpleBufferQueue loc_bufq = {SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, 2};
    SLDataFormat_PCM format_pcm = {SL_DATAFORMAT_PCM,           //PCM 格式
                                   channel_,                    //两个声道
                                   sampleRateInHz_,             //采样率 44100 Hz
                                   audioFormat_,                //采样位数 16位
                                   audioFormat_,                //容器为 16 位
                                   channelConfig_,              //左右双声道
                                   SL_BYTEORDER_LITTLEENDIAN};  //小端格式
    // 设置音频数据源 , 配置缓冲区 ( loc_bufq ) 与 音频格式 (format_pcm)
    SLDataSource audioSrc = {&loc_bufq, &format_pcm};
    SLDataLocator_OutputMix loc_outmix = {SL_DATALOCATOR_OUTPUTMIX, outputMixObject};
    SLDataSink audioSink = {&loc_outmix, nullptr};
    const SLInterfaceID ids_player[3] = {SL_IID_BUFFERQUEUE, SL_IID_VOLUME, SL_IID_EFFECTSEND,
            /*SL_IID_MUTESOLO,*/};
    const SLboolean req_player[3] = {SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE,
            /*SL_BOOLEAN_TRUE,*/ };

    // 创建播放器
    result = (*engineEngine)->CreateAudioPlayer(
            engineEngine,
            &bqPlayerObject,
            &audioSrc, //音频输入
            &audioSink, //音频输出
            bqPlayerSampleRate ? 2 : 3,//
            ids_player,
            req_player);
    if (result != SL_RESULT_SUCCESS)return 0;
    // 创建播放器对象
    result = (*bqPlayerObject)->Realize(bqPlayerObject, SL_BOOLEAN_FALSE);
    if (result != SL_RESULT_SUCCESS)return 0;
    return 1;
}


//获取播放器接口 和 缓冲队列接口
int AudioChannel::getOther1() {
    // 获取播放器 Player 接口 : 该接口用于设置播放器状态 , 开始 暂停 停止 播放 等操作
    result = (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_PLAY, &bqPlayerPlay);
    if (result != SL_RESULT_SUCCESS)return 0;
    // 获取播放器 缓冲队列 接口 : 该接口用于控制 音频 缓冲区数据 播放
    result = (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_BUFFERQUEUE,
                                             &bqPlayerBufferQueue);
    if (result != SL_RESULT_SUCCESS)return 0;
//    //注册缓冲区队列的回调函数 , 每次播放完数据后 , 会自动回调该函数
//    result = (*bqPlayerBufferQueue)->RegisterCallback(bqPlayerBufferQueue, bqPlayerCallback,
//                                                      this);
//    if (result != SL_RESULT_SUCCESS)return 0;
    return 1;
}

// 获取效果器接口 和 音量控制接口
int AudioChannel::getOther2() {
    // 获取效果器发送接口 ( get the effect send interface )
    if (0 != bqPlayerSampleRate) {
        result = (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_EFFECTSEND,
                                                 &bqPlayerEffectSend);
        if (result != SL_RESULT_SUCCESS) {
            result = (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_MUTESOLO,
                                                     &bqPlayerMuteSolo);
        }
    }
    if (result != SL_RESULT_SUCCESS)return 0;

    // 获取音量控制接口

    // 获取音量控制接口 ( get the volume interface ) [ 如果需要调节音量可以获取该接口 ]
    result = (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_VOLUME, &bqPlayerVolume);
    if (result != SL_RESULT_SUCCESS)return 0;
    return 1;
}