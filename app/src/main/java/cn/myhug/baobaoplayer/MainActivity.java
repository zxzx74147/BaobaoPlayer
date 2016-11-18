package cn.myhug.baobaoplayer;

import android.app.Activity;
import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import cn.myhug.baobaoplayer.data.IntentData;
import cn.myhug.baobaoplayer.databinding.ActivityMainBinding;
import cn.myhug.baobaoplayer.edit.VideoEditActivity;
import cn.myhug.baobaoplayer.record.RecordActivty;
import cn.myhug.baobaoplayer.util.FileSelectUtil;
import cn.myhug.baobaoplayer.util.ZXActivityJumpHelper;

public class MainActivity extends BaseActivity {

    private ActivityMainBinding mBinding = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_main);

        // Example of a call to a native method
        TextView tv = (TextView) findViewById(R.id.sample_text);
//        tv.setText(stringFromJNI());

        mBinding.start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, RecordActivty.class);
                startActivity(intent);
            }
        });

        mBinding.playVideo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
                startActivity(intent);
            }
        });

//        mBinding.startVideo.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                FileSelectUtil.selectFile(MainActivity.this, "video/mp4", new FileSelectUtil.IFileSelector() {
//                    @Override
//                    public void onFileSelect(int resultCode, Intent data) {
//                        if(resultCode== Activity.RESULT_OK){
//                            IntentData intentData = new IntentData();
//                            intentData.uri= data.getData();
////                            ZXActivityJumpHelper.startActivity(MainActivity.this,PlayerVideoActivity.class,intentData);
//                            ZXActivityJumpHelper.startActivity(MainActivity.this,PlayerActivity.class,intentData);
//                            return;
//                        }
//
//                    }
//                });
//            }
//        });

        mBinding.startVideo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FileSelectUtil.selectFile(MainActivity.this, "video/mp4", new FileSelectUtil.IFileSelector() {
                    @Override
                    public void onFileSelect(int resultCode, Intent data) {
                        if(resultCode== Activity.RESULT_OK){
                            IntentData intentData = new IntentData();
                            intentData.uri= data.getData();
//                            ZXActivityJumpHelper.startActivity(MainActivity.this,PlayerVideoActivity.class,intentData);
                            ZXActivityJumpHelper.startActivity(MainActivity.this,VideoEditActivity.class,intentData);
                            return;
                        }

                    }
                });
            }
        });
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
//    public native String stringFromJNI();

    // Used to load the 'native-lib' library on application startup.
//    static {
//        System.loadLibrary("native-lib");
//    }
}
