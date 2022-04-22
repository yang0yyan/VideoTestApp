package com.example.videotestapp;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.example.nativelib.YYPlayer;
import com.example.videotestapp.databinding.ActivityMainBinding;
import com.example.videotestapp.util.PermissionUtil;
import com.example.videotestapp.util.RotationUtil;
import com.example.videotestapp.util.TimeUtil;

import java.util.LinkedList;

public class MainActivity extends BaseActivity implements View.OnClickListener, YYPlayer.MediaStatusListener {
    private static final String TAG = "MainActivity";
    private ActivityMainBinding binding;
    private Button button;
    private RotationUtil rotationUtil;
    private YYPlayer yyPlayer;

    private LinkedList<String> logLines = new LinkedList<>();
    private boolean showTools = true;
    private boolean isFirst = true;
    private int width;
    private int height;

    private int videoWidth = 0;
    private int videoHeight = 0;
    private long currentTime = 0;
    private long totalTime = 0;

    @Override
    protected View getLayoutId() {
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        return binding.getRoot();
    }

    @Override
    protected void initView() {
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.clSurfaceWindow.setOnClickListener(this);
        binding.ivFullScreen.setOnClickListener(this);
        binding.ivPlay.setOnClickListener(this);

        binding.seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                StringBuilder builder = new StringBuilder().append(TimeUtil.microsecondToClock(progress, 0)).append("/").append(TimeUtil.microsecondToClock(totalTime, 0));
                binding.tvTimeProgress.setText(builder.toString());
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                yyPlayer.seekTo(seekBar.getProgress());
            }
        });


        int mCurrentOrientation = getResources().getConfiguration().orientation;
        if (mCurrentOrientation == Configuration.ORIENTATION_PORTRAIT) {
            binding.btnChooseFile.setOnClickListener(this);
            binding.btnOpenCamera.setOnClickListener(this);

            /*设置获取状态栏高度，设置状态-----------------------开始*/
            Resources resources = getResources();
            int resourceId = resources.getIdentifier("status_bar_height", "dimen", "android");
            int statusBarHeight = resources.getDimensionPixelSize(resourceId);
            binding.statusBar.setHeight(statusBarHeight);
            showSystemUI();
            /*设置获取状态栏高度，设置状态-----------------------结束*/

            /*设置布局宽高-----------------------开始*/
            DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
            //屏幕宽
            int widthPixels = displayMetrics.widthPixels;
            //屏幕高
            int heightPixels = displayMetrics.heightPixels;
            //屏幕密度
            float density = displayMetrics.density;
            width = widthPixels;
            height = widthPixels * widthPixels / heightPixels;
            //        new Thread(runnable).start();

            ConstraintLayout.LayoutParams params1 = (ConstraintLayout.LayoutParams) binding.clSurfaceWindow.getLayoutParams();
            params1.height = height;
            params1.width = width;
            binding.clSurfaceWindow.setLayoutParams(params1);

            ConstraintLayout.LayoutParams params2 = (ConstraintLayout.LayoutParams) binding.surface.getLayoutParams();
            params2.height = height;
            params2.width = width;
            binding.surface.setLayoutParams(params2);
            /*设置布局宽高-----------------------结束*/

            /*显示日志-----------------------开始*/
            final StringBuilder stringBuffer = new StringBuilder();
            for (String line : logLines) {
                stringBuffer.append(line).append('\n');
            }
            binding.tvLog.setText(stringBuffer.toString());
            /*显示日志-----------------------结束*/

        } else if (mCurrentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
            //屏幕宽
            int widthPixels = displayMetrics.widthPixels;
            //屏幕高
            int heightPixels = displayMetrics.heightPixels;
            //屏幕密度
            float density = displayMetrics.density;
            width = widthPixels;
            height = heightPixels;
            hideSystemUI();
        }
        if (videoWidth != 0 && videoHeight != 0) {
            setSurfaceViewLayoutParams(videoWidth, videoHeight);
        }
        binding.surface.getHolder().addCallback(callback);
        setTime();
    }

    @Override
    protected void initData() {
        rotationUtil = new RotationUtil(this);
        yyPlayer = new YYPlayer(this);
        yyPlayer.setMediaStatusListener(this);
        yyPlayer.init();
        isFirst = false;

        PermissionUtil.getPermissions(this, PermissionUtil.WRITE_EXTERNAL_STORAGE, PermissionUtil.READ_EXTERNAL_STORAGE, PermissionUtil.CAMERA);
    }

    @Override
    public void onClick(View v) {
        if (v == binding.clSurfaceWindow) {
            if (showTools) {
                binding.llSurfaceTopToole.setVisibility(View.GONE);
                binding.llSurfaceBottomToole.setVisibility(View.GONE);
            } else {
                binding.llSurfaceTopToole.setVisibility(View.VISIBLE);
                binding.llSurfaceBottomToole.setVisibility(View.VISIBLE);
            }
            showTools = !showTools;
        } else if (v == binding.ivFullScreen) {
            int mCurrentOrientation = getResources().getConfiguration().orientation;
            if (mCurrentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            } else {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            }
        } else if (v == binding.btnChooseFile) {
            openFile();
        } else if (v == binding.ivPlay) {
            yyPlayer.startPlay();
        } else if (v == binding.btnOpenCamera) {
            yyPlayer.opC();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            int mCurrentOrientation = getResources().getConfiguration().orientation;
            if (mCurrentOrientation != Configuration.ORIENTATION_PORTRAIT) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                return true;
            }

        }
        return super.onKeyDown(keyCode, event);
    }

    int mCurrentOrientation = -1;

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_PORTRAIT || orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (mCurrentOrientation != orientation) {
                mCurrentOrientation = orientation;
                initView();
            }
        }
    }

    private void openFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, 0X01);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 0X01 && resultCode == Activity.RESULT_OK) {
            final Uri uri = data.getData();
            yyPlayer.readMediaFile(uri);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        rotationUtil.close();
        yyPlayer.release();
    }

    @Override
    public void onVideoInfo(int width, int height, long time) {
        setSurfaceViewLayoutParams(width, height);
        totalTime = time;
        setTime();
    }

    @Override
    public void onPlayStatusChanged(boolean status) {
        binding.ivPlay.post(new Runnable() {
            @Override
            public void run() {
                if(!status){
                    binding.ivPlay.setImageResource(R.drawable.play_icon);
                }else{
                    binding.ivPlay.setImageResource(R.drawable.pause_icon);
                }
            }
        });
    }

    @Override
    public void onProgressChanged(long us) {
        Log.d(TAG, "onProgressChanged: "+us+";"+totalTime);
        binding.tvTimeProgress.post(new Runnable() {
            @Override
            public void run() {
                StringBuilder builder = new StringBuilder().append(TimeUtil.microsecondToClock(us, 0)).append("/").append(TimeUtil.microsecondToClock(totalTime, 0));
                binding.tvTimeProgress.setText(builder.toString());
                binding.seekBar.setProgress((int) us);
            }
        });
    }

    @Override
    public void logMediaInfo(String msg) {
        logFromUi(msg);
    }


    private void setTime() {
        StringBuilder builder = new StringBuilder().append(TimeUtil.microsecondToClock(currentTime, 0)).append("/").append(TimeUtil.microsecondToClock(totalTime, 0));
        binding.tvTimeProgress.setText(builder.toString());
        binding.seekBar.setMax((int) totalTime);
        binding.seekBar.setProgress((int) currentTime);
    }

    // 设置屏幕尺寸
    @Keep //混淆后不加注解会报错
    public void setSurfaceViewLayoutParams(final int width, final int height) {
        this.videoWidth = width;
        this.videoHeight = height;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                int w = width;
                int h = height;
                float scale = 1;
                if ((w / h) > (MainActivity.this.width / MainActivity.this.height)) {
                    scale = (float) MainActivity.this.width / (float) w;
                } else {
                    scale = (float) MainActivity.this.height / (float) h;
                }
                w = (int) ((float) w * scale);
                h = (int) ((float) h * scale);
                ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) binding.surface.getLayoutParams();
                params.height = h;
                params.width = w;
                binding.surface.setLayoutParams(params);
            }
        });
    }

    // 打印日志到屏幕上
    public void logFromUi(String msg) {
        logLines.add(msg);
        if (logLines.size() > 200) {
            logLines.removeFirst();
        }
        final StringBuilder stringBuffer = new StringBuilder();
        for (String line : logLines) {
            stringBuffer.append(line).append('\n');
        }
        if (null != binding.tvLog) {
            binding.tvLog.post(new Runnable() {
                @Override
                public void run() {
                    binding.tvLog.setText(stringBuffer.toString());
                    binding.scroll.fullScroll(View.FOCUS_DOWN);
                }
            });
        }

    }

    boolean surfaceCreate = false;

    SurfaceHolder.Callback callback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(@NonNull final SurfaceHolder holder) {
            surfaceCreate = true;
            yyPlayer.setSurface(holder.getSurface());

        }

        @Override
        public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {

        }

        @Override
        public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
            surfaceCreate = false;
            yyPlayer.removeSurface();
        }
    };


}