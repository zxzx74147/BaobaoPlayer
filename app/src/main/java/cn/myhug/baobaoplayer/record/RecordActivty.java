package cn.myhug.baobaoplayer.record;

import android.app.Activity;
import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;

import cn.myhug.baobaoplayer.BaseActivity;
import cn.myhug.baobaoplayer.R;
import cn.myhug.baobaoplayer.data.IntentData;
import cn.myhug.baobaoplayer.databinding.ActivityRecordActivtyBinding;
import cn.myhug.baobaoplayer.edit.VideoEditActivity;
import cn.myhug.baobaoplayer.filter.MagicCameraDisplay;
import cn.myhug.baobaoplayer.media.AudioRecordSource;
import cn.myhug.baobaoplayer.media.MediaEncoder;
import cn.myhug.baobaoplayer.util.FileSelectUtil;
import cn.myhug.baobaoplayer.util.FileUtil;
import cn.myhug.baobaoplayer.util.ZXActivityJumpHelper;

public class RecordActivty extends BaseActivity {

    private static final int STATE_PREPAREING = 0;
    private static final int STATE_RECORDING = 1;
    private static final int STATE_PAUSE = 2;
    private static final int STATE_STOP = 3;

    private static volatile int mState = STATE_PREPAREING;

    private ActivityRecordActivtyBinding mBinding = null;
    private MagicCameraDisplay mMagicCameraDisplay;
    private MediaEncoder mMediaEncoder = null;
    private AudioRecordSource mRecordSource = null;
    private Thread mRecordThread = null;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_record_activty);
        mBinding.record.setOnTouchListener(mRecordTouchListener);
        init();
//        initMediaRecorder();

//        mRecordThread = new Thread(mRecordRunnable);
//        mRecordThread.start();
//        mRecordThread.setName("record media thread");
    }

    private void init() {
        GLSurfaceView glSurfaceView = mBinding.cameraView;
        mMagicCameraDisplay = new MagicCameraDisplay(this, glSurfaceView);

    }

    private boolean initMediaRecorder() {
        mMediaEncoder = new MediaEncoder();
        mMediaEncoder.setOutputFile(FileUtil.getFile("record.mp4"));
        mMediaEncoder.prepare();
        Surface surface = mMediaEncoder.getEncoderSurface();
        mMagicCameraDisplay.setEncodeSurface(surface);

        mRecordSource = new AudioRecordSource();
        mRecordSource.prepare();
        mRecordSource.startRecord();
        return true;
    }

    @Override
    public void onPause() {
        super.onPause();
        mMagicCameraDisplay.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        mMagicCameraDisplay.onResume();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        switchState(STATE_STOP);
    }

    public void onSelectFile(View v) {
        FileSelectUtil.selectFile(this, "video/mp4", new FileSelectUtil.IFileSelector() {
            @Override
            public void onFileSelect(int resultCode, Intent data) {
                if (resultCode == Activity.RESULT_OK) {
                    IntentData intentData = new IntentData();
                    intentData.uri = data.getData();
                    ZXActivityJumpHelper.startActivity(RecordActivty.this, VideoEditActivity.class, intentData);
                    return;
                }
            }
        });
    }

    public void onSwapCamera(View v) {

    }

    private View.OnTouchListener mRecordTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (mState == STATE_STOP) {
                return false;
            }

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    switchState(STATE_RECORDING);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_OUTSIDE:
                case MotionEvent.ACTION_CANCEL:
                    switchState(STATE_PAUSE);
                    break;
            }
            return false;
        }
    };

    public void switchState(int state){
        if(mState == state){
            return;
        }
        if(mState == STATE_RECORDING) {
            mRecordSource.resumeRecord();
            mMagicCameraDisplay.resumeRecord();
        }else if(mState == STATE_PAUSE){
            mRecordSource.pauseRecord();
            mMagicCameraDisplay.pauseRecord();
        }else if(mState == STATE_STOP){
            mRecordSource.stop();
            mMagicCameraDisplay.stop();
        }
    }

//    private Runnable mRecordRunnable = new Runnable() {
//        @Override
//        public void run() {
//            try {
//                initMediaRecorder();
//            } catch (Exception e) {
//                e.printStackTrace();
//            } finally {
//                mRecordSource.stop();
//
//            }
//        }
//    };

}
