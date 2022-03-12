package com.example.videotestapp.util;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.view.OrientationEventListener;

/**
 * 监听“方向锁定”开关状态
 * 根据传感器设置屏幕方向
 *
 * 关闭方向锁定，屏幕方向跟随传感器旋转
 * 打开方向锁定，如果是横屏状态，可以进行反转
 */
public class RotationUtil {
    private RotationObserver mRotationObserver;
    private OrientationEventListener mOrientationListener;
    private Activity activity = null;
    private boolean freeRotation;

    public RotationUtil(Activity activity) {
        this.activity = activity;

        init();
    }


    private void init() {
        mOrientationListener = new OrientationEventListener(activity, SensorManager.SENSOR_DELAY_NORMAL) {
            @Override
            public void onOrientationChanged(int rotation) {
                // 设置竖屏
                if (activity.isFinishing()) {
                    return;
                }
                requestedOrientation(rotation);//根据传感器设置屏幕方向
            }
        };

        mRotationObserver = new RotationObserver(new Handler());
        mRotationObserver.startObserver();
        enableSensor();
        refreshRotation();
    }

    public void close() {
        if (mRotationObserver != null) {
            mRotationObserver.stopObserver();
            mRotationObserver = null;
        }
        if (mOrientationListener != null) {
            mOrientationListener.disable();
            mOrientationListener = null;
        }
    }

    //设置listener是否启动，在相应时机调用，只有在enable时才能监听传感器方向
    private void enableSensor() {
        if (mOrientationListener.canDetectOrientation()) {
            mOrientationListener.enable();
        } else {
            mOrientationListener.disable();
        }
    }

    private void requestedOrientation(int rotation) {//此时的rotation为传感器旋转的角度
        if (freeRotation) {
            if (rotation < 15 || rotation > 345) {// 手机顶部向上
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            } else if (rotation < 105 && rotation > 75) {// 手机右边向上
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
            } else if (rotation < 195 && rotation > 165) {// 手机低边向上
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT);
            } else if (rotation < 285 && rotation > 255) {// 手机左边向上
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            }
        } else {
            int mCurrentOrientation = activity.getResources().getConfiguration().orientation;
            if (mCurrentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                if (rotation < 105 && rotation > 75) {// 手机右边向上
                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
                } else if (rotation < 285 && rotation > 255) {// 手机左边向上
                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                }
            }
        }
    }

    //更新按钮状态
    private void refreshRotation() {
        freeRotation = getRotationStatus(activity) == 1;
    }

    // 更新按钮状态 需要系统级权限
    private void switchButton() {
        if (getRotationStatus(activity) == 1) {
            setRotationStatus(activity.getContentResolver(), 0);
        } else {
            setRotationStatus(activity.getContentResolver(), 1);
        }
    }

    // 得到屏幕旋转的状态
    private int getRotationStatus(Context context) {
        int status = 0;
        try {
            status = Settings.System.getInt(context.getContentResolver(),
                    Settings.System.ACCELEROMETER_ROTATION);
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }
        return status;
    }

    // 开关自动旋转 需要系统级权限
    private void setRotationStatus(ContentResolver resolver, int status) {
        //得到uri
        Uri uri = Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION);
        //沟通设置status的值改变屏幕旋转设置
        Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, status);
        //通知改变
        resolver.notifyChange(uri, null);
    }


    //观察屏幕旋转设置变化，类似于注册动态广播监听变化机制
    private class RotationObserver extends ContentObserver {
        ContentResolver mResolver;

        public RotationObserver(Handler handler) {
            super(handler);
            mResolver = activity.getContentResolver();
        }

        //屏幕旋转设置改变时调用
        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            //更新状态
            refreshRotation();
        }

        public void startObserver() {
            mResolver.registerContentObserver(Settings.System
                            .getUriFor(Settings.System.ACCELEROMETER_ROTATION), false,
                    this);
        }

        public void stopObserver() {
            mResolver.unregisterContentObserver(this);
        }
    }
}
