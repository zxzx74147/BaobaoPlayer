package cn.myhug.baobaoplayer.record;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;

import java.nio.ByteBuffer;

import static android.media.MediaCodec.BUFFER_FLAG_SYNC_FRAME;


/**
 * Created by zhengxin on 16/2/18.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class CommonEncoderPump {
    private static final boolean VERBOSE = true;
    public final String TAG = "CommonEncoderPump";
    private volatile int mStatus = 0;
    private static final int STATUS_STOP = 0;
    private static final int STATUS_RUNNING = 1;
    private ByteBuffer mBuffer = null;
    private ByteBuffer[] mBuffers = null;
    private MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
    public MediaFormat mMediaFormat;
    private Thread mPumpThread = null;
    private Mp4Muxer mMuxer = null;


    private ByteBuffer mH264Keyframe;
    private int mH264MetaSize;
    private byte[] videoConfig;
    private static final int LEN = 1024 * 300;


    private MediaCodec mMediaCodec;


    public CommonEncoderPump(MediaCodec codec) {
        mMediaCodec = codec;
    }

    public boolean start() {
        if (mPumpThread != null) {
            stop();
        }

        mStatus = STATUS_RUNNING;
        mPumpThread = new Thread(mPumpRunnable);
        mPumpThread.start();
        return true;
    }

    public boolean stop() {
        mStatus = STATUS_STOP;
        if (mPumpThread != null) {
            mPumpThread.interrupt();
            mPumpThread = null;
        }

        return true;
    }


    public Runnable mPumpRunnable = new Runnable() {
        @Override
        public void run() {

            try {
                mBuffers = mMediaCodec.getOutputBuffers();
                byte[] tmpInputBuffer = new byte[LEN];
                while (mStatus == STATUS_RUNNING) {
                    int mIndex = -1;
                    try {

                        mIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, 500000);
//                        if (VERBOSE) Log.d(TAG, "buffer info= " +mIndex+"|"+ mBufferInfo.flags+"|"+mBufferInfo.size+"|"+mBufferInfo.presentationTimeUs);

                        if (mIndex >= 0) {
//                            Log.d(TAG, "Index: " + mIndex + " Time: " + mBufferInfo.presentationTimeUs + " size: " + mBufferInfo.size);
                            mBuffer = mBuffers[mIndex];
                            if(mMuxer.mVideoTrackIndex<0){
                                mMuxer.addVideoTrack(mMediaCodec.getOutputFormat());
                            }


                            if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                mBufferInfo.size = 0;
                            }
                            if ((mBufferInfo.flags & BUFFER_FLAG_SYNC_FRAME) != 0) {
                                if (VERBOSE)  Log.d(TAG, "get a key frame & size: " + mBufferInfo.size+"|"+mMuxer.isStarted());
                            }else{
//                                if (VERBOSE) Log.d(TAG, "get a key no frame & size: " + mBufferInfo.flags+"|"+mBufferInfo.size+"|"+mBufferInfo.presentationTimeUs);
                            }


                            if (mMuxer != null) {
                                mMuxer.writeVideo(mBuffer,mBufferInfo);
                            }
                            mMediaCodec.releaseOutputBuffer(mIndex, false);
                            if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                break;      // out of while
                            }

                        } else if (mIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                            mBuffers = mMediaCodec.getOutputBuffers();
                        } else if (mIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            mMediaFormat = mMediaCodec.getOutputFormat();
                            Log.i(TAG, mMediaFormat.toString());
                            mMuxer.addVideoTrack(mMediaFormat);
                            if (VERBOSE) Log.v(TAG, "format changed ...");
                        } else if (mIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                            if (VERBOSE) Log.v(TAG, "No buffer available...");
                            try {
                                // wait 10ms
                                Thread.sleep(10);
                            } catch (InterruptedException e) {
                            }
                        } else {
                            Log.e(TAG, "Message: " + mIndex);
                            //return 0;
                        }

                        if (mBuffer != null) {
                            mBuffer = null;
                        }
                    } catch (IllegalStateException il) {
                        il.printStackTrace();
                        Thread.sleep(500);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
                mMediaCodec.stop();
                mMediaCodec.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };


    private void captureH264MetaData(byte[] encodedData, MediaCodec.BufferInfo bufferInfo, int capacity) {
        mH264MetaSize = bufferInfo.size;
        mH264Keyframe = ByteBuffer.allocateDirect(capacity);
        videoConfig = new byte[bufferInfo.size];
        System.arraycopy(encodedData, 0, videoConfig, 0, bufferInfo.size);
        mH264Keyframe.put(videoConfig, 0, bufferInfo.size);
    }


    private void packageH264Keyframe(byte[] encodedData, MediaCodec.BufferInfo bufferInfo) {
        mH264Keyframe.position(mH264MetaSize);
        mH264Keyframe.put(encodedData, bufferInfo.offset, bufferInfo.size); // BufferOverflow
    }



    /**
     * previous presentationTimeUs for writing
     */
    private long prevOutputPTSUs = 0;

    protected final Object mSync = new Object();

    private long offsetPTSUs = 0;
    /**
     * get next encoding presentationTimeUs
     * @return
     */
    protected long getPTSUs() {
        long result;
        synchronized (mSync) {
            result = System.nanoTime() / 1000L - offsetPTSUs;
        }
        // presentationTimeUs should be monotonic
        // otherwise muxer fail to write
        if (result < prevOutputPTSUs)
            result = (prevOutputPTSUs - result) + result;
        return result;
    }

    public void setMuxer(Mp4Muxer muxer) {
        mMuxer = muxer;
    }
}
