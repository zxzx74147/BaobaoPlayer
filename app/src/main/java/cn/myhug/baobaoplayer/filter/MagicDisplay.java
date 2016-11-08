package cn.myhug.baobaoplayer.filter;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.opengl.GLSurfaceView.Renderer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import cn.myhug.baobaoplayer.filter.base.gpuimage.GPUImageFilter;
import cn.myhug.baobaoplayer.filter.helper.MagicFilterFactory;
import cn.myhug.baobaoplayer.filter.helper.MagicFilterType;
import cn.myhug.baobaoplayer.utils.OpenGlUtils;
import cn.myhug.baobaoplayer.utils.TextureRotationUtil;

public abstract class MagicDisplay implements Renderer {
	/**
	 * 所选择的滤镜，类型为MagicBaseGroupFilter
	 * 1.mCameraInputFilter将SurfaceTexture中YUV数据绘制到FrameBuffer
	 * 2.mFilters将FrameBuffer中的纹理绘制到屏幕中
	 */
	protected GPUImageFilter mFilters;
	
	/**
	 * 所有预览数据绘制画面
	 */
	protected final GLSurfaceView mGLSurfaceView;
	
	/**
	 * SurfaceTexure纹理id
	 */
	protected int mTextureId = OpenGlUtils.NO_TEXTURE;
	
	/**
	 * 顶点坐标
	 */
	protected final FloatBuffer mGLCubeBuffer;
	
	/**
	 * 纹理坐标
	 */
	protected final FloatBuffer mGLTextureBuffer;

	
	/**
	 * GLSurfaceView的宽高
	 */
	protected int mSurfaceWidth, mSurfaceHeight;
	
	/**
	 * 图像宽高
	 */
	protected int mImageWidth, mImageHeight;
	
	protected Context mContext;
	
	public MagicDisplay(Context context, GLSurfaceView glSurfaceView){
		mContext = context;
		mGLSurfaceView = glSurfaceView;  
		
		mFilters = MagicFilterFactory.initFilters(MagicFilterType.NONE);
		
		mGLCubeBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.CUBE.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mGLCubeBuffer.put(TextureRotationUtil.CUBE).position(0);

        mGLTextureBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mGLTextureBuffer.put(TextureRotationUtil.TEXTURE_NO_ROTATION).position(0);

		mGLSurfaceView.setEGLContextClientVersion(2);
		mGLSurfaceView.setRenderer(this);
		mGLSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
	}
	
	/**
	 * 设置滤镜
	 * @param
	 */
	public void setFilter(final MagicFilterType filterType) {
		mGLSurfaceView.queueEvent(new Runnable() {
       		
            @Override
            public void run() {
            	if(mFilters != null)
            		mFilters.destroy();
            	mFilters = null;
            	mFilters = MagicFilterFactory.initFilters(filterType);
            	if(mFilters != null)
	            	mFilters.init();
            	onFilterChanged();
            }
        });
		mGLSurfaceView.requestRender();
    }
	
	protected void onFilterChanged(){
		if(mFilters == null)
			return;
		mFilters.onDisplaySizeChanged(mSurfaceWidth, mSurfaceHeight);

	}
	
	protected void onResume(){
		
	}
	
	protected void onPause(){
	}
	
	protected void onDestroy(){
		
	}
	

}
