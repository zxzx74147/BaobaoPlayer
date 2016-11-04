package cn.myhug.baobaoplayer.filter.widget;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;

import java.util.List;

import cn.myhug.baobaoplayer.filter.data.FilterData;

/**
 * Created by zhengxin on 2016/11/1.
 */

public class FilterAdapter extends BaseQuickAdapter<FilterData> {
    public FilterAdapter(List data) {
        super(data);
    }



    @Override
    protected void convert(BaseViewHolder baseViewHolder, FilterData filterData) {

    }
}
