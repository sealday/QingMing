package me.theegg.qingming;

import android.app.Activity;
import android.graphics.Point;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

import java.io.InputStream;


public class ImageViewerActivity extends ActionBarActivity{
    private static final String TAG = "ImageViewerActivity";
    private static final String KEY_X = "X";
    private static final String KEY_Y = "Y";

    private static final String QINGMING = "qing_ming2.jpg";

    private MainView mainView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        mainView = (MainView) findViewById(R.id.worldview);

        // 恢复状态
        if (savedInstanceState != null && savedInstanceState.containsKey(KEY_X) && savedInstanceState.containsKey(KEY_Y)) {
            Log.d(TAG, "正在恢复状态");
            int x = (Integer) savedInstanceState.get(KEY_X);
            int y = (Integer) savedInstanceState.get(KEY_Y);

            try {
                mainView.setInputStream(getAssets().open(QINGMING));
            } catch (java.io.IOException e) {
                Log.e(TAG, e.getMessage());
            }
        } else {
            try {
                InputStream
                        is = getAssets().open(QINGMING);
                mainView.setInputStream(is);
            } catch (java.io.IOException e) {
                Log.e(TAG, e.getMessage());
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        Point p = new Point();
        outState.putInt(KEY_X, p.x);
        outState.putInt(KEY_Y, p.y);
        super.onSaveInstanceState(outState);
    }
}
