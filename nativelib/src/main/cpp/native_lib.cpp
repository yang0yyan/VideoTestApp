//
// Created by Administrator on 2022/1/10.
//

#include "native_lib.h"

jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    return JNI_VERSION_1_6;
}
///-------------------------------------video-----------------------------开始
extern "C"
JNIEXPORT jint JNICALL
Java_com_example_nativelib_IMediaPlayer_initVideo(JNIEnv *env, jclass clazz, jint width,
                                                  jint height, jint rotation) {
    // TODO: implement init()
    videoChannel.init(env, width, height, rotation);
    return 1;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_example_nativelib_IMediaPlayer_setSurface(JNIEnv *env, jclass clazz, jobject surface) {
    // TODO: implement setSurface()
    videoChannel.setSurface(env, surface);
    return 1;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_example_nativelib_IMediaPlayer_removeSurface(JNIEnv *env, jclass clazz) {
    // TODO: implement removeSurface()
    videoChannel.removeSurface();
    return 1;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_example_nativelib_IMediaPlayer_write(JNIEnv *env, jclass clazz, jbyteArray bytes) {
    // TODO: implement write()
    uint8_t *audio_buffer;
    jsize size = env->GetArrayLength(bytes);
    jbyte *sample_byte_array = env->GetByteArrayElements(bytes, JNI_FALSE);

//    audio_buffer = new uint8_t[size];
//    memcpy(sample_byte_array, audio_buffer, (size_t) size);

    char *chars = new char[size];
    memcpy(chars, sample_byte_array, size);

    videoChannel.write(reinterpret_cast<uint8_t *>(chars), size);
    env->ReleaseByteArrayElements(bytes, sample_byte_array, JNI_FALSE);
    return 1;
}
///-------------------------------------video-----------------------------结束



///-------------------------------------audio-----------------------------开始
extern "C"
JNIEXPORT jint JNICALL
Java_com_example_nativelib_IMediaPlayer_initAudio(JNIEnv *env, jclass clazz, jint sampleRateInHz,
                                                  jint channelConfig, jint audioFormat) {
    audioChannel.init(env, sampleRateInHz, channelConfig, audioFormat);
    return 1;
}
extern "C"
JNIEXPORT jint JNICALL
Java_com_example_nativelib_IMediaPlayer_createAudio(JNIEnv *env, jclass clazz) {
    return audioChannel.create();
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_example_nativelib_IMediaPlayer_playAudio(JNIEnv *env, jclass clazz) {
    audioChannel.play();
    return 1;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_example_nativelib_IMediaPlayer_writeAudio(JNIEnv *env, jclass clazz, jbyteArray bytes) {
    jsize size = env->GetArrayLength(bytes);
    jbyte *sample_byte_array = env->GetByteArrayElements(bytes, JNI_FALSE);

    char *chars = new char[size];
    memcpy(chars, sample_byte_array, size);

    audioChannel.write(reinterpret_cast<uint8_t *>(chars), size);
    env->ReleaseByteArrayElements(bytes, sample_byte_array, JNI_FALSE);
    return 1;
}
extern "C"
JNIEXPORT jint JNICALL
Java_com_example_nativelib_IMediaPlayer_releaseAudio(JNIEnv *env, jclass clazz) {
    audioChannel.release();
    return 1;
}

///-------------------------------------audio-----------------------------结束
