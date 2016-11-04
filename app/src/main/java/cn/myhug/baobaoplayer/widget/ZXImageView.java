package cn.myhug.baobaoplayer.widget;

import android.content.Context;
import android.util.AttributeSet;

import com.makeramen.roundedimageview.RoundedImageView;

/**
 * Created by zhengxin on 15/8/27.
 */
public class ZXImageView extends RoundedImageView {

    private float mRatio = 0;
    private String mUrl = null;

    public ZXImageView(Context context) {
        super(context);
    }

    public ZXImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ZXImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setImageUrl(String url) {
        mUrl = url;
    }

    public void setRatio(float ratio){
        mRatio = ratio;
        requestLayout();
    }

    public String getImageUrl() {
        return mUrl;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if(mRatio==0){
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = (int) (width * mRatio);
        setMeasuredDimension(width, height);
    }

}
