package cn.myhug.baobaoplayer.databinding;

import android.content.Context;
import android.databinding.BindingAdapter;
import android.widget.ImageView;

import com.bumptech.glide.Glide;

import cn.myhug.baobaoplayer.PlayerApplication;
import cn.myhug.baobaoplayer.widget.ZXImageView;

/**
 * Created by zhengxin on 15/9/25.
 */
public class ImageBindUtil {

    @BindingAdapter({"android:src"})
    public static void setImageViewResource(ImageView imageView, int resource) {
        imageView.setImageResource(resource);
    }

    @BindingAdapter({"img_url"})
    public static void loadImage(ImageView imageView, String url) {
        if (imageView == null) {
            return;
        }
        if (imageView instanceof ZXImageView) {
            if (url!=null && url.equals(((ZXImageView) imageView).getImageUrl())) {
                return;
            } else {
                imageView.setImageDrawable(null);
            }
        }
        ((ZXImageView)imageView).setImageUrl(url);
        if (url==null||url.length()==0) {
            return;
        }
        Context context = imageView.getContext();
        if (context == null) {
            context = PlayerApplication.sharedInstance();
        }
        Glide.with(context)
                .load(url)
                .asBitmap()
                .into(imageView);
//        Picasso.with(imageView.getContext()).load(url).into(imageView);
//        if(url == null){
//            view.setController(null);
//            return;
//        }
    }

//    private static ControllerListener listener = new BaseControllerListener() {};

}
