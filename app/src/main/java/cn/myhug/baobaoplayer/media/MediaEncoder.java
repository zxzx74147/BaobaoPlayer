package cn.myhug.baobaoplayer.media;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import cn.myhug.baobaoplayer.util.TimeStampLogUtil;

import static cn.myhug.baobaoplayer.media.Mp4Config.MIME_TYPE_AUDIO;

/**
 * Created by guoheng on 2016/9/1.
 */
public class MediaEncoder {

    private static final String TAG = "MediaEncoder";
    private static final boolean VERBOSE = false;           // lots of logging
    private static final String MIME_TYPE = "video/avc";    // H.264 Advanced Video Coding
    private static final int WIDTH = Mp4Config.VIDEO_WIDTH;
    private static final int HEIGHT = Mp4Config.VIDEO_HEIGHT;
    private static final int BIT_RATE = Mp4Config.VIDEO_BITRATE;            // 2Mbps
    public static final int FRAME_RATE = Mp4Config.VIDEO_FRAME_RATE;               // 30fps
    private static final int IFRAME_INTERVAL = Mp4Config.VIDEO_I_FRAME_INTERVAL;          // 10 seconds between I-frames
    public static final int TIMEOUT_USEC = 100000;

    MediaCodec mVideoEncoder = null;
    MediaCodec mAudioEncoder = null;
    Surface mEncodesurface;

    private MediaCodec.BufferInfo mAudioBufferInfo;
    private MediaCodec.BufferInfo mVideoBufferInfo;
    public MediaMuxer mMuxer;

    public  int mVideoTrackIndex = -1;
    public  int mAudioTrackIndex = -1;
    public volatile boolean mVideoEnded = false;
    public volatile boolean mAudioEnded = false;
    public volatile boolean mMuxerStarted;
    private File mOutputFile = null;

    private Thread mAudioThread = null;
    private Thread mVideoTrread = null;

    private Handler mHandler = null;

    private Object mStartLock = new Object();

    public void setHandler(Handler handler){
        mHandler = handler;
    }

    public void prepare() {

//        String outputPath = FileUtil.getFile("output.mp4").toString();

        mAudioBufferInfo = new MediaCodec.BufferInfo();
        mVideoBufferInfo = new MediaCodec.BufferInfo();

        {
            MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, WIDTH, HEIGHT);

            // Set some properties.  Failing to specify some of these can cause the MediaCodec
            // configure() call to throw an unhelpful exception.
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);

            mVideoEncoder = null;

            try {
                mMuxer = new MediaMuxer(mOutputFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                mVideoEncoder = MediaCodec.createEncoderByType(MIME_TYPE);

                mVideoEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                mEncodesurface = mVideoEncoder.createInputSurface();
                mVideoEncoder.start();


            } catch (IOException ioe) {
                throw new RuntimeException("failed init mVideoEncoder", ioe);
            }
        }

