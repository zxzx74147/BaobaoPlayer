package cn.myhug.baobaoplayer.media;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

import cn.myhug.baobaoplayer.record.Mp4Muxer;
import cn.myhug.baobaoplayer.record.TimeStampGenerator;
import cn.myhug.baobaoplayer.util.TimeStampLogUtil;

import static cn.myhug.baobaoplayer.media.Mp4Config.MIME_TYPE_AUDIO;

/**
 * Created by zhengxin on 2016/11/8.
 */

public class AudioRecordSource {

    private static final String TAG = "AudioRecordSource";
    private static final int TIMEOUT_USEC = 10000;
    private static final boolean VERBOSE = false;
    private int frequence = Mp4Config.OUTPUT_AUDIO_SAMPLE_RATE_HZ; //录制频率，单位hz.这里的值注意了，写的不好，可能实例化AudioRecord对象的时候，会出错。我开始写成11025就不行。这取决于硬件设备
    private int channelConfig = AudioFormat.CHANNEL_IN_MONO;
    private int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;

    private AudioRecord mAudioRecord = null;
    private ByteBuffer mByteBuffer = null;
    private byte[] mInputBuffer = null;

    private Thread mRecordThread = null;
    private volatile boolean mIsRecording = false;
    private MediaCodec mAudioEncoder = null;
    private MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
    private volatile boolean mIsStop = false;
    private Mp4Muxer mMuxer = null;



    public void prepare() {
        int bufferSize = AudioRecord.getMinBufferSize(frequence, channelConfig, audioEncoding);
        //实例化AudioRecord
        mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.CAMCORDER, frequence, channelConfig, audioEncoding, bufferSize);
        if(mInputBuffer==null) {
            mInputBuffer = new byte[bufferSize * 2];
        }
        if(mByteBuffer==null) {
            mByteBuffer = ByteBuffer.allocate(bufferSize * 2);
        }
        mAudioRecord.startRecording();

