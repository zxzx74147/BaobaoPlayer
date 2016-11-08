package cn.myhug.baobaoplayer.media;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
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

    private static final String TAG = "MediaMixer";
    private static final boolean VERBOSE = false;           // lots of logging
    private static final String MIME_TYPE = "video/avc";    // H.264 Advanced Video Coding
    private static final int WIDTH = Mp4Config.VIDEO_WIDTH;
    private static final int HEIGHT = Mp4Config.VIDEO_HEIGHT;
    private static final int BIT_RATE = Mp4Config.VIDEO_BITRATE;            // 2Mbps
    public static final int FRAME_RATE = Mp4Config.VIDEO_FRAME_RATE;               // 30fps
    private static final int IFRAME_INTERVAL = Mp4Config.VIDEO_I_FRAME_INTERVAL;          // 10 seconds between I-frames
    public static  final int TIMEOUT_USEC = 10000;

    MediaCodec mVideoEncoder = null;
    MediaCodec mAudioEncoder = null;
    Surface mEncodesurface;

    private MediaCodec.BufferInfo mBufferInfo;
    public MediaMuxer mMuxer;

    public int mVideoTrackIndex = -1;
    public int mAudioTrackIndex = -1;
    public boolean mMuxerStarted;
    private File mOutputFile = null;


    public void prepare() {

//        String outputPath = FileUtil.getFile("output.mp4").toString();

        mBufferInfo = new MediaCodec.BufferInfo();

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


            mAudioEncoder = null;

            try {
                mAudioEncoder = MediaCodec.createByCodecName(audioCodecInfo.getName());
                mAudioEncoder.configure(outputAudioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                mAudioEncoder.start();

            } catch (IOException ioe) {
                throw new RuntimeException("failed init mVideoEncoder", ioe);
            }
        }

        MediaFormat videoFormat = mVideoEncoder.getOutputFormat();
        MediaFormat audioFormat = mAudioEncoder.getOutputFormat();
//        mVideoTrackIndex = mMuxer.addTrack(videoFormat);
//        mAudioTrackIndex = mMuxer.addTrack(audioFormat);
//        mMuxer.start();
        mMuxerStarted = false;

    }

    public void setOutputFile(File file){
         mOutputFile = file;
    }


    public void drainAudioEncoder(boolean endOfStream, ByteBuffer inputBuffer, MediaCodec.BufferInfo info) {

        if (VERBOSE) Log.d(TAG, "drainAudioEncoder(" + endOfStream + ")");

        TimeStampLogUtil.logTimeStamp(1,"drainAudioEncoder start====");
        int inputIndex = -1;

        inputIndex = mAudioEncoder.dequeueInputBuffer(TIMEOUT_USEC);
        TimeStampLogUtil.logTimeStamp(1,"dequeueInputBuffer====");
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
            TimeStampLogUtil.logTimeStamp(1,"dequeueOutputBuffer====");
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
                if (mMuxerStarted) {
                    throw new RuntimeException("format changed twice");
                }
                MediaFormat newFormat = mAudioEncoder.getOutputFormat();
                Log.d(TAG, "mAudioEncoder output format changed: " + newFormat);

                // now that we have the Magic Goodies, start the muxer
                mAudioTrackIndex = mMuxer.addTrack(newFormat);
                if (mVideoTrackIndex >= 0) {
                    mMuxer.start();
                    mMuxerStarted = true;
                }
            } else if (encoderStatus < 0) {
                Log.w(TAG, "unexpected result from mVideoEncoder.dequeueOutputBuffer: " +
                        encoderStatus);
                // let's ignore it
            } else {
                if(!mMuxerStarted){

                    mAudioEncoder.releaseOutputBuffer(encoderStatus, false);

                    return;
                }
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

                if (mBufferInfo.size != 0) {
//                    if (!mMuxerStarted) {
//                        throw new RuntimeException("muxer hasn't started");
//                    }

                    // adjust the ByteBuffer values to match BufferInfo (not needed?)
                    encodedData.position(mBufferInfo.offset);
                    encodedData.limit(mBufferInfo.offset + mBufferInfo.size);

                    mMuxer.writeSampleData(mAudioTrackIndex, encodedData, mBufferInfo);
                    TimeStampLogUtil.logTimeStamp(1,"writeSampleData====");
                    if (VERBOSE) Log.d(TAG, "sent " + mBufferInfo.size + "audio bytes to muxer");
                }

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


    public void drainVideoEncoder(boolean endOfStream) {
        final int TIMEOUT_USEC = 10000;
        if (VERBOSE) Log.d(TAG, "drainVideoEncoder(" + endOfStream + ")");

        if (endOfStream) {
            if (VERBOSE) Log.d(TAG, "sending EOS to mVideoEncoder");
            mVideoEncoder.signalEndOfInputStream();
        }

        ByteBuffer[] encoderOutputBuffers = mVideoEncoder.getOutputBuffers();
        while (true) {
            int encoderStatus = mVideoEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
                if (!endOfStream) {
                    break;      // out of while
                } else {
                    if (VERBOSE) Log.d(TAG, "no output available, spinning to await EOS");
                }
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

                mVideoTrackIndex = mMuxer.addTrack(newFormat);
                // now that we have the Magic Goodies, start the muxer
                if (mAudioTrackIndex >= 0) {
                    mMuxer.start();
                    mMuxerStarted = true;
                }
            } else if (encoderStatus < 0) {
                Log.w(TAG, "unexpected result from mVideoEncoder.dequeueOutputBuffer: " +
                        encoderStatus);
                // let's ignore it
            } else {
                if(!mMuxerStarted){
                    mVideoEncoder.releaseOutputBuffer(encoderStatus, false);
                    return;
                }
                ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                if (encodedData == null) {
                    throw new RuntimeException("encoderOutputBuffer " + encoderStatus +
                            " was null");
                }

                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // The codec config data was pulled out and fed to the muxer when we got
                    // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                    if (VERBOSE) Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");

                    MediaFormat format =
                            MediaFormat.createVideoFormat(MIME_TYPE, WIDTH, HEIGHT);
                    format.setByteBuffer("csd-0", encodedData);

                    mBufferInfo.size = 0;
                }

                if (mBufferInfo.size != 0) {
//                    if (!mMuxerStarted) {
//                        throw new RuntimeException("muxer hasn't started");
//                    }

                    // adjust the ByteBuffer values to match BufferInfo (not needed?)
                    encodedData.position(mBufferInfo.offset);
                    encodedData.limit(mBufferInfo.offset + mBufferInfo.size);

                    mMuxer.writeSampleData(mVideoTrackIndex, encodedData, mBufferInfo);
                    if (VERBOSE) Log.d(TAG, "sent " + mBufferInfo.size + "video bytes to muxer");
                }

                mVideoEncoder.releaseOutputBuffer(encoderStatus, false);

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
