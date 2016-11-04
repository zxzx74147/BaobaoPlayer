package cn.myhug.baobaoplayer.widget.recyclerview;


import java.util.ArrayList;
import java.util.List;

import cn.myhug.baobaoplayer.data.BaseItemData;

/**
 * Created by zhengxin on 16/8/22.
 */

public class CommonDataConverter {

    public static <T> List<BaseItemData<T>> convertData(int type, List<T> datas) {
        if (datas == null) {
            return new ArrayList<>(0);
        }
        List<BaseItemData<T>> result = new ArrayList<>(datas.size());

        for (T data : datas) {
            BaseItemData<T> itemData = new BaseItemData<>();
            itemData.setItemType(type);
            itemData.data = data;
            result.add(itemData);
        }
        return result;
    }
}
