package me.efesser.flauncher.arabic;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Home-only ambient screen. No service, receiver, network access, or system-setting changes. */
final class AmbientScreen extends FrameLayout {
    private final Activity activity;
    private final SharedPreferences prefs;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService decoder=Executors.newSingleThreadExecutor();
    private final ImageView picture;
    private final TextView time, gregorian, hijri;
    private final Set<Integer> swallowedKeys=new HashSet<>();
    private boolean active, showing, closed, swallowTouch, decoding;
    private int generation, scene=-1;
    private static final int[] SCENES={R.raw.scene_mountains,R.raw.scene_city,R.raw.scene_coast,
        R.raw.scene_dunes,R.raw.scene_forest,R.raw.scene_lake};
    private Bitmap bitmap;
    private View previousFocus;
    private final Runnable idle=() -> show();
    private final Runnable rotate=new Runnable() {
        @Override public void run() {if(showing&&active)loadNext();}
    };
    private final Runnable clockTick=new Runnable() {
        @Override public void run() {
            if(!showing||!active)return;
            updateClock();
            handler.postDelayed(this,60000-System.currentTimeMillis()%60000);
        }
    };

    AmbientScreen(Activity activity,SharedPreferences prefs) {
        super(activity);this.activity=activity;this.prefs=prefs;
        setBackgroundColor(Color.BLACK);setVisibility(GONE);setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        picture=new ImageView(activity);picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
        addView(picture,new LayoutParams(-1,-1));
        View shade=new View(activity);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{0x00000000,0x08000000,0x99000000}));
        addView(shade,new LayoutParams(-1,-1));
        LinearLayout labels=new LinearLayout(activity);labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.RIGHT);labels.setPadding(0,0,dp(72),dp(54));
        time=label(66,0xfff3f1ed);time.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));
        // Explicit LTR isolates Western digits while the date lines remain RTL Arabic.
        time.setTextDirection(TEXT_DIRECTION_LTR);
        gregorian=label(19,0xffe2dfda);hijri=label(18,0xffd6d3ce);
        gregorian.setTextDirection(TEXT_DIRECTION_RTL);hijri.setTextDirection(TEXT_DIRECTION_RTL);
        labels.addView(time);labels.addView(gregorian);labels.addView(hijri);
        LayoutParams lp=new LayoutParams(-2,-2,Gravity.RIGHT|Gravity.BOTTOM);addView(labels,lp);
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView label(int size,int color) {
        TextView t=new TextView(activity);t.setTextSize(size);t.setTextColor(color);
        t.setGravity(Gravity.RIGHT);t.setIncludeFontPadding(false);t.setPadding(0,dp(5),0,0);
        t.setShadowLayer(dp(3),0,dp(1),0x66000000);return t;
    }
    void setHostActive(boolean value) {
        if(closed)return;
        active=value;
        if(!active){hide(false);handler.removeCallbacks(idle);}
        else resetIdle();
    }
    void resetIdle() {
        handler.removeCallbacks(idle);
        if(active&&!showing&&!closed&&prefs.getBoolean("ambientEnabled",true))
            handler.postDelayed(idle,prefs.getInt("ambientMinutes",5)*60000L);
    }
    boolean interceptKey(KeyEvent e) {
        int key=e.getKeyCode();
        if(swallowedKeys.contains(key)) {
            if(e.getAction()==KeyEvent.ACTION_UP)swallowedKeys.remove(key);
            return true;
        }
        if(showing) {
            if(e.getAction()==KeyEvent.ACTION_DOWN)swallowedKeys.add(key);
            hide(true);return true;
        }
        resetIdle();return false;
    }
    boolean interceptTouch(MotionEvent e) {
        if(showing) {swallowTouch=true;hide(true);}
        if(swallowTouch) {
            if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL)swallowTouch=false;
            return true;
        }
        resetIdle();return false;
    }
    boolean interceptMotion(MotionEvent e) {
        if(showing){hide(true);return true;}
        resetIdle();return false;
    }
    void preview(){if(active&&!closed)show();}
    private void show() {
        if(!active||closed||showing)return;
        showing=true;generation++;previousFocus=activity.getCurrentFocus();
        handler.removeCallbacks(idle);setVisibility(VISIBLE);
        // Keep the TV awake only while this Home-owned slideshow is actually visible.
        setKeepScreenOn(true);clockTick.run();loadNext();
    }
    private void hide(boolean restoreFocus) {
        showing=false;generation++;handler.removeCallbacks(rotate);handler.removeCallbacks(clockTick);
        picture.animate().cancel();setVisibility(GONE);setKeepScreenOn(false);
        picture.setImageDrawable(null);
        bitmap=null;
        if(restoreFocus&&previousFocus!=null&&previousFocus.isAttachedToWindow())previousFocus.requestFocus();
        previousFocus=null;
        resetIdle();
    }
    private void updateClock() {
        Date now=new Date();
        time.setText(new SimpleDateFormat("HH:mm",Locale.US).format(now));
        gregorian.setText(western(new SimpleDateFormat("EEEE، d MMMM yyyy",new Locale("ar")).format(now))+" م");
        android.icu.text.SimpleDateFormat islamic=new android.icu.text.SimpleDateFormat("d MMMM yyyy",
            new android.icu.util.ULocale("ar_SA@calendar=islamic-umalqura;numbers=latn"));
        islamic.setTimeZone(android.icu.util.TimeZone.getDefault());
        hijri.setText(western(islamic.format(now))+" هـ");
    }
    private static String western(String value) {
        StringBuilder out=new StringBuilder();
        for(int i=0;i<value.length();i++) {
            char c=value.charAt(i);int n=Character.digit(c,10);out.append(n>=0?(char)('0'+n):c);
        }
        return out.toString();
    }
    private void loadNext() {
        if(decoding||!showing||!active)return;
        decoding=true;final int token=generation;final int next=(scene+1)%SCENES.length;
        decoder.execute(() -> {
            Bitmap decoded=null;
            try {
                BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
                try(InputStream input=getResources().openRawResource(SCENES[next])) {BitmapFactory.decodeStream(input,null,bounds);}
                BitmapFactory.Options options=new BitmapFactory.Options();options.inPreferredConfig=Bitmap.Config.RGB_565;
                options.inSampleSize=1;
                // Keep crisp Full HD detail without allocating a full 4K bitmap on a 1 GB TV.
                while(bounds.outWidth/(options.inSampleSize*2)>=1920&&bounds.outHeight/(options.inSampleSize*2)>=1080)options.inSampleSize*=2;
                try(InputStream input=getResources().openRawResource(SCENES[next])) {decoded=BitmapFactory.decodeStream(input,null,options);}
            } catch(Exception ignored) { }
            final Bitmap ready=decoded;
            handler.post(() -> {
                decoding=false;
                if(closed||token!=generation||!showing||!active) {
                    if(ready!=null)ready.recycle();
                    if(!closed&&showing&&active)loadNext();return;
                }
                if(ready!=null) {
                    bitmap=ready;picture.setImageBitmap(ready);scene=next;
                    // Let Android release displayed bitmaps after rendering completes.
                    if(!prefs.getBoolean("reduceMotion",false)) {picture.setAlpha(.35f);picture.animate().alpha(1f).setDuration(700).start();}
                    else picture.setAlpha(1f);
                }
                handler.postDelayed(rotate,prefs.getInt("ambientRotateSeconds",120)*1000L);
            });
        });
    }
    void close() {
        active=false;closed=true;hide(false);handler.removeCallbacksAndMessages(null);decoder.shutdownNow();
    }
}
