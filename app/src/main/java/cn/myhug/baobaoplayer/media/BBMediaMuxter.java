package cn.myhug.baobaoplayer.media;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.opengl.GLES20;
import android.util.Log;
import android.util.SparseArray;
import android.view.Surface;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import cn.myhug.baobaoplayer.filter.advanced.MagicSketchFilter;
import cn.myhug.baobaoplayer.filter.base.MagicSurfaceInputFilter;
import cn.myhug.baobaoplayer.filter.base.gpuimage.GPUImageFilter;
import cn.myhug.baobaoplayer.gles.EglCore;
import cn.myhug.baobaoplayer.gles.GlUtil;
import cn.myhug.baobaoplayer.gles.WindowSurface;
import cn.myhug.baobaoplayer.util.TimeStampLogUtil;
import cn.myhug.baobaoplayer.utils.OpenGlUtils;
import cn.myhug.baobaoplayer.utils.Rotation;
import cn.myhug.baobaoplayer.utils.TextureRotationUtil;

/**
 * Created by zhengxin on 2016/11/2.
 */

public class BBMediaMuxter {
    private static final boolean VERBOSE = true;
    private static final boolean DECODE = false;

    private static final String TAG = "BBMediaMuxter";

    public interface IBBMediaMuxterPrgressListener {
        void onProgress(int progress);

        void onState(int state);
    }

    private static final String MIME_TYPE_VIDEO = "video/avc";    // H.264 Advanced Video Coding
    private static final String MIME_TYPE_AUDIO = "audio/mp4a-latm";    // H.264 Advanced Video Coding
    private static final int OUTPUT_AUDIO_BIT_RATE = 32 * 1024;
    private static final int OUTPUT_AUDIO_AAC_PROFILE =
            MediaCodecInfo.CodecProfileLevel.AACObjectHE;
    private static final int OUTPUT_AUDIO_SAMPLE_RATE_HZ = 44100; // Must match the input stream.

    private IBBMediaMuxterPrgressListener mListener = null;
    private File mOutputFile = null;
    private Thread mMixThread = null;
    private boolean isCancel = false;

    private File mVideoInputFile = null;
    private Uri mVideoInputUri = null;
    private File mAudioInputFile = null;

    private MediaExtractor mVideoInputExtractor = null;
    private MediaExtractor mAudioInputExtractor = null;
    private MediaMuxer mMuxer = null;
    private int mVideoDecodeIndex = -1;
    private int mAudioDecodeIndex = -1;
    private MediaCodec mVideoDecodeCodec = null;
    private MediaCodec mAudioDecodeCodec = null;
    private MediaCodec mBgmDecodeCodec = null;
    private int mVideoMixTrack = -1;
    private int mAudioMixTrack = -1;

    private MediaCodec mAudioEncodeCodec = null;
    private MediaCodec mVideoEncodeCodec = null;
    private Context mContext = null;


    private ByteBuffer mVideoInputVideoBuffer = ByteBuffer.allocate(1024 * 10 * 10);

    private EglCore mEglCore = null;
    private SurfaceTexture mDecodeSurfaceTexture;
    protected int textureId = OpenGlUtils.NO_TEXTURE;
    private MagicSurfaceInputFilter mSurfaceFilter = null;
    private GPUImageFilter mImageFilter;
    private FloatBuffer mGLCubeBuffer;
    private FloatBuffer mGLTextureBuffer;
    private WindowSurface mDecodeInputSurface = null;
//    private InputSurface mVideoEncodeInput = null;
//    private OutputSurface mVideoDecodeOutput = null;
    private ByteBuffer[] mVideoDecoderInputBuffer = null;


    public BBMediaMuxter(Context context) {
        mContext = context;
        mGLCubeBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.CUBE.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mGLTextureBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mGLCubeBuffer.clear();
        mGLCubeBuffer.put(TextureRotationUtil.CUBE).position(0);
        mGLTextureBuffer.clear();
        float[] textureCords = TextureRotationUtil.getRotation(Rotation.fromInt(0), false, true);
        mGLTextureBuffer.put(textureCords).position(0);
    }

    public void addVideoSource(File file) {
        mVideoInputFile = file;
    }

