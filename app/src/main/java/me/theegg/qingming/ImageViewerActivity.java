package me.theegg.qingming;

import android.app.Activity;
import android.graphics.Point;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

import java.io.InputStream;


public class ImageViewerActivity extends Activity {
    private static final String TAG = "ImageViewerActivity";
    private static final String KEY_X = "X";
    private static final String KEY_Y = "Y";

    private static final String QINGMING = "qing_ming2.jpg";

    private ImageSurfaceView imageSurfaceView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 隐藏窗口标题
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_main);
        imageSurfaceView = (ImageSurfaceView) findViewById(R.id.worldview);

        // 恢复状态
        if (savedInstanceState != null && savedInstanceState.containsKey(KEY_X) && savedInstanceState.containsKey(KEY_Y)) {
            Log.d(TAG, "正在恢复状态");
            int x = (Integer) savedInstanceState.get(KEY_X);
            int y = (Integer) savedInstanceState.get(KEY_Y);

            try {
                imageSurfaceView.setInputStream(getAssets().open(QINGMING));
                imageSurfaceView.setViewport(new Point(x, y));
            } catch (java.io.IOException e) {
                Log.e(TAG, e.getMessage());
            }
        } else {
            try {
                InputStream
                        is = getAssets().open(QINGMING);
                imageSurfaceView.setInputStream(is);
            } catch (java.io.IOException e) {
                Log.e(TAG, e.getMessage());
            }
            imageSurfaceView.setViewportCenter();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i("resume", String.format(" w -> %d, h -> %d", imageSurfaceView.getWidth(), imageSurfaceView.getHeight()));
        imageSurfaceView.setViewport(new Point(19207, imageSurfaceView.getHeight()/2));
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        Point p = new Point();
        imageSurfaceView.getViewport(p);
        outState.putInt(KEY_X, p.x);
        outState.putInt(KEY_Y, p.y);
        super.onSaveInstanceState(outState);
    }
}
