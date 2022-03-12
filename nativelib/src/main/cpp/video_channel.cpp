//
// Created by Administrator on 2022/1/10.
//

#include "video_channel.h"

int VideoChannel::init(JNIEnv *env, int width, int height, int rotation) {
    this->videoWidth = width;
    this->videoHeight = height;
    this->rotation = rotation;
    return 1;
}

int VideoChannel::setSurface(JNIEnv *env, jobject surface) {
    createNativeWindow(env, surface);
    toShow = true;
    return 0;
}

int VideoChannel::removeSurface() {
    toShow = false;
    releaseNativeWindow();
    return 0;
}

int VideoChannel::createNativeWindow(JNIEnv *env, jobject surface) {
    releaseNativeWindow();
    pthread_mutex_lock(&mutex);
    native_window = ANativeWindow_fromSurface(env, surface);
    ANativeWindow_setBuffersGeometry(native_window, videoWidth, videoHeight,
                                     WINDOW_FORMAT_RGBA_8888);
    pthread_mutex_unlock(&mutex);
    return 1;
}


void VideoChannel::releaseNativeWindow() {
    pthread_mutex_lock(&mutex);
    if (native_window) {
        ANativeWindow_release(native_window);
        native_window = nullptr;
    }
    pthread_mutex_unlock(&mutex);
}

int VideoChannel::write(uint8_t *src, int srcLen) {

    if (!native_window || !toShow)return 0;

    uint8_t *dst_data[4];
    int dst_linesize[4];
    yuv420sp_to_rgb32(src, dst_data, dst_linesize, videoWidth, videoHeight);

    uint8_t *buffer = dst_data[0];
    int bufferStride = dst_linesize[0];

//    uint8_t *buffer = new uint8_t [videoHeight*videoWidth*4];
//    int bufferStride = videoWidth*4;
//
//    NV12_or_NV21_to_rgb32(12,src,buffer,videoWidth,videoHeight);

    if (!native_window || !toShow)return 0;
    ANativeWindow_Buffer windowBuffer;
    int code = ANativeWindow_lock(native_window, &windowBuffer, nullptr);
    if (code) {
        delete src;
        delete[] buffer;
        return -19;
    }
    auto *dst = static_cast<uint8_t *>(windowBuffer.bits);
    int dstStride = windowBuffer.stride * 4;
    if (!dst || dstStride <= 0)return 0;
    for (int h = 0; h < videoHeight; h++) {
        if (nullptr == src || dstStride <= 0 || !toShow) {
            break;
        }
        memcpy(dst + h * dstStride, buffer + h * bufferStride, (size_t) bufferStride);
    }
//    if (nullptr != src) {
//        memcpy(dst, buffer, (size_t) videoHeight * bufferStride);
//    }
    if (native_window && toShow) {
        ANativeWindow_unlockAndPost(native_window);
    }
    delete src;
    delete[] buffer;
    return 1;
}

void VideoChannel::yuv420sp_to_rgb32(uint8_t *yuvbuffer, uint8_t *rgb_data[4], int rgb_linesize[4],
                                     int width, int height) {
    // 指针数组 , 数组中存放的是指针
    uint8_t *yuv_data[4];
    // 普通的 int 数组
    int yuv_linesize[4];

    av_image_fill_arrays(yuv_data, yuv_linesize, yuvbuffer, AV_PIX_FMT_NV12, width, height, 1);

    // 申请图像数据存储内存
    av_image_alloc(
            rgb_data,//指向图像数据的指针
            rgb_linesize,//图像数据存储的数据行数
            width,//图像的宽度
            height,//图像的高度
            AV_PIX_FMT_RGBA,//图像的像素格式,ARGB
            1);//未知

    struct SwsContext *sws_ctx = sws_getContext(
            width,//源图像的宽度
            width,//源图像的高度
            AV_PIX_FMT_NV12,//源图像的像素格式
            width,//目标图像的宽度
            width,//目标图像的高度
            AV_PIX_FMT_RGBA,//目标图像的像素格式
            SWS_BILINEAR,//使用的转换算法    有：快速、高质量
            nullptr,//源图像滤镜
            nullptr, //目标图像滤镜
            nullptr);//额外参数

    // NV12转RGBA
    sws_scale(sws_ctx, yuv_data,
              yuv_linesize, 0, height,
              rgb_data, rgb_linesize);
}


/*
功能：NV12 转 RGB24
耗时：210ms左右
使用举例：NV12_to_rgb24(0, srcSlice[0], RGB24, tex_w, tex_h) ;
     因为没有区分 格式，因此第一个参数 随便写，
     同时定义一个转换之后的指针：
     		unsigned char RGB24[1920*1080*10] = {0};
*/

void VideoChannel::init_yuv420p_table() {
    long int crv, cbu, cgu, cgv;
    int i, ind;
    static int init = 0;

    if (init == 1) return;

    crv = 104597;
    cbu = 132201;  /* fra matrise i global.h */
    cgu = 25675;
    cgv = 53279;

    for (i = 0; i < 256; i++) {
        crv_tab[i] = (i - 128) * crv;
        cbu_tab[i] = (i - 128) * cbu;
        cgu_tab[i] = (i - 128) * cgu;
        cgv_tab[i] = (i - 128) * cgv;
        tab_76309[i] = 76309 * (i - 16);
    }

    for (i = 0; i < 384; i++)
        clp[i] = 0;
    ind = 384;
    for (i = 0; i < 256; i++)
        clp[ind++] = i;
    ind = 640;
    for (i = 0; i < 384; i++)
        clp[ind++] = 255;

    init = 1;
}

