package me.theegg.qingming;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by seal on 14-11-10.
 */
public class MainView extends SurfaceView implements SurfaceHolder.Callback{

    private DrawThread drawThread;
    private InputStreamScene scene;
    private InputStream inputStream;

    public void setInputStream(InputStream inputStream) {
        this.inputStream = inputStream;
    }


    public MainView(Context context) {
        this(context, null);
    }

    public MainView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MainView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            scene = new InputStreamScene(inputStream);
            drawThread = new DrawThread(holder);
            drawThread.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        scene.getViewport().setSize(width, height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }

    private class DrawThread extends Thread {
        private final SurfaceHolder holder;

        public DrawThread(SurfaceHolder holder) {
            this.holder = holder;
        }

        @Override
        public void run() {
            while(true) {
                Canvas canvas;
                canvas = holder.lockCanvas();
                try {
                    synchronized (holder) {
                        if (canvas != null) {
                            scene.draw(canvas);
                        }
                    }
                } finally {
                    if (canvas != null) {
                        holder.unlockCanvasAndPost(canvas);
                    }
                }
            }
        }
    }
}
