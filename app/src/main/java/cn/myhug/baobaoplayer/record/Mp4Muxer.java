package cn.myhug.baobaoplayer.record;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.util.Log;


import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import cn.myhug.baobaoplayer.util.FileUtil;

/**
 * Created by zhengxin on 2016/11/9.
 */

public class Mp4Muxer {
    private static final String TAG = "Mp4Muxer";

    private final boolean VERBOSE = true;
    private MediaMuxer mMuxer = null;
    public int mVideoTrackIndex = -1;
    private int mAudioTrackIndex = -1;

    private boolean mVideoFinished = false;
    private boolean mAudioFinished = false;
    private boolean mIsStarted = false;
    private Object mStartLock = new Object();
    File mDstFile = null;

    public Mp4Muxer() {
        init();

    }

    private void init() {
        try {
            mDstFile = FileUtil.getFile("record.mp4");
            if (mDstFile.exists()) {
                mDstFile.delete();
            }
            mDstFile.createNewFile();
            mMuxer = new MediaMuxer(mDstFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

        } catch (IOException e) {
            e.printStackTrace();
        }

        mVideoTrackIndex = -1;
        mAudioTrackIndex = -1;
        mVideoFinished = false;
        mAudioFinished = false;
        mIsStarted = false;
    }

    public void addVideoTrack(MediaFormat format) {
        Log.d(TAG, "video start");
        mVideoTrackIndex = mMuxer.addTrack(format);
        checkStart();
    }

    public void checkStart() {
        if (mVideoTrackIndex >= 0 && mAudioTrackIndex >= 0) {
            mMuxer.start();
            mIsStarted = true;
            synchronized (mStartLock) {
                mStartLock.notifyAll();
            }
        }

    }

    public void addAudioTrack(MediaFormat format) {
        Log.d(TAG, "audio start");
        mAudioTrackIndex = mMuxer.addTrack(format);
        checkStart();
    }

    public void writeVideo(ByteBuffer buffer, MediaCodec.BufferInfo info) {

        if (mVideoFinished) {
            return;
        }
        if (!mIsStarted) {
            try {
                synchronized (mStartLock) {
                    mStartLock.wait();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (VERBOSE) Log.d(TAG, "writeVideo = " + info.size + "|" + info.presentationTimeUs);


        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            mVideoFinished = true;
            if (info.presentationTimeUs == 0) {
                info.presentationTimeUs = TimeStampGenerator.sharedInstance().getAudioStamp();
            }
            Log.d(TAG, "video finish");
        }
        mMuxer.writeSampleData(mVideoTrackIndex, buffer, info);
        if (mVideoFinished && mAudioFinished) {
            finish();
        }
    }

    public boolean isStarted() {
        return mIsStarted;
    }

    public void writeAudio(ByteBuffer buffer, MediaCodec.BufferInfo info) {
        if (mAudioFinished) {
            return;
        }
        if (!mIsStarted) {
            try {
                synchronized (mStartLock) {
                    mStartLock.wait();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (VERBOSE) Log.d(TAG, "writeAudio = " + info.size + "|" + info.presentationTimeUs);

        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            mAudioFinished = true;
            if (info.presentationTimeUs == 0) {
                info.presentationTimeUs = TimeStampGenerator.sharedInstance().getAudioStamp();
            }
            Log.d(TAG, "audio finish");
        }
        mMuxer.writeSampleData(mAudioTrackIndex, buffer, info);
        if (mVideoFinished && mAudioFinished) {
            finish();
        }
    }

    public void finish() {
        Log.d(TAG, "finish");
        if (mIsStarted) {
            mIsStarted = false;
            mMuxer.stop();
            mMuxer.release();
        }
        Uri uri = Uri.fromFile(mDstFile);
        EventBus.getDefault().post(uri);

    }

    public void reset() {
        try {
            mMuxer.stop();
        } catch (Exception e) {

        }
        try {
            mMuxer.release();
        } catch (Exception e) {

        }
        init();
    }
}
