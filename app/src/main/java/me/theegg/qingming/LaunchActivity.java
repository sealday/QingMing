package me.theegg.qingming;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;


public class LaunchActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_launch);

        Intent intent = new Intent(this, ImageViewerActivity.class);
        startActivity(intent);
        finish();
    }

}