    public void addVideoSource(Uri uri) {
        mVideoInputUri = uri;
    }

    public void addAudioSource(File file) {
        mAudioInputFile = file;
    }

    public void setOutput(File file) {
        mOutputFile = file;
    }

    public void setFilter(GPUImageFilter filter) {

    }

    public void setMediaMuxterPrgressListener(IBBMediaMuxterPrgressListener listener) {
        mListener = listener;
    }

    public void stop() {

    }

    private void release() {
        if (mVideoInputExtractor != null) {
            mVideoInputExtractor.release();
            mVideoInputExtractor = null;
        }

        if (mAudioInputExtractor != null) {
            mAudioInputExtractor.release();
            mAudioInputExtractor = null;
        }

        if (mMuxer != null) {
            mMuxer.stop();
            mMuxer.release();
            mMuxer = null;
        }

        if (mVideoDecodeCodec != null) {
            mVideoDecodeCodec.stop();
            mVideoDecodeCodec.release();
            mVideoDecodeCodec = null;
        }

        if (mAudioDecodeCodec != null) {
            mAudioDecodeCodec.stop();
            mAudioDecodeCodec.release();
            mAudioDecodeCodec = null;
        }

        if (mVideoEncodeCodec != null) {
            mVideoEncodeCodec.stop();
            mVideoEncodeCodec.release();
            mVideoEncodeCodec = null;
        }

        if (mAudioEncodeCodec != null) {
            mAudioEncodeCodec.stop();
            mAudioEncodeCodec.release();
            mAudioEncodeCodec = null;
        }


    }

    public boolean startMix() {
//        boolean result = prepareExtractor();
//        if (!result) {
//            return false;
//        }
//        result = prepareEncoder();
//        if (!result) {
//            return false;
//        }

        mMixThread = new Thread(mMixRunnable);
        mMixThread.start();
        isCancel = false;

        return true;
    }

    private boolean prepareExtractor() {
        mVideoInputExtractor = new MediaExtractor();
        mAudioInputExtractor = new MediaExtractor();
        try {
            if (mVideoInputUri != null) {
                mVideoInputExtractor.setDataSource(mContext, mVideoInputUri, null);
                mAudioInputExtractor.setDataSource(mContext, mVideoInputUri, null);
            } else {
                mVideoInputExtractor.setDataSource(mVideoInputFile.getAbsolutePath());
                mAudioInputExtractor.setDataSource(mVideoInputFile.getAbsolutePath());
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

//        if (mAudioInputFile != null) {
//            mAudioInputExtractor = new MediaExtractor();
//            try {
//                mAudioInputExtractor.setDataSource(mAudioInputFile.getAbsolutePath());
//            } catch (IOException e) {
//                e.printStackTrace();
//                return false;
//            }
//        }
        return true;
    }

    private boolean prepareEncoder() {
        try {
            if(mEglCore == null){
                mEglCore = new EglCore(null,EglCore.FLAG_RECORDABLE);
            }
            MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE_VIDEO, Mp4Config.VIDEO_WIDTH, Mp4Config.VIDEO_HEIGHT);
            // Set some properties.  Failing to specify some of these can cause the MediaCodec
            // configure() call to throw an unhelpful exception.
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, Mp4Config.VIDEO_BITRATE);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 24);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, Mp4Config.VIDEO_I_FRAME_INTERVAL);
            MediaCodecInfo codecInfo = Mp4Config.selectCodec(MIME_TYPE_VIDEO);
            mVideoEncodeCodec = MediaCodec.createByCodecName(codecInfo.getName());
            mVideoEncodeCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
//            mVideoEncodeInput = new InputSurface(mVideoEncodeCodec.createInputSurface());
            mDecodeInputSurface = new WindowSurface(mEglCore,mVideoEncodeCodec.createInputSurface(),true);
            mVideoEncodeCodec.start();
            mDecodeInputSurface.makeCurrent();


            MediaCodecInfo audioCodecInfo = Mp4Config.selectCodec(MIME_TYPE_AUDIO);
            if (audioCodecInfo == null) {
                // Don't fail CTS if they don't have an AAC codec (not here, anyway).
                Log.e(TAG, "Unable to find an appropriate codec for " + MIME_TYPE_AUDIO);
                return false;
            }

