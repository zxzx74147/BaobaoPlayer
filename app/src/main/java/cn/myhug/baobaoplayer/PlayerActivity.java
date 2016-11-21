package cn.myhug.baobaoplayer;

import android.databinding.DataBindingUtil;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;

import cn.myhug.baobaoplayer.databinding.ActivityPlayerBinding;
import cn.myhug.baobaoplayer.media.AudioDecoder;
import cn.myhug.baobaoplayer.util.FileUtil;
import cn.myhug.baobaoplayer.utils.MagicParams;

public class PlayerActivity extends BaseActivity {
    private static final String TAG = "PlayerActivity";
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
        AudioDecoder mAudioDecoer = new AudioDecoder();
        mAudioDecoer.setUri(Uri.fromFile(FileUtil.getFile("Twinkle.mp3")));
        try {
            mAudioDecoer.prepare();
            int len = mAudioDecoer.pumpAudioBuffer(4096);
            Log.e(TAG,"len="+len);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
    @Override
    public void onPause() {
        super.onPause();

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
