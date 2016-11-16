package cn.myhug.baobaoplayer.data;

import android.databinding.BindingAdapter;
import android.widget.ImageView;

import cn.myhug.baobaoplayer.R;
import cn.myhug.baobaoplayer.record.RecordActivty;

/**
 * Created by zhengxin on 2016/11/10.
 */

public class RecordData {
    public static  final int FLASH_OFF = 0;
    public static  final int FLASH_ON= 1;
    public static  final int FLASH_DISABLE_DISABLE = 2;
    public int state = RecordActivty.STATE_RECORDING;
    public long duration = 0;
    public int flashMode = FLASH_OFF;
    public boolean ready = false;


    @BindingAdapter({"flash"})
    public static void setImageViewResource(ImageView imageView, int mode) {
        imageView.setEnabled(true);
        switch (mode){
            case FLASH_DISABLE_DISABLE:
                imageView.setImageResource(R.drawable.icon_photoflash_off_24_d);
                imageView.setEnabled(false);
                break;
            case FLASH_ON:
                imageView.setImageResource(R.drawable.icon_photoflash_open_24_n);
                break;
            case FLASH_OFF:
                imageView.setImageResource(R.drawable.icon_photoflash_off_24_n);
                break;
        }
    }

}
