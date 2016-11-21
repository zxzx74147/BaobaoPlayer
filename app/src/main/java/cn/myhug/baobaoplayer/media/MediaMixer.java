package cn.myhug.baobaoplayer.media;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;

import cn.myhug.baobaoplayer.filter.base.gpuimage.GPUImageFilter;
import cn.myhug.baobaoplayer.util.TimeStampLogUtil;

/**
 * Created by guoheng on 2016/8/31.
 */
public class MediaMixer {

    private static final String TAG = "MediaMixer";
    private static final boolean VERBOSE = false;           // lots of logging

    private Handler mHandler = null;
    public static final int MSG_PERCENT = 1;
    public static final int MSG_ERROR = 2;
    public static final int MSG_DONE = 3;

    private int dealPercent = 0;

    private MediaDecoder mDecoder = new MediaDecoder();
    private MediaEncoder mEncoder = new MediaEncoder();
    private AudioDecoder mAudioDecoder = new AudioDecoder();
    private byte[] mAudioBytes = new byte[AudioDecoder.BUFFER_LEN];
    private ByteBuffer mAudioByteBuffer = ByteBuffer.allocate(AudioDecoder.BUFFER_LEN);


    public void setFilter(GPUImageFilter gpuImageFilter) {
        mDecoder.setFilter(gpuImageFilter);
    }


    public interface IBBMediaMuxterPrgressListener {
        void onProgress(int progress);

        void onDone();

        void onError(Exception e);
    }

    private IBBMediaMuxterPrgressListener mListener = null;

