package cn.myhug.baobaoplayer.record;

import android.app.Activity;
import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import cn.myhug.baobaoplayer.BaseActivity;
import cn.myhug.baobaoplayer.R;
import cn.myhug.baobaoplayer.data.IntentData;
import cn.myhug.baobaoplayer.data.RecordData;
import cn.myhug.baobaoplayer.databinding.ActivityRecordActivtyBinding;
import cn.myhug.baobaoplayer.edit.VideoEditActivity;
import cn.myhug.baobaoplayer.util.FileSelectUtil;
import cn.myhug.baobaoplayer.util.ZXActivityJumpHelper;

public class RecordActivty extends BaseActivity {

    public static final int STATE_PREPAREING = 0;
    public static final int STATE_RECORDING = 1;
    public static final int STATE_PAUSE = 2;
    public static final int STATE_STOP = 3;
    private boolean isDoneRequest = false;


    private   int mState = STATE_PREPAREING;

    private ActivityRecordActivtyBinding mBinding = null;
    private RecordData mRecordData = new RecordData();


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_record_activty);
//        mBinding.record.setOnTouchListener(mRecordTouchListener);
        init();
        mRecordData.state = STATE_PREPAREING;
        mBinding.setHandlers(this);
        EventBus.getDefault().register(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(Object event) {
        if(!isDoneRequest){
            return;
        }

        IntentData intentData = new IntentData();
        intentData.uri = (Uri) event;
        ZXActivityJumpHelper.startActivity(RecordActivty.this, VideoEditActivity.class, intentData);
        Intent intent = new Intent();
        intent.setData((Uri) event);
        setResult(Activity.RESULT_OK,intent);
        finish();
    }

    private void init() {
        mBinding.recordView.postDelayed(new Runnable() {
            @Override
            public void run() {
                mBinding.recordView.startPreview();
                mState = STATE_PAUSE;
            }
        },300);
    }

    @Override
    public void onPause() {
        super.onPause();

    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
        switchState(STATE_STOP);
        mBinding.recordView.stop();
        mBinding.setData(mRecordData);
    }

    public void onSelectFile(View v) {
        if(mState == STATE_RECORDING){
            return;
        }
        FileSelectUtil.selectFile(this, "video/mp4", new FileSelectUtil.IFileSelector() {
            @Override
            public void onFileSelect(int resultCode, Intent data) {
                if (resultCode == Activity.RESULT_OK) {
                    IntentData intentData = new IntentData();
                    intentData.uri = data.getData();
                    ZXActivityJumpHelper.startActivity(RecordActivty.this, VideoEditActivity.class, intentData);
                    Intent intent = new Intent();
                    intent.setData(data.getData());
                    setResult(Activity.RESULT_OK,intent);
                    finish();
                    return;
                }
            }
        });
    }

    public void onDelete(View v) {
        if(mState == STATE_RECORDING){
            return;
        }
        mRecordData.state = STATE_PAUSE;
        mRecordData.duration = 0;
        mRecordData.ready = false;
        mBinding.recordView.resetRecord();

    }

    public void onDone(View v) {
        if(mState == STATE_RECORDING){
            return;
        }
        isDoneRequest = true;
        switchState(STATE_STOP);

    }

    public void onFlash(View v) {
        if(mRecordData.flashMode == RecordData.FLASH_DISABLE_DISABLE){
            return;
        }

        if(mRecordData.flashMode == RecordData.FLASH_OFF){
            mRecordData.flashMode = RecordData.FLASH_ON;
        }else if(mRecordData.flashMode == RecordData.FLASH_ON){
            mRecordData.flashMode = RecordData.FLASH_OFF;
        }
        mBinding.recordView.setFlashMode(mRecordData.flashMode);
        mBinding.setData(mRecordData);

    }

    public void onSwapCamera(View v) {
        mBinding.recordView.switchCamera();
    }

    public void onRecord(View v){
        if (mState == STATE_STOP) {
            return ;
        }
        switch (mState) {
            case STATE_RECORDING:
                mBinding.record.setImageResource(R.drawable.but_xiaosp_record_n);
                switchState(STATE_PAUSE);
                break;
            case STATE_PAUSE:
                mBinding.record.setImageResource(R.drawable.but_xiaosp_record_s);
                switchState(STATE_RECORDING);
                break;
        }

    }

//    private View.OnTouchListener mRecordTouchListener = new View.OnTouchListener() {
//        @Override
//        public boolean onTouch(View v, MotionEvent event) {
//
//            switch (event.getAction()) {
//                case MotionEvent.ACTION_DOWN:
//                    switchState(STATE_RECORDING);
//                    mBinding.record.setImageResource(R.drawable.but_xiaosp_record_s);
//                    break;
//                case MotionEvent.ACTION_UP:
//                case MotionEvent.ACTION_OUTSIDE:
//                case MotionEvent.ACTION_CANCEL:
//                    switchState(STATE_PAUSE);
//                    mBinding.record.setImageResource(R.drawable.but_xiaosp_record_n);
//                    break;
//            }
//            return true;
//        }
//    };

    public void switchState(int state){
        if(mState == state){
            return;
        }
        mState = state;

        if(mState == STATE_RECORDING) {
            mBinding.recordView.post(mCheckStateRunnable);
            mBinding.recordView.resumeRecord();
        }else if(mState == STATE_PAUSE){
            mBinding.recordView.pauseRecord();
        }else if(mState == STATE_STOP){
            mBinding.recordView.stopRecord();
        }
    }

    private Runnable mCheckStateRunnable = new Runnable() {
        @Override
        public void run() {
            mRecordData.duration  = TimeStampGenerator.sharedInstance().getDuration();
            if(mBinding.progressBar.getProgress()>333){
                mRecordData.ready = true;
                mBinding.done.setEnabled(true);
            }else{
                mBinding.done.setEnabled(false);
            }
            mBinding.setData(mRecordData);
            if(mState == STATE_RECORDING) {
                mBinding.recordView.postDelayed(this,30);
            }
            if(mBinding.progressBar.getProgress()>=1000){
                switchState(STATE_STOP);
            }

        }
    };


}
