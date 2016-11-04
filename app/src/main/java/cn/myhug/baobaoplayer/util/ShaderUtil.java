package cn.myhug.baobaoplayer.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import cn.myhug.baobaoplayer.PlayerApplication;

/**
 * Created by zhengxin on 2016/10/27.
 */

public class ShaderUtil {

    public static String getStringFromAssert(String path){
        StringBuilder buf=new StringBuilder();
        InputStream json= null;
        try {
            json = PlayerApplication.sharedInstance().getAssets().open(path);
            BufferedReader in=
                    new BufferedReader(new InputStreamReader(json, "UTF-8"));
            String str;

            while ((str=in.readLine()) != null) {
                buf.append(str);
                buf.append('\n');
            }

            in.close();
            return buf.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }



}
