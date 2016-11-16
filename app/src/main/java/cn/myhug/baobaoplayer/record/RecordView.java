package cn.myhug.baobaoplayer.record;

import android.app.Activity;
import android.content.Context;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;

import cn.myhug.baobaoplayer.media.AudioRecordSource;


/**
 * Created by zhengxin on 16/2/21.
 */
public class RecordView extends FrameLayout implements SurfaceHolder.Callback {

    private static final String TAG = RecordView.class.getName();

    public static final int BROADCAST_ERROR_OPEN_CAMERA_FAIL = 701;
    public static final int BROADCAST_ERROR_SURFACE_NULL = 702;
    public static final int BROADCAST_ERROR_SURFACE_INVALID_START_PREVIEW = 703;
    public static final int BROADCAST_ERROR_SURFACE_INVALID_FIRST = 704;
    public static final int BROADCAST_ERROR_SURFACE_INVALID_SURFACE_AVAILABLE = 706;
    public static final int BROADCAST_ERROR_SURFACE_INVALID_CREATE = 707;
    public static final int BROADCAST_ERROR_UNKOWN = 708;

    // The holder for our SurfaceView.  The Surface can outlive the Activity (e.g. when
    // the screen is turned off and back on with the power button).
    //
    // This becomes non-null after the surfaceCreated() callback is called, and gets set
    // to null when surfaceDestroyed() is called.
    private static SurfaceHolder sSurfaceHolder;

    // Thread that handles rendering and controls the camera.  Started in onResume(),
    // stopped in onPause().
    private RenderThread mRenderThread;

