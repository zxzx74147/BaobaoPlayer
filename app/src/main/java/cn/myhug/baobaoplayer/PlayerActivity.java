package cn.myhug.baobaoplayer;

import android.databinding.DataBindingUtil;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;

import cn.myhug.baobaoplayer.databinding.ActivityPlayerBinding;
import cn.myhug.baobaoplayer.utils.MagicParams;
import fm.jiecao.jcvideoplayer_lib.JCVideoPlayer;
import fm.jiecao.jcvideoplayer_lib.JCVideoPlayerStandard;

public class PlayerActivity extends BaseActivity {

    private Handler mHandler = new Handler();

    private boolean isForcePause = false;
    private boolean isForcePause2 = false;
    private ActivityPlayerBinding mBinding;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MagicParams.context = this;
        mBinding = DataBindingUtil.setContentView(this,R.layout.activity_player);
        final Uri uri = mIntentData.uri;
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mBinding.player.setUp("http://2449.vod.myqcloud.com/2449_22ca37a6ea9011e5acaaf51d105342e3.f20.mp4", JCVideoPlayerStandard.SCREEN_LAYOUT_LIST, "123");
//                mBinding.videoView.setVideoURI(Uri.parse("http://pws.myhug.cn/video/w/9/54308d9ee7170e98e3243a9514037500"));
//                mBinding.videoView.start();

//                mBinding.videoView2.setVideoURI(uri);
//                mBinding.videoView2.start();

            }
        }, 300);

    }
    @Override
    public void onPause() {
        super.onPause();
//        mBinding.videoView.onPause();
        JCVideoPlayer.releaseAllVideos();


    }

    @Override
    public void onResume() {
        super.onResume();
//


    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
