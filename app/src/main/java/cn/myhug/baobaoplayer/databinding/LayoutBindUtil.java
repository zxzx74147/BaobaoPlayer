package cn.myhug.baobaoplayer.databinding;

import android.databinding.BindingAdapter;
import android.view.View;
import android.view.ViewGroup;

import java.util.LinkedList;

/**
 * Created by zhengxin on 15/9/28.
 */
public class LayoutBindUtil {

    @BindingAdapter({"layout_wh"})
    public static void setLayout(View view, LinkedList<Integer> layout) {
        if(layout == null){
            return;
        }
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if(layoutParams == null){
            return;
        }
        layoutParams.width = layout.get(0);
        layoutParams.height = layout.get(1);
        view.setLayoutParams(layoutParams);
    }


}