/*
	函数功能：将NV12或者NV21格式数据 转换成RGB24
	参数说明：
	   frame_type：指的是pixfmt.h中结构体 AVPixelFormat 中对应的帧格式
*/
void VideoChannel::NV12_or_NV21_to_rgb32(int frame_type, unsigned char *yuvbuffer,
                                         unsigned char *rgbbuffer, int width, int height) {
    NV12_or_NV21_to_rgb32(frame_type, yuvbuffer, width * height / 2 * 3, rgbbuffer, width, height);
//    int y1, y2, u, v;
//    unsigned char *py1, *py2;
//    int i, j, c1, c2, c3, c4;
//    unsigned char *d1, *d2;
//    unsigned char *src_u;
//    static int init_yuv420p = 0;
//
//    src_u = yuvbuffer + width * height;   // u
//
//    py1 = yuvbuffer;   // y
//    py2 = py1 + width;
//    d1 = rgbbuffer;
//    d2 = d1 + 4 * width;
//
//    init_yuv420p_table();
//
//    for (j = 0; j < height; j += 2) {
//        for (i = 0; i < width; i += 2) {
//
//            if (frame_type == 12) {
//                u = *(src_u++);
//                v = *(src_u++);      // v紧跟u，在u的下一个位置
//            }
//            if (frame_type == 21) {
//                v = *(src_u++);
//                u = *(src_u++);      // u紧跟v，在v的下一个位置
//            }
//
//            c1 = crv_tab[v];
//            c2 = cgu_tab[u];
//            c3 = cgv_tab[v];
//            c4 = cbu_tab[u];
//
//            //up-left
//            y1 = tab_76309[*(py1++)];
//            *(d1++) = clp[384 + ((y1 + c1) >> 16)];
//            *(d1++) = clp[384 + ((y1 - c2 - c3) >> 16)];
//            *(d1++) = clp[384 + ((y1 + c4) >> 16)];
//            *(d1++) = 255;
//
//            //down-left
//            y2 = tab_76309[*(py2++)];
//            *(d2++) = clp[384 + ((y2 + c1) >> 16)];
//            *(d2++) = clp[384 + ((y2 - c2 - c3) >> 16)];
//            *(d2++) = clp[384 + ((y2 + c4) >> 16)];
//            *(d2++) = 255;
//
//            //up-right
//            y1 = tab_76309[*(py1++)];
//            *(d1++) = clp[384 + ((y1 + c1) >> 16)];
//            *(d1++) = clp[384 + ((y1 - c2 - c3) >> 16)];
//            *(d1++) = clp[384 + ((y1 + c4) >> 16)];
//            *(d1++) = 255;
//
//            //down-right
//            y2 = tab_76309[*(py2++)];
//            *(d2++) = clp[384 + ((y2 + c1) >> 16)];
//            *(d2++) = clp[384 + ((y2 - c2 - c3) >> 16)];
//            *(d2++) = clp[384 + ((y2 + c4) >> 16)];
//            *(d2++) = 255;
//        }
//        d1 += 4 * width;
//        d2 += 4 * width;
//        py1 += width;
//        py2 += width;
//    }
}

void VideoChannel::NV12_or_NV21_to_rgb32(int frame_type, unsigned char *yuvbuffer, int yuvbufferLen,
                                         unsigned char *rgbbuffer, int width, int height) {
    int y1, y2, u, v;
    unsigned char *py1, *py2;
    int i, j, c1, c2, c3, c4;
    unsigned char *d1, *d2;
    unsigned char *src_u;
    static int init_yuv420p = 0;

    src_u = yuvbuffer + yuvbufferLen / 3 * 2;   // u

    py1 = yuvbuffer;   // y
    py2 = py1 + width;
    d1 = rgbbuffer;
    d2 = d1 + 4 * width;

    init_yuv420p_table();

    for (j = 0; j < height; j += 2) {
        for (i = 0; i < width; i += 2) {

            if (frame_type == 12) {
                u = *src_u++;
                v = *src_u++;      // v紧跟u，在u的下一个位置
            }
            if (frame_type == 21) {
                v = *src_u++;
                u = *src_u++;      // u紧跟v，在v的下一个位置
            }
            c1 = crv_tab[v];
            c2 = cgu_tab[u];
            c3 = cgv_tab[v];
            c4 = cbu_tab[u];

            //up-left
            y1 = tab_76309[*py1++];
            *d1++ = clp[384 + ((y1 + c1) >> 16)];
            *d1++ = clp[384 + ((y1 - c2 - c3) >> 16)];
            *d1++ = clp[384 + ((y1 + c4) >> 16)];
            *d1++ = 255;

            //down-left
            y2 = tab_76309[*py2++];
            *d2++ = clp[384 + ((y2 + c1) >> 16)];
            *d2++ = clp[384 + ((y2 - c2 - c3) >> 16)];
            *d2++ = clp[384 + ((y2 + c4) >> 16)];
            *d2++ = 255;

            //up-right
            y1 = tab_76309[*py1++];
            *d1++ = clp[384 + ((y1 + c1) >> 16)];
            *d1++ = clp[384 + ((y1 - c2 - c3) >> 16)];
            *d1++ = clp[384 + ((y1 + c4) >> 16)];
            *d1++ = 255;

            //down-right
            y2 = tab_76309[*py2++];
            *d2++ = clp[384 + ((y2 + c1) >> 16)];
            *d2++ = clp[384 + ((y2 - c2 - c3) >> 16)];
            *d2++ = clp[384 + ((y2 + c4) >> 16)];
            *d2++ = 255;
        }
        d1 += 4 * width;
        d2 += 4 * width;
        py1 += width;
        py2 += width;
    }
}