        {
            MediaCodecInfo audioCodecInfo = Mp4Config.selectCodec(Mp4Config.MIME_TYPE_AUDIO);
            MediaFormat outputAudioFormat =
                    MediaFormat.createAudioFormat(
                            MIME_TYPE_AUDIO, Mp4Config.OUTPUT_AUDIO_SAMPLE_RATE_HZ,
                            1);
            outputAudioFormat.setInteger(MediaFormat.KEY_BIT_RATE, Mp4Config.OUTPUT_AUDIO_BIT_RATE);
            outputAudioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, Mp4Config.OUTPUT_AUDIO_AAC_PROFILE);
            outputAudioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufferSize*2);


            mAudioEncoder = null;

            try {
                mAudioEncoder = MediaCodec.createByCodecName(audioCodecInfo.getName());
                mAudioEncoder.configure(outputAudioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                mAudioEncoder.start();

            } catch (IOException ioe) {
                throw new RuntimeException("failed init mVideoEncoder", ioe);
            }
        }

    }

    public void startRecord() {
        if (mRecordThread != null) {
            mRecordThread.interrupt();
            mRecordThread = null;
        }
        mRecordThread = new Thread(mRecordRunnable);
        mRecordThread.start();
        mIsStop = false;
    }

    public void setMuxer(Mp4Muxer muxer) {
        mMuxer = muxer;
//        mMuxer.addAudioTrack(mAudioEncoder.getOutputFormat());
//        if (VERBOSE)  Log.d(TAG, "old format="+mAudioEncoder.getOutputFormat());
    }

    public void stop() {
        mIsStop = true;
    }

    public void reset(){
        mAudioEncoder.stop();
        mAudioEncoder.release();
        prepare();
    }

    public void resumeRecord() {
        mIsRecording = true;
    }

    public void pauseRecord() {
        mIsRecording = false;
    }

    public MediaFormat getFormat() {
        return mAudioEncoder.getOutputFormat();
    }

    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
    private Runnable mRecordRunnable = new Runnable() {
        @Override
        public void run() {
            try {

                while (true) {
                    //从bufferSize中读取字节，返回读取的short个数
                    int bufferReadResult = mAudioRecord.read(mInputBuffer, 0, mInputBuffer.length);
                    if (mAudioEncoder != null) {
                        mByteBuffer.position(0);
                        mByteBuffer.put(mInputBuffer, 0, bufferReadResult);
                        mByteBuffer.flip();
                        info.offset = 0;
                        info.size = bufferReadResult;
//                        if (VERBOSE) Log.d(TAG, "nano time="+System.nanoTime());

                        if(mIsRecording) {
                            if (VERBOSE) Log.d(TAG, "time stamp="+info.presentationTimeUs);
                            drainAudioEncoder(false, mByteBuffer, info);
                            info.presentationTimeUs = TimeStampGenerator.sharedInstance().getAudioStamp();
                        }


                        if(mIsStop){
                            info.flags|=MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                            info.size = 0;
                            info.presentationTimeUs = TimeStampGenerator.sharedInstance().getAudioStamp();
                            drainAudioEncoder(true, mByteBuffer, info);
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();

            } finally {
                if (VERBOSE) Log.d(TAG, "AudioRecordSource stop");
//                drainAudioEncoder(true, null, null);
                mAudioRecord.stop();
                mAudioRecord.release();
                mAudioRecord = null;
            }

        }
    };


    public void drainAudioEncoder(boolean endOfStream, ByteBuffer inputBuffer, MediaCodec.BufferInfo info) {

//        if (VERBOSE) Log.d(TAG, "drainAudioEncoder(" + endOfStream + ")");

        TimeStampLogUtil.logTimeStamp(1, "drainAudioEncoder start====");
        int inputIndex = -1;

        inputIndex = mAudioEncoder.dequeueInputBuffer(TIMEOUT_USEC);
        TimeStampLogUtil.logTimeStamp(1, "dequeueInputBuffer====");
        if (inputIndex >= 0) {
            if (endOfStream) {
                if (VERBOSE) Log.d(TAG, "sending EOS to drainAudioEncoder");
                mAudioEncoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
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


        ByteBuffer[] encoderOutputBuffers = mAudioEncoder.getOutputBuffers();
        while (true) {
            int encoderStatus = mAudioEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);

            if (VERBOSE) Log.d(TAG, "index="+encoderStatus+"|mBufferInfo.time="+mBufferInfo.presentationTimeUs);
            TimeStampLogUtil.logTimeStamp(1, "dequeueOutputBuffer====");
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
                if (!endOfStream) {
                    break;      // out of while
                } else {
                    if (VERBOSE) Log.d(TAG, "no output available, spinning to await EOS");
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                // not expected for an mAudioEncoder
                encoderOutputBuffers = mAudioEncoder.getOutputBuffers();
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // should happen before receiving buffers, and should only happen once

                MediaFormat newFormat = mAudioEncoder.getOutputFormat();
                if (VERBOSE) Log.d(TAG, "mAudioEncoder output format changed: " + newFormat);
                mMuxer.addAudioTrack(newFormat);

            } else if (encoderStatus < 0) {
                Log.w(TAG, "unexpected result from mVideoEncoder.dequeueOutputBuffer: " +
                        encoderStatus);
                // let's ignore it
            } else {

                ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                if (encodedData == null) {
                    throw new RuntimeException("encoderOutputBuffer " + encoderStatus +
                            " was null");
                }

                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // The codec config data was pulled out and fed to the muxer when we got
                    // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                    if (VERBOSE) Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                    MediaFormat outputAudioFormat =
                            MediaFormat.createAudioFormat(
                                    MIME_TYPE_AUDIO, Mp4Config.OUTPUT_AUDIO_SAMPLE_RATE_HZ,
                                    2);
                    outputAudioFormat.setByteBuffer("csd-0", encodedData);

                    mBufferInfo.size = 0;
                }

//                if (mBufferInfo.size != 0) {
                    // adjust the ByteBuffer values to match BufferInfo (not needed?)
                    if (mMuxer != null ) {
                        mMuxer.writeAudio(encodedData, mBufferInfo);
                    }
                    TimeStampLogUtil.logTimeStamp(1, "writeSampleData====");
                    if (VERBOSE) Log.d(TAG, "sent " + mBufferInfo.size + " audio bytes to muxer");
//                }

                mAudioEncoder.releaseOutputBuffer(encoderStatus, false);

                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    if (!endOfStream) {
                        Log.w(TAG, "reached end of stream unexpectedly");
                    } else {
                        if (VERBOSE) Log.d(TAG, "end of stream reached");
                    }
                    break;      // out of while
                }
            }
        }
    }
}
