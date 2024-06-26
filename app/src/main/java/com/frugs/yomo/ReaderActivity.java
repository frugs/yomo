package com.frugs.yomo;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FlingAnimation;

import com.frugs.yomo.book.Book;
import com.frugs.yomo.book.SyosetuBook;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

import kotlin.Unit;

/**
 * Copyright (C) 2017   Tom Kliethermes
 *
 * This file is part of BookyMcBookface and is is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published by the
 * Free Software Foundation; either version 3 of the License, or (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 */

public class ReaderActivity extends Activity {

    private static final String TAG = "ReaderActivity";
    public static final String READEREXITEDNORMALLY = "readerexitednormally";
    private static final String FULLSCREEN = "fullscreen";

    private static final float FLING_THRESHOLD = 0.01f;

    private Book book;

    private WebView webView;

    public static final String FILENAME = "filename";

    private final Object timerSync = new Object();
    private Timer timer;

    private TimerTask nowakeTask = null;

    private final Handler handler = new Handler();

    private CheckBox fullscreenBox;

    private ProgressBar progressBar;

    private Throwable exception;

    private boolean hasLightSensor = false;

    private AutoCloseable pageReadyListener = null;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reader);
        final Intent intent = getIntent();

        ActionBar ab = getActionBar();
        if (ab != null) {
            ab.hide();
        }

        SensorManager sensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        Sensor lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        if (lightSensor != null) {
            hasLightSensor = true;
        }

        final ImageButton showMore = findViewById(R.id.control_view_more);

        webView = findViewById(R.id.page_view);

        webView.getSettings().setDefaultFontSize(16);
        webView.getSettings().setDefaultFixedFontSize(16);
        webView.getSettings().setAllowFileAccess(true);

        webView.setNetworkAvailable(false);

        webView.setOnTouchListener(new View.OnTouchListener() {
            private final GestureDetector gestureDetector = new GestureDetector(
                    ReaderActivity.this,
                    new GestureDetector.SimpleOnGestureListener() {
                        @Override
                        public boolean onDoubleTap(@NonNull MotionEvent e) {
                            showMenu();
                            return true;
                        }

                        @Override
                        public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                            View slideMenuView = findViewById(R.id.slide_menu);
                            if (slideMenuView != null && slideMenuView.getVisibility() == View.VISIBLE) {
                                hideMenu();
                                return true;
                            }

                            return false;
                        }

                        @Override
                        public boolean onFling(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
                            if (book == null) {
                                return false;
                            }

                            if (Math.abs(velocityX) < 2 * Math.abs(velocityY)) {
                                // ignore flings that aren't primarily horizontal
                                return false;
                            }

                            if (velocityX >= FLING_THRESHOLD && book.hasPreviousSection()) {
                                startReaderFlingAnimation(
                                        velocityX,
                                        (animation, canceled, value, velocity) -> {
                                            prevPage();
                                        });
                                return true;
                            } else if (velocityX <= -FLING_THRESHOLD && book.hasNextSection()) {
                                startReaderFlingAnimation(
                                        velocityX,
                                        (animation, canceled, value, velocity) -> {
                                            nextPage();
                                        });
                                return true;
                            }

                            return false;
                        }
                    });

            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                return gestureDetector.onTouchEvent(motionEvent);
            }
        });

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Log.i(TAG, "Attempting to load URL: " + url);

                handleLink(url);
                return true;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (uri.getScheme()!=null && uri.getScheme().equals("file")) {
                    handleLink(uri.toString());
                    return true;
                }
                return false;
            }


            public void onPageFinished(WebView view, String url) {
                try {
                    restoreBgColor();
                    restoreScrollOffsetDelayed(100);
                    restorePos();
                } catch (Throwable t) {
                    Log.e(TAG, t.getMessage(), t);
                }
            }

        });

        progressBar = findViewById(R.id.progressBar);

        findViewById(R.id.prev_button).setOnClickListener(view -> prevPage());
        findViewById(R.id.next_button).setOnClickListener(view -> nextPage());
        findViewById(R.id.zoom_button).setOnClickListener(view -> selectFontSize());
        findViewById(R.id.brightness_button).setOnClickListener(view -> showBrightnessControl());

        findViewById(R.id.contents_button).setOnClickListener(view -> {
            showToc();
            //hideMenu();
        });

        showMore.setOnClickListener(morelessControls);
        findViewById(R.id.control_view_less).setOnClickListener(morelessControls);

        fullscreenBox = findViewById(R.id.fullscreen_box);

        fullscreenBox.setOnCheckedChangeListener((compoundButton, b) -> {
            setFullscreen(b);
            if (b) {
                fullscreenBox.postDelayed(
                        new Runnable() {
                            @Override
                            public void run() {
                                mkFull();
                                hideMenu();
                            }
                        }, 500);
            } else {
                fullscreenBox.postDelayed(
                        new Runnable() {
                            @Override
                            public void run() {
                                mkReg();
                                hideMenu();
                            }
                        }, 500);
            }
        });

        findViewById(R.id.fullscreen_button).setOnClickListener(view -> fullscreenBox.setChecked(!fullscreenBox.isChecked()));

        //findFile();
        String filename = intent.getStringExtra(FILENAME);
        if (filename!=null) {
            //if the app crashes on this book,
            // this flag will remain to let the booklist activity know not to auto start it again.
            // it gets set to true in onPause.
            if (getSharedPreferences(BookListActivity.prefname, Context.MODE_PRIVATE).edit().putBoolean(READEREXITEDNORMALLY, false).commit()) {
                loadFile(new File(filename));
            }
        }
    }

    private void startReaderFlingAnimation(float velocityX,
                                           DynamicAnimation.OnAnimationEndListener endListener) {
        if (webView != null) {
            float signum = Math.signum(velocityX);
            float magnitude = Math.abs(velocityX);
            float flingVelocity = signum * Math.max(magnitude, 4000);

            FlingAnimation fling = new FlingAnimation(webView, DynamicAnimation.X);
            fling.setStartVelocity(flingVelocity)
                    .setMinValue(webView.getWidth() * -0.75f)
                    .setMaxValue(webView.getWidth() * 0.75f)
                    .setFriction(0.5f)
                    .addEndListener(endListener)
                    .start();
        }
    }

    @Override
    public void onBackPressed() {
        View slideMenuView = findViewById(R.id.slide_menu);
        if (slideMenuView != null && slideMenuView.getVisibility() == View.VISIBLE) {
            hideMenu();
            return;
        }

        finish();
        Intent main = new Intent(this, BookListActivity.class);
        main.setAction(BookListActivity.ACTION_SHOW_LAST_STATUS);
        startActivity(main);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void addEOCPadding() {
        //Add padding to end of section to reduce confusing partial page scrolls
        webView.getSettings().setJavaScriptEnabled(true);
        webView.evaluateJavascript("document.getElementsByTagName('body')[0].innerHTML += '<br/><br/><br/><br/><br/><br/><br/><br/><br/><br/><br/>'", null);
        webView.getSettings().setJavaScriptEnabled(false);
    }

    private final View.OnClickListener morelessControls = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            View v = findViewById(R.id.slide_menu);
            if (v.getVisibility()==View.GONE) {
                showMenu();
            } else {
                hideMenu();
            }
        }
    };

    private void setFullscreenMode() {
        if (book!=null && book.hasDataDir()) {
            setFullscreen(book.getFlag(FULLSCREEN, true));
        }
    }

    private void setFullscreen(boolean full) {
        if (book!=null && book.hasDataDir()) book.setFlag(FULLSCREEN, full);

        fullscreenBox.setChecked(full);
    }

    private void showMenu() {
        View v = findViewById(R.id.slide_menu);
        v.setVisibility(View.VISIBLE);
        findViewById(R.id.control_view_more).setVisibility(View.GONE);
        findViewById(R.id.control_view_less).setVisibility(View.VISIBLE);
        findViewById(R.id.controls_layout).setVisibility(View.VISIBLE);
        mkReg();
    }

    private void hideMenu() {
        View v = findViewById(R.id.slide_menu);
        v.setVisibility(View.GONE);
        findViewById(R.id.control_view_more).setVisibility(View.VISIBLE);
        findViewById(R.id.control_view_less).setVisibility(View.GONE);
        findViewById(R.id.controls_layout).setVisibility(View.GONE);
        mkFull();
    }

    private void prevPage() {
        if (book != null) {
            showUri(book.gotoPreviousSection());
        }

        hideMenu();
    }

    private void nextPage() {
        if (book != null) {
            showUri(book.gotoNextSection());
        }

        hideMenu();
    }

    private void saveScrollOffset() {
        webView.computeScroll();
        saveScrollOffset(webView.getScrollY());
    }

    private void saveScrollOffset(int offset) {
        if (book==null) return;
        book.setSectionOffset(offset);
    }

    private void restoreScrollOffsetDelayed(int delay) {
        handler.postDelayed(() -> {
            if (book == null) {
                return;
            }

            int spos = book.getSectionOffset();
            webView.computeScroll();
            if (spos >= 0) {
                webView.scrollTo(0, spos);
                Log.d(TAG, "restoreScrollOffset " + spos);
            }
        }, delay);
    }

    private void restorePos() {
        handler.post(() -> {
            if (webView != null) {
                webView.setTranslationX(0);
            }
        });
    }

    private void loadFile(File file) {

        webView.loadData("Loading " + file.getPath(),"text/plain", "utf-8");

        new LoaderTask(this, file).execute();

    }


    private static class LoaderTask extends  AsyncTask<Void,Integer,Book>  {

        private final File file;
        private final WeakReference<ReaderActivity> ractref;

        LoaderTask(ReaderActivity ract, File file) {
            this.file = file;
            this.ractref = new WeakReference<>(ract);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            ReaderActivity ract = ractref.get();
            if (ract!=null) {
                ract.progressBar.setProgress(0);
                ract.progressBar.setVisibility(View.VISIBLE);

            }
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            ReaderActivity ract = ractref.get();
            if (ract!=null) {
                ract.progressBar.setProgress(values[0]);
            }
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            ReaderActivity ract = ractref.get();
            if (ract!=null) {
                ract.progressBar.setVisibility(View.GONE);
            }
        }

        @Override
        protected Book doInBackground(Void... voids) {
            ReaderActivity ract = ractref.get();
            if (ract==null) return null;
            try {
                ract.book = Book.getBookHandler(ract, file.getPath());
                Log.d(TAG, "File " + file);
                if (ract.book != null) {
                    ract.book.load(file);
                    return ract.book;
                }

                //publishProgress(1);

            } catch (Throwable e) {
                ract.exception = e;
                Log.e(TAG, e.getMessage(), e);
            }
            return null;
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        protected void onPostExecute(Book book) {

            ReaderActivity ract = ractref.get();
            if (ract==null) return;

            String badtext = ract.getString(R.string.book_bug);
            try {
                if (book==null && ract.exception!=null) {
                    ract.webView.setOnTouchListener(null);
                    ract.webView.setWebViewClient(null);
                    ract.webView.loadData(badtext + ract.exception.getLocalizedMessage(),"text/plain", "utf-8");
                    throw ract.exception;
                }
                if (book !=null && ract.book != null && ract.book.hasDataDir()) {
                    int fontsize = ract.book.getFontSize();
                    if (fontsize != -1) {
                        ract.setFontSize(fontsize);
                    }
                    Uri uri = ract.book.getCurrentSection();
                    if (uri != null) {
                        ract.showUri(uri);
                    } else {
                        Toast.makeText(ract, badtext + " (no sections)", Toast.LENGTH_LONG).show();
                    }
                    if (ract.book.getFlag(FULLSCREEN, true)) {
                        ract.mkFull();
                    } else {
                        ract.mkReg();
                    }
                    ract.setFullscreenMode();
                    ract.setAwake();
                }
            } catch (Throwable e) {
                Log.e(TAG, e.getMessage(), e);
                Toast.makeText(ract, badtext + e.getMessage(), Toast.LENGTH_LONG).show();
            }

        }
    }


    private void showUri(Uri uri) {
        if (uri != null) {
            Log.d(TAG, "trying to load " + uri);

            if (pageReadyListener != null) {
                try {
                    pageReadyListener.close();
                } catch (Exception e) {
                    Log.e(TAG, "Unexpected error closing page ready listener", e);
                }
            }

            if (book instanceof SyosetuBook syosetu) {
                pageReadyListener = syosetu.addCurrentPageUpdatedListener(() -> {
                    progressBar.setVisibility(View.GONE);
                    webView.loadUrl(uri.toString());
                    return Unit.INSTANCE;
                });
            }

            File file = new File(uri.getPath());
            if (file.exists()) {
                progressBar.setVisibility(View.GONE);
                webView.loadUrl(uri.toString());
            } else {
                progressBar.setVisibility(View.VISIBLE);
            }
        }
    }

    private void handleLink(String clickedLink) {
        if (clickedLink!=null) {
            Log.d(TAG, "clicked on " + clickedLink);
            showUri(book.handleClickedLink(clickedLink));
        }
    }


    private void fontSizeToggle() {

        int defsize = webView.getSettings().getDefaultFontSize();
        int minsize = webView.getSettings().getMinimumFontSize();

        defsize += 4;
        if (defsize>40) {
            defsize = minsize;
        }

        setFontSize(defsize);

    }

    private void setFontSize(int size) {
        book.setFontSize(size);
        webView.getSettings().setDefaultFontSize(size);
        webView.getSettings().setDefaultFixedFontSize(size);
    }

    private void selectFontSize() {
        final int defsize = webView.getSettings().getDefaultFontSize();
        int minsize = webView.getSettings().getMinimumFontSize();
        final float scale = getResources().getDisplayMetrics().density;


        // Log.d(TAG, "def " + defsize + " " + scale);
        final PopupMenu sizemenu = new PopupMenu(this, findViewById(R.id.zoom_button));
        for (int size=minsize; size<=36; size+=2) {
            final int s = size;

            MenuItem mi = sizemenu.getMenu().add(" "+size);
            mi.setCheckable(true);
            mi.setChecked(size==defsize);

            mi.setOnMenuItemClickListener(menuItem -> {
                Log.d(TAG, "def " + (defsize-s));
                int scrolloffset = (int)(-webView.getScrollY()*(defsize - s)/scale/2.7);
                Log.d(TAG, "scrollby " + scrolloffset);

                setFontSize(s);

                //attempt to adjust the scroll to keep the same text position.
                //  needs much work
                webView.scrollBy(0, scrolloffset);
                sizemenu.dismiss();
                return true;
            });
        }
        sizemenu.show();


    }

    private void mkFull() {

        if (book == null || !book.getFlag(FULLSCREEN, true)) return;
//        findViewById(R.id.fullscreen_no_button).setVisibility(View.VISIBLE);
//        findViewById(R.id.fullscreen_button).setVisibility(View.GONE);

        View decorView = getWindow().getDecorView();
        // Hide the status bar.
        int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE;
        decorView.setSystemUiVisibility(uiOptions);
    }

    private void mkReg() {

//        findViewById(R.id.fullscreen_button).setVisibility(View.VISIBLE);
//        findViewById(R.id.fullscreen_no_button).setVisibility(View.GONE);

        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        decorView.setSystemUiVisibility(uiOptions);
    }

    @Override
    protected void onResume() {
        super.onResume();

        synchronized (timerSync) {
            if (timer != null) {
                timer.cancel();
            }
            timer = new Timer();
        }
        restoreBgColor();
    }

    @Override
    protected void onPause() {
        setNoAwake();
        unlistenLight();
        synchronized (timerSync) {
            if (timer != null) {
                timer.cancel();
                timer.purge();
                timer = null;
            }
        }

        if (exception == null) {
            try {
                saveScrollOffset();
            } catch (Throwable t) {
                Log.e(TAG, t.getMessage(), t);
            }
            getSharedPreferences(BookListActivity.prefname, Context.MODE_PRIVATE).edit().putBoolean(READEREXITEDNORMALLY, true).apply();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (timer != null) {
            timer.cancel();
            timer.purge();
            timer = null;
        }

        if (pageReadyListener != null) {
            try {
                pageReadyListener.close();
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error closing page ready listener", e);
            }
        }

        super.onDestroy();
    }

    //    @Override
//    public void onWindowFocusChanged(boolean hasFocus) {
//        super.onWindowFocusChanged(hasFocus);
//        //if (hasFocus) mkFull();
//    }

    private void showToc() {
        Map<String,String> tocmap = book.getToc();
        PopupMenu tocmenu = new PopupMenu(this, findViewById(R.id.contents_button));
        for (final String point: tocmap.keySet()) {
            String text = tocmap.get(point);
            MenuItem m = tocmenu.getMenu().add(text);
            //Log.d("EPUB", "TOC2: " + text + ". File: " + point);
            m.setOnMenuItemClickListener(menuItem -> {
                handleLink(point);
                return true;
            });
        }
        if (tocmap.size()==0) {
            tocmenu.getMenu().add(R.string.no_toc_found);
        }

        tocmenu.show();

    }


    //keep the screen on for a few minutes, but not forever
    private void setAwake() {
        try {
            Window w = this.getWindow();
            w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            synchronized (timerSync) {
                if (nowakeTask != null) {
                    nowakeTask.cancel();
                    if (timer==null)  {
                        timer = new Timer();
                        Log.d(TAG, "timer was null?");
                    }
                    timer.purge();
                }
                nowakeTask = new TimerTask() {
                    @Override
                    public void run() {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    setNoAwake();
                                    Log.d(TAG, "Clear FLAG_KEEP_SCREEN_ON");
                                } catch (Throwable t) {
                                    Log.e(TAG, t.getMessage(), t);
                                }

                            }
                        });
                    }
                };

                try {
                    if (timer==null)  return;
                    timer.schedule(nowakeTask, 3 * 60 * 1000);
                } catch (IllegalStateException e) {
                    Log.d(TAG, e.getMessage(), e);
                    //Toast.makeText(this, "Something went wrong. Please report a 'setAwake' bug.", Toast.LENGTH_LONG).show();
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, t.getMessage(), t);
            setNoAwake();
        }

    }

    private void setNoAwake() {
        try {
            Window w = ReaderActivity.this.getWindow();
            w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }catch (Throwable t) {
            Log.e(TAG, t.getMessage(), t);
        }
    }

    private SensorEventListener lightSensorListener;


    private void listenLight() {

        unlistenLight();

        SensorManager sensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        Sensor lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        if (lightSensor != null) {

            lightSensorListener = new SensorEventListener() {

                private final AtomicInteger currentLux = new AtomicInteger(0);
                private int lastCol = 0;

                private final int mincol = 30;
                private final int maxcol = 240;
                private final double luxThreshold = 50;
                private final double multfac = (maxcol-mincol)/luxThreshold;

                private Runnable changer;

                @Override
                public void onSensorChanged(SensorEvent event) {

                    try {
                        currentLux.set((int) event.values[0]);

                        if (changer == null) {
                            changer = new Runnable() {
                                @Override
                                public void run() {
                                    changer = null;
                                    try {
                                        float lux = currentLux.get();

                                        int col = maxcol;
                                        if (lux < luxThreshold) {

                                            col = (int) (lux * multfac + mincol);
                                            if (col < mincol) col = mincol;
                                            if (col > maxcol) col = maxcol;

                                        }
                                        Log.d(TAG, "lightval " + lux + " grey " + col);

                                        if (Math.abs(lastCol - col) > 1 * multfac) {

                                            lastCol = col;
                                            int color = Color.argb(255, col + 15, col + 10, (int) (col + Math.min(lux / luxThreshold * 10, 10)));

                                            applyColor(color, Color.BLACK);
                                        }
                                    } catch (Throwable t) {
                                        Log.e(TAG, t.getMessage(), t);
                                    }

                                }
                            };
                            handler.postDelayed(changer, 3000);


                        }
                    } catch (Throwable t) {
                        Log.e(TAG, t.getMessage(), t);
                    }
                }

                @Override
                public void onAccuracyChanged(Sensor sensor, int accuracy) {

                }
            };

            sensorManager.registerListener(
                    lightSensorListener,
                    lightSensor,
                    SensorManager.SENSOR_DELAY_NORMAL);
        }

    }

    private void unlistenLight() {
        try {
            if (lightSensorListener != null) {
                SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
                sensorManager.unregisterListener(lightSensorListener);
                lightSensorListener = null;
            }
        }  catch (Throwable t) {
            Log.e(TAG, t.getMessage(), t);
        }
    }


    private void showBrightnessControl() {
        if (book==null) return;

        PopupMenu bmenu = new PopupMenu(this, findViewById(R.id.brightness_button));
        int bg = book.getBackgroundColor();

        MenuItem norm = bmenu.getMenu().add(R.string.book_default);

        if (bg == Integer.MAX_VALUE) {
            norm.setCheckable(true);
            norm.setChecked(true);
        }

        norm.setOnMenuItemClickListener(item -> {
            unlistenLight();
            saveScrollOffset();
            book.clearBackgroundColor();
            resetColor();
            webView.reload();
            return true;
        });


        if (hasLightSensor) {
            MenuItem auto = bmenu.getMenu().add(getString(R.string.auto_bright));

            if (bg == Color.TRANSPARENT) {
                auto.setCheckable(true);
                auto.setChecked(true);
            }

            auto.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    book.setBackgroundColor(Color.TRANSPARENT);
                    restoreBgColor();
                    return true;
                }
            });

        }


        for (int i = 0; i<7; i++) {
            int b = i*33;
            final int color = Color.argb(255, 255-b, 250-b, 250-i-b);
            String strcolor;
            switch (i) {
                case 0:
                    strcolor = (i+1) + " - " + getString(R.string.bright);
                    break;
                case 3:
                    strcolor = (i+1) + " - " + getString(R.string.bright_medium);
                    break;
                case 6:
                    strcolor = (i+1) + " - " + getString(R.string.dim);
                    break;
                default:
                    strcolor = (i+1) + "";

            }

            MenuItem m = bmenu.getMenu().add(strcolor);
            m.setIcon(new ColorDrawable(color));
            if (bg==color) {
                m.setCheckable(true);
                m.setChecked(true);
            }

            m.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    unlistenLight();
                    book.setBackgroundColor(color);
                    restoreBgColor();
                    return true;
                }
            });
        }
        bmenu.show();
    }

    private void restoreBgColor() {
        if (book != null && book.hasDataDir()) {
            int bgColor = book.getBackgroundColor();
            switch (bgColor) {
                case Color.TRANSPARENT:
                    listenLight();
                    break;
                case Integer.MAX_VALUE:
                    unlistenLight();
                    resetColor();
                    //book.clearBackgroundColor();
                    //webView.reload();
                    break;
                default:
                    unlistenLight();
                    applyColor(bgColor, Color.BLACK);
            }
        }
    }

    private void resetColor() {
        applyColor(
                Color.parseColor("#FF31363F"),
                Color.parseColor("#FFEEEEEE"));
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void applyColor(int bgColor, int fgColor) {
        try {

            ViewGroup controls = findViewById(R.id.controls_layout);
            setDimLevel(controls, bgColor, fgColor);
            for (int i = 0; i < controls.getChildCount(); i++) {
                View button = controls.getChildAt(i);
                setDimLevel(button, bgColor, fgColor);
            }

            ViewGroup extraControls = findViewById(R.id.slide_menu);
            for (int i = 0; i < extraControls.getChildCount(); i++) {
                View button = extraControls.getChildAt(i);
                setDimLevel(button, bgColor, fgColor);
            }

            ReaderActivity.this.getWindow().setBackgroundDrawable(null);
            webView.setBackgroundColor(bgColor);
            ReaderActivity.this.getWindow().setBackgroundDrawable(new ColorDrawable(bgColor));

            Log.d("bgCssColorCode", String.format("#%6X", bgColor & 0xFFFFFF));
            Log.d("fgCssColorCode", String.format("#%6X", fgColor & 0xFFFFFF));
            webView.getSettings().setJavaScriptEnabled(true);
            webView.evaluateJavascript("(function(){var newSS, styles='* { background: " + String.format("#%6X", bgColor & 0xFFFFFF) + " ! important; color: " + String.format("#%6X", fgColor & 0xFFFFFF) + " !important } :link, :link * { color: #000088 !important } :visited, :visited * { color: #44097A !important }'; if(document.createStyleSheet) {document.createStyleSheet(\"javascript:'\"+styles+\"'\");} else { newSS=document.createElement('link'); newSS.rel='stylesheet'; newSS.href='data:text/css,'+escape(styles); document.getElementsByTagName(\"head\")[0].appendChild(newSS); } })();", null);
            webView.getSettings().setJavaScriptEnabled(false);

        } catch (Throwable t) {
            Log.e(TAG, t.getMessage(), t);
            Toast.makeText(this, t.getLocalizedMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setDimLevel(View view, int bgColor, int fgColor) {
        try {
            Drawable btnDrawable = ResourcesCompat.getDrawable(
                    getResources(),
                    android.R.drawable.btn_default,
                    null);

            if (btnDrawable != null) {
                Drawable mutDrawable = btnDrawable.mutate();
                mutDrawable.setColorFilter(bgColor, PorterDuff.Mode.MULTIPLY);
                view.setBackground(mutDrawable);
            } else {
                view.setBackground(null);
            }

            if (view instanceof ImageButton imageButton) {
                imageButton.getDrawable().mutate().setColorFilter(fgColor, PorterDuff.Mode.MULTIPLY);
            }

            if (view instanceof Button button) {
                button.setTextColor(fgColor);
            }
        } catch (Throwable t) {
            Log.e(TAG, t.getMessage(), t);
        }
    }
}
