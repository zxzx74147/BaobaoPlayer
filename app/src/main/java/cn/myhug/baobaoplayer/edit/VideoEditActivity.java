package cn.myhug.baobaoplayer.edit;

import android.content.res.AssetFileDescriptor;
import android.databinding.DataBindingUtil;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SimpleItemAnimator;
import android.view.View;
import android.widget.SeekBar;

import com.afollestad.materialdialogs.MaterialDialog;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import cn.myhug.baobaoplayer.BaseActivity;
import cn.myhug.baobaoplayer.R;
import cn.myhug.baobaoplayer.audio.AudioManager;
import cn.myhug.baobaoplayer.common.PlayerRecyclerViewTable;
import cn.myhug.baobaoplayer.data.BaseItemData;
import cn.myhug.baobaoplayer.databinding.ActivityVideoEditBinding;
import cn.myhug.baobaoplayer.databinding.LayoutFilterThumbBinding;
import cn.myhug.baobaoplayer.filter.FilterConfig;
import cn.myhug.baobaoplayer.filter.base.gpuimage.GPUImageFilter;
import cn.myhug.baobaoplayer.filter.data.FilterData;
import cn.myhug.baobaoplayer.filter.helper.MagicFilterFactory;
import cn.myhug.baobaoplayer.filter.helper.MagicFilterType;
import cn.myhug.baobaoplayer.filter.widget.FilterTypeHelper;
import cn.myhug.baobaoplayer.media.MediaMixer;
import cn.myhug.baobaoplayer.util.FileUtil;
import cn.myhug.baobaoplayer.widget.recyclerview.CommonDataConverter;
import cn.myhug.baobaoplayer.widget.recyclerview.CommonRecyclerViewAdapter;

public class VideoEditActivity extends BaseActivity {

    private Handler mHandler = new Handler();
    private ActivityVideoEditBinding mBinding = null;
    private MediaPlayer mBgmPlayer = null;
    private MaterialDialog mProgressDialog = null;
    private  Uri mSource = null;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_video_edit);
        mBinding.videoSeekBar.setPadding(0, 0, 0, 0);
        mBinding.setHandler(this);
        mSource = mIntentData.uri;

        initFilter();
        initSeekBar();

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                addBgm();
            }
        }, 300);

        mBinding.videoView.postDelayed(new Runnable() {
            @Override
            public void run() {
                mBinding.videoView.setVideoURI(mSource);
                mBinding.videoView.start();
                mBinding.videoView.seekTo(stopPosition);
            }
        },300);

    }


    private FilterData mLastFilter = null;

    private void initFilter() {
        mBinding.filterRecyclerview.setLayoutManager(new LinearLayoutManager(VideoEditActivity.this, LinearLayoutManager.HORIZONTAL, false));
        RecyclerView.ItemAnimator animator = mBinding.filterRecyclerview.getItemAnimator();
        if (animator instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) animator).setSupportsChangeAnimations(false);
        }
        List<FilterData> mFilterData = new LinkedList<>();
        for (MagicFilterType type : FilterConfig.FILTER_TYPE) {
            FilterData filter = new FilterData();
            filter.mType = type;
            filter.mResourceId = FilterTypeHelper.FilterType2Thumb(type);
            filter.mFilterName = getResources().getString(FilterTypeHelper.FilterType2Name(type));
            mFilterData.add(filter);

        }
        mLastFilter = mFilterData.get(0);
        mLastFilter.isSelected = true;
        List<BaseItemData> mList = new LinkedList<>();
        List<BaseItemData<FilterData>> filterData = CommonDataConverter.convertData(R.layout.layout_filter_thumb, mFilterData);

        PlayerRecyclerViewTable table = new PlayerRecyclerViewTable();
        final CommonRecyclerViewAdapter mAdapter = new CommonRecyclerViewAdapter(table, mList);
        mAdapter.addData(filterData);
        mBinding.filterRecyclerview.setAdapter(mAdapter);
        table.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LayoutFilterThumbBinding binding = (LayoutFilterThumbBinding) v.getTag(R.id.tag_holder);
                FilterData data = (FilterData) v.getTag(R.id.tag_data);
                GPUImageFilter filter = MagicFilterFactory.initFilters(data.mType);
                data.isSelected = true;
                mLastFilter.isSelected = false;
                mLastFilter = data;
                binding.setItem(data);
                mAdapter.notifyDataSetChanged();
                mBinding.videoView.setFilter(filter);
