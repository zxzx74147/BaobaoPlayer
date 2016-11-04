package cn.myhug.baobaoplayer.filter.advanced;


import java.util.ArrayList;
import java.util.List;

import cn.myhug.baobaoplayer.filter.base.MagicBaseGroupFilter;
import cn.myhug.baobaoplayer.filter.base.gpuimage.GPUImageBrightnessFilter;
import cn.myhug.baobaoplayer.filter.base.gpuimage.GPUImageContrastFilter;
import cn.myhug.baobaoplayer.filter.base.gpuimage.GPUImageExposureFilter;
import cn.myhug.baobaoplayer.filter.base.gpuimage.GPUImageFilter;
import cn.myhug.baobaoplayer.filter.base.gpuimage.GPUImageHueFilter;
import cn.myhug.baobaoplayer.filter.base.gpuimage.GPUImageSaturationFilter;
import cn.myhug.baobaoplayer.filter.base.gpuimage.GPUImageSharpenFilter;

public class MagicImageAdjustFilter extends MagicBaseGroupFilter {
	
	public MagicImageAdjustFilter() {
		super(initFilters());
	}
	
	private static List<GPUImageFilter> initFilters(){
		List<GPUImageFilter> filters = new ArrayList<GPUImageFilter>();
		filters.add(new GPUImageContrastFilter());
		filters.add(new GPUImageBrightnessFilter());
		filters.add(new GPUImageExposureFilter());
		filters.add(new GPUImageHueFilter());
		filters.add(new GPUImageSaturationFilter());
		filters.add(new GPUImageSharpenFilter());
		return filters;		
	}
	
	public void setSharpness(final float range){
		((GPUImageSharpenFilter) filters.get(5)).setSharpness(range);
	}
	
	public void setHue(final float range){
		((GPUImageHueFilter) filters.get(3)).setHue(range);
	}
	
	public void setBrightness(final float range){
		((GPUImageBrightnessFilter) filters.get(1)).setBrightness(range);
	}
	
	public void setContrast(final float range){
		((GPUImageContrastFilter) filters.get(0)).setContrast(range);
	}
	
	public void setSaturation(final float range){
		((GPUImageSaturationFilter) filters.get(4)).setSaturation(range);
	}
	
	public void setExposure(final float range){
		((GPUImageExposureFilter) filters.get(2)).setExposure(range);
	}
}
