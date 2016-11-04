package cn.myhug.baobaoplayer;

import android.databinding.DataBindingUtil;
import android.media.session.MediaController;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.RequiresApi;

import cn.myhug.baobaoplayer.databinding.ActivityPlayerVideoBinding;


public class PlayerVideoActivity extends BaseActivity {

    private ActivityPlayerVideoBinding mBinding;
    private Handler mHandler = new Handler();
    private boolean isForcePause = false;
    private boolean isForcePause2 = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_player_video);
        final Uri uri = mIntentData.uri;
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mBinding.videoView.setVideoURI(uri);
                mBinding.videoView.start();
            }
        }, 300);

    }

    @Override
    public void onPause() {
        super.onPause();
        if (mBinding.videoView.canPause()) {
            isForcePause = true;
            mBinding.videoView.pause();
        }
        if (mBinding.videoView2.canPause()) {
            isForcePause2 = true;
            mBinding.videoView2.pause();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isForcePause && !mBinding.videoView.isPlaying()) {
            isForcePause = false;
            mBinding.videoView.resume();
        }

        if (isForcePause2 && !mBinding.videoView2.isPlaying()) {
            isForcePause2 = false;
            mBinding.videoView2.resume();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mBinding.videoView.stopPlayback();
        mBinding.videoView2.stopPlayback();
    }

}
