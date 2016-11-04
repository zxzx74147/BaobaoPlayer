package cn.myhug.baobaoplayer.widget.recyclerview;

import com.chad.library.adapter.base.BaseViewHolder;

import cn.myhug.baobaoplayer.data.BaseItemData;

/**
 * Created by zhengxin on 16/8/21.
 */

public interface CommonRecyclerViewTable {
    int[] getLayoutId();

    void convert(BaseViewHolder baseViewHolder, BaseItemData baseItemData);
}