//                mBinding.videoView.seekTo(0);
            }
        });
    }

    public void onDone(View view) {
        startMix();
    }

    int stopPosition = 0;

    @Override
    public void onPause() {
        super.onPause();
        if (mBinding.videoView.canPause()) {
            stopPosition = mBinding.videoView.getCurrentPosition();
            mBinding.videoView.pause();

        }
        if (mBgmPlayer != null && mBgmPlayer.isPlaying()) {
            mBgmPlayer.pause();
        }
    }

    public void onResume() {
        super.onResume();
        if(!mBinding.videoView.isPlaying()) {
            mBinding.videoView.seekTo(stopPosition);
            mBinding.videoView.start();
        }

        if (mBgmPlayer != null && !mBgmPlayer.isPlaying()) {
            mBgmPlayer.start();
        }
    }


    private void initSeekBar() {
        runOnUiThread(mSeekBarRunnable);
        mBinding.videoSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                mBinding.videoView.removeCallbacks(mSeekBarRunnable);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int progress = seekBar.getProgress();
                int duration = mBinding.videoView.getDuration();
                int seekTo = progress * duration / 1000;
                mBinding.videoView.seekTo(seekTo);
                if (mBgmPlayer != null) {
                    duration = mBgmPlayer.getDuration();
                    seekTo = progress * duration / 1000;
                    seekTo %= duration;
                    mBgmPlayer.seekTo(seekTo);
                }
                runOnUiThread(mSeekBarRunnable);
            }
        });
    }

    private Runnable mSeekBarRunnable = new Runnable() {
        @Override
        public void run() {
            if (mBinding.videoView.getDuration() > 0) {
                int pos = mBinding.videoView.getCurrentPosition() * 1000 / mBinding.videoView.getDuration();
                mBinding.videoSeekBar.setProgress(pos);

            }
            mBinding.videoView.postDelayed(this, 100);
        }
    };

    private void addBgm() {
        AssetFileDescriptor descriptor = null;
        try {
            descriptor = getAssets().openFd("music/Twinkle.mp3");
            mBgmPlayer = AudioManager.sharedInstance().playMusic(descriptor);
            mBgmPlayer.setLooping(true);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                descriptor.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    private MediaMixer mMuxter = null;

    private void startMix() {
        final Uri uri = mIntentData.uri;
        File file = FileUtil.getFile("output.mp4");
        if (file.exists()) {
            file.delete();
        }
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        mProgressDialog = new MaterialDialog.Builder(this)
                .title("处理中")
                .content("请稍等")
                .progress(false, 100, true)
                .canceledOnTouchOutside(false)
//                .cancelable(false)
                .show();

        mMuxter = new MediaMixer();
        mMuxter.setInputUri(uri);
        mMuxter.setOutputFile(file);
        mMuxter.setMixAudio(Uri.fromFile(FileUtil.getFile("Twinkle.mp3")));
        mMuxter.setFilter(MagicFilterFactory.initFilters(mLastFilter.mType));
        mMuxter.setListener(mEncodeListener);

        try {
            mMuxter.startMix();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }

    }

    private MediaMixer.IBBMediaMuxterPrgressListener mEncodeListener = new MediaMixer.IBBMediaMuxterPrgressListener() {
        @Override
        public void onProgress(int progress) {
            if (mProgressDialog != null) {
                mProgressDialog.setProgress(progress);
            }
        }

        @Override
        public void onDone() {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }

        @Override
        public void onError(Exception e) {

        }
    };


    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mBgmPlayer != null) {
            mBgmPlayer.stop();
            mBgmPlayer.release();
            mBgmPlayer = null;
        }
        mBinding.videoView.stopPlayback();

    }


}
