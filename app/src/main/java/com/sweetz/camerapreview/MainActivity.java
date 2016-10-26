package com.sweetz.camerapreview;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.TextView;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "CameraPreview::: ";
    private static final String FORMAT_FPS = "YUV420SP->ARGB %d fps\nAve. %.3fms\nmin %.3fms max %.3fms";
    private static int PREVIEW_WIDTH = 640;
    private static int PREVIEW_HEIGHT = 480;

    private SurfaceView mPreviewSurfaceView;
    private SurfaceView mFilterSurfaceView;
    private Camera mCamera;


    //for filter
    private int[] mRGBData = new int[PREVIEW_WIDTH * PREVIEW_HEIGHT];
    private Paint mPaint = new Paint();

    //for fps
    private TextView mFpsTextView;
    private long mSumEffectTime;
    private long mMinEffectTime = 0;
    private long mMaxEffectTime = 0;
    private long mFrames;
    private long mPrivFrames;
    private String mFpsString;
    private Timer mFpsTimer;

    private SurfaceHolder.Callback mPreviewSurfaceListener  = new SurfaceHolder.Callback(){

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            Log.i(TAG,"surfaceCreated!");

            if (Camera.getNumberOfCameras() > 1) {
                mCamera = Camera.open(1);
            } else {
                mCamera = Camera.open(0);
            }
            if (mCamera != null) {
                mCamera.setPreviewCallbackWithBuffer(mPreviewCallback);
                try {
                    mCamera.setPreviewDisplay(holder);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
            Log.i(TAG,"surfaceChanged!!");

            if (mCamera != null) {
                mCamera.stopPreview();
                Camera.Parameters params = mCamera.getParameters();

                params.setPreviewSize(PREVIEW_WIDTH, PREVIEW_HEIGHT);
                mCamera.setParameters(params);
                mCamera.startPreview();

                mCamera.addCallbackBuffer(new byte[PREVIEW_WIDTH * PREVIEW_HEIGHT * 3 ]);
            }


            // start timer
            mFrames = 0;
            mPrivFrames = 0;
            mSumEffectTime = 0;
            mFpsTimer = new Timer();
            mFpsTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    if ((mPrivFrames > 0) && (mSumEffectTime > 0)) {
                        long frames = mFrames - mPrivFrames;
                        mFpsString = String.format(FORMAT_FPS, frames,
                                ((double) mSumEffectTime) / (frames * 1000000.0),
                                ((double) mMinEffectTime) / (1000000.0), ((double) mMaxEffectTime) / (1000000.0));
                        mSumEffectTime = 0;
                        mMinEffectTime = 0;
                        mMaxEffectTime = 0;
                        runOnUiThread(new Runnable() {
                            public void run() {
                                mFpsTextView.setText(mFpsString);
                            }
                        });
                    }
                    mPrivFrames = mFrames;
                }
            }, 0, 1000); // 1000ms periodic
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

            Log.i(TAG,"surfaceDestroyed!!");

            // stop timer
            if (mFpsTimer != null) {
                mFpsTimer.cancel();
                mFpsTimer = null;
            }

            // deinit preview
            if (mCamera != null) {
                mCamera.stopPreview();
                mCamera.setPreviewCallbackWithBuffer(null);
                mCamera.release();
                mCamera = null;
            }

        }
    };


    /**
     * Camera Preview Callback 얻어내기
     */
    private Camera.PreviewCallback mPreviewCallback = new Camera.PreviewCallback() {

        public void onPreviewFrame(byte[] data, Camera camera) {
            Log.i(TAG,"onPreviewFrame");
            //아무것도 생각하지 않고 버퍼를 add하고 있지만 후변환 처리하고 있기때문에 사실은 좋지 않습니다
            //이 영상은 3개 이상의 면의 버퍼를 가지고 다른 Thread로 이미지 처리 작업 대기열 관리가 필요합니다
            if (camera != null) {
                camera.addCallbackBuffer(data);
            }

            // YUV420SP→ARGB변환。
            // 끈질기게 쓰고있지만 "YUV"와 "YUV420SP"가 상당히 나타내는 범위가 다르므로 정확히 표기해야 합니다
            long before = System.nanoTime();
            // call JNI method.
            NativeFilter.decodeYUV420SP(mRGBData, data, PREVIEW_WIDTH, PREVIEW_HEIGHT);
            long after = System.nanoTime();
            updateEffectTimes(after - before);

            // int[]의 ARGB라인으로 변환할 수 있으면 Canvas drawBitmap으로 그립니다.
            if (mFilterSurfaceView != null) {
                SurfaceHolder holder = mFilterSurfaceView.getHolder();
                Canvas canvas = holder.lockCanvas();
                // canvas.save();
                // canvas.scale(mSurfaceWidth / PREVIEW_WIDTH, mSurfaceHeight / PREVIEW_HEIGHT);
                canvas.drawBitmap(mRGBData, 0, PREVIEW_WIDTH, 0, 0, PREVIEW_WIDTH, PREVIEW_HEIGHT, false, mPaint);
                // canvas.restore();
                holder.unlockCanvasAndPost(canvas);
                mFrames++;
            }
        }
    };


    /**
     * 처리시간 업데이트 메서드들
     *
     * @param elapsed 경과시간
     */
    private void updateEffectTimes(long elapsed) {
        Log.i(TAG,"updateEffectTimes");
        if (elapsed <= 0) {
            return;
        }
        if ((mMinEffectTime == 0) || (elapsed < mMinEffectTime)) {
            mMinEffectTime = elapsed;
        }
        if ((mMaxEffectTime == 0) || (mMaxEffectTime < elapsed)) {
            mMaxEffectTime = elapsed;
        }
        mSumEffectTime += elapsed;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG,"onCreate()");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        init();
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG,"onDestroy()");
        super.onDestroy();

        deinit();
    }

    @SuppressWarnings("deprecation")
    private void init() {
        Log.i(TAG,"init()");
        mPreviewSurfaceView = (SurfaceView) findViewById(R.id.preview_surface);
        SurfaceHolder holder = mPreviewSurfaceView.getHolder();
        holder.addCallback(mPreviewSurfaceListener);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }

        mFilterSurfaceView = (SurfaceView) findViewById(R.id.filter_surface);
        mFilterSurfaceView.setZOrderOnTop(true);
        mFpsTextView = (TextView) findViewById(R.id.fps_text);
    }

    private void deinit() {
        Log.i(TAG,"deinit()");
        SurfaceHolder holder = mPreviewSurfaceView.getHolder();
        holder.removeCallback(mPreviewSurfaceListener);

        mPreviewSurfaceView = null;
        mFilterSurfaceView = null;
        mFpsTextView = null;
    }

}