            MediaFormat outputAudioFormat =
                    MediaFormat.createAudioFormat(
                            MIME_TYPE_AUDIO, OUTPUT_AUDIO_SAMPLE_RATE_HZ,
                            1);
            outputAudioFormat.setInteger(MediaFormat.KEY_BIT_RATE, OUTPUT_AUDIO_BIT_RATE);
            outputAudioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, OUTPUT_AUDIO_AAC_PROFILE);
            // Create a MediaCodec for the desired codec, then configure it as an encoder with
            // our desired properties. Request a Surface to use for input.
            mAudioEncodeCodec = MediaCodec.createByCodecName(codecInfo.getName());
            mAudioEncodeCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mAudioEncodeCodec.start();


            if (!mOutputFile.exists()) {
                File folder = mOutputFile.getParentFile();
                if (!folder.exists()) {
                    folder.mkdirs();
                }
                mOutputFile.createNewFile();
            }

            mMuxer = new MediaMuxer(mOutputFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            mVideoMixTrack = mMuxer.addTrack(format);
            mAudioMixTrack = mMuxer.addTrack(outputAudioFormat);
            mMuxer.start();

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }


    private Runnable mMixRunnable = new Runnable() {
        @Override
        public void run() {
            boolean result = prepareExtractor();
            if (!result) {
                return;
            }
            result = prepareEncoder();
            if (!result) {
                return;
            }
/*
            {
                int videoInputTrack = MediaMixUtil.getAndSelectVideoTrackIndex(mVideoInputExtractor);
                assertTrue("missing video track in test video", videoInputTrack != -1);
                MediaFormat inputFormat = mVideoInputExtractor.getTrackFormat(videoInputTrack);


                try {
                    mVideoDecodeCodec = MediaMixUtil.createVideoDecoder(inputFormat, mVideoDecodeOutput.getSurface());

                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }
            }

            {
                int audioInputTrack = MediaMixUtil.getAndSelectAudioTrackIndex(mAudioInputExtractor);
                assertTrue("missing audio track in test video", audioInputTrack != -1);
                MediaFormat inputFormat = mAudioInputExtractor.getTrackFormat(audioInputTrack);
                try {
                    mAudioDecodeCodec = MediaMixUtil.createAudioDecoder(inputFormat);

                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }
            }
            MediaMixUtil.doExtractDecodeEditEncodeMux(mVideoInputExtractor, mAudioInputExtractor,
                    mVideoDecodeCodec, mVideoEncodeCodec, mAudioDecodeCodec, mAudioEncodeCodec, mMuxer, mVideoEncodeInput, mVideoDecodeOutput);
*/


            mSurfaceFilter = new MagicSurfaceInputFilter();
            mSurfaceFilter.init();
            mImageFilter = new MagicSketchFilter();
            mImageFilter.init();
            SparseArray<MediaCodec> mDecodeTable = new SparseArray<>(2);



            //deal mVideoDecoder
            int numTracks = mVideoInputExtractor.getTrackCount();
            try {
                for (int i = 0; i < numTracks; ++i) {
                    MediaFormat format = mVideoInputExtractor.getTrackFormat(i);
                    String mime = format.getString(MediaFormat.KEY_MIME);
                    mVideoInputExtractor.selectTrack(i);
                    if (mime.startsWith("video")) {
                        mVideoDecodeCodec = MediaCodec.createDecoderByType(MIME_TYPE_VIDEO);
                        mDecodeTable.put(i, mVideoDecodeCodec);
                        mVideoDecodeIndex = i;
                        if (textureId == OpenGlUtils.NO_TEXTURE) {
                            textureId = OpenGlUtils.getExternalOESTextureID();
                            if (textureId != OpenGlUtils.NO_TEXTURE) {
                                mDecodeSurfaceTexture = new SurfaceTexture(textureId);
                                mDecodeSurfaceTexture.setOnFrameAvailableListener(mDecodeFrameAvaliableListener);
                                Surface surface = new Surface(mDecodeSurfaceTexture);
                                mVideoDecodeCodec.configure(format, surface, null, 0);
                            }
                        }

                        mVideoDecodeCodec.start();
                    } else if (mime.startsWith("audio")) {
                        mAudioEncodeCodec = MediaCodec.createDecoderByType(mime);
                        mAudioDecodeIndex = i;
                        Log.d("TAG", "format : " + format);
                        mDecodeTable.put(i, mAudioEncodeCodec);
                        mAudioEncodeCodec.configure(format, null, null, 0);
                        mAudioEncodeCodec.start();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }


            //deal encoder
            final int TIMEOUT_USEC = 20000;
            MediaCodec.BufferInfo mDecodeInfo = new MediaCodec.BufferInfo();
            MediaCodec.BufferInfo mEncodeInfo = new MediaCodec.BufferInfo();

            TimeStampLogUtil.logTimeStamp("start====");
            while (!isCancel) {

                int trackIndex = mVideoInputExtractor.getSampleTrackIndex();
                if (trackIndex < 0) {
                    for (int i = 0; i < mDecodeTable.size(); i++) {
                        int inputBufIndex = mDecodeTable.valueAt(i).dequeueInputBuffer(TIMEOUT_USEC);
                        mDecodeTable.valueAt(i).queueInputBuffer(inputBufIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    }
//                    mVideoEncodeCodec.signalEndOfInputStream();
//                    mAudioEncodeCodec.signalEndOfInputStream();
                    break;
                }
                MediaCodec codec = mDecodeTable.get(trackIndex);

                int inputBufIndex = codec.dequeueInputBuffer(TIMEOUT_USEC);


                ByteBuffer[] inputBuffers = codec.getInputBuffers();
                ByteBuffer[] outputBuffers = codec.getOutputBuffers();
                ByteBuffer tempBuffer = ByteBuffer.allocate(1024 * 20);
                ByteBuffer buffer = inputBuffers[inputBufIndex];
                int mDecodeOutputIndex = -1;

                boolean mDecodeDone = false;
                int sampleSize = mVideoInputExtractor.readSampleData(buffer, 0);
                mVideoInputExtractor.advance();
                if (sampleSize < 0) {
                    codec.queueInputBuffer(inputBufIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    mVideoInputExtractor.release();
                    Log.i("Extractor end", "" + sampleSize);
                    break;
                } else {
                    long presentationTimeUs = mVideoInputExtractor.getSampleTime();
                    codec.queueInputBuffer(inputBufIndex, 0, sampleSize, presentationTimeUs, 0);

                    Log.i("mVideoInputExtractor", trackIndex + "|" + presentationTimeUs);

                    mDecodeOutputIndex = codec.dequeueOutputBuffer(mDecodeInfo, TIMEOUT_USEC);
                    switch (mDecodeOutputIndex) {
                        case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                            Log.d("DecodeActivity", "INFO_OUTPUT_BUFFERS_CHANGED");
                            outputBuffers = codec.getOutputBuffers();
                            break;
                        case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                            Log.d(TAG, "New format " + codec.getOutputFormat());
                            break;
                        case MediaCodec.INFO_TRY_AGAIN_LATER:
                            Log.d(TAG, "dequeueOutputBuffer timed out!");
                            break;
                        default:

                            if ((mDecodeInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                if (VERBOSE) Log.d(TAG, "video mVideoDecoder: codec config buffer");
                                codec.releaseOutputBuffer(mDecodeOutputIndex, false);
                            }else {
                                ByteBuffer outBuffer = outputBuffers[mDecodeOutputIndex];
                                mDecodeDone = true;
//                                outBuffer.position(mDecodeInfo.offset);
//                                outBuffer.limit(mDecodeInfo.offset + mDecodeInfo.size);
//                                tempBuffer.position(0);
//                                tempBuffer.put(outBuffer);
//                                tempBuffer.position(0);
//                                tempBuffer.limit(mDecodeInfo.size);
                                if(trackIndex == mVideoDecodeIndex){
                                    draw(mDecodeInfo.presentationTimeUs);
                                }
                                Log.v(TAG, "We can't use this buffer but render it due to the API limit, " + buffer);
                                Log.d(TAG, "Decode timestamp ====== " + trackIndex + "|" + mDecodeInfo.presentationTimeUs);
//                            mDecodeInfo.presentationTimeUs = presentationTimeUs;
                                codec.releaseOutputBuffer(mDecodeOutputIndex, true);
                            }
                    }


                }
                TimeStampLogUtil.logTimeStamp("extract====");
                if (DECODE) {
                    continue;
                }



                ByteBuffer[] videoEncoderOutputBuffers = mVideoEncodeCodec.getOutputBuffers();
                ByteBuffer[] audioEncoderOutputBuffers = mAudioEncodeCodec.getOutputBuffers();
                ByteBuffer[] audioEncoderInputBuffers = mAudioEncodeCodec.getInputBuffers();
                MediaFormat encoderOutputVideoFormat = null;
                MediaFormat encoderOutputAudioFormat = null;
                if (mDecodeDone) {
                    //处理视频编码
                    if (trackIndex == mVideoDecodeIndex) {
//                        draw(mDecodeInfo.presentationTimeUs);


                        int encoderOutputBufferIndex = mVideoEncodeCodec.dequeueOutputBuffer(
                                mEncodeInfo, TIMEOUT_USEC);
                        if (encoderOutputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                            if (VERBOSE) Log.d(TAG, "no video encoder output buffer");
                        }
                        if (encoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                            if (VERBOSE) Log.d(TAG, "video encoder: output buffers changed");
                            videoEncoderOutputBuffers = mVideoEncodeCodec.getOutputBuffers();
                        }
                        if (encoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            if (VERBOSE) Log.d(TAG, "video encoder: output format changed");
                            if (mVideoMixTrack >= 0) {
//                                Assert.fail("video encoder changed its output format again?");
                            }
                            encoderOutputVideoFormat = mVideoEncodeCodec.getOutputFormat();
                        }
                        TimeStampLogUtil.logTimeStamp("draw===="+encoderOutputBufferIndex);
                        if (encoderOutputBufferIndex < 0) {
                            continue;
                        }
                        ByteBuffer encoderOutputBuffer =
                                videoEncoderOutputBuffers[encoderOutputBufferIndex];
                        if ((mEncodeInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG)
                                != 0) {
                            if (VERBOSE) Log.d(TAG, "video encoder: codec config buffer");
                            // Simply ignore codec config buffers.
                            mVideoEncodeCodec.releaseOutputBuffer(encoderOutputBufferIndex, false);
//                            break;
                        }
                        if (mEncodeInfo.size != 0) {
                            mMuxer.writeSampleData(
                                    mVideoMixTrack, encoderOutputBuffer, mEncodeInfo);
                        }
                        if ((mEncodeInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                != 0) {
                            if (VERBOSE) Log.d(TAG, "video encoder: EOS");
                        }
                        TimeStampLogUtil.logTimeStamp("video muxer====");
                    } else if (trackIndex == mAudioDecodeIndex) {
//                        boolean mEncodeDone = false;
//                        int encoderInputBufferIndex = -1;
////                        while (!isCancel) {
//                            encoderInputBufferIndex = mAudioEncodeCodec.dequeueInputBuffer(TIMEOUT_USEC);
//                            if (encoderInputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
//                                if (VERBOSE) Log.d(TAG, "no audio encoder input buffer");
////                                continue;
//                            }
//                            if (VERBOSE) {
//                                Log.d(TAG, "audio encoder: returned input buffer: " + encoderInputBufferIndex);
//                            }
////                            if (encoderInputBufferIndex >= 0) {
////                                break;
////                            }
////                        }
//                        ByteBuffer encoderInputBuffer = audioEncoderInputBuffers[encoderInputBufferIndex];
//                        int size = mDecodeInfo.size;
//                        long presentationTime = mDecodeInfo.presentationTimeUs;
//                        if (VERBOSE) {
//                            Log.d(TAG, "audio mVideoDecoder: pending buffer of size " + size);
//                            Log.d(TAG, "audio mVideoDecoder: pending buffer for time ====== " + mDecodeInfo.presentationTimeUs);
//                        }
//                        if (size >= 0) {
//                            encoderInputBuffer.position(0);
//                            encoderInputBuffer.put(tempBuffer);
//                            mAudioEncodeCodec.queueInputBuffer(encoderInputBufferIndex, 0, size, presentationTime, mDecodeInfo.flags);
//                            mEncodeDone = true;
//
//                        }
//
//                        if (mEncodeDone) {
//                            int encoderOutputBufferIndex = -1;
//                            while (!isCancel) {
//                                encoderOutputBufferIndex = mAudioEncodeCodec.dequeueOutputBuffer(
//                                        mEncodeInfo, TIMEOUT_USEC);
//                                if (encoderOutputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
//                                    if (VERBOSE) Log.d(TAG, "no audio encoder output buffer");
//                                    continue;
//                                }
//                                if (encoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
//                                    if (VERBOSE)
//                                        Log.d(TAG, "audio encoder: output buffers changed");
//                                    audioEncoderOutputBuffers = mAudioEncodeCodec.getOutputBuffers();
//                                    continue;
//                                }
//                                if (encoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
//                                    if (VERBOSE) Log.d(TAG, "audio encoder: output format changed");
////                                    if (outputAudioTrack >= 0) {
////                                    fail("audio encoder changed its output format again?");
////                                    }
//                                    encoderOutputAudioFormat = mAudioEncodeCodec.getOutputFormat();
//                                    continue;
//                                }
//                                if (encoderOutputBufferIndex >= 0) {
//                                    break;
//                                }
//                            }
//
//                            ByteBuffer encoderOutputBuffer =
//                                    audioEncoderOutputBuffers[encoderOutputBufferIndex];
//                            if ((mEncodeInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG)
//                                    != 0) {
//                                if (VERBOSE) Log.d(TAG, "audio encoder: codec config buffer");
//                                // Simply ignore codec config buffers.
//                                mAudioEncodeCodec.releaseOutputBuffer(encoderOutputBufferIndex, false);
//                                break;
//                            }
//                            if (VERBOSE) {
//                                Log.d(TAG, "audio encoder: returned buffer for time "
//                                        + mEncodeInfo.presentationTimeUs);
//                            }
//                            if (mEncodeInfo.size != 0) {
//                                Log.d(TAG, "audio encoder time=:  " + trackIndex+"|"+mEncodeInfo.presentationTimeUs);
//                                mMuxer.writeSampleData(
//                                        mAudioMixTrack, encoderOutputBuffer, mEncodeInfo);
//                            }
//                            if ((mEncodeInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM)
//                                    != 0) {
//                                if (VERBOSE) Log.d(TAG, "audio encoder: EOS");
////                                    audioEncoderDone = true;
//                            }
//                            mAudioEncodeCodec.releaseOutputBuffer(encoderOutputBufferIndex, false);
//                        }
//
                    }

                }
            }
            if (isCancel) {

            }
            release();
        }


    };

    private SurfaceTexture.OnFrameAvailableListener mDecodeFrameAvaliableListener = new SurfaceTexture.OnFrameAvailableListener() {
        public void onFrameAvailable(SurfaceTexture texture) {
//            mDecodeSurfaceTexture.updateTexImage();
            Log.i("onFrameAvailable", "" + texture.getTimestamp());
//            draw();
        }
    };

    float[] mtx = new float[16];

    private void draw(long presentationTimeUs) {
        mDecodeSurfaceTexture.updateTexImage();
        mDecodeInputSurface.makeCurrent();

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        mDecodeSurfaceTexture.getTransformMatrix(mtx);
        mSurfaceFilter.setTextureTransformMatrix(mtx);
        if (mImageFilter == null) {
            mSurfaceFilter.onDrawFrame(textureId, mGLCubeBuffer, mGLTextureBuffer);
        } else {
            int id = mSurfaceFilter.onDrawToTexture(textureId);
            mImageFilter.onDrawFrame(id, mGLCubeBuffer, mGLTextureBuffer);
        }
        mDecodeInputSurface.setPresentationTime(presentationTimeUs * 1000);
        mDecodeInputSurface.swapBuffers();

        GlUtil.checkGlError("draw done");

    }


}
