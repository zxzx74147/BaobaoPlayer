package cn.myhug.baobaoplayer.media;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

import cn.myhug.baobaoplayer.filter.base.gpuimage.GPUImageFilter;

/**
 * Created by guoheng on 2016/8/31.
 */
public class EncodeDecodeSurface {

    private static final String TAG = "EncodeDecodeSurface";
    private static final boolean VERBOSE = false;           // lots of logging

    private Handler mHandler = null;
    private static final int MSG_PERCENT = 1;
    private static final int MSG_ERROR = 2;
    private static final int MSG_DONE = 3;

    private int dealPercent = 0;

    private SurfaceDecoder mDecoder = new SurfaceDecoder();
    private SurfaceEncoder mEncoder = new SurfaceEncoder();

    public void setFilter(GPUImageFilter gpuImageFilter) {
        mDecoder.setFilter(gpuImageFilter);
    }

    public interface IBBMediaMuxterPrgressListener {
        void onProgress(int progress);

        void onDone();

        void onError(Exception e);
    }
    private IBBMediaMuxterPrgressListener mListener = null;

    public EncodeDecodeSurface(){
        mHandler = new Handler(Looper.getMainLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                if(mListener==null){
                    return false;
                }
                switch (msg.what){
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
    }

    public void setListener(IBBMediaMuxterPrgressListener listener){
        mListener = listener;
    }
    /**
     * test entry point
     */
    public void startMix() throws Throwable {
        EncodeDecodeSurfaceWrapper.runTest(this);
    }

    public void setInputUri(Uri uri) {
        mDecoder.setUri(uri);
    }

    private static class EncodeDecodeSurfaceWrapper implements Runnable {
        private Throwable mThrowable;
        private EncodeDecodeSurface mTest;

        private EncodeDecodeSurfaceWrapper(EncodeDecodeSurface test) {
            mTest = test;
        }

        @Override
        public void run() {
            try {
                mTest.Prepare();
            } catch (Throwable th) {
                mThrowable = th;
                th.printStackTrace();
            }
        }

        /**
         * Entry point.
         */
        public static void runTest(EncodeDecodeSurface obj) throws Throwable {
            EncodeDecodeSurfaceWrapper wrapper = new EncodeDecodeSurfaceWrapper(obj);
            Thread th = new Thread(wrapper, "codec test");
            th.start();
            //th.join();
            if (wrapper.mThrowable != null) {
                throw wrapper.mThrowable;
            }
        }
    }

    private void Prepare() throws IOException {
        try {

            mEncoder.VideoEncodePrepare();
            mDecoder.SurfaceDecoderPrePare(mEncoder.getEncoderSurface());
            doExtract();
        } catch (Exception e){
            e.printStackTrace();
            Message msg = mHandler.obtainMessage(MSG_ERROR);
            msg.obj = e;
            mHandler.sendMessage(msg);
        } finally{
            mDecoder.release();
            mEncoder.release();
        }
    }

    void doExtract() throws IOException {
        final int TIMEOUT_USEC = 100000;
        ByteBuffer[] decoderInputBuffers = mDecoder.mVideoDecoder.getInputBuffers();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int inputChunk = 0;
        int decodeCount = 0;
        long frameSaveTime = 0;

        boolean outputDone = false;
        boolean inputDone = false;
        while (!outputDone) {
            if (VERBOSE) Log.d(TAG, "loop");

            // Feed more data to the mVideoDecoder.
            if (!inputDone) {
                int inputBufIndex = mDecoder.mVideoDecoder.dequeueInputBuffer(TIMEOUT_USEC);
                if (inputBufIndex >= 0) {
                    ByteBuffer inputBuf = decoderInputBuffers[inputBufIndex];
                    int chunkSize = mDecoder.extractor.readSampleData(inputBuf, 0);
                    if (chunkSize < 0) {
                        mDecoder.mVideoDecoder.queueInputBuffer(inputBufIndex, 0, 0, 0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                        if (VERBOSE) Log.d(TAG, "sent input EOS");
                    } else {
                        if (mDecoder.extractor.getSampleTrackIndex() != mDecoder.mDecodeTrackVideoIndex) {
                            Log.w(TAG, "WEIRD: got sample from track " +
                                    mDecoder.extractor.getSampleTrackIndex() + ", expected " + mDecoder.mDecodeTrackVideoIndex);
                        }
                        long presentationTimeUs = mDecoder.extractor.getSampleTime();
                        long duration = mDecoder.getDuration();
                        if(duration!=0) {
                            int percent = (int) (presentationTimeUs*100 / duration );
//                            Log.i("EncodeDecodeSurface","onProgress"+percent+"|"+presentationTimeUs+"|"+duration);
                            if(dealPercent!=percent) {
                                dealPercent = percent;
                                Message msg = mHandler.obtainMessage(MSG_PERCENT);
                                msg.arg1 = percent;
                                mHandler.sendMessage(msg);
                            }
                        }
                        mDecoder.mVideoDecoder.queueInputBuffer(inputBufIndex, 0, chunkSize,
                                presentationTimeUs, 0 /*flags*/);
                        if (VERBOSE) {
                            Log.d(TAG, "submitted frame " + inputChunk + " to dec, size=" +
                                    chunkSize);
                        }
                        inputChunk++;
                        mDecoder.extractor.advance();
                    }
                } else {
                    if (VERBOSE) Log.d(TAG, "input buffer not available");
                }
            }

            if (!outputDone) {
                int decoderStatus = mDecoder.mVideoDecoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // no output available yet
                    if (VERBOSE) Log.d(TAG, "no output from mVideoDecoder available");
                } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    // not important for us, since we're using Surface
                    if (VERBOSE) Log.d(TAG, "mVideoDecoder output buffers changed");
                } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = mDecoder.mVideoDecoder.getOutputFormat();
                    if (VERBOSE) Log.d(TAG, "mVideoDecoder output format changed: " + newFormat);
                } else if (decoderStatus < 0) {

                } else { // decoderStatus >= 0
                    if (VERBOSE) Log.d(TAG, "surface mVideoDecoder given buffer " + decoderStatus +
                            " (size=" + info.size + ")");
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        if (VERBOSE) Log.d(TAG, "output EOS");
                        outputDone = true;
                    }

                    boolean doRender = (info.size != 0);

                    mDecoder.mVideoDecoder.releaseOutputBuffer(decoderStatus, doRender);
                    if (doRender) {
                        if (VERBOSE) Log.d(TAG, "awaiting decode of frame " + decodeCount);

                        mDecoder.outputSurface.makeCurrent(1);
                        mDecoder.outputSurface.awaitNewImage();
                        mDecoder.outputSurface.drawImage(true);

                        mEncoder.drainEncoder(false);
                        mDecoder.outputSurface.setPresentationTime(computePresentationTimeNsec(decodeCount));
                        mDecoder.outputSurface.swapBuffers();

                        decodeCount++;
                    }

                }
            }
        }

        mEncoder.drainEncoder(true);
        mHandler.sendEmptyMessage(MSG_DONE);
//        int numSaved = (MAX_FRAMES < decodeCount) ? MAX_FRAMES : decodeCount;
//        Log.d(TAG, "Saving " + numSaved + " frames took " +
//                (frameSaveTime / numSaved / 1000) + " us per frame");
    }


    private static long computePresentationTimeNsec(int frameIndex) {
        final long ONE_BILLION = 1000000000;
        return frameIndex * ONE_BILLION / 30;
    }


}

