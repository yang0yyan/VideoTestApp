//
// Created by Administrator on 2022/1/10.
//

#ifndef VIDEOTESTAPP_VIDEO_CHANNEL_H
#define VIDEOTESTAPP_VIDEO_CHANNEL_H

#include <jni.h>
#include <malloc.h>
#include <cstring>
#include <pthread.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>

extern "C"
{
#include <libswscale/swscale.h>
#include <libavutil/imgutils.h>
};


class VideoChannel {


private:
    long int crv_tab[256];
    long int cbu_tab[256];
    long int cgu_tab[256];
    long int cgv_tab[256];
    long int tab_76309[256];
    unsigned char clp[1024];   //for clip in CCIR601

    bool toShow = false;

    int videoWidth;
    int videoHeight;
    int rotation;

    pthread_mutex_t mutex;

    ANativeWindow *native_window = nullptr;

    int createNativeWindow(JNIEnv *env, jobject surface);

    void releaseNativeWindow();

public:
    VideoChannel() {
        pthread_mutex_init(&mutex, NULL);
    }

    ~VideoChannel() {
        pthread_mutex_destroy(&mutex);
    }

    int init(JNIEnv *env, int width, int height, int rotation);

    int setSurface(JNIEnv *env, jobject surface);

    int removeSurface();

    int write(uint8_t *src, int srcStride);

    void
    NV12_or_NV21_to_rgb32(int frame_type, unsigned char *yuvbuffer, unsigned char *rgbbuffer,
                          int width,
                          int height);

    void
    NV12_or_NV21_to_rgb32(int frame_type, unsigned char *yuvbuffer, int yuvbufferLen,
                          unsigned char *rgbbuffer, int width,
                          int height);

    void
    yuv420sp_to_rgb32(uint8_t *yuvbuffer, uint8_t *dst_data[4], int dst_linesize[4], int width,
                      int height);

    void init_yuv420p_table();
};


#endif //VIDEOTESTAPP_VIDEO_CHANNEL_H
