package com.example.nativelib;

import static android.os.SystemClock.sleep;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.nativelib.iface.IYYMediaCodec;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class YYMediaCodec implements IYYMediaCodec {
    private static final String TAG = "MediaCodecManager";
    private Context context;

    private List<String> videoDecoderInfos = new ArrayList<>();
    private List<String> audioDecoderInfos = new ArrayList<>();
    private MediaExtractor videoMediaExtractor;
    private MediaExtractor audioMediaExtractor;
    private MediaCodec videoMediaCodec;
    private MediaCodec audioMediaCodec;
    private ChildThread videoThread;
    private ChildThread audioThread;

    private int videoSupport = 0;
    private int audioSupport = 0;
    private int rotation = 0;
    private int videoWidth = 0;
    private int videoHeight = 0;
    private long videoDurationUs = 0;
    private long audioDurationUs = 0;
    private int sampleRateInHz = 0;
    private int channelConfig = 0;
    private int audioFormat = 0;

    private long startMs = 0;

    private Uri fileUri;

    public YYMediaCodec(Context context) {
        this.context = context;
    }

    private MediaDecodeListener mediaDecodeCallback;

    public void setMediaDecodeListener(MediaDecodeListener listener) {
        this.mediaDecodeCallback = listener;
        getMediaCodecList();
    }

    private void init() {
        videoSupport = 0;
        audioSupport = 0;
        createThread();
    }

    @Override
    public void readMediaFile(Uri uri) {
        release();
        init();
        fileUri = uri;
        readMedia(uri);
        if (videoSupport != 1 || audioSupport != 1) return;
        mediaDecodeCallback.readVideoComplete(videoWidth, videoHeight, rotation, Math.min(videoDurationUs, audioDurationUs));
        mediaDecodeCallback.readAudioComplete(sampleRateInHz, channelConfig, audioFormat);
    }

    @Override
    public void startDecode() {
        if (videoSupport != 1 || audioSupport != 1) return;
        if (null == videoMediaCodec || null == audioMediaCodec) return;
        mediaDecodeCallback.logMediaInfo("开始播放");
        videoMediaCodec.start();
        audioMediaCodec.start();
        startMs = System.currentTimeMillis();
        mediaDecodeCallback.onVideoStatusChange(true);
    }

    @Override
    public void pauseDecode() {

    }

    @Override
    public void stopDecode() {

    }

    @Override
    public void seekTo(long timeUs) {
        if(null!=audioMediaExtractor||null!=videoMediaExtractor){
            audioMediaExtractor.seekTo(timeUs,MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            videoMediaExtractor.seekTo(timeUs,MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        }
    }

    @Override
    public void release() {
        releaseVideo();
        releaseAudio();
        if (null != videoThread && videoThread.isAlive()) {
            videoThread.interrupt();
        }
        if (null != audioThread && audioThread.isAlive()) {
            audioThread.interrupt();
        }
    }

    private void releaseVideo() {
        if (null != videoMediaExtractor) {
            videoMediaExtractor.release();
            videoMediaExtractor = null;
        }
        if (null != videoMediaCodec) {
            videoMediaCodec.stop();
            videoMediaCodec.release();
            videoMediaCodec = null;
        }
        Log.d(TAG, "close: 资源释放  视频");
    }

    private void releaseAudio() {
        if (null != audioMediaCodec) {
            audioMediaCodec.stop();
            audioMediaCodec.release();
            audioMediaCodec = null;
        }
        if (null != audioMediaExtractor) {
            audioMediaExtractor.release();
            audioMediaExtractor = null;
        }
        Log.d(TAG, "close: 资源释放  音频");
    }

    private void createThread(){
        if (null == videoThread) {
            videoThread = new ChildThread();
            videoThread.start();
        }
        if (null == audioThread) {
            audioThread = new ChildThread();
            audioThread.start();
        }
    }


    private void getMediaCodecList() {
        videoDecoderInfos.clear();
        audioDecoderInfos.clear();
        MediaCodecList mediaCodecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
        MediaCodecInfo[] mediaCodecInfos = mediaCodecList.getCodecInfos();
        for (int i = mediaCodecInfos.length - 1; i >= 0; i--) {
            MediaCodecInfo codecInfo = mediaCodecInfos[i];
            if (!codecInfo.isEncoder()) {
                for (String t : codecInfo.getSupportedTypes()) {
                    if (t.startsWith("video/")) {
//                        videoDecoderInfos.put(t, codecInfo.getCapabilitiesForType(t));
                        videoDecoderInfos.add(t);
                    } else if (t.startsWith("audio/")) {
//                        audioDecoderInfos.put(t, codecInfo.getCapabilitiesForType(t));
                        audioDecoderInfos.add(t);
                    }
                }
            }
        }
        Log.d(TAG, "getMediaCodecList: " + videoDecoderInfos.toString());
        Log.d(TAG, "getMediaCodecList: " + audioDecoderInfos.toString());
        mediaDecodeCallback.logMediaInfo("支持的视频格式：" + videoDecoderInfos.toString());
        mediaDecodeCallback.logMediaInfo("支持的音频格式：" + audioDecoderInfos.toString());
    }

    public void readMedia(Uri uri) {
        MediaExtractor mediaExtractor = new MediaExtractor();
        try {
            mediaExtractor.setDataSource(context, uri, null);
            int trackCount = mediaExtractor.getTrackCount();
            mediaDecodeCallback.logMediaInfo("轨道数: " + trackCount);
            for (int i = 0; i < trackCount; i++) {
                MediaFormat mediaFormat = mediaExtractor.getTrackFormat(i);
                String mime = mediaFormat.getString(MediaFormat.KEY_MIME);

                if (mime.startsWith("video/") && videoSupport != 1) {
                    readVideo(mediaFormat, i);
                } else if (mime.startsWith("audio/") && audioSupport != 1) {
                    readAudio(mediaFormat, i);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            mediaExtractor.release();
        }
    }

    public void readVideo(MediaFormat mediaFormat, int index) throws IOException {
        String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
        if (!videoDecoderInfos.contains(mime) || audioSupport == -1) {
            mediaDecodeCallback.logMediaInfo("格式不支持: " + mime);
            videoSupport = -1;
            return;
        }
        videoSupport = 1;
        mediaDecodeCallback.logMediaInfo("视频格式: " + mime);
        Log.d(TAG, "视频格式: " + mediaFormat.toString());
        rotation = 0;
        if (mediaFormat.containsKey(MediaFormat.KEY_ROTATION)) {
            rotation = mediaFormat.getInteger(MediaFormat.KEY_ROTATION);
        }
        int videoSampleRateInHz = 0;
        if (mediaFormat.containsKey(MediaFormat.KEY_FRAME_RATE)) {
            videoSampleRateInHz = mediaFormat.getInteger(MediaFormat.KEY_FRAME_RATE);
        }
        int videoWidth = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
        int videoHeight = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);

//        if (mediaFormat.containsKey("crop-left") && mediaFormat.containsKey("crop-right")) {
//            videoWidth = mediaFormat.getInteger("crop-right") + 1 - mediaFormat.getInteger("crop-left");
//        }
//        if (mediaFormat.containsKey("crop-top") && mediaFormat.containsKey("crop-bottom")) {
//            videoHeight = mediaFormat.getInteger("crop-bottom") + 1 - mediaFormat.getInteger("crop-top");
//        }

        this.videoWidth = videoWidth;
        this.videoHeight = videoHeight;
        videoDurationUs = mediaFormat.getLong(MediaFormat.KEY_DURATION);

//        if (rotation == 0) {
//            mediaDecodeCallback.setSurfaceViewLayoutParams(videoWidth, videoHeight);
//        } else if (rotation == 90) {
//            mediaDecodeCallback.setSurfaceViewLayoutParams(videoHeight, videoWidth);
//        }
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
        int colorFormat = mediaFormat.getInteger(MediaFormat.KEY_COLOR_FORMAT);
        mediaDecodeCallback.logMediaInfo("videoSampleRateInHz: " + videoSampleRateInHz + "; videoWidth:" + videoWidth + "; videoHeight:" + videoHeight + "; rotation:" + rotation + "; colorFormat:" + colorFormat);

        if (null == videoMediaExtractor)
            videoMediaExtractor = new MediaExtractor();

        videoMediaExtractor.setDataSource(context, fileUri, null);
        // 选择轨道
        videoMediaExtractor.selectTrack(index);
        // 创建解码器
        videoMediaCodec = MediaCodec.createDecoderByType(mime);
        // 设置异步回调,输入加密数据，输出解码的NV12数据
        videoMediaCodec.setCallback(videoCallback, new Handler(videoThread.childLooper));
        //
        videoMediaCodec.configure(mediaFormat, null, null, 0);
//        showSupportedColorFormat(videoMediaCodec.getCodecInfo().getCapabilitiesForType(mime));
    }

    public void readAudio(MediaFormat mediaFormat, int index) throws IOException {
        String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
        if (!audioDecoderInfos.contains(mime) || videoSupport == -1) {
            mediaDecodeCallback.logMediaInfo("格式不支持: " + mime);
            audioSupport = -1;
            return;
        }
        audioSupport = 1;
        mediaDecodeCallback.logMediaInfo("音频格式: " + mime);
        Log.d(TAG, "音频格式: " + mediaFormat.toString());
        sampleRateInHz = mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        int channelCount = mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        if (mediaFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
            audioFormat = mediaFormat.getInteger(MediaFormat.KEY_PCM_ENCODING);
        }
        channelConfig = AudioFormat.CHANNEL_OUT_STEREO;
        if (channelCount == 1) {
            channelConfig = AudioFormat.CHANNEL_OUT_MONO;
        }
        audioDurationUs = mediaFormat.getLong(MediaFormat.KEY_DURATION);
        mediaDecodeCallback.logMediaInfo("sampleRateInHz: " + sampleRateInHz + "; channelConfig:" + channelConfig + "; audioFormat:" + audioFormat);
        if (null == audioMediaExtractor)
            audioMediaExtractor = new MediaExtractor();
        audioMediaExtractor.setDataSource(context, fileUri, null);
        audioMediaExtractor.selectTrack(index);
        audioMediaCodec = MediaCodec.createDecoderByType(mime);
        audioMediaCodec.setCallback(audioCallback, new Handler(audioThread.childLooper));
        audioMediaCodec.configure(mediaFormat, null, null, 0);
    }

    /**
     * -----------------------------------------视频------------------------------------------------
     */
    private MediaCodec.Callback videoCallback = new MediaCodec.Callback() {

        byte[] data;

        @Override
        public void onInputBufferAvailable(@NonNull MediaCodec codec, int inIndex) {
            ByteBuffer inBuffer = codec.getInputBuffer(inIndex);
            int size = videoMediaExtractor.readSampleData(inBuffer, 0);
            if (size < 0) {
                Log.d(TAG, "mybe eos or error");
                codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            } else {
                codec.queueInputBuffer(inIndex, 0, size, videoMediaExtractor.getSampleTime(), 0);
                videoMediaExtractor.advance();
                inBuffer.clear();
            }
        }

        @Override
        public void onOutputBufferAvailable(@NonNull MediaCodec codec, int outIndex, @NonNull MediaCodec.BufferInfo outBufferInfo) {
            if ((outBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                codec.releaseOutputBuffer(outIndex, true);
                releaseVideo();
                mediaDecodeCallback.logMediaInfo("视频结束");
                Log.d(TAG, "run: 视频  结束");
                data = null;
                mediaDecodeCallback.onVideoStatusChange(false);
                return;
            }
            if (outBufferInfo.presentationTimeUs / 1000 > System.currentTimeMillis() - startMs) {
                sleep(outBufferInfo.presentationTimeUs / 1000 - (System.currentTimeMillis() - startMs));
            }
            if (outBufferInfo.size > 0) {
                ByteBuffer outBuffer = codec.getOutputBuffer(outIndex);
                outBuffer.position(outBufferInfo.offset);
                outBuffer.limit(outBufferInfo.offset + outBufferInfo.size);
                if (data == null)
                    data = new byte[outBufferInfo.size];
                Arrays.fill(data, (byte) 0);
                outBuffer.get(data);
                mediaDecodeCallback.onVideoOutput(data);
                outBuffer.clear();
            }
            codec.releaseOutputBuffer(outIndex, true);
        }

        @Override
        public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {
            releaseVideo();
            mediaDecodeCallback.logMediaInfo("视频错误");
        }

        @Override
        public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {
            Log.d(TAG, "onOutputFormatChanged: ");
        }
    };
    /**
     * -----------------------------------------音频------------------------------------------------
     */
    private MediaCodec.Callback audioCallback = new MediaCodec.Callback() {

        private byte[] data;

        @Override
        public void onInputBufferAvailable(@NonNull MediaCodec codec, int inIndex) {
            ByteBuffer inBuffer = codec.getInputBuffer(inIndex);
            int size = audioMediaExtractor.readSampleData(inBuffer, 0);
            if (size < 0) {
                Log.d(TAG, "mybe eos or error");
                codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            } else {
                codec.queueInputBuffer(inIndex, 0, size, audioMediaExtractor.getSampleTime(), 0);
                audioMediaExtractor.advance();
                inBuffer.clear();
            }
        }

        @Override
        public void onOutputBufferAvailable(@NonNull MediaCodec codec, int outIndex, @NonNull MediaCodec.BufferInfo outBufferInfo) {
            if ((outBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                codec.releaseOutputBuffer(outIndex, true);
                Log.d(TAG, "run: 音频  结束");
                mediaDecodeCallback.logMediaInfo("音频结束");
                data = null;
                return;
            }
            if (outBufferInfo.presentationTimeUs / 1000 > System.currentTimeMillis() - startMs) {
                sleep(outBufferInfo.presentationTimeUs / 1000 - (System.currentTimeMillis() - startMs));
            }
            if (outBufferInfo.size > 0) {
                ByteBuffer outBuffer = codec.getOutputBuffer(outIndex);
                outBuffer.position(outBufferInfo.offset);
                outBuffer.limit(outBufferInfo.offset + outBufferInfo.size);
                if (data == null)
                    data = new byte[outBufferInfo.size];
                Arrays.fill(data, (byte) 0);
                outBuffer.get(data);
                mediaDecodeCallback.onAudioOutput(data);
                outBuffer.clear();

                mediaDecodeCallback.onAudioTimeChange(outBufferInfo.presentationTimeUs);
            }
            codec.releaseOutputBuffer(outIndex, false);
        }

        @Override
        public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {
            Log.d(TAG, "onError2: ");
            releaseAudio();
            Log.d(TAG, "run: 音频  结束");
            mediaDecodeCallback.logMediaInfo("音频错误");
        }

        @Override
        public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {
            Log.d(TAG, "onOutputFormatChanged2: ");
        }
    };

    private static class ChildThread extends Thread {
        Looper childLooper;

        @Override
        public void run() {
            super.run();
            Looper.prepare();
            childLooper = Looper.myLooper();
            Looper.loop();
        }
    }

    class TimeThread extends Thread {

        @Override
        public void run() {
            super.run();

        }
    }


    public interface MediaDecodeListener {
        void readAudioComplete(int sampleRateInHz, int channelConfig, int audioFormat);

        void readVideoComplete(int width, int height, int rotation, long time);

        void onVideoOutput(byte[] bytes);

        void onAudioOutput(byte[] bytes);

        void onAudioTimeChange(long time);

        void onVideoStatusChange(boolean status);

        void logMediaInfo(String msg);
    }
}