        {
            MediaCodecInfo audioCodecInfo = Mp4Config.selectCodec(Mp4Config.MIME_TYPE_AUDIO);
            MediaFormat outputAudioFormat =
                    MediaFormat.createAudioFormat(
                            MIME_TYPE_AUDIO, Mp4Config.OUTPUT_AUDIO_SAMPLE_RATE_HZ,
                            2);
            outputAudioFormat.setInteger(MediaFormat.KEY_BIT_RATE, Mp4Config.OUTPUT_AUDIO_BIT_RATE);
            outputAudioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, Mp4Config.OUTPUT_AUDIO_AAC_PROFILE);
            outputAudioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 65536);


            mAudioEncoder = null;

            try {
                mAudioEncoder = MediaCodec.createByCodecName(audioCodecInfo.getName());
                mAudioEncoder.configure(outputAudioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                mAudioEncoder.start();

            } catch (IOException ioe) {
                throw new RuntimeException("failed init mVideoEncoder", ioe);
            }
        }

        mMuxerStarted = false;

    }

    public void setOutputFile(File file) {
        mOutputFile = file;
    }


    public void start() {
        mAudioThread = new Thread(mAudioRunnable);
        mAudioThread.setName("audio encode thread");
        mAudioThread.start();

        mVideoTrread = new Thread(mVidoeRunnable);
        mVideoTrread.setName("video encode thread");
        mVideoTrread.start();
    }

    public Runnable mAudioRunnable = new Runnable() {
        @Override
        public void run() {
            while (true) {
                ByteBuffer[] encoderOutputBuffers = mAudioEncoder.getOutputBuffers();
                int encoderStatus = mAudioEncoder.dequeueOutputBuffer(mAudioBufferInfo, TIMEOUT_USEC);
                TimeStampLogUtil.logTimeStamp(1, "dequeueOutputBuffer====");
                if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (VERBOSE) Log.d(TAG, "no output available, spinning to await EOS");

                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    // not expected for an mAudioEncoder
                    encoderOutputBuffers = mAudioEncoder.getOutputBuffers();
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // should happen before receiving buffers, and should only happen once
                    if (mMuxerStarted) {
                        throw new RuntimeException("format changed twice");
                    }
                    MediaFormat newFormat = mAudioEncoder.getOutputFormat();
                    Log.d(TAG, "mAudioEncoder output format changed: " + newFormat);
                    synchronized (mMuxer) {
                        mAudioTrackIndex = mMuxer.addTrack(newFormat);
                        if (mVideoTrackIndex >= 0) {
                            mMuxer.start();
                            mMuxerStarted = true;
                            synchronized (mStartLock) {
                                mStartLock.notifyAll();
                            }
                        }
                    }
                } else if (encoderStatus < 0) {
                    Log.w(TAG, "unexpected result from mVideoEncoder.dequeueOutputBuffer: " +
                            encoderStatus);
                    // let's ignore it
                } else {
                    if (!mMuxerStarted) {
                        try {
                            synchronized (mStartLock) {
                                mStartLock.wait();
                            }
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                    if (encodedData == null) {
                        throw new RuntimeException("encoderOutputBuffer " + encoderStatus +
                                " was null");
                    }

                    if ((mAudioBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        // The codec config data was pulled out and fed to the muxer when we got
                        // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                        if (VERBOSE) Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
//                    MediaFormat outputAudioFormat =
//                            MediaFormat.createAudioFormat(
//                                    MIME_TYPE_AUDIO, Mp4Config.OUTPUT_AUDIO_SAMPLE_RATE_HZ,
//                                    2);
//                    outputAudioFormat.setByteBuffer("csd-0", encodedData);

                        mAudioBufferInfo.size = 0;
                    }

                    if (mAudioBufferInfo.size != 0) {
//                    if (!mMuxerStarted) {
//                        throw new RuntimeException("muxer hasn't started");
//                    }

                        // adjust the ByteBuffer values to match BufferInfo (not needed?)
                        encodedData.position(mAudioBufferInfo.offset);
                        encodedData.limit(mAudioBufferInfo.offset + mAudioBufferInfo.size);
                        synchronized (mMuxer) {
                            mMuxer.writeSampleData(mAudioTrackIndex, encodedData, mAudioBufferInfo);
                        }
                        TimeStampLogUtil.logTimeStamp(1, "writeSampleData====");
                        if (VERBOSE)
                            Log.d(TAG, "sent " + mAudioBufferInfo.size + "audio bytes to muxer" + mAudioBufferInfo.presentationTimeUs);
                    }

                    mAudioEncoder.releaseOutputBuffer(encoderStatus, false);

                    if ((mAudioBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {

                        if (VERBOSE) Log.d(TAG, "end of audio stream reached");
                        break;
                    }
                }

            }
            mAudioEnded = true;
            checkEnd();
        }
    };

    private void checkEnd() {
        if(mAudioEnded&&mVideoEnded){
            release();
            mHandler.sendEmptyMessage(MediaMixer.MSG_DONE);
        }
    }

    public Runnable mVidoeRunnable = new Runnable() {
        @Override
        public void run() {
            ByteBuffer[] encoderOutputBuffers = mVideoEncoder.getOutputBuffers();
            while (true) {
                int encoderStatus = mVideoEncoder.dequeueOutputBuffer(mVideoBufferInfo, TIMEOUT_USEC);
                if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // no output available yet

                    if (VERBOSE) Log.d(TAG, "no output available, spinning to await EOS");

                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    // not expected for an mVideoEncoder
                    encoderOutputBuffers = mVideoEncoder.getOutputBuffers();
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // should happen before receiving buffers, and should only happen once
                    if (mMuxerStarted) {
                        throw new RuntimeException("format changed twice");
                    }
                    MediaFormat newFormat = mVideoEncoder.getOutputFormat();
                    Log.d(TAG, "mVideoEncoder output format changed: " + newFormat);
                    synchronized (mMuxer) {
                        mVideoTrackIndex = mMuxer.addTrack(newFormat);
                        // now that we have the Magic Goodies, start the muxer
                        if (mAudioTrackIndex >= 0) {
                            mMuxer.start();
                            mMuxerStarted = true;
                            synchronized (mStartLock) {
                                mStartLock.notifyAll();
                            }
                        }
                    }
                } else if (encoderStatus < 0) {
                    Log.w(TAG, "unexpected result from mVideoEncoder.dequeueOutputBuffer: " +
                            encoderStatus);
                    // let's ignore it
                } else {
                    if (!mMuxerStarted) {

                        try {
                            synchronized (mStartLock) {
                                mStartLock.wait();
                            }
                        } catch (InterruptedException e) {
                            e.printStackTrace();

                        }
                    }
                    ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                    if (encodedData == null) {
                        throw new RuntimeException("encoderOutputBuffer " + encoderStatus +
                                " was null");
                    }

                    if ((mVideoBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        if (VERBOSE) Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                        mVideoBufferInfo.size = 0;
                    }

                    if (mVideoBufferInfo.size != 0) {
                        encodedData.position(mVideoBufferInfo.offset);
                        encodedData.limit(mVideoBufferInfo.offset + mVideoBufferInfo.size);
                        synchronized (mMuxer) {
                            mMuxer.writeSampleData(mVideoTrackIndex, encodedData, mVideoBufferInfo);
                        }
                        if (VERBOSE)
                            Log.d(TAG, "sent " + mVideoBufferInfo.size + "video bytes to muxer");
                    }

                    mVideoEncoder.releaseOutputBuffer(encoderStatus, false);

                    if ((mVideoBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {

                        if (VERBOSE) Log.d(TAG, "end of video stream reached");
                        break;      // out of while
                    }
                }
            }
            mVideoEnded = true;
            checkEnd();
        }
    };


    public void drainAudioEncoder(boolean endOfStream, ByteBuffer inputBuffer, MediaCodec.BufferInfo info) {

        if (VERBOSE) Log.d(TAG, "drainAudioEncoder(" + endOfStream + ")");

        TimeStampLogUtil.logTimeStamp(1, "drainAudioEncoder start====");
        int inputIndex = -1;

        inputIndex = mAudioEncoder.dequeueInputBuffer(TIMEOUT_USEC);
        TimeStampLogUtil.logTimeStamp(1, "dequeueInputBuffer====");
        if (inputIndex >= 0) {
            if (endOfStream) {
                if (VERBOSE) Log.d(TAG, "sending EOS to drainAudioEncoder");
                mAudioEncoder.queueInputBuffer(inputIndex, 0, 0, info.presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            } else {
                ByteBuffer buffer = mAudioEncoder.getInputBuffers()[inputIndex];
                buffer.position(0);
                buffer.put(inputBuffer);
                buffer.flip();
                mAudioEncoder.queueInputBuffer(inputIndex, 0, info.size, info.presentationTimeUs, 0);
            }
        } else {
            if (VERBOSE) Log.d(TAG, "audio encode input buffer not available");
        }
    }


    public void drainVideoEncoder(boolean endOfStream) {

        if (VERBOSE) Log.d(TAG, "drainVideoEncoder(" + endOfStream + ")");

        if (endOfStream) {
            if (VERBOSE) Log.d(TAG, "sending EOS to mVideoEncoder");
            mVideoEncoder.signalEndOfInputStream();
        }
    }


    void release() {
        if (VERBOSE) Log.d(TAG, "release");
        if (mVideoEncoder != null) {
            mVideoEncoder.stop();
            mVideoEncoder.release();
            mVideoEncoder = null;
        }

        if (mAudioEncoder != null) {
            mAudioEncoder.stop();
            mAudioEncoder.release();
            mAudioEncoder = null;
        }
        if (mMuxer != null) {
            mMuxer.stop();
            mMuxer.release();
            mMuxer = null;
        }
    }


    public Surface getEncoderSurface() {
        return mEncodesurface;
    }

}
