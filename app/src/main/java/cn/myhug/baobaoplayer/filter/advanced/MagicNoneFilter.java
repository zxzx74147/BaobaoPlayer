package cn.myhug.baobaoplayer.filter.advanced;

import cn.myhug.baobaoplayer.R;
import cn.myhug.baobaoplayer.filter.base.gpuimage.GPUImageFilter;
import cn.myhug.baobaoplayer.utils.OpenGlUtils;


public class MagicNoneFilter extends GPUImageFilter {

	public MagicNoneFilter(){
		super(NO_FILTER_VERTEX_SHADER, OpenGlUtils.readShaderFromRawResource(R.raw.default_no_filter_fragment));
	}
	
	protected void onDestroy() {
        super.onDestroy();

    }

	protected void onInit(){
		super.onInit();
	}
	
	protected void onInitialized(){
		super.onInitialized();
	}
}
