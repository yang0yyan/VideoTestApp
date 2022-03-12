package com.example.nativelib;

import android.view.Surface;

public class IMediaPlayer {
    static {
        System.loadLibrary("media_lib");
    }

    public static native int initVideo(int width, int height, int rotation);

    public static native int setSurface(Surface surface);

    public static native int removeSurface();

    public static native int write(byte[] bytes);


    public static native int initAudio(int sampleRateInHz, int channelConfig, int audioFormat);

    public static native int createAudio();

    public static native int playAudio();

    public static native int writeAudio(byte[] bytes);

    public static native int releaseAudio();
}
