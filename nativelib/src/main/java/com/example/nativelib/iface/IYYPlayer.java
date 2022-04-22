package com.example.nativelib.iface;

import android.net.Uri;
import android.view.Surface;

public interface IYYPlayer {

    void readMediaFile(Uri uri);

    void startPlay();

    void seekTo(long timeUs);

    void playStateChange(int state);
    /**
     * 设置需要显示的布局
     *
     * @param surface 表面
     */
    void setSurface(Surface surface);

    /**
     * 移除当前设置的Surface
     * 当需要切换Surface或是即将销毁Surface时需要调用
     * 否则会报错闪退
     */
    void removeSurface();

    void release();
}
