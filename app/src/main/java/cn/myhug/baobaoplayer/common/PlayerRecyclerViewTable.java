package cn.myhug.baobaoplayer.common;

import android.databinding.DataBindingUtil;
import android.graphics.Color;
import android.view.View;

import com.chad.library.adapter.base.BaseViewHolder;

import cn.myhug.baobaoplayer.R;
import cn.myhug.baobaoplayer.data.BaseItemData;
import cn.myhug.baobaoplayer.databinding.LayoutFilterThumbBinding;
import cn.myhug.baobaoplayer.filter.data.FilterData;
import cn.myhug.baobaoplayer.widget.recyclerview.CommonRecyclerViewTable;

/**
 * Created by zhengxin on 2016/11/1.
 */

public class PlayerRecyclerViewTable implements CommonRecyclerViewTable {
    private int[] mTable = new int[]{
            R.layout.layout_filter_thumb
    };

    private View.OnClickListener mListener = null;
    @Override
    public int[] getLayoutId() {
        return mTable;
    }

    @Override
    public void convert(BaseViewHolder baseViewHolder, BaseItemData baseItemData) {

        switch (baseItemData.getItemType()) {
            case R.layout.layout_filter_thumb:
                LayoutFilterThumbBinding itemFilterBinding = DataBindingUtil.bind(baseViewHolder.convertView);
                itemFilterBinding.thumb.setImageResource(((FilterData) baseItemData.data).mResourceId);
                FilterData data = (FilterData) baseItemData.data;
                itemFilterBinding.setItem(data);
                itemFilterBinding.getRoot().setTag(R.id.tag_holder,itemFilterBinding);
                itemFilterBinding.getRoot().setTag(R.id.tag_data,baseItemData.data);
                itemFilterBinding.getRoot().setOnClickListener(mListener);
                if(data.isSelected) {
                    itemFilterBinding.thumb.setBorderColor(Color.GREEN);
                }else{
                    itemFilterBinding.thumb.setBorderColor(0);
                }
                break;
        }
    }

    public void setOnClickListener(View.OnClickListener listener){
        mListener = listener;
    }
}