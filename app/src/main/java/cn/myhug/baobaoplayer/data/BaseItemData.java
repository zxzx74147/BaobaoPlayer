package cn.myhug.baobaoplayer.data;

import com.chad.library.adapter.base.entity.MultiItemEntity;

/**
 * Created by zhengxin on 16/8/21.
 */

public class BaseItemData<T> implements MultiItemEntity {
    public T data;
    private int mType = 0;

    public void setItemType(int type){
        mType = type;
    }
    @Override
    public int getItemType() {
        return mType;
    }
}
