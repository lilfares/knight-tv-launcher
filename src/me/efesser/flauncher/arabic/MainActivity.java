/*
 * Arabic TV, derived from the native app-discovery/launching approach in FLauncher.
 * FLauncher copyright (C) 2021 Étienne Fesser.
 * Arabic native interface and additions, 2026.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package me.efesser.flauncher.arabic;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.LinearGradient;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;

public class MainActivity extends Activity {
    private static final int BG = 0xff19172e, FG = 0xfff5f6fa;
    private static final int MUTED = 0xffd5c7e2, CARD = 0xff302544, FOCUS = 0xff584168;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<App> apps = new ArrayList<>();
    private final List<String> favorites = new ArrayList<>();
    private final Map<String, View> targets = new LinkedHashMap<>();
    private final Map<String, App> appCache = new LinkedHashMap<>();
    private final List<RowGroup> appGroups = new ArrayList<>();
    private final List<Tile> deviceTiles = new ArrayList<>();
    private LinearLayout root;
    private AmbientScreen ambient;
    private boolean resumed, previewAmbient;
    private SharedPreferences prefs;
    private TextView title, subtitle, clock;
    private LinearLayout content;
    private LinearLayout scrollingPage;
    private ScrollView scroll;
    private View allToggle;
    private int artHeight, savedScroll;
    private boolean refreshPending, restoring;
    private RowGroup activeGroup;
    private boolean loaded, destroyed, loading, expanded;
    private boolean motion;
    private String lastFocus = "hdmi1";
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (clock != null) clock.setText(DateFormat.getTimeInstance(DateFormat.SHORT, new Locale("ar")).format(new Date()));
            handler.postDelayed(this, 60000 - System.currentTimeMillis() % 60000);
        }
    };
    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { loadApps(); }
    };

    @Override protected void attachBaseContext(Context base) {
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(new Locale("ar"));
        config.setLayoutDirection(new Locale("ar"));
        super.attachBaseContext(base.createConfigurationContext(config));
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        prefs = getSharedPreferences("launcher", MODE_PRIVATE);
        motion = !prefs.getBoolean("reduceMotion", false);
        lastFocus = prefs.getString("lastFocus", "hdmi1");
        savedScroll = prefs.getInt("scrollY", 0);
        if(state!=null) { lastFocus=state.getString("focus",lastFocus); savedScroll=state.getInt("scroll",savedScroll); }
        try {
            JSONArray array = new JSONArray(prefs.getString("favorites", "[]"));
            for (int i=0; i<array.length(); i++) favorites.add(array.getString(i));
        } catch (Exception ignored) { }
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        registerReceiver(packages, filter);
        build();
        loadApps();
    }

    @Override protected void onResume() {
        super.onResume();
        resumed=true;
        if(ambient!=null)ambient.setHostActive(hasWindowFocus());
        handler.removeCallbacks(tick);
        tick.run();
        if(refreshPending && loaded) { refreshPending=false; build(); }
    }
    @Override protected void onPause() {
        resumed=false;
        if(ambient!=null)ambient.setHostActive(false);
        handler.removeCallbacks(tick);
        for(View target:targets.values()) if(target instanceof Tile) ((Tile)target).cancelHold();
        prefs.edit().putString("lastFocus",lastFocus).putInt("scrollY",scroll==null?0:scroll.getScrollY()).apply();
        super.onPause();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("focus",lastFocus); state.putInt("scroll",scroll==null?0:scroll.getScrollY());
        super.onSaveInstanceState(state);
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); }
    @Override public void onBackPressed() {
        // This activity is now a Home screen: EXIT must not finish it and reveal another launcher.
        if(scroll!=null && scroll.getScrollY()>0 && !appGroups.isEmpty()) {
            appGroups.get(0).tiles.get(0).requestFocus(); scroll.smoothScrollTo(0,0);
        } else if(!deviceTiles.isEmpty()) { deviceTiles.get(0).requestFocus(); }
    }
    @Override public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if(ambient!=null) {
            ambient.setHostActive(resumed&&focus);
            if(focus&&resumed&&previewAmbient) {previewAmbient=false;ambient.preview();}
        }
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if(ambient!=null&&ambient.interceptKey(event))return true;
        return super.dispatchKeyEvent(event);
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if(ambient!=null&&ambient.interceptTouch(event))return true;
        return super.dispatchTouchEvent(event);
    }
    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if(ambient!=null&&ambient.interceptMotion(event))return true;
        return super.dispatchGenericMotionEvent(event);
    }
    @Override public boolean onKeyDown(int key,KeyEvent event) {
        if(key==KeyEvent.KEYCODE_MENU || key==KeyEvent.KEYCODE_ESCAPE) return true;
        return super.onKeyDown(key,event);
    }
    @Override public boolean onKeyUp(int key,KeyEvent event) {
        if(key==KeyEvent.KEYCODE_MENU) {
            View focused=getCurrentFocus();
            String focusKey=focused!=null && focused.getTag() instanceof String?(String)focused.getTag():"";
            int separator=focusKey.indexOf(':');
            App app=separator<0?null:find(focusKey.substring(separator+1));
            if(app!=null) appMenu(app); else options();
            return true;
        }
        if(key==KeyEvent.KEYCODE_ESCAPE) { onBackPressed(); return true; }
        return super.onKeyUp(key,event);
    }
    @Override protected void onDestroy() {
        destroyed = true;
        if(ambient!=null)ambient.close();
        handler.removeCallbacksAndMessages(null);
        unregisterReceiver(packages);
        worker.shutdownNow();
        super.onDestroy();
    }

    private int dp(float value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setFontFeatureSettings("kern");
        view.setIncludeFontPadding(false);
        view.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        view.setFocusable(false);
        return view;
    }
    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view;
    }
    private GradientDrawable shape(int color, int stroke) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(13));
        if (stroke != 0) drawable.setStroke(dp(2), stroke);
        return drawable;
    }

    private void createShell() {
        root=column(); root.setBackground(new TwilightBackground());
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(dp(48),dp(27),dp(48),dp(22));
        root.setClipChildren(true);
        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand=text("الرئيسية",19,FG); brand.setTypeface(null,Typeface.BOLD);
        top.addView(brand,new LinearLayout.LayoutParams(0,dp(32),1));
        clock=text("",16,MUTED); top.addView(clock);
        TextView optionsButton=text("خيارات الواجهة",13,FG);
        optionsButton.setPadding(dp(16),0,dp(16),0); optionsButton.setFocusable(true);
        optionsButton.setId(View.generateViewId()); optionsButton.setTag("options");
        optionsButton.setBackground(shape(CARD,0)); optionsButton.setOnClickListener(v -> options());
        optionsButton.setOnFocusChangeListener((v,focus) -> {
            optionsButton.setBackground(shape(focus?FOCUS:CARD,focus?FG:0));
            if(focus) {lastFocus="options";title.setText("خيارات الواجهة");subtitle.setText("الحركة والمفضلة ومعلومات الواجهة");}
        });
        LinearLayout.LayoutParams optionParams=new LinearLayout.LayoutParams(-2,dp(32));
        optionParams.setMarginStart(dp(20));top.addView(optionsButton,optionParams);
        targets.put("options",optionsButton);root.addView(top);
        TextView deviceHeading=text("الأجهزة والإعدادات",14,MUTED);
        LinearLayout.LayoutParams headingParams=new LinearLayout.LayoutParams(-1,dp(24));
        headingParams.topMargin=dp(10);
        scrollingPage=column();scrollingPage.addView(deviceHeading,headingParams);
        LinearLayout shortcuts=row();
        for(int i=1;i<=3;i++) {
            final int port=i;
            Tile tile=new Tile("HDMI "+i,"فتح الجهاز المتصل بالمدخل "+i,"hdmi"+i,1,null,false,() -> hdmi(port),null);
            deviceTiles.add(tile);addCell(shortcuts,tile,i-1,dp(62));
        }
        Tile tv=new Tile("إعدادات التلفزيون","قائمة التلفزيون المعتادة","tvSettings",2,null,false,
            () -> start(new Intent(Settings.ACTION_SETTINGS)),null);
        Tile android=new Tile("إعدادات أندرويد","إعدادات أندرويد الكاملة","androidSettings",3,null,false,
            () -> start(new Intent().setComponent(new ComponentName("com.android.settings","com.android.settings.homepage.SettingsHomepageActivity"))),null);
        deviceTiles.add(tv);deviceTiles.add(android);addCell(shortcuts,tv,3,dp(62));addCell(shortcuts,android,4,dp(62));
        scrollingPage.addView(shortcuts);wireHorizontal(deviceTiles);
        title=text("أهلًا بك",28,FG);title.setTypeface(null,Typeface.BOLD);
        title.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);title.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        title.setSingleLine(true);title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams=new LinearLayout.LayoutParams(-1,dp(42));titleParams.topMargin=dp(12);
        root.addView(title,titleParams);
        subtitle=text("كل ما تحب، في مكان واحد",14,MUTED);subtitle.setSingleLine(true);subtitle.setEllipsize(TextUtils.TruncateAt.END);
        root.addView(subtitle,new LinearLayout.LayoutParams(-1,dp(25)));
        scroll=new ScrollView(this) {
            @Override public boolean requestChildRectangleOnScreen(View child,android.graphics.Rect rectangle,boolean immediate) {
                return false; // Focused rows are positioned explicitly; content never moves the fixed header.
            }
        };
        scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(true);scroll.setClipToPadding(true);scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setPadding(0,dp(4),0,dp(4));
        content=column();content.setClipChildren(false);content.setClipToPadding(false);
        content.setPadding(dp(7),dp(6),dp(7),dp(16));
        scrollingPage.addView(content,new LinearLayout.LayoutParams(-1,-2));
        scroll.addView(scrollingPage,new ScrollView.LayoutParams(-1,-2));
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));


        float widthDp=getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density;
        float tileWidth=(widthDp-96-14-4*20)/5f;
        artHeight=dp(tileWidth*9f/16f);
        FrameLayout host=new FrameLayout(this);
        host.addView(root,new FrameLayout.LayoutParams(-1,-1));
        ambient=new AmbientScreen(this,prefs);
        host.addView(ambient,new FrameLayout.LayoutParams(-1,-1));
        setContentView(host);
        clock.setText(DateFormat.getTimeInstance(DateFormat.SHORT,new Locale("ar")).format(new Date()));
    }

    private void build() {
        int previousScroll=scroll==null?savedScroll:scroll.getScrollY();
        if(root==null) createShell();
        restoring=true;activeGroup=null;
        for(View view:new ArrayList<>(targets.values())) if(view instanceof Tile) ((Tile)view).cancelHold();
        targets.entrySet().removeIf(e -> e.getKey().contains(":"));
        targets.remove("allToggle");appGroups.clear();content.removeAllViews();
        List<App> pinned=new ArrayList<>();
        for(String id:favorites) {App app=find(id);if(app!=null)pinned.add(app);}
        if(pinned.isEmpty()) {
            section("المفضلة");
            TextView hint=text("زر MENU على أي تطبيق لإضافته إلى المفضلة وترتيبه",14,MUTED);
            content.addView(hint,new LinearLayout.LayoutParams(-1,dp(32)));gap(12);
        } else appRows(pinned,"fav","المفضلة");
        List<App> frequent=new ArrayList<>();
        for(App app:apps) if(!favorites.contains(app.id)&&count(app)>0)frequent.add(app);
        Collections.sort(frequent,(a,b) -> {
            int value=Integer.compare(count(b),count(a));
            return value!=0?value:Long.compare(prefs.getLong("last."+b.id,0),prefs.getLong("last."+a.id,0));
        });
        if(!frequent.isEmpty())appRows(frequent.subList(0,Math.min(5,frequent.size())),"frequent","الأكثر استخدامًا");
        LinearLayout appsHeader=row();appsHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading=text("التطبيقات",17,MUTED);appsHeader.addView(heading,new LinearLayout.LayoutParams(0,dp(34),1));
        TextView toggle=text(expanded?"عرض أقل":"عرض الكل",14,FG);
        toggle.setPadding(dp(16),dp(6),dp(16),dp(6));toggle.setFocusable(true);
        toggle.setId(View.generateViewId());toggle.setTag("allToggle");allToggle=toggle;
        toggle.setBackground(shape(CARD,0));
        toggle.setOnFocusChangeListener((v,focus) -> {
            toggle.setBackground(shape(focus?FOCUS:CARD,focus?FG:0));
            if(focus) {lastFocus="allToggle";title.setText("التطبيقات");subtitle.setText(expanded?"جميع التطبيقات":"التطبيقات الرئيسية");
                if(!restoring) scroll.post(() -> scroll.smoothScrollTo(0,Math.max(0,content.getTop()+appsHeader.getTop()-dp(50))));}
        });
        toggle.setOnClickListener(v -> {expanded=!expanded;lastFocus="allToggle";build();});
        targets.put("allToggle",toggle);appsHeader.addView(toggle);content.addView(appsHeader);gap(8);
        if(!loaded)content.addView(text("جارٍ تحميل التطبيقات…",16,MUTED));
        List<App> display=new ArrayList<>();
        for(App app:apps)if(expanded||(app.primary&&!app.id.equals("me.efesser.flauncher")))display.add(app);
        appRows(expanded?display:display.subList(0,Math.min(10,display.size())),"all",null);
        wireVertical();
        final int restoreY=previousScroll;
        root.post(() -> {
            if(destroyed)return;
            View focus=targets.get(lastFocus);
            if(focus==null)focus=deviceTiles.get(0);
            focus.requestFocus();scroll.scrollTo(0,restoreY);restoring=false;
        });
    }

    private LinearLayout row() {
        LinearLayout row=new LinearLayout(this);row.setClipChildren(false);row.setClipToPadding(false);return row;
    }
    private void section(String value) {
        TextView label=text(value,17,MUTED);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(28));p.bottomMargin=dp(6);content.addView(label,p);
    }
    private void gap(int size) {content.addView(new View(this),new LinearLayout.LayoutParams(1,dp(size)));}
    private void addCell(LinearLayout row,View view,int index,int height) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,height,1);
        if(index>0)p.setMarginStart(dp(20));row.addView(view,p);
    }
    private void appRows(List<App> entries,String prefix,String heading) {
        for(int i=0;i<entries.size();i+=5) {
            RowGroup group=new RowGroup(appGroups.size());group.setTag(prefix);
            if(i==0&&heading!=null) {
                TextView label=text(heading,17,MUTED);
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(28));p.bottomMargin=dp(6);group.addView(label,p);
            }
            LinearLayout row=row();
            for(int j=0;j<5;j++) {
                if(i+j>=entries.size()) {addCell(row,new View(this),j,artHeight+dp(30));continue;}
                App app=entries.get(i+j);
                LinearLayout cell=column();cell.setClipChildren(false);cell.setClipToPadding(false);
                Tile tile=new Tile(app.name,favorites.contains(app.id)?"من تطبيقاتك المفضلة":"ENTER للفتح · MENU للخيارات",
                    prefix+":"+app.id,0,app.image,app.banner,() -> launch(app),() -> appMenu(app));
                tile.group=group;tile.softBitmap=app.soft;group.tiles.add(tile);
                cell.addView(tile,new LinearLayout.LayoutParams(-1,artHeight));
                TextView name=text(app.name,13,FG);name.setGravity(Gravity.CENTER);name.setSingleLine(true);name.setEllipsize(TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(24));p.topMargin=dp(6);cell.addView(name,p);
                addCell(row,cell,j,artHeight+dp(30));
            }
            group.addView(row);content.addView(group);gap(16);appGroups.add(group);wireHorizontal(group.tiles);
        }
    }

    private void wireHorizontal(List<Tile> tiles) {
        for(int i=0;i<tiles.size();i++) {
            Tile tile=tiles.get(i);
            tile.setNextFocusLeftId(tiles.get(Math.min(tiles.size()-1,i+1)).getId());
            tile.setNextFocusRightId(tiles.get(Math.max(0,i-1)).getId());
        }
    }
    private void wireVertical() {
        for(int i=0;i<deviceTiles.size();i++) {
            Tile device=deviceTiles.get(i);device.setNextFocusUpId(targets.get("options").getId());
            device.setNextFocusDownId(appGroups.isEmpty()?allToggle.getId():appGroups.get(0).tiles.get(Math.min(i,appGroups.get(0).tiles.size()-1)).getId());
        }
        for(int row=0;row<appGroups.size();row++) {
            List<Tile> above=row==0?deviceTiles:appGroups.get(row-1).tiles;
            List<Tile> below=row==appGroups.size()-1?appGroups.get(row).tiles:appGroups.get(row+1).tiles;
            for(int col=0;col<appGroups.get(row).tiles.size();col++) {
                Tile tile=appGroups.get(row).tiles.get(col);
                tile.setNextFocusUpId(above.get(Math.min(col,above.size()-1)).getId());
                tile.setNextFocusDownId(below.get(Math.min(col,below.size()-1)).getId());
            }
        }
        targets.get("options").setNextFocusDownId(deviceTiles.get(4).getId());
        for(int i=0;i<appGroups.size();i++) {
            RowGroup group=appGroups.get(i);
            if("all".equals(group.getTag())) {
                for(Tile tile:group.tiles)tile.setNextFocusUpId(allToggle.getId());
                allToggle.setNextFocusDownId(group.tiles.get(0).getId());
                allToggle.setNextFocusUpId(i==0?deviceTiles.get(4).getId():appGroups.get(i-1).tiles.get(0).getId());
                break;
            }
        }
    }
    private void focusRow(RowGroup selected) {
        activeGroup=selected;
        for(RowGroup group:appGroups)for(Tile tile:group.tiles)tile.setQuiet(selected!=null&&group.index<selected.index&&!group.getTag().equals(selected.getTag()));
        if(selected==null&&!restoring)scroll.post(() -> {
            if(activeGroup==null) {if(motion)scroll.smoothScrollTo(0,0);else scroll.scrollTo(0,0);}
        });
        if(selected!=null&&!restoring)scroll.post(() -> {
            if(activeGroup!=selected||destroyed)return;
            int y=Math.max(0,content.getTop()+selected.getTop()-dp(55));
            if(motion)scroll.smoothScrollTo(0,y);else scroll.scrollTo(0,y);
        });
    }

    private int count(App app) { return prefs.getInt("count."+app.id,0); }
    private App find(String id) { for(App app:apps) if(app.id.equals(id)) return app; return null; }
    private void launch(App app) {
        Intent intent=getPackageManager().getLeanbackLaunchIntentForPackage(app.id);
        if(intent==null) intent=getPackageManager().getLaunchIntentForPackage(app.id);
        if(start(intent)) {
            prefs.edit().putInt("count."+app.id,count(app)+1).putLong("last."+app.id,System.currentTimeMillis()).apply();
            refreshPending=true;
        }
    }
    private boolean start(Intent intent) {
        try {
            if(intent==null) throw new IllegalArgumentException();
            startActivity(intent); return true;
        } catch(Exception ex) { Toast.makeText(this,"تعذّر فتح هذا الخيار",Toast.LENGTH_SHORT).show(); return false; }
    }
    private void hdmi(int port) {
        // Verified against this TV's CtvSourceId constants: HDMI=9, HDMI2=10, HDMI3=11.
        start(new Intent(Intent.ACTION_VIEW).setComponent(new ComponentName("com.cultraview.livetv","com.cultraview.livetv.MainActivity"))
            .putExtra("sourceId",8+port).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }

    private void appMenu(App app) {
        int index=favorites.indexOf(app.id);
        List<String> labels=new ArrayList<>(),actions=new ArrayList<>();
        labels.add(index<0?"إضافة إلى المفضلة":"إزالة من المفضلة"); actions.add("pin");
        if(index>0) { labels.add("تقديم في المفضلة"); actions.add("earlier"); }
        if(index>=0 && index<favorites.size()-1) { labels.add("تأخير في المفضلة"); actions.add("later"); }
        labels.add("معلومات التطبيق"); actions.add("info");
        new AlertDialog.Builder(this).setTitle(app.name).setItems(labels.toArray(new String[0]),(dialog,which) -> {
            String action=actions.get(which);
            if(action.equals("info")) {
                start(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+app.id))); return;
            }
            if(action.equals("pin")) { if(index<0) favorites.add(app.id); else favorites.remove(index); }
            if(action.equals("earlier")) Collections.swap(favorites,index,index-1);
            if(action.equals("later")) Collections.swap(favorites,index,index+1);
            prefs.edit().putString("favorites",new JSONArray(favorites).toString()).apply();
            build();
        }).setNegativeButton("إغلاق",null).show();
    }

    private void options() {
        String[] items={motion?"تقليل الحركة":"تفعيل الحركة الخفيفة","تصفير الأكثر استخدامًا","حول الواجهة","شاشة الاستراحة"};
        new AlertDialog.Builder(this).setTitle("خيارات الواجهة").setItems(items,(dialog,which) -> {
            if(which==0) {
                motion=!motion;prefs.edit().putBoolean("reduceMotion",!motion).apply();
                for(View view:targets.values())if(view instanceof Tile)((Tile)view).focusStyle(view.hasFocus());
            }
            if(which==1) {
                new AlertDialog.Builder(this).setMessage("تصفير سجل الأكثر استخدامًا؟").setPositiveButton("تصفير",(d,w) -> {
                    SharedPreferences.Editor edit=prefs.edit();
                    for(String key:prefs.getAll().keySet()) if(key.startsWith("count.")||key.startsWith("last.")) edit.remove(key);
                    edit.apply(); build();
                }).setNegativeButton("إلغاء",null).show();
            }
            if(which==3) ambientOptions();
            if(which==2) new AlertDialog.Builder(this).setTitle("Knight TV Launcher — 1.3")
                .setMessage("واجهة عربية خفيفة.\nالمفضلة بالترتيب الذي تختاره.\nالأكثر استخدامًا يتعلّم من التطبيقات التي تفتحها هنا.\n\nمستوحاة من FLauncher\n© 2021 Étienne Fesser\nGPL-3.0-or-later\ngitlab.com/flauncher/flauncher")
                .setPositiveButton("حسنًا",null).show();
        }).setNegativeButton("إغلاق",null).show();
    }

    private void ambientOptions() {
        String[] items={prefs.getBoolean("ambientEnabled",true)?"إيقاف شاشة الاستراحة":"تفعيل شاشة الاستراحة",
            "مدة الانتظار: "+prefs.getInt("ambientMinutes",5)+" دقائق",
            "تغيير الصورة: "+prefs.getInt("ambientRotateSeconds",120)+" ثانية","معاينة الآن"};
        new AlertDialog.Builder(this).setTitle("شاشة الاستراحة").setItems(items,(dialog,which) -> {
            if(which==0) {prefs.edit().putBoolean("ambientEnabled",!prefs.getBoolean("ambientEnabled",true)).apply();ambient.resetIdle();}
            if(which==1) new AlertDialog.Builder(this).setTitle("مدة الانتظار").setItems(new String[]{"1 دقيقة","3 دقائق","5 دقائق","10 دقائق"},(d,w) -> {
                prefs.edit().putInt("ambientMinutes",new int[]{1,3,5,10}[w]).apply();ambient.resetIdle();
            }).setNegativeButton("إلغاء",null).show();
            if(which==2) new AlertDialog.Builder(this).setTitle("تغيير الصورة").setItems(new String[]{"30 ثانية","60 ثانية","120 ثانية","300 ثانية"},(d,w) -> {
                prefs.edit().putInt("ambientRotateSeconds",new int[]{30,60,120,300}[w]).apply();
            }).setNegativeButton("إلغاء",null).show();
            if(which==3)previewAmbient=true;
        }).setNegativeButton("إغلاق",null).show();
    }

    private void loadApps() {
        if(loading||destroyed) return; loading=true;
        worker.execute(() -> {
            List<App> result=new ArrayList<>();
            try {
                PackageManager pm=getPackageManager();
                Map<String,ActivityInfo> found=new LinkedHashMap<>();
                for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER}) {
                    for(ResolveInfo info:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category),0)) {
                        ActivityInfo activity=info.activityInfo;
                        if(!activity.packageName.equals(getPackageName()) && activity.enabled && activity.exported && !found.containsKey(activity.packageName)) {
                            found.put(activity.packageName,activity);
                        }
                    }
                }
                for(ActivityInfo activity:found.values()) {
                    try {
                        long updated=pm.getPackageInfo(activity.packageName,0).lastUpdateTime;
                        App app=appCache.get(activity.packageName);
                        if(app==null||app.updated!=updated) {
                            Drawable drawable=activity.loadBanner(pm);
                            boolean isBanner=drawable!=null;
                            if(drawable==null) drawable=activity.loadIcon(pm);
                            boolean primary=isBanner || (activity.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM)==0;
                            Bitmap image=bitmap(drawable,isBanner);
                            app=new App(activity.packageName,activity.loadLabel(pm).toString(),image,soften(image),isBanner,primary,updated);
                            appCache.put(app.id,app);
                        }
                        result.add(app);
                    } catch(Exception ignored) { }
                }
                appCache.keySet().retainAll(found.keySet());
                Collections.sort(result,(a,b) -> a.name.compareToIgnoreCase(b.name));
            } catch(Exception ignored) { }
            handler.post(() -> {
                loading=false;
                if(destroyed) return;
                apps.clear(); apps.addAll(result); loaded=true; build();
            });
        });
    }

    private Bitmap bitmap(Drawable drawable,boolean banner) {
        if(drawable==null) return null;
        int maxW=banner?320:96,maxH=banner?180:96;
        int w=Math.max(1,drawable.getIntrinsicWidth()),h=Math.max(1,drawable.getIntrinsicHeight());
        float ratio=Math.min(1f,Math.min((float)maxW/w,(float)maxH/h));
        Bitmap bitmap=Bitmap.createBitmap(Math.max(1,Math.round(w*ratio)),Math.max(1,Math.round(h*ratio)),Bitmap.Config.ARGB_8888);
        drawable.setBounds(0,0,bitmap.getWidth(),bitmap.getHeight());drawable.draw(new Canvas(bitmap));
        if(!banner) {
            int bw=bitmap.getWidth(),bh=bitmap.getHeight();int[] pixels=new int[bw*bh];
            bitmap.getPixels(pixels,0,bw,0,0,bw,bh);
            int left=bw,right=-1,top=bh,bottom=-1;
            for(int y=0;y<bh;y++)for(int x=0;x<bw;x++)if((pixels[y*bw+x]>>>24)>20) {
                left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);
            }
            if(right>=left&&bottom>=top&&(left>0||top>0||right<bw-1||bottom<bh-1)) {
                Bitmap cropped=Bitmap.createBitmap(bitmap,left,top,right-left+1,bottom-top+1);bitmap.recycle();return cropped;
            }
        }
        return bitmap;
    }
    private Bitmap soften(Bitmap bitmap) {
        if(bitmap==null)return null;
        // Cached once on the worker. Bilinear upscaling supplies soft focus on Android 11, without a live blur shader.
        return Bitmap.createScaledBitmap(bitmap,Math.max(1,bitmap.getWidth()/10),Math.max(1,bitmap.getHeight()/10),true);
    }
    private static class App {
        final String id,name;final Bitmap image,soft;final boolean banner,primary;final long updated;
        App(String id,String name,Bitmap image,Bitmap soft,boolean banner,boolean primary,long updated) {
            this.id=id;this.name=name;this.image=image;this.soft=soft;this.banner=banner;this.primary=primary;this.updated=updated;
        }
    }

    private class RowGroup extends LinearLayout {
        final int index;final List<Tile> tiles=new ArrayList<>();
        RowGroup(int index) {super(MainActivity.this);this.index=index;setOrientation(VERTICAL);setClipChildren(false);setClipToPadding(false);}
    }

    private class TwilightBackground extends Drawable {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private Shader base,left,right,top;
        @Override protected void onBoundsChange(android.graphics.Rect bounds) {
            float w=Math.max(1,bounds.width()),h=Math.max(1,bounds.height());
            base=new LinearGradient(0,0,w,h,new int[]{0xff19172e,0xff30203d,0xff482a47},new float[]{0,.75f,1},Shader.TileMode.CLAMP);
            left=new RadialGradient(-.05f*w,.9f*h,.75f*w,new int[]{0xcc754a82,0x00754a82},null,Shader.TileMode.CLAMP);
            right=new RadialGradient(w,1.1f*h,.7f*w,new int[]{0xaaa75a70,0x00a75a70},null,Shader.TileMode.CLAMP);
            top=new RadialGradient(.7f*w,0,.6f*w,new int[]{0x66323668,0x00323668},null,Shader.TileMode.CLAMP);
        }
        @Override public void draw(Canvas canvas) {
            for(Shader shader:new Shader[]{base,left,right,top}) {paint.setShader(shader);canvas.drawRect(getBounds(),paint);}
        }
        @Override public void setAlpha(int alpha) {paint.setAlpha(alpha);}
        @Override public void setColorFilter(android.graphics.ColorFilter filter) {paint.setColorFilter(filter);}
        @Override public int getOpacity() {return android.graphics.PixelFormat.OPAQUE;}
    }

    private class Tile extends LinearLayout {
        private final TextView name;
        private final Bitmap normalBitmap;
        private Bitmap softBitmap;
        private ImageView artwork;
        private RowGroup group;
        private boolean quiet;
        private Runnable longAction;
        private boolean keyHeld, longTriggered;
        private final Runnable holdAction = () -> {
            if (keyHeld && !longTriggered && longAction != null) {
                longTriggered=true; setPressed(false); longAction.run();
            }
        };
        Tile(String label,String description,String key,int icon,Bitmap bitmap,boolean banner,Runnable click,Runnable longClick) {
            super(MainActivity.this); setOrientation(VERTICAL); setGravity(Gravity.CENTER);
            normalBitmap=bitmap;
            longAction=longClick;
            setId(View.generateViewId());setTag(key);
            setFocusable(true); setFocusableInTouchMode(true); setClickable(true); setClipToOutline(true);
            setBackground(shape(CARD,0)); setContentDescription(label); targets.put(key,this);
            if(icon>0) {
                ImageView image=new ImageView(MainActivity.this); image.setImageDrawable(new Symbol(icon));
                addView(image,new LinearLayout.LayoutParams(dp(23),dp(23)));
                name=text(label,13,FG); name.setGravity(Gravity.CENTER); name.setSingleLine(true); name.setEllipsize(TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(21));p.topMargin=dp(4);addView(name,p);
            } else {
                name=null;
                ImageView image=new ImageView(MainActivity.this);artwork=image;
                if(bitmap!=null) image.setImageBitmap(bitmap); else image.setImageDrawable(new Symbol(1));
                image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                if(banner) {
                    image.setPadding(dp(8),dp(8),dp(8),dp(8));addView(image,new LinearLayout.LayoutParams(-1,-1));
                } else {
                    int size=Math.min(dp(64),Math.round(artHeight*.72f));addView(image,new LinearLayout.LayoutParams(size,size));
                }
            }
            setOnClickListener(v -> click.run());
            if(longClick!=null) setOnLongClickListener(v -> { longClick.run(); return true; });
            GestureDetector pointer = new GestureDetector(MainActivity.this,new GestureDetector.SimpleOnGestureListener() {
                @Override public boolean onDown(MotionEvent event) { requestFocus(); return true; }
                @Override public boolean onSingleTapUp(MotionEvent event) { performClick(); return true; }
                @Override public void onLongPress(MotionEvent event) { if(longAction!=null) performLongClick(); }
            });
            setOnTouchListener((v,event) -> pointer.onTouchEvent(event));
            setOnFocusChangeListener((v,focus) -> {
                if(!focus) { handler.removeCallbacks(holdAction); setPressed(false); }
                focusStyle(focus);
                if(focus) {
                    lastFocus=key; title.setText(label); subtitle.setText(description);
                    focusRow(group);
                }
            });
        }
        void focusStyle(boolean focus) {
            setBackground(shape(focus?FOCUS:CARD,focus?FG:0));animate().cancel();
            float scale=focus&&motion&&group!=null?1.025f:1f;
            animate().scaleX(scale).scaleY(scale).setDuration(motion?150:0).setInterpolator(new DecelerateInterpolator()).start();
            setElevation(0);
        }
        void setQuiet(boolean value) {
            if(quiet==value)return;quiet=value;
            if(artwork!=null&&normalBitmap!=null) {
                Bitmap chosen=quiet&&softBitmap!=null?softBitmap:normalBitmap;
                BitmapDrawable drawable=new BitmapDrawable(getResources(),chosen);drawable.setFilterBitmap(true);
                artwork.setImageDrawable(drawable);artwork.setAlpha(quiet?.52f:1f);
            }
        }
        void cancelHold() {handler.removeCallbacks(holdAction);keyHeld=false;setPressed(false);}
        private boolean selectKey(int key) {
            return key==KeyEvent.KEYCODE_DPAD_CENTER || key==KeyEvent.KEYCODE_ENTER ||
                key==KeyEvent.KEYCODE_NUMPAD_ENTER || key==KeyEvent.KEYCODE_BUTTON_A;
        }
        @Override public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if(ambient!=null) {
            ambient.setHostActive(resumed&&focus);
            if(focus&&resumed&&previewAmbient) {previewAmbient=false;ambient.preview();}
        }
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if(ambient!=null&&ambient.interceptKey(event))return true;
        return super.dispatchKeyEvent(event);
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if(ambient!=null&&ambient.interceptTouch(event))return true;
        return super.dispatchTouchEvent(event);
    }
    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if(ambient!=null&&ambient.interceptMotion(event))return true;
        return super.dispatchGenericMotionEvent(event);
    }
    @Override public boolean onKeyDown(int key,KeyEvent event) {
            if(!selectKey(key)) return super.onKeyDown(key,event);
            if(event.getRepeatCount()==0) {
                keyHeld=true; longTriggered=false; setPressed(true);
                handler.removeCallbacks(holdAction);
                if(longAction!=null) handler.postDelayed(holdAction,650);
            } else if(event.isLongPress() && keyHeld && !longTriggered && longAction!=null) {
                handler.removeCallbacks(holdAction); longTriggered=true; setPressed(false); longAction.run();
            }
            return true;
        }
        @Override public boolean onKeyUp(int key,KeyEvent event) {
            if(!selectKey(key)) return super.onKeyUp(key,event);
            handler.removeCallbacks(holdAction); setPressed(false);
            if(keyHeld && !longTriggered && !event.isCanceled()) performClick();
            keyHeld=false; return true;
        }
    }

    private class Symbol extends Drawable {
        private final int kind;
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        Symbol(int kind) {this.kind=kind;}
        @Override public void draw(Canvas canvas) {
            canvas.save(); canvas.translate(getBounds().left,getBounds().top);
            canvas.scale(getBounds().width()/32f,getBounds().height()/32f);
            paint.setColor(FG);paint.setStrokeWidth(1.8f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStyle(Paint.Style.STROKE);
            if(kind==1) {
                Path p=new Path();p.moveTo(5,7);p.lineTo(27,7);p.lineTo(27,20);p.lineTo(23,25);p.lineTo(9,25);p.lineTo(5,20);p.close();canvas.drawPath(p,paint);
                for(int x=10;x<=22;x+=4) canvas.drawLine(x,12,x,16,paint);
            } else if(kind==2) {
                canvas.drawCircle(16,16,7,paint);canvas.drawCircle(16,16,2,paint);
                for(int i=0;i<8;i++) {canvas.save();canvas.rotate(i*45,16,16);canvas.drawLine(16,4,16,7,paint);canvas.restore();}
            } else {
                canvas.drawRoundRect(new RectF(7,12,25,25),3,3,paint);canvas.drawArc(new RectF(7,5,25,20),180,180,false,paint);
                canvas.drawLine(10,7,8,3,paint);canvas.drawLine(22,7,24,3,paint);
                canvas.drawPoint(12,11,paint);canvas.drawPoint(20,11,paint);
            }
            canvas.restore();
        }
        @Override public void setAlpha(int alpha) {paint.setAlpha(alpha);}
        @Override public void setColorFilter(android.graphics.ColorFilter filter) {paint.setColorFilter(filter);}
        @Override public int getOpacity() {return android.graphics.PixelFormat.TRANSLUCENT;}
    }
}
