package cn.myhug.baobaoplayer.record;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Build;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.HashMap;

import cn.myhug.baobaoplayer.gles.Drawable2d;
import cn.myhug.baobaoplayer.gles.EglCore;
import cn.myhug.baobaoplayer.gles.EglSurfaceBase;
import cn.myhug.baobaoplayer.gles.FullFrameRect;
import cn.myhug.baobaoplayer.gles.GlUtil;
import cn.myhug.baobaoplayer.gles.OffscreenSurface;
import cn.myhug.baobaoplayer.gles.ScaledDrawable2d;
import cn.myhug.baobaoplayer.gles.Sprite2d;
import cn.myhug.baobaoplayer.gles.Texture2dProgram;
import cn.myhug.baobaoplayer.gles.WindowSurface;
import cn.myhug.baobaoplayer.media.Mp4Config;


/**
 * Created by zhengxin on 16/2/21.
 */
public class RenderThread extends Thread implements
        SurfaceTexture.OnFrameAvailableListener {
    private static final int DEFAULT_ZOOM_PERCENT = 0;      // 0-100
    private static final int DEFAULT_ROTATE = 0;    // 0-100

    // Requested values; actual may differ.
    private static final int REQ_CAMERA_WIDTH = Mp4Config.VIDEO_WIDTH;
    private static final int REQ_CAMERA_HEIGHT = Mp4Config.VIDEO_HEIGHT;

    private static final int REQ_CAMERA_FPS = 30;
    private static final int VIDEO_INV = 1000 / 24;
    private int mYOffset = 0;
    private boolean hasShow = false;
    private int mBeauty = 0;

    private static final String TAG = "RenderThread";
    // Object must be created on render thread to get correct Looper, but is used from
    // UI thread, so we need to declare it volatile to ensure the UI thread sees a fully
    // constructed object.
    private volatile RenderHandler mHandler;

    // Used to wait for the thread to start.
    private Object mStartLock = new Object();
    private boolean mReady = false;

    private MainHandler mMainHandler;

    private Camera mCamera;
    private int mCameraPreviewWidth, mCameraPreviewHeight;

    private EglCore mEglCore;
    //        private EglCore mCodecEglCore;
    private WindowSurface mWindowSurface;
    private WindowSurface mCodecWindowSurface;
    private int mWindowSurfaceWidth;
    private int mWindowSurfaceHeight;

    // Receives the output from the camera preview.
    private SurfaceTexture mCameraTexture;

    // Orthographic projection matrix.
    private float[] mDisplayProjectionMatrix = new float[16];
    private float[] mEncodeProjectionMatrix = new float[16];

    private Texture2dProgram mTexProgram;
    private final ScaledDrawable2d mRectDrawable =
            new ScaledDrawable2d(Drawable2d.Prefab.RECTANGLE);
    private final Sprite2d mRect = new Sprite2d(mRectDrawable);

    private int mZoomPercent = DEFAULT_ZOOM_PERCENT;
    private int mRotate = DEFAULT_ROTATE;
    private float mPosX, mPosY;

    private int mFps = 0;
    private float mFpsLowpass = 24;
    private long mFpsStart = System.currentTimeMillis();
    private int mFpsCount = 0;
    private int mFpsAllCount = 0;
    private long mFpsAllStart = 0;

    private WeakReference<Activity> mActivityReference = null;
    private int mCameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
    //    private int mCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
//    PGSkinPrettifyEngine m_pPGSkinPrettifyEngine;
    private int mEncodeWidth = 0;
    private int mEncodeHeight = 0;
    private boolean mUse360 = false;
    private long mStartTime = System.currentTimeMillis();
    private static HashMap<String, Integer> mBeautyLevel = new HashMap<>(20);

    // Used for off-screen rendering.
    private int mOffscreenTexture;
    private int mFramebuffer;
    private int mDepthBuffer;
    private FullFrameRect mFullScreen;
    private final float[] mIdentityMatrix;
    private volatile boolean mIsRecording = false;

    // openh264 encode


    /**
     * Constructor.  Pass in the MainHandler, which allows us to send stuff back to the
     * Activity.
     */
    public RenderThread(MainHandler handler) {
        mMainHandler = handler;
        mIdentityMatrix = new float[16];
        Matrix.setIdentityM(mIdentityMatrix, 0);
    }

    public void setActivity(Activity activity) {
        mActivityReference = new WeakReference<Activity>(activity);
    }

    public void resumeRecord() {
        mIsRecording = true;
    }

    public void pauseRecord() {
        mIsRecording = false;
    }


    /**
     * Thread entry point.
     */
    @Override
    public void run() {
        Looper.prepare();
        try {
            mCameraId = 0;
            // We need to create the Handler before reporting ready.
            mHandler = new RenderHandler(this);
            synchronized (mStartLock) {
                mReady = true;
                mStartLock.notify();    // signal waitUntilReady()
            }

            // Prepare EGL and open the camera before we start handling messages.
            mEglCore = new EglCore(null, EglCore.FLAG_RECORDABLE);
            try {
                openCamera(REQ_CAMERA_WIDTH, REQ_CAMERA_HEIGHT, REQ_CAMERA_FPS);
            } catch (RuntimeException e) {
                if (mMainHandler != null) {
                    mMainHandler.sendEmptyMessage(MainHandler.MSG_SEND_OPEN_CAMERA_FAIL);
                }
            }


            Looper.loop();
        } catch (Exception e) {
            if (mMainHandler != null) {
                mMainHandler.sendEmptyMessage(MainHandler.MSG_SEND_UNKNOWN_FAIL);
            }
            e.printStackTrace();
        } finally {
            Log.d(TAG, "looper quit");
            releaseCamera();
            releaseGl();
            mEglCore.release();
            synchronized (mStartLock) {
                mReady = false;
            }
        }


    }

    /**
     * Waits until the render thread is ready to receive messages.
     * <p/>
     * Call from the UI thread.
     */
    public void waitUntilReady() {
        synchronized (mStartLock) {
            while (!mReady) {
                try {
                    mStartLock.wait();
                } catch (InterruptedException ie) { /* not expected */ }
            }
        }
    }

    /**
     * Shuts everything down.
     */
    public void shutdown() {


        mFpsAllStart = 0;
        mFpsAllCount = 0;
        Looper.myLooper().quit();
    }

    /**
     * Returns the render thread's Handler.  This may be called from any thread.
     */
    public RenderHandler getHandler() {
        return mHandler;
    }

    /**
     * Handles the surface-created callback from SurfaceView.  Prepares GLES and the Surface.
     */
    public void surfaceAvailable(SurfaceHolder holder, boolean newSurface) {
        Surface surface = holder.getSurface();
        if (mWindowSurface == null) {
            try {
                mWindowSurface = new WindowSurface(mEglCore, surface, false);
                mWindowSurface.makeCurrent();
            } catch (IllegalArgumentException e) {
                if (mMainHandler != null) {
                    mMainHandler.sendEmptyMessage(MainHandler.MSG_SEND_OPEN_INVALID_SURFACE_AVAILABLE);
                }
            } catch (Exception e) {
                if (mMainHandler != null) {
                    mMainHandler.sendEmptyMessage(MainHandler.MSG_SEND_SURFACE_INVALID_CREATE);
                }
            }
        }
        if (mFullScreen == null) {
            mFullScreen = new FullFrameRect(
                    new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_2D));
        }

        if (mCameraTexture == null) {
//            mTexProgram = new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT);
            mTexProgram = new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT_MAGIC_2);
            if (mCameraPreviewWidth > 0) {
                mTexProgram.setInputWH(mCameraPreviewWidth, mCameraPreviewHeight);
            }
            int textureId = mTexProgram.createTextureObject();
            mCameraTexture = new SurfaceTexture(textureId);
            mRect.setTexture(textureId);
//            mTexProgram.setBeautyLevel(mBeauty);
            if (!newSurface) {
                // This Surface was established on a previous run, so no surfaceChanged()
                // message is forthcoming.  Finish the surface setup now.
                //
                // We could also just call this unconditionally, and perhaps do an unnecessary
                // bit of reallocating if a surface-changed message arrives.
                mWindowSurfaceWidth = mWindowSurface.getWidth();
                mWindowSurfaceHeight = mWindowSurface.getHeight();
                finishSurfaceSetup();
            }
            mCameraTexture.setOnFrameAvailableListener(this);
        }

    }

    // @width  MediaFormat width
    // @height MediaFormat height
    public void setEncoderInfo(int width, int height) {
        mEncodeWidth = width;
        mEncodeHeight = height;
    }


    public void setEncoderSurface(Surface surface) {
        try {
            mCodecWindowSurface = new WindowSurface(mEglCore, surface, true);
        } catch (IllegalArgumentException e) {
            if (mMainHandler != null) {
                mMainHandler.sendEmptyMessage(MainHandler.MSG_SEND_OPEN_INVALID_SURFACE_AVAILABLE);
            }
        } catch (Exception e) {
            if (mMainHandler != null) {
                mMainHandler.sendEmptyMessage(MainHandler.MSG_SEND_SURFACE_INVALID_CREATE);
            }
        }
    }

    /**
     * Releases most of the GL resources we currently hold (anything allocated by
     * surfaceAvailable()).
     * <p/>
     * Does not release EglCore.
     */
    private void releaseGl() {
        GlUtil.checkGlError("releaseGl start");
        int[] values = new int[1];

        if (mWindowSurface != null) {
            mWindowSurface.release();
            mWindowSurface = null;
        }
        if (mCodecWindowSurface != null) {
            mCodecWindowSurface.release();
            mCodecWindowSurface = null;
        }


        if (mCameraTexture != null) {
            mCameraTexture.release();
            mCameraTexture = null;
        }
        if (mTexProgram != null) {
            mTexProgram.release();
            mTexProgram = null;
        }

        if (mOffscreenTexture > 0) {
            values[0] = mOffscreenTexture;
            GLES20.glDeleteTextures(1, values, 0);
            mOffscreenTexture = -1;
        }
        if (mFramebuffer > 0) {
            values[0] = mFramebuffer;
            GLES20.glDeleteFramebuffers(1, values, 0);
            mFramebuffer = -1;
        }
        if (mDepthBuffer > 0) {
            values[0] = mDepthBuffer;
            GLES20.glDeleteRenderbuffers(1, values, 0);
            mDepthBuffer = -1;
        }
        if (mFullScreen != null) {
            mFullScreen.release(true); // TODO: should be "true"; must ensure mEglCore current
            mFullScreen = null;
        }

        GlUtil.checkGlError("releaseGl done");
        mEglCore.makeNothingCurrent();
    }


    /**
     * Handles the surfaceChanged message.
     * <p/>
     * We always receive surfaceChanged() after surfaceCreated(), but surfaceAvailable()
     * could also be called with a Surface created on a previous run.  So this may not
     * be called.
     */
    public void surfaceChanged(int width, int height) {
        Log.d(TAG, "RenderThread surfaceChanged " + width + " x " + height);
        Log.d(TAG, "RenderThread mWindowSurface " + mWindowSurfaceWidth + " x " + mWindowSurfaceHeight);
        if (mWindowSurfaceHeight > height || mWindowSurfaceWidth > width) {
            mYOffset = mWindowSurfaceHeight - height;

            return;
        }
        mYOffset = 0;
        mWindowSurfaceWidth = width;
        mWindowSurfaceHeight = height;
        finishSurfaceSetup();
    }

    /**
     * Handles the surfaceDestroyed message.
     */
    public void surfaceDestroyed() {
        // In practice this never appears to be called -- the activity is always paused
        // before the surface is destroyed.  In theory it could be called though.
        Log.d(TAG, "RenderThread surfaceDestroyed");
//        releaseGl();
        releaseWindowSurface();
    }

    /**
     * Sets up anything that depends on the window size.
     * <p/>
     * Open the camera (to set mCameraAspectRatio) before calling here.
     */
    public void finishSurfaceSetup() {
        int width = mWindowSurfaceWidth;
        int height = mWindowSurfaceHeight;
        Log.d(TAG, "finishSurfaceSetup size=" + width + "x" + height +
                " Camera=" + mCameraPreviewWidth + "x" + mCameraPreviewHeight);

        // Use full window.
        GLES20.glViewport(0, 0, width, height);

        Matrix.orthoM(mDisplayProjectionMatrix, 0, 0, mCameraPreviewHeight, 0, mCameraPreviewWidth, -1, 1);


        // Default position is center of screen.


        mPosX = width / 2.0f;
        mPosY = height / 2.0f - mYOffset;

        mPosX = mCameraPreviewHeight / 2.0f;
        mPosY = mCameraPreviewWidth / 2.0f - mYOffset;

        updateGeometry();

        // Ready to go, start the camera.
        Log.d(TAG, "starting camera preview");
        if (mCamera != null) {
            try {
                mCamera.setPreviewTexture(mCameraTexture);
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
            mCamera.startPreview();
            if (mCameraId == Camera.CameraInfo.CAMERA_FACING_BACK) {
//            mCamera.autoFocus(mAutoFocusCallback);
            }
        }
        prepareFramebuffer(mCameraPreviewHeight, mCameraPreviewWidth);

    }

    private Camera.AutoFocusCallback mAutoFocusCallback = new Camera.AutoFocusCallback() {
        @Override
        public void onAutoFocus(boolean success, Camera camera) {

        }
    };

    /**
     * Updates the geometry of mRect, based on the size of the window and the current
     * values set by the UI.
     */
    private int newWindowSurfaceWidth = 0;
    private int newWindowSurfaceHeight = 0;
    private int newEncodeSurfaceWidth = 0;
    private int newEncodeSurfaceHeight = 0;

    private void updateGeometry() {
        float scaleDivEncodeWidth = 0f;
        float scaleDivEncodeHeight = 0f;
        float scaleDivPrevWidth = 0f;
        float scaleDivPrevHeight = 0f;
        int rotAngle = mRotate;

        if (mEncodeHeight == 0 && mEncodeWidth == 0) {
            mEncodeWidth = 368;
            mEncodeHeight = 640;
        }

        if (rotAngle % 180 > 80) {
            scaleDivEncodeWidth = (float) mEncodeHeight / mCameraPreviewWidth;
            scaleDivEncodeHeight = (float) mEncodeWidth / mCameraPreviewHeight;
            scaleDivPrevWidth = (float) mWindowSurfaceHeight / mCameraPreviewWidth;
            scaleDivPrevHeight = (float) mWindowSurfaceWidth / mCameraPreviewHeight;
        } else {
            scaleDivEncodeWidth = (float) mEncodeWidth / mCameraPreviewWidth;
            scaleDivEncodeHeight = (float) mEncodeHeight / mCameraPreviewHeight;
            scaleDivPrevWidth = (float) mWindowSurfaceWidth / mCameraPreviewWidth;
            scaleDivPrevHeight = (float) mWindowSurfaceHeight / mCameraPreviewHeight;
        }


        float scalePreview = Math.max(scaleDivPrevWidth, scaleDivPrevHeight);
        newWindowSurfaceWidth = (int) (scalePreview * mCameraPreviewWidth);
        newWindowSurfaceHeight = (int) (scalePreview * mCameraPreviewHeight);

        float encodePreview = Math.max(scaleDivEncodeWidth, scaleDivEncodeHeight);
        newEncodeSurfaceWidth = (int) (encodePreview * mCameraPreviewWidth);
        newEncodeSurfaceHeight = (int) (encodePreview * mCameraPreviewHeight);

        int newWidth = mCameraPreviewWidth;
        int newHeight = mCameraPreviewHeight;
        float zoomFactor = 1.0f - (mZoomPercent / 100.0f);

        if (mCameraId == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            mRect.setScale(-newWidth, newHeight);
        } else {
            mRect.setScale(newWidth, newHeight);
        }
        if (mRotate % 180 > 80) {
            mPosY = newWidth / 2;
            mPosX = newHeight / 2;
            int temp = newWindowSurfaceWidth;
            newWindowSurfaceWidth = newWindowSurfaceHeight;
            newWindowSurfaceHeight = temp;
            temp = newEncodeSurfaceWidth;
            newEncodeSurfaceWidth = newEncodeSurfaceHeight;
            newEncodeSurfaceHeight = temp;
        } else {
            mPosY = newHeight / 2;
            mPosX = newWidth / 2;
        }
        mRect.setPosition(mPosX, mPosY);
        mRect.setRotation(rotAngle);
        mRectDrawable.setScale(zoomFactor);

        mMainHandler.sendRectSize(newWidth, newHeight);

        mMainHandler.sendZoomArea(Math.round(mCameraPreviewWidth * zoomFactor),
                Math.round(mCameraPreviewHeight * zoomFactor));
        mMainHandler.sendRotateDeg(rotAngle);

    }

    @Override   // SurfaceTexture.OnFrameAvailableListener; runs on arbitrary thread
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        mHandler.sendFrameAvailable();
    }

    /**
     * Handles incoming frame of data from the camera.
     */
    public void frameAvailable() {
        if (!hasShow) {
            hasShow = true;
            mMainHandler.sendEmptyMessage(MainHandler.MSG_SEND_PREVIEW_START);
        }
        try {
            mCameraTexture.updateTexImage();
            draw();
        } catch (Exception e) {

        }
    }


    private void doDrawEncodeSurface(EglSurfaceBase window) {
        if (window != null && mIsRecording) {
            if (mFpsAllStart == 0) {
                mFpsAllStart = System.currentTimeMillis();
            }
            long now = System.currentTimeMillis();
            long second_offset = now % 1000;
            if (now / 1000 != mFpsStart / 1000) {
                mFps = mFpsCount;
                mFpsStart = now;
                Log.i("fps=", "" + mFpsCount);
                mFpsCount = 0;
                mFpsLowpass = mFpsLowpass * 0.8f + mFps * 0.2f;
                if (mFpsLowpass < 13) {
                    //20s之后,策略生效,防止大礼物出现时候美颜效果变差
                    if (now - mStartTime > 30 * 1000) {
                        return;
                    }
                    if (mBeauty == 0) {
                        return;
                    }

                }
            }

            if (mFpsCount * VIDEO_INV < second_offset) {
                mFpsCount++;
                mFpsAllCount++;
                try {
                    window.makeCurrent();
                    GlUtil.checkGlError("draw start");
                    GLES20.glClearColor(0f, 0f, 0f, 1.0f);
                    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
                    GLES20.glViewport(0, 0, newEncodeSurfaceWidth, newEncodeSurfaceHeight);
                    mFullScreen.drawFrame(mOffscreenTexture, mIdentityMatrix);
                    window.setPresentationTime(TimeStampGenerator.sharedInstance().getVideoStamp());
                    window.swapBuffers();

                } catch (Exception e) {
                    try {
                        if (window instanceof WindowSurface) {
                            WindowSurface surface = (WindowSurface) window;
                            surface.release();
                            mCodecWindowSurface = null;
                        } else if (window instanceof OffscreenSurface) {
                            OffscreenSurface surface = (OffscreenSurface) window;
                            surface.release();
                        }
                    } catch (Exception ex) {

                    }
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Draws the scene and submits the buffer.
     */
    public void draw() {
        if (mWindowSurface == null) {
            return;
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFramebuffer);
        GlUtil.checkGlError("glBindFramebuffer");

        GlUtil.checkGlError("draw start");
        GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glViewport(0, 0, mCameraPreviewHeight, mCameraPreviewWidth);

        mRect.setPosition(mPosX, mPosY);
        mRect.draw(mTexProgram, mDisplayProjectionMatrix);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GlUtil.checkGlError("glBindFramebuffer");

        mWindowSurface.makeCurrent();
        GLES20.glViewport(0, -mYOffset, newWindowSurfaceWidth, newWindowSurfaceHeight);
        mFullScreen.drawFrame(mOffscreenTexture, mIdentityMatrix);
        mWindowSurface.swapBuffers();


        doDrawEncodeSurface(mCodecWindowSurface);


        GlUtil.checkGlError("draw done");
    }

    public void setZoom(int percent) {
        mZoomPercent = percent;
        updateGeometry();
    }

    public void setSize(int percent) {
//        mSizePercent = percent;
        updateGeometry();
    }

    public void setRotate(int rotate) {
        mRotate = rotate;
        updateGeometry();
    }

    public void setPosition(int x, int y) {
        mPosX = x;
        mPosY = mWindowSurfaceHeight - y;   // GLES is upside-down
        updateGeometry();
    }

    public int getmWindowSurfaceWidth() {
        return mWindowSurfaceWidth;
    }

    public int getmWindowSurfaceHeight() {
        return mWindowSurfaceHeight;
    }

    /**
     * Opens a camera, and attempts to establish preview mode at the specified width
     * and height with a fixed frame rate.
     * <p/>
     * Sets mCameraPreviewWidth / mCameraPreviewHeight.
     */
    private void openCamera(int desiredWidth, int desiredHeight, int desiredFps) {
        if (mCamera != null) {
            CameraUtils.releaseCamera(mCamera);
            mCamera = null;
//            throw new RuntimeException("camera already initialized");
        }

        Camera.CameraInfo info = new Camera.CameraInfo();

        // Try to find a front-facing camera (e.g. for videoconferencing).
        int numCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numCameras; i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == mCameraId) {
                mCamera = Camera.open(i);
                break;
            }
        }
        if (mCamera == null) {
            Log.d(TAG, "No front-facing camera found; opening default");
            mCamera = Camera.open();    // opens first back-facing camera
        }
        if (mCamera == null) {
            throw new RuntimeException("Unable to open camera");
        }

        Camera.Parameters parms = mCamera.getParameters();

        if ((Build.DEVICE.equals("A33m") || Build.DEVICE.equals("A31m")) && mCameraId == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            parms.setPreviewSize(960, 720);
        } else {
            CameraUtils.choosePreviewSize(parms, desiredWidth, desiredHeight);
        }
        // Try to set the frame rate to a constant value.
        int thousandFps = CameraUtils.chooseFixedPreviewFps(parms, desiredFps * 1000);

        // Give the camera a hint that we're recording video.  This can have a big
        // impact on frame rate.
        parms.setRecordingHint(true);
        if (mCameraId == Camera.CameraInfo.CAMERA_FACING_BACK) {
            parms.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        }
        try {
            mCamera.setParameters(parms);
        } catch (RuntimeException e) {
            e.printStackTrace();
        }

        int[] fpsRange = new int[2];
        Camera.Size mCameraPreviewSize = parms.getPreviewSize();
        parms.getPreviewFpsRange(fpsRange);
        String previewFacts = mCameraPreviewSize.width + "x" + mCameraPreviewSize.height;
        if (fpsRange[0] == fpsRange[1]) {
            previewFacts += " @" + (fpsRange[0] / 1000.0) + "fps";
        } else {
            previewFacts += " @[" + (fpsRange[0] / 1000.0) +
                    " - " + (fpsRange[1] / 1000.0) + "] fps";
        }
        Log.i(TAG, "Camera config: " + previewFacts);

        mCameraPreviewWidth = mCameraPreviewSize.width;
        mCameraPreviewHeight = mCameraPreviewSize.height;
        if (mTexProgram != null) {
//            mTexProgram.setInputWH(mCameraPreviewWidth, mCameraPreviewHeight);
        }
        mMainHandler.sendCameraParams(mCameraPreviewWidth, mCameraPreviewHeight,
                thousandFps / 1000.0f);

        if (mActivityReference != null) {
            Activity activity = mActivityReference.get();
            if (activity != null) {
                mRotate = CameraUtils.getCameraDisplayOrientation(activity, mCameraId);
                mRotate += 180;
            }
        }

    }


    /**
     * Stops camera preview, and releases the camera to the system.
     */
    private void releaseCamera() {
        if (mCamera != null) {

            CameraUtils.releaseCamera(mCamera);
            mCamera = null;
            Log.d(TAG, "releaseCamera -- done");
        }
    }

    private void releaseWindowSurface() {
        if (mWindowSurface != null) {
            mWindowSurface.release();
            mWindowSurface = null;
        }

    }

    public void switchCamera() {
        releaseCamera();
        if (mCameraId == Camera.CameraInfo.CAMERA_FACING_BACK) {
            mCameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
        } else {
            mCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
        }
        openCamera(REQ_CAMERA_WIDTH, REQ_CAMERA_HEIGHT, REQ_CAMERA_FPS);
        finishSurfaceSetup();
    }


    /**
     * Prepares the off-screen framebuffer.
     */
    private void prepareFramebuffer(int width, int height) {
        int[] values = new int[1];
        if (mOffscreenTexture > 0) {
            values[0] = mOffscreenTexture;
            GLES20.glDeleteTextures(1, values, 0);
            mOffscreenTexture = -1;
        }
        if (mFramebuffer > 0) {
            values[0] = mFramebuffer;
            GLES20.glDeleteFramebuffers(1, values, 0);
            mFramebuffer = -1;
        }
        if (mDepthBuffer > 0) {
            values[0] = mDepthBuffer;
            GLES20.glDeleteRenderbuffers(1, values, 0);
            mDepthBuffer = -1;
        }

        GlUtil.checkGlError("prepareFramebuffer start");

        // Create a texture object and bind it.  This will be the color buffer.
        GLES20.glGenTextures(1, values, 0);
        GlUtil.checkGlError("glGenTextures");

        mOffscreenTexture = values[0];   // expected > 0
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mOffscreenTexture);
        GlUtil.checkGlError("glBindTexture " + mOffscreenTexture);

        // Create texture storage.
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);

        // Set parameters.  We're probably using non-power-of-two dimensions, so
        // some values may not be available for use.
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE);
        GlUtil.checkGlError("glTexParameter");

        // Create framebuffer object and bind it.
        GLES20.glGenFramebuffers(1, values, 0);
        GlUtil.checkGlError("glGenFramebuffers");
        mFramebuffer = values[0];    // expected > 0
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFramebuffer);
        GlUtil.checkGlError("glBindFramebuffer " + mFramebuffer);

        // Create a depth buffer and bind it.
        GLES20.glGenRenderbuffers(1, values, 0);
        GlUtil.checkGlError("glGenRenderbuffers");
        mDepthBuffer = values[0];    // expected > 0
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, mDepthBuffer);
        GlUtil.checkGlError("glBindRenderbuffer " + mDepthBuffer);

        // Allocate storage for the depth buffer.
        GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16,
                width, height);
        GlUtil.checkGlError("glRenderbufferStorage");

        // Attach the depth buffer and the texture (color buffer) to the framebuffer object.
        GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT,
                GLES20.GL_RENDERBUFFER, mDepthBuffer);
        GlUtil.checkGlError("glFramebufferRenderbuffer");
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, mOffscreenTexture, 0);
        GlUtil.checkGlError("glFramebufferTexture2D");

        // See if GLES is happy with all this.
        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("Framebuffer not complete, status=" + status);
        }

        // Switch back to the default framebuffer.
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        GlUtil.checkGlError("prepareFramebuffer done");
    }


}