    public MediaMixer() {
        mHandler = new Handler(Looper.getMainLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                if (mListener == null) {
                    return false;
                }
                switch (msg.what) {
                    case MSG_PERCENT:
                        mListener.onProgress(msg.arg1);
                        break;
                    case MSG_ERROR:
                        mListener.onError((Exception) msg.obj);
                        break;
                    case MSG_DONE:
                        mListener.onDone();
                        break;
                }
                return false;
            }
        });
        mEncoder.setHandler(mHandler);
    }

    public void setListener(IBBMediaMuxterPrgressListener listener) {
        mListener = listener;
    }

    /**
     * test entry point
     */
    public void startMix() throws Throwable {
        MediaMixWapper.startMix(this);
    }

    public void setMixAudio(Uri uri){
        mAudioDecoder.setUri(uri);
    }

    public void setMixFileDescriptor(FileDescriptor descriptor){
        mAudioDecoder.setFileDescriptor(descriptor);
    }

    public void setInputUri(Uri uri) {
        mDecoder.setUri(uri);
    }

    public void setOutputFile(File file) {
       mEncoder.setOutputFile(file);
    }

    private static class MediaMixWapper implements Runnable {
        private Throwable mThrowable;
        private MediaMixer mTest;

        private MediaMixWapper(MediaMixer test) {
            mTest = test;
        }

        @Override
        public void run() {
            try {
                mTest.prepare();
            } catch (Throwable th) {
                mThrowable = th;
                th.printStackTrace();
            }
        }

        /**
         * Entry point.
         */
        public static void startMix(MediaMixer obj) throws Throwable {
            MediaMixWapper wrapper = new MediaMixWapper(obj);
            Thread th = new Thread(wrapper, "codec test");
            th.start();
            //th.join();
            if (wrapper.mThrowable != null) {
                throw wrapper.mThrowable;
            }
        }
    }

    private void prepare() throws IOException {
        try {

            mEncoder.prepare();
            mEncoder.start();
            mDecoder.SurfaceDecoderPrePare(mEncoder.getEncoderSurface());
            mAudioDecoder.prepare();
            doExtract();
        } catch (Exception e) {
            e.printStackTrace();
            Message msg = mHandler.obtainMessage(MSG_ERROR);
            msg.obj = e;
            mHandler.sendMessage(msg);
        } finally {
            mDecoder.release();
            mAudioDecoder.release();
            mDecoder = null;
            mAudioDecoder = null;
        }
    }

    public void release(){
        if(mEncoder !=null){
            mEncoder.release();
            mEncoder = null;
        }

    }
    void doExtract() throws IOException {
        TimeStampLogUtil.logTimeStamp("start====");
        final int TIMEOUT_USEC = 100000;
        ByteBuffer[] mVideoDecoderInputBuffers = mDecoder.mVideoDecoder.getInputBuffers();
        ByteBuffer[] mAudioDecoderInputBuffers = mDecoder.mAudioDecoder.getInputBuffers();
        ByteBuffer[] mVideoDecoderOutputBuffers = mDecoder.mVideoDecoder.getOutputBuffers();
        ByteBuffer[] mAudioDecoderOutputBuffers = mDecoder.mAudioDecoder.getOutputBuffers();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int mVideoChunkSize = 0;
        int mAudioChunkSize = 0;
        int decodeCount = 0;
        long frameSaveTime = 0;
        int inputIndex = 0;

        boolean mVideoOutputDone = false;
        boolean mAudioOutputDone = false;
        boolean inputDone = false;
        while (!mVideoOutputDone || !mAudioOutputDone) {
            if (VERBOSE) Log.d(TAG, "loop");
            // Feed more data to the mVideoDecoder.
            int mTrackIndex = -1;
            if (!inputDone) {
                ByteBuffer inputBuf = null;
                mTrackIndex = mDecoder.extractor.getSampleTrackIndex();
                if(mDecoder.extractor.getSampleTime()>Mp4Config.MAX_LEN){
                    mTrackIndex = -1;
                }
                if (mTrackIndex == mDecoder.mDecodeTrackVideoIndex) {
                    TimeStampLogUtil.logTimeStamp("video start====");
                    inputIndex = mDecoder.mVideoDecoder.dequeueInputBuffer(TIMEOUT_USEC);
                    inputBuf = mVideoDecoderInputBuffers[inputIndex];

                } else if (mTrackIndex == mDecoder.mDecodeTrackAudioIndex) {
                    TimeStampLogUtil.logTimeStamp("audio start====");
                    inputIndex = mDecoder.mAudioDecoder.dequeueInputBuffer(TIMEOUT_USEC);
                    inputBuf = mAudioDecoderInputBuffers[inputIndex];
                } else {
                    inputIndex = mDecoder.mVideoDecoder.dequeueInputBuffer(TIMEOUT_USEC);
                    mDecoder.mVideoDecoder.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    inputIndex = mDecoder.mAudioDecoder.dequeueInputBuffer(TIMEOUT_USEC);
                    mDecoder.mAudioDecoder.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    inputDone = true;
                    inputIndex = -1000;
                    if (VERBOSE) Log.d(TAG, "sent input EOS");
                }
                if (inputIndex >= 0) {
                    TimeStampLogUtil.logTimeStamp("decoder dequeueInputBuffer====");
                    int chunkSize = mDecoder.extractor.readSampleData(inputBuf, 0);
                    if (chunkSize < 0) {
                        inputIndex = mDecoder.mVideoDecoder.dequeueInputBuffer(TIMEOUT_USEC);
                        mDecoder.mVideoDecoder.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputIndex = mDecoder.mAudioDecoder.dequeueInputBuffer(TIMEOUT_USEC);
                        mDecoder.mAudioDecoder.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                        if (VERBOSE) Log.d(TAG, "sent input EOS");
                    } else {
                        long presentationTimeUs = mDecoder.extractor.getSampleTime();

                        if (mDecoder.extractor.getSampleTrackIndex() == mDecoder.mDecodeTrackVideoIndex) {
                            long duration = Math.min(mDecoder.getDuration(),Mp4Config.MAX_LEN);
                            if (duration != 0) {
                                int percent = (int) (presentationTimeUs * 100 / duration);
                                if (dealPercent != percent) {
                                    dealPercent = percent;
                                    Message msg = mHandler.obtainMessage(MSG_PERCENT);
                                    msg.arg1 = percent;
                                    mHandler.sendMessage(msg);
                                }
                            }
                            mDecoder.mVideoDecoder.queueInputBuffer(inputIndex, 0, chunkSize,
                                    presentationTimeUs, 0 /*flags*/);
                            if (VERBOSE) {
                                Log.d(TAG, "submitted video frame " + mVideoChunkSize + " to dec, size=" +
                                        chunkSize);
                            }
                            mVideoChunkSize++;
                        } else if (mDecoder.extractor.getSampleTrackIndex() == mDecoder.mDecodeTrackAudioIndex) {
                            mDecoder.mAudioDecoder.queueInputBuffer(inputIndex, 0, chunkSize,
                                    presentationTimeUs, 0 /*flags*/);
                            if (VERBOSE) {
                                Log.d(TAG, "submitted audio frame " + mAudioChunkSize + " to dec, size=" +
                                        chunkSize);
                            }
                            mAudioChunkSize++;
                        } else {
                            if (mDecoder.extractor.getSampleTrackIndex() != mDecoder.mDecodeTrackVideoIndex) {
                                Log.w(TAG, "WEIRD: got sample from track " +
                                        mDecoder.extractor.getSampleTrackIndex() + ", expected " + mDecoder.mDecodeTrackVideoIndex);
                            }
                        }
                        mDecoder.extractor.advance();
                        TimeStampLogUtil.logTimeStamp("decoder queueInputBuffer====");
                    }
                } else {
                    if (VERBOSE) Log.d(TAG, "input buffer not available");
                }
            }

            if (!mVideoOutputDone && (inputDone || mTrackIndex == mDecoder.mDecodeTrackVideoIndex)) {
                int decoderStatus = mDecoder.mVideoDecoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                TimeStampLogUtil.logTimeStamp("encoder dequeueOutputBuffer====");
                if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // no output available yet
                    if (VERBOSE) Log.d(TAG, "no output from mVideoDecoder available");
                } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    // not important for us, since we're using Surface
                    if (VERBOSE) Log.d(TAG, "mVideoDecoder output buffers changed");
                    mVideoDecoderOutputBuffers = mDecoder.mVideoDecoder.getOutputBuffers();
                } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = mDecoder.mVideoDecoder.getOutputFormat();
                    if (VERBOSE) Log.d(TAG, "mVideoDecoder output format changed: " + newFormat);
                } else if (decoderStatus < 0) {

                } else { // decoderStatus >= 0
                    if (VERBOSE) Log.d(TAG, "surface mVideoDecoder given buffer " + decoderStatus +
                            " (size=" + info.size + ")");
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        if (VERBOSE) Log.d(TAG, "output EOS");
                        mVideoOutputDone = true;
                        mEncoder.drainVideoEncoder(true);
                    }


                    boolean doRender = (info.size != 0);
                    mDecoder.mVideoDecoder.releaseOutputBuffer(decoderStatus, doRender);
                    if (doRender) {
                        if (VERBOSE) Log.d(TAG, "awaiting decode of frame " + decodeCount);

                        mDecoder.outputSurface.makeCurrent(1);
                        mDecoder.outputSurface.awaitNewImage();
                        mDecoder.outputSurface.drawImage(true);
                        mDecoder.outputSurface.setPresentationTime(info.presentationTimeUs*1000);
                        if(VERBOSE) Log.d(TAG,"timestamp="+info.presentationTimeUs);
                        mEncoder.drainVideoEncoder(false);
                        TimeStampLogUtil.logTimeStamp("encoder drainVideoEncoder====");
//                        mDecoder.outputSurface.setPresentationTime(computePresentationTimeNsec(decodeCount));
                        mDecoder.outputSurface.swapBuffers();

                        decodeCount++;
                    }
                }
                TimeStampLogUtil.logTimeStamp("video end====");
            }

            if (!mAudioOutputDone && (inputDone || mTrackIndex == mDecoder.mDecodeTrackAudioIndex)) {
                int decoderStatus = mDecoder.mAudioDecoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                TimeStampLogUtil.logTimeStamp("encoder dequeueOutputBuffer====");
                if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // no output available yet
                    if (VERBOSE) Log.d(TAG, "no output from mVideoDecoder available");
                } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    // not important for us, since we're using Surface
                    if (VERBOSE) Log.d(TAG, "mAudioDecoder output buffers changed");
                    mAudioDecoderOutputBuffers = mDecoder.mAudioDecoder.getOutputBuffers();
                } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = mDecoder.mAudioDecoder.getOutputFormat();
                    if (VERBOSE) Log.d(TAG, "mAudioDecoder output format changed: " + newFormat);
                } else if (decoderStatus < 0) {

                } else { // decoderStatus >= 0
                    if (VERBOSE) Log.d(TAG, "surface mAudioDecoder given buffer " + decoderStatus +
                            " (size=" + info.size + ")");
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        if (VERBOSE) Log.d(TAG, "output EOS");
                        mAudioOutputDone = true;
                        mEncoder.drainAudioEncoder(true, null, info);
                    }else {
                        if (mAudioDecoder.hasSource()) {
                            int length = mAudioDecoder.pumpAudioBuffer(info.size);
                            if (VERBOSE)
                                Log.d(TAG, String.format("decode mix audio len=%d,time=%d,audio len = %d time=%d ",
                                        length, mAudioDecoder.latest.presentationTimeUs,
                                        info.size, info.presentationTimeUs));


                            mAudioDecoderOutputBuffers[decoderStatus].get(mAudioBytes, 0, info.size);
                            AudioUtil.mixVoice(mAudioBytes, mAudioDecoder.getResult(), info.size);
                            mAudioByteBuffer.position(0);
                            info.offset = 0;
                            mAudioByteBuffer.put(mAudioBytes, 0, info.size);
                            mAudioByteBuffer.flip();
                            mEncoder.drainAudioEncoder(false, mAudioByteBuffer, info);
                        } else {
                            mEncoder.drainAudioEncoder(false, mAudioDecoderOutputBuffers[decoderStatus], info);
                        }
                    }
                    TimeStampLogUtil.logTimeStamp("encoder drainAudioEncoder====");
                    mDecoder.mAudioDecoder.releaseOutputBuffer(decoderStatus, false);
                }
                TimeStampLogUtil.logTimeStamp("audio end====");
            }
        }
        TimeStampLogUtil.logTimeStamp("game over ====");



//        int numSaved = (MAX_FRAMES < decodeCount) ? MAX_FRAMES : decodeCount;
//        Log.d(TAG, "Saving " + numSaved + " frames took " +
//                (frameSaveTime / numSaved / 1000) + " us per frame");
    }



}

