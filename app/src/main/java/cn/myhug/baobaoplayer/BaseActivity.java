package cn.myhug.baobaoplayer;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import cn.myhug.baobaoplayer.data.IntentData;
import cn.myhug.baobaoplayer.util.FileSelectUtil;
import cn.myhug.baobaoplayer.util.ZXActivityJumpHelper;

public abstract class BaseActivity extends AppCompatActivity {

    protected IntentData mIntentData = null;
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        mIntentData = (IntentData) getIntent().getSerializableExtra(ZXActivityJumpHelper.INTENT_DATA);
        if(mIntentData == null){
            mIntentData = new IntentData();
        }
        mIntentData.uri = getIntent().getData();
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode / FileSelectUtil.PICK_INTENT==1) {
            FileSelectUtil.dealOnActivityResult(requestCode, resultCode, data);
        }
    }
}
