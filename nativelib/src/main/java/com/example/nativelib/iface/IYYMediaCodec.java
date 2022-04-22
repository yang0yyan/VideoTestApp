package com.example.nativelib.iface;

import android.net.Uri;

public interface IYYMediaCodec {

    void readMediaFile(Uri uri);

    void startDecode();

    void pauseDecode();

    void stopDecode();

    void seekTo(long timeUs);

    void release();
}
