package cn.myhug.baobaoplayer.preview;

import android.databinding.DataBindingUtil;
import android.os.Bundle;

import cn.myhug.baobaoplayer.BaseActivity;
import cn.myhug.baobaoplayer.R;
import cn.myhug.baobaoplayer.databinding.ActivityPreviewActivtyBinding;

public class PreviewActivty extends BaseActivity {

    private ActivityPreviewActivtyBinding mBinding = null;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = DataBindingUtil.setContentView(this,R.layout.activity_preview_activty);

    }
}