    // Receives messages from renderer thread.
    private MainHandler mHandler = new MainHandler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MainHandler.MSG_SEND_PREVIEW_START:
                    if (mPreviewStartRunnable != null) {
                        mPreviewStartRunnable.run();
                    }
                    break;
                case MainHandler.MSG_SEND_OPEN_CAMERA_FAIL:
                    if (mErrorCallback != null) {
                        mErrorCallback.onError(BROADCAST_ERROR_OPEN_CAMERA_FAIL);
                    }
                    break;
                case MainHandler.MSG_SEND_OPEN_INVALID_SURFACE_AVAILABLE:
                    if (mErrorCallback != null) {
                        mErrorCallback.onError(BROADCAST_ERROR_SURFACE_INVALID_SURFACE_AVAILABLE);
                    }
                    break;
                case MainHandler.MSG_SEND_SURFACE_INVALID_CREATE:
                    if (mErrorCallback != null) {
                        mErrorCallback.onError(BROADCAST_ERROR_SURFACE_INVALID_CREATE);
                    }
                    break;
                case MainHandler.MSG_SEND_UNKNOWN_FAIL:
                    if (mErrorCallback != null) {
                        mErrorCallback.onError(BROADCAST_ERROR_UNKOWN);
                    }
                    break;

            }
        }
    };

    private SurfaceView mSurfaceView = null;


    private H264Encoder mEncoder = null;
    private Runnable mPreviewStartRunnable = null;
    private IBroadCastErrorCallback mErrorCallback = null;


    public static int mScreenWidth = 720;
    public static int mScreenHeight = 1280;
    public static int mScreenScaleWidth = 540;
    public static int mScreenScaleHeight = 960;
    public static int mScreenRecordWidth = mScreenScaleWidth;
    public static int mScreenRecordHeight = mScreenScaleHeight;

    public static float mStatusHeight = 0;
    public static float mVButtonHeight = 0;
    public boolean mRequestPreview = false;
    private boolean mRequestStart = false;

    private boolean isBroadCast = false;
    private Mp4Muxer mMuxer = new Mp4Muxer();
    private AudioRecordSource mAudioSource = null;
    private long mTimeStamp = 0;


    public void setPreviewStartCallback(Runnable runnable) {
        mPreviewStartRunnable = runnable;
    }


    private boolean isZOrderMediaOverlay = false;

    public RecordView(Context context) {
        super(context);
        init();
    }

    public RecordView(Context context, boolean isZOverlay) {
        super(context);
        isZOrderMediaOverlay = isZOverlay;
        init();
    }

    public RecordView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RecordView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {

        if (mSurfaceView == null) {
            mSurfaceView = new SurfaceView(getContext());
            mSurfaceView.setZOrderMediaOverlay(isZOrderMediaOverlay);
            addView(mSurfaceView);
        }
        SurfaceHolder sh = mSurfaceView.getHolder();
        sh.addCallback(this);


    }

    public void setBroadCastErrorCallback(IBroadCastErrorCallback callback) {
        mErrorCallback = callback;
    }

    // 现在所有的调用都在主线程中，所以说加锁的作用理论上没有作用。
    private void initEncoder() {
        if (mEncoder == null) {
            if (null == mEncoder) {
                H264Encoder encoder = new H264Encoder();
                H264EncodeConfig encodeConfig = new H264EncodeConfig();

                int errcode = encoder.configure(encodeConfig);

                if (0 == errcode) {
                    encoder.setMuxer(mMuxer);
                    encoder.start();

                    mAudioSource = new AudioRecordSource();
                    mAudioSource.prepare();
                    mAudioSource.setMuxer(mMuxer);
                    mAudioSource.startRecord();
                    mEncoder = encoder;
                    TimeStampGenerator.sharedInstance().reset();

                } else {
                    if (mErrorCallback != null) {
                        mErrorCallback.onError(errcode);
                    }
                }
            }
        }

    }


    public void startPreview() {
        if (isBroadCast) {
            return;
        }
        Log.d(TAG, "onResume BEGIN");
        if (mRenderThread != null) {
            return;
        }

        mRenderThread = new RenderThread(mHandler);
        mRenderThread.setActivity((Activity) getContext());
        mRenderThread.setName("TexFromCam Render");
        mRenderThread.start();
        mRenderThread.waitUntilReady();

        RenderHandler rh = mRenderThread.getHandler();

        if (sSurfaceHolder != null && sSurfaceHolder.getSurface() != null && sSurfaceHolder.getSurface().isValid()) {
            Log.d(TAG, "Sending previous surface");
            rh.sendSurfaceAvailable(sSurfaceHolder, false);
            if (mEncoder == null) {
                initEncoder();

                if (null != mEncoder) {
                    Surface surface = mEncoder.getEncoderSurface();
                    if (surface != null) {
                        rh.sendEncoderAvailable(surface);
                    } else {
                        if (mErrorCallback != null) {
                            mErrorCallback.onError(BROADCAST_ERROR_SURFACE_NULL);
                        }
                        return;
                    }
                } else {
                    if (mErrorCallback != null) {
                        mErrorCallback.onError(BROADCAST_ERROR_SURFACE_NULL);
                    }
                    return;
                }


            }
        } else {
            if (mErrorCallback != null) {
                mErrorCallback.onError(BROADCAST_ERROR_SURFACE_INVALID_START_PREVIEW);
            }
            Log.d(TAG, "No previous surface");
        }
        Log.d(TAG, "onResume END");
        isBroadCast = true;

        if (mRequestStart) {
            restart();
        }

    }

    public void restart() {
        mRequestStart = false;

    }

    public void stop() {
        Log.d(TAG, "onPause BEGIN");

        if (mEncoder != null) {
            mEncoder.stop();
            mEncoder = null;
        }


        if (mRenderThread != null) {
            RenderHandler rh = mRenderThread.getHandler();
            rh.sendShutdown();
            try {
                mRenderThread.join();
            } catch (InterruptedException ie) {
                // not expected
                throw new RuntimeException("join was interrupted", ie);
            }
        }
        mRenderThread = null;
        Log.d(TAG, "onPause END");

    }


    @Override   // SurfaceHolder.Callback
    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(TAG, "surfaceCreated holder=" + holder + " (static=" + sSurfaceHolder + ")");
        if (sSurfaceHolder != null) {
            throw new RuntimeException("sSurfaceHolder is already set");
        }

        sSurfaceHolder = holder;

        if (mRenderThread != null) {
            // Normal case -- render thread is running, tell it about the new surface.
            RenderHandler rh = mRenderThread.getHandler();
            if (holder != null && holder.getSurface() != null && holder.getSurface().isValid()) {
                rh.sendSurfaceAvailable(holder, true);
            } else {
                if (mErrorCallback != null) {
                    mErrorCallback.onError(BROADCAST_ERROR_SURFACE_INVALID_FIRST);
                }
            }
        } else {
            // Sometimes see this on 4.4.x N5: power off, power on, unlock, with device in
            // landscape and a lock screen that requires portrait.  The surface-created
            // message is showing up after onPause().
            //
            // Chances are good that the surface will be destroyed before the activity is
            // unpaused, but we track it anyway.  If the activity is un-paused and we start
            // the RenderThread, the SurfaceHolder will be passed in right after the thread
            // is created.
            Log.d(TAG, "render thread not running");
        }
        if (mRequestPreview) {
            mRequestPreview = false;
            postDelayed(new Runnable() {
                @Override
                public void run() {
                    startPreview();
                }
            }, 100);
        }
    }

    @Override   // SurfaceHolder.Callback
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.d(TAG, "surfaceChanged fmt=" + format + " size=" + width + "x" + height +
                " holder=" + holder);

        if (mRenderThread != null) {
            RenderHandler rh = mRenderThread.getHandler();
            rh.sendSurfaceChanged(format, width, height);
        } else {
            Log.d(TAG, "Ignoring surfaceChanged");
            return;
        }
    }

    @Override   // SurfaceHolder.Callback
    public void surfaceDestroyed(SurfaceHolder holder) {
        // In theory we should tell the RenderThread that the surface has been destroyed.
        if (mRenderThread != null) {
            RenderHandler rh = mRenderThread.getHandler();
            rh.sendSurfaceDestroyed();
        }
        Log.d(TAG, "surfaceDestroyed holder=" + holder);
        sSurfaceHolder = null;
    }


    public void switchCamera() {
        if (mRenderThread != null) {
            RenderHandler rh = mRenderThread.getHandler();
            rh.switchCamera();
        }
    }


    public void resumeRecord() {
        TimeStampGenerator.sharedInstance().start();
        if (mRenderThread != null) {
            mRenderThread.resumeRecord();
        }
        if (mAudioSource != null) {
            mAudioSource.resumeRecord();
        }

    }

    public void pauseRecord() {
        TimeStampGenerator.sharedInstance().pause();
        if (mRenderThread != null) {
            mRenderThread.pauseRecord();
        }
        if (mAudioSource != null) {
            mAudioSource.pauseRecord();
        }
    }

    public void stopRecord() {
        TimeStampGenerator.sharedInstance().stop();
        pauseRecord();
        postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mEncoder != null) {
                    mEncoder.stop();
                }
                if (mAudioSource != null) {
                    mAudioSource.stop();
                }
            }
        }, 20);

    }

    public void resetRecord() {
        TimeStampGenerator.sharedInstance().reset();
        if (mEncoder != null) {
            mEncoder.reset();
        }
        mMuxer.reset();
        if (mAudioSource != null) {
            mAudioSource.reset();
        }
    }

    public void setFlashMode(int flashMode) {
        mRenderThread.switchFlash(flashMode);
    }
}
