package com.example.nativelib;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

public class YYAudioTrack {
    private int sampleRateInHz = 8000;
    private int channelConfig = AudioFormat.CHANNEL_IN_STEREO;
    private int audioFormat = AudioFormat.ENCODING_PCM_8BIT;
    private int bufferSizeInBytes;
    private AudioTrack mAudioTrack = null;

    public YYAudioTrack() {
    }

    public void init(int sampleRateInHz, int channelConfig, int audioFormat) {
        if (this.sampleRateInHz == sampleRateInHz && this.channelConfig == channelConfig && this.audioFormat == audioFormat && null != mAudioTrack) {
            mAudioTrack.stop();
            return;
        }
        this.sampleRateInHz = sampleRateInHz;
        this.channelConfig = channelConfig;
        this.audioFormat = audioFormat;
        if (null != mAudioTrack)
            release();
        createAudioTrack();
    }

    private void createAudioTrack() {
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build();
        AudioFormat format = new AudioFormat.Builder()
                .setChannelMask(channelConfig)
                .setEncoding(audioFormat)
                .setSampleRate(sampleRateInHz)
                .build();
        bufferSizeInBytes = AudioTrack.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);
        mAudioTrack = new AudioTrack(attributes, format, bufferSizeInBytes, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
    }

    public void play() {
        if (null != mAudioTrack)
            mAudioTrack.play();
    }

    public void write(byte[] data) {
        if (null != mAudioTrack)
            mAudioTrack.write(data, 0, data.length);
    }

    public void stop() {
        if (null != mAudioTrack)
            mAudioTrack.stop();
    }


    public void release() {
        if (null != mAudioTrack) {
            mAudioTrack.stop();
            mAudioTrack.release();
            mAudioTrack = null;
        }
    }
}
