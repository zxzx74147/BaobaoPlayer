package cn.myhug.baobaoplayer.filter;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.graphics.SurfaceTexture.OnFrameAvailableListener;
import android.hardware.Camera.Size;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.view.Surface;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import cn.myhug.baobaoplayer.filter.base.MagicCameraInputFilter;
import cn.myhug.baobaoplayer.filter.helper.MagicFilterType;
import cn.myhug.baobaoplayer.gles.EglCore;
import cn.myhug.baobaoplayer.gles.Texture2dProgram;
import cn.myhug.baobaoplayer.gles.WindowSurface;
import cn.myhug.baobaoplayer.record.CameraEngine;
import cn.myhug.baobaoplayer.utils.OpenGlUtils;
import cn.myhug.baobaoplayer.utils.Rotation;
import cn.myhug.baobaoplayer.utils.TextureRotationUtil;

/**
 * MagicCameraDisplay is used for camera preview
 */
public class MagicCameraDisplay extends MagicDisplay {
    /**
     * 用于绘制相机预览数据，当无滤镜及mFilters为Null或者大小为0时，绘制到屏幕中，
     * 否则，绘制到FrameBuffer中纹理
     */
    private final MagicCameraInputFilter mCameraInputFilter;

    /**
     * Camera预览数据接收层，必须和OpenGL绑定
     * 过程见{@link OpenGlUtils.getExternalOESTextureID()};
     */
    private SurfaceTexture mSurfaceTexture;
    private Surface mEncodeSurface = null;

    private EglCore mEglCore;
    private WindowSurface mWindowSurface;

    private volatile boolean mIsRecording = false;

    public void setEncodeSurface(Surface surface) {
        mEncodeSurface = surface;
    }

    public MagicCameraDisplay(Context context, GLSurfaceView glSurfaceView) {
        super(context, glSurfaceView);
        mCameraInputFilter = new MagicCameraInputFilter();
//        setFilter(MagicFilterType.SKETCH);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glDisable(GL10.GL_DITHER);
        GLES20.glClearColor(0, 0, 0, 0);
        GLES20.glEnable(GL10.GL_CULL_FACE);
        GLES20.glEnable(GL10.GL_DEPTH_TEST);
        mCameraInputFilter.init();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        mSurfaceWidth = width;
        mSurfaceHeight = height;
        onFilterChanged();
    }

    float[] mtx = new float[16];

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        mSurfaceTexture.updateTexImage();
        mSurfaceTexture.getTransformMatrix(mtx);
        mCameraInputFilter.setTextureTransformMatrix(mtx);
        if (mFilters == null) {
            mCameraInputFilter.onDrawFrame(mTextureId, mGLCubeBuffer, mGLTextureBuffer);
        } else {
            int textureID = mCameraInputFilter.onDrawToTexture(mTextureId);
            mFilters.onDrawFrame(textureID, mGLCubeBuffer, mGLTextureBuffer);
        }

        if (mIsRecording && mEncodeSurface != null) {

        }
    }

    private OnFrameAvailableListener mOnFrameAvailableListener = new OnFrameAvailableListener() {

        @Override
        public void onFrameAvailable(SurfaceTexture surfaceTexture) {
            // TODO Auto-generated method stub
            mGLSurfaceView.requestRender();
        }
    };

    private void setUpCamera() {
        mGLSurfaceView.queueEvent(new Runnable() {

            @Override
            public void run() {
                if (mTextureId == OpenGlUtils.NO_TEXTURE) {
                    mTextureId = OpenGlUtils.getExternalOESTextureID();
                    mSurfaceTexture = new SurfaceTexture(mTextureId);
                    mSurfaceTexture.setOnFrameAvailableListener(mOnFrameAvailableListener);
                }
                Size size = CameraEngine.getPreviewSize();
                int orientation = CameraEngine.getOrientation();
                if (orientation == 90 || orientation == 270) {
                    mImageWidth = size.height;
                    mImageHeight = size.width;
                } else {
                    mImageWidth = size.width;
                    mImageHeight = size.height;
                }
                mCameraInputFilter.onOutputSizeChanged(mImageWidth, mImageHeight);
                CameraEngine.startPreview(mSurfaceTexture);
            }
        });
    }

    protected void onFilterChanged() {
        super.onFilterChanged();
        mCameraInputFilter.onDisplaySizeChanged(mSurfaceWidth, mSurfaceHeight);
        if (mFilters != null)
            mCameraInputFilter.initCameraFrameBuffer(mImageWidth, mImageHeight);
        else
            mCameraInputFilter.destroyFramebuffers();
    }

    public void onResume() {
        super.onResume();
        if (CameraEngine.getCamera() == null)
            CameraEngine.openCamera();
        if (CameraEngine.getCamera() != null) {
            boolean flipHorizontal = CameraEngine.isFlipHorizontal();
            adjustPosition(CameraEngine.getOrientation(), flipHorizontal, !flipHorizontal);
        }
        setUpCamera();
    }

    public void onPause() {
        super.onPause();
        CameraEngine.releaseCamera();
    }

    public void resumeRecord() {
        mIsRecording = true;
    }

    public void pauseRecord() {
        mIsRecording = false;
    }

    public void onDestroy() {
        super.onDestroy();
    }


    private void adjustPosition(int orientation, boolean flipHorizontal, boolean flipVertical) {
        Rotation mRotation = Rotation.fromInt(orientation);
        float[] textureCords = TextureRotationUtil.getRotation(mRotation, flipHorizontal, flipVertical);
        mGLTextureBuffer.clear();
        mGLTextureBuffer.put(textureCords).position(0);
    }

    public void stop() {

    }
}
