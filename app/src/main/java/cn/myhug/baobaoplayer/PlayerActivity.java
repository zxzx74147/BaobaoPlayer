package cn.myhug.baobaoplayer;

import android.databinding.DataBindingUtil;
import android.net.Uri;
import android.os.Handler;
import android.os.Bundle;

import cn.myhug.baobaoplayer.databinding.ActivityPlayerBinding;
import cn.myhug.baobaoplayer.gles.Texture2dProgram;
import cn.myhug.baobaoplayer.utils.MagicParams;

public class PlayerActivity extends BaseActivity {

    private Handler mHandler = new Handler();

    private boolean isForcePause = false;
    private boolean isForcePause2 = false;
    ActivityPlayerBinding mBinding;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MagicParams.context = this;
        mBinding = DataBindingUtil.setContentView(this,R.layout.activity_player);
        final Uri uri = mIntentData.uri;
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mBinding.videoView.mFilterType = Texture2dProgram.ProgramType.TEXTURE_EXT_FILTER;
                mBinding.videoView.setVideoURI(uri);
                mBinding.videoView.start();

//                mBinding.videoView2.setVideoURI(uri);
//                mBinding.videoView2.start();

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


    }

    @Override
    public void onResume() {
        super.onResume();
        if (isForcePause && !mBinding.videoView.isPlaying()) {
            isForcePause = false;
            mBinding.videoView.resume();
        }


    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mBinding.videoView.stopPlayback();
    }
}
