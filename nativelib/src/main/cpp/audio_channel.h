//
// Created by Administrator on 2022/1/25.
//

#ifndef VIDEOTESTAPP_AUDIO_CHANNEL_H
#define VIDEOTESTAPP_AUDIO_CHANNEL_H


#include <cstdint>
#include <jni.h>

#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>

class AudioChannel {

public:
    int init(JNIEnv *env, int sampleRateInHz, int channelConfig, int audioFormat);

    int create();
    int play();

    int write(uint8_t *src, int srcStride);

    int release();

private:
    int sampleRateInHz = 44100;
    int channelConfig = SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT;
    int audioFormat = SL_PCMSAMPLEFORMAT_FIXED_16;

    SLresult result;
    SLObjectItf engineObject;//引擎对象
    SLEngineItf engineEngine;//引擎接口
    SLObjectItf outputMixObject = nullptr;//输出混音器对象

    SLEnvironmentalReverbItf outputMixEnvironmentalReverb = nullptr;//环境混响接口
    SLEnvironmentalReverbSettings reverbSettings = SL_I3DL2_ENVIRONMENT_PRESET_STONECORRIDOR;

    SLObjectItf bqPlayerObject = nullptr;//播放器对象
    SLmilliHertz bqPlayerSampleRate = 0;

    SLPlayItf bqPlayerPlay;//播放器接口     开始 暂停 停止 播放
    SLAndroidSimpleBufferQueueItf bqPlayerBufferQueue;//播放器缓冲队列接口    用于控制 音频 缓冲区数据 播放

    SLEffectSendItf bqPlayerEffectSend;//效果器发送接口
    SLMuteSoloItf bqPlayerMuteSolo;;//效果器发送接口
    SLVolumeItf bqPlayerVolume;//音量控制接口

private:
    int CreateEngine();

    int createOutputMix();

    int setOutputMix();

    int createAudioPlayer();

    //获取播放器接口 和 缓冲队列接口
    int getOther1();

    // 获取效果器接口 和 音量控制接口
    int getOther2();

};


#endif //VIDEOTESTAPP_AUDIO_CHANNEL_H
