package com.example.nativelib;

import android.content.Context;
import android.net.Uri;
import android.view.Surface;

import com.example.nativelib.iface.IYYPlayer;

public class YYPlayer implements IYYPlayer, YYMediaCodec.MediaDecodeListener {

    Context context;
    private YYMediaCodec mediaCodec;
    private YYAudioTrack audioTrack;
//    private YYCamera camera;

    public YYPlayer(Context context) {
        this.context = context;
    }

    public void init() {
        mediaCodec = new YYMediaCodec(context);
        mediaCodec.setMediaDecodeListener(this);
        audioTrack = new YYAudioTrack();

//        camera = new YYCamera(context);
//        camera.setMediaDecodeListener(this);
//        camera.init();
    }

    @Override
    public void readMediaFile(Uri uri) {
        mediaCodec.readMediaFile(uri);
    }

    @Override
    public void startPlay() {
        mediaCodec.startDecode();
        audioTrack.play();
//        IMediaPlayer.playAudio();
    }

    @Override
    public void seekTo(long timeUs) {
        mediaCodec.seekTo(timeUs);
    }

    @Override
    public void playStateChange(int state) {

    }


    @Override
    public void setSurface(Surface surface) {
        IMediaPlayer.setSurface(surface);
    }

    @Override
    public void removeSurface() {
        IMediaPlayer.removeSurface();
    }

    @Override
    public void release() {
        mediaCodec.release();
        audioTrack.release();
//        camera.release();
//        IMediaPlayer.releaseAudio();
    }

    public void opC(){
//        camera.open();
    }

    private MediaStatusListener listener;

    public void setMediaStatusListener(MediaStatusListener listener) {
        this.listener = listener;
    }

    @Override
    public void readAudioComplete(int sampleRateInHz, int channelConfig, int audioFormat) {
        audioTrack.init(sampleRateInHz, channelConfig, audioFormat);
//        IMediaPlayer.initAudio(sampleRateInHz, channelConfig, audioFormat);
//        IMediaPlayer.createAudio();
    }

    @Override
    public void readVideoComplete(int width, int height, int rotation, long time) {
        listener.onVideoInfo(width, height, time);
        IMediaPlayer.initVideo(width, height, rotation);
    }

    @Override
    public void onVideoOutput(byte[] bytes) {
        IMediaPlayer.write(bytes);
    }

    @Override
    public void onAudioOutput(byte[] bytes) {
//        IMediaPlayer.writeAudio(bytes);
        audioTrack.write(bytes);
    }

    @Override
    public void onAudioTimeChange(long time) {
        listener.onProgressChanged( time);
    }

    @Override
    public void onVideoStatusChange(boolean status) {
        listener.onPlayStatusChanged(status);
    }

    @Override
    public void logMediaInfo(String msg) {
        listener.logMediaInfo(msg);
    }

    public static interface MediaStatusListener {
        void onVideoInfo(int width, int height, long time);

        void onPlayStatusChanged(boolean status);

        void onProgressChanged(long us);

        void logMediaInfo(String msg);
    }
}
