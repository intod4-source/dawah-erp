package com.nazarvpn.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.wireguard.android.backend.GoBackend;
import com.wireguard.android.backend.Statistics;
import com.wireguard.android.backend.Tunnel;
import com.wireguard.config.Config;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQ_VPN = 1001;
    private static final int REQ_CONFIG = 1002;
    private static final String PREFS = "nazarvpn";

    private static final int NAV_HOME = 0;
    private static final int NAV_LOCATIONS = 1;
    private static final int NAV_PREMIUM = 2;
    private static final int NAV_SETTINGS = 3;

    private final String[] freeNames = {"Singapore", "Germany", "Netherlands"};
    private final String[] freeFlags = {"🇸🇬", "🇩🇪", "🇳🇱"};
    private final String[] keys = {"sg", "de", "nl"};
    private final String[] tunnelNames = {"nazar_sg", "nazar_de", "nazar_nl"};
    private final String[] paidNames = {"United States", "United Kingdom", "France", "Canada"};
    private final String[] paidFlags = {"🇺🇸", "🇬🇧", "🇫🇷", "🇨🇦"};
    private final String[] premiumNames = {"Japan", "UAE", "Australia", "Switzerland"};
    private final String[] premiumFlags = {"🇯🇵", "🇦🇪", "🇦🇺", "🇨🇭"};

    private FrameLayout content;
    private LinearLayout navBar;
    private TextView connectionStatus, timerText, trafficText, powerIcon;
    private Button powerButton;
    private int selectedFreeIndex = 0;
    private int pendingConnectIndex = -1;
    private long connectedAt = 0L;
    private GoBackend backend;
    private NazarTunnel activeTunnel;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private final Runnable statsUpdater = new Runnable() {
        @Override public void run() {
            if (activeTunnel == null || connectedAt == 0L) return;
            long e = Math.max(0, (System.currentTimeMillis() - connectedAt) / 1000L);
            if (timerText != null) timerText.setText(String.format("%02d:%02d:%02d", e/3600, (e%3600)/60, e%60));
            executor.execute(() -> {
                try {
                    Statistics st = backend.getStatistics(activeTunnel);
                    String line = humanBytes(st.totalRx()) + " ↓   " + humanBytes(st.totalTx()) + " ↑";
                    main.post(() -> { if (trafficText != null) trafficText.setText(line); });
                } catch (Exception ignored) { }
            });
            main.postDelayed(this, 1000);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        backend = new GoBackend(getApplicationContext());
        selectedFreeIndex = getSharedPreferences(PREFS, MODE_PRIVATE).getInt("selected_free", 0);
        setContentView(buildShell());
        showScreen(NAV_HOME);
    }

    private View buildShell() {
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(Color.rgb(246,248,252));
        content = new FrameLayout(this);
        shell.addView(content, new LinearLayout.LayoutParams(-1,0,1f));
        navBar = new LinearLayout(this);
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        navBar.setPadding(dp(8),dp(8),dp(8),dp(10));
        navBar.setBackgroundColor(Color.WHITE);
        addNav("⌂","Home",NAV_HOME); addNav("◎","Locations",NAV_LOCATIONS);
        addNav("♛","Premium",NAV_PREMIUM); addNav("⚙","Settings",NAV_SETTINGS);
        shell.addView(navBar,new LinearLayout.LayoutParams(-1,dp(76)));
        return shell;
    }

    private void addNav(String icon,String label,int index) {
        LinearLayout box = new LinearLayout(this);
        box.setGravity(Gravity.CENTER); box.setOrientation(LinearLayout.VERTICAL);
        TextView i=text(icon,22,Color.rgb(61,75,93),true); i.setGravity(Gravity.CENTER);
        TextView l=text(label,11,Color.rgb(61,75,93),false); l.setGravity(Gravity.CENTER);
        box.addView(i,new LinearLayout.LayoutParams(-1,dp(32)));
        box.addView(l,new LinearLayout.LayoutParams(-1,dp(22)));
        box.setOnClickListener(v->showScreen(index));
        navBar.addView(box,new LinearLayout.LayoutParams(0,-1,1f));
    }

    private void showScreen(int s) {
        content.removeAllViews();
        if(s==NAV_HOME) content.addView(buildHome());
        else if(s==NAV_LOCATIONS) content.addView(buildLocations());
        else if(s==NAV_PREMIUM) content.addView(buildPremium());
        else content.addView(buildSettings());
    }

    private View buildHome() {
        ScrollView scroll=new ScrollView(this); LinearLayout root=column(22);
        root.setPadding(dp(22),dp(22),dp(22),dp(28)); scroll.addView(root);
        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand=text("NazarVPN",27,Color.rgb(15,40,72),true);
        brand.setOnLongClickListener(v->{showAdmin();return true;});
        top.addView(brand,new LinearLayout.LayoutParams(0,dp(48),1f));
        top.addView(pill("FREE"),new LinearLayout.LayoutParams(dp(66),dp(34))); root.addView(top,match());
        TextView tag=text("Private. Fast. One tap away.",14,Color.rgb(105,116,132),false); tag.setPadding(0,0,0,dp(18)); root.addView(tag,match());

        LinearLayout loc=card();
        loc.addView(text("CURRENT LOCATION",11,Color.rgb(119,130,145),true),match());
        loc.addView(text(freeFlags[selectedFreeIndex]+"  "+freeNames[selectedFreeIndex],20,Color.rgb(24,36,52),true),match());
        TextView change=text("Change ›",14,Color.rgb(42,110,205),true); change.setGravity(Gravity.RIGHT); change.setOnClickListener(v->showScreen(NAV_LOCATIONS)); loc.addView(change,match());
        root.addView(loc,margin(0,0,0,22));

        LinearLayout hero=card(); hero.setGravity(Gravity.CENTER_HORIZONTAL); hero.setPadding(dp(22),dp(26),dp(22),dp(26));
        connectionStatus=text(activeTunnel==null?"Not connected":"Protected",17,activeTunnel==null?Color.rgb(112,124,140):Color.rgb(20,145,86),true); connectionStatus.setGravity(Gravity.CENTER); hero.addView(connectionStatus,match());
        powerIcon=text(activeTunnel==null?"⏻":"✓",56,Color.WHITE,true); powerIcon.setGravity(Gravity.CENTER); GradientDrawable c=new GradientDrawable(); c.setShape(GradientDrawable.OVAL); c.setColor(activeTunnel==null?Color.rgb(48,111,218):Color.rgb(24,171,101)); powerIcon.setBackground(c); powerIcon.setOnClickListener(v->toggleConnection()); LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(dp(128),dp(128)); cp.setMargins(0,dp(20),0,dp(18)); hero.addView(powerIcon,cp);
        powerButton=button(activeTunnel==null?"Quick Connect":"Disconnect"); primary(powerButton); powerButton.setOnClickListener(v->toggleConnection()); hero.addView(powerButton,new LinearLayout.LayoutParams(-1,dp(54)));
        timerText=text(activeTunnel==null?"00:00:00":elapsedText(),18,Color.rgb(35,47,62),true); timerText.setGravity(Gravity.CENTER); timerText.setPadding(0,dp(18),0,dp(4)); hero.addView(timerText,match());
        trafficText=text("0 B ↓   0 B ↑",13,Color.rgb(115,126,142),false); trafficText.setGravity(Gravity.CENTER); hero.addView(trafficText,match());
        root.addView(hero,margin(0,0,0,18));

        LinearLayout p=card(); p.addView(text("Unlock faster global access",18,Color.rgb(27,37,53),true),match()); p.addView(text("More countries, priority servers and advanced privacy controls.",13,Color.rgb(102,112,128),false),margin(0,6,0,14)); Button up=button("View Premium Plans"); dark(up); up.setOnClickListener(v->showScreen(NAV_PREMIUM)); p.addView(up,new LinearLayout.LayoutParams(-1,dp(48))); root.addView(p,match());
        if(activeTunnel!=null) main.post(statsUpdater); return scroll;
    }

    private View buildLocations() {
        ScrollView s=new ScrollView(this); LinearLayout r=column(12); r.setPadding(dp(20),dp(22),dp(20),dp(28)); s.addView(r);
        r.addView(text("Locations",28,Color.rgb(20,35,55),true),match()); TextView sub=text("Choose your virtual location",14,Color.rgb(110,120,135),false); sub.setPadding(0,dp(4),0,dp(16)); r.addView(sub,match());
        r.addView(section("FREE LOCATIONS"),match()); for(int i=0;i<freeNames.length;i++) r.addView(locationRow(freeFlags[i],freeNames[i],"Free",i,false));
        r.addView(section("PAID LOCATIONS"),margin(0,16,0,0)); for(int i=0;i<paidNames.length;i++) r.addView(locationRow(paidFlags[i],paidNames[i],"Paid",-1,true));
        r.addView(section("PREMIUM LOCATIONS"),margin(0,16,0,0)); for(int i=0;i<premiumNames.length;i++) r.addView(locationRow(premiumFlags[i],premiumNames[i],"Premium",-1,true)); return s;
    }

    private View locationRow(String flag,String name,String plan,int idx,boolean locked) {
        LinearLayout row=card(); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(16),dp(10),dp(16),dp(10));
        row.addView(text(flag,28,Color.BLACK,false),new LinearLayout.LayoutParams(dp(54),dp(54)));
        row.addView(text(name+"\n"+plan,15,Color.rgb(30,42,59),true),new LinearLayout.LayoutParams(0,dp(54),1f));
        TextView e=text(locked?"🔒":(idx==selectedFreeIndex?"✓":"›"),18,locked?Color.GRAY:Color.rgb(36,111,210),true); e.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL); row.addView(e,new LinearLayout.LayoutParams(dp(44),dp(54)));
        row.setOnClickListener(v->{if(locked)showScreen(NAV_PREMIUM);else{selectedFreeIndex=idx;getSharedPreferences(PREFS,MODE_PRIVATE).edit().putInt("selected_free",idx).apply();showScreen(NAV_HOME);}}); return row;
    }

    private View buildPremium() {
        ScrollView s=new ScrollView(this); LinearLayout r=column(14); r.setPadding(dp(20),dp(22),dp(20),dp(30)); s.addView(r);
        r.addView(text("NazarVPN Premium",28,Color.rgb(20,35,55),true),match()); TextView intro=text("Choose a plan that fits your needs.",14,Color.rgb(110,120,135),false); intro.setPadding(0,dp(4),0,dp(16)); r.addView(intro,match());
        r.addView(planCard("PAID","Rs 1,000","More countries\nPriority server pool\nFaster support",false),match()); r.addView(planCard("PREMIUM","Rs 5,000","All available countries\nPriority routing\nAdvanced privacy controls\nPremium support",true),match());
        LinearLayout pay=card(); pay.addView(text("EasyPaisa payment",17,Color.rgb(28,39,54),true),match()); pay.addView(text("03434710858",24,Color.rgb(24,122,77),true),margin(0,8,0,4)); pay.addView(text("After payment, contact NazarVPN support for activation.",13,Color.rgb(103,113,129),false),match()); r.addView(pay,match()); return s;
    }

    private View planCard(String plan,String price,String features,boolean premium) {
        LinearLayout c=card(); if(premium)c.setBackground(round(Color.rgb(23,36,58),22)); int tc=premium?Color.WHITE:Color.rgb(25,36,52), sc=premium?Color.rgb(205,214,230):Color.rgb(100,111,126);
        c.addView(text(plan,13,premium?Color.rgb(122,190,255):Color.rgb(42,109,201),true),match()); c.addView(text(price,29,tc,true),margin(0,6,0,12)); c.addView(text(features,14,sc,false),margin(0,0,0,16)); Button b=button("Get "+plan); if(premium)primary(b);else dark(b); b.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(plan+" activation").setMessage("Send "+price+" to EasyPaisa 03434710858, then contact NazarVPN support with your payment confirmation.").setPositiveButton("OK",null).show()); c.addView(b,new LinearLayout.LayoutParams(-1,dp(48))); return c;
    }

    private View buildSettings() {
        ScrollView s=new ScrollView(this); LinearLayout r=column(12); r.setPadding(dp(20),dp(22),dp(20),dp(28)); s.addView(r); r.addView(text("Settings",28,Color.rgb(20,35,55),true),match()); TextView sub=text("Privacy and connection preferences",14,Color.rgb(110,120,135),false); sub.setPadding(0,dp(4),0,dp(16)); r.addView(sub,match());
        r.addView(settingsRow("⚡","Auto connect","Connect quickly to your selected server","ON")); View a=settingsRow("🛡","Always-on VPN","Open Android VPN security settings","›"); a.setOnClickListener(v->openVpnSettings()); r.addView(a); View k=settingsRow("🔒","Kill switch","Available through Android lockdown mode","›"); k.setOnClickListener(v->openVpnSettings()); r.addView(k); r.addView(settingsRow("◉","Protocol","WireGuard","WG")); r.addView(settingsRow("?","Support","EasyPaisa / activation assistance","›")); return s;
    }

    private View settingsRow(String icon,String title,String subtitle,String end) {
        LinearLayout row=card(); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(16),dp(10),dp(16),dp(10)); row.addView(text(icon,22,Color.rgb(45,91,155),false),new LinearLayout.LayoutParams(dp(46),dp(52))); row.addView(text(title+"\n"+subtitle,14,Color.rgb(35,46,61),true),new LinearLayout.LayoutParams(0,dp(52),1f)); TextView e=text(end,13,Color.rgb(83,96,114),true); e.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL); row.addView(e,new LinearLayout.LayoutParams(dp(42),dp(52))); return row;
    }

    private void toggleConnection() {
        if(activeTunnel!=null){disconnect();return;} String raw=getStoredConfig(selectedFreeIndex); if(raw==null||raw.trim().isEmpty()){new AlertDialog.Builder(this).setTitle("Server temporarily unavailable").setMessage("This location is not provisioned yet. NazarVPN is ready for one-tap connection as soon as its secure server profile is activated by the administrator.").setPositiveButton("OK",null).show();return;} pendingConnectIndex=selectedFreeIndex; Intent p=VpnService.prepare(this); if(p!=null)startActivityForResult(p,REQ_VPN);else connectNow(pendingConnectIndex);
    }

    private void connectNow(int index) {
        setBusy(true,"Connecting…"); executor.execute(()->{try{String raw=getStoredConfig(index); Config cfg=Config.parse(new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8))); NazarTunnel t=new NazarTunnel(tunnelNames[index]); backend.setState(t,Tunnel.State.UP,cfg); activeTunnel=t; connectedAt=System.currentTimeMillis(); main.post(()->{setBusy(false,"Protected");showScreen(NAV_HOME);});}catch(Exception e){activeTunnel=null;connectedAt=0L;main.post(()->{Toast.makeText(this,readableError(e),Toast.LENGTH_LONG).show();showScreen(NAV_HOME);});}});
    }

    private void disconnect() {
        setBusy(true,"Disconnecting…"); NazarTunnel t=activeTunnel; executor.execute(()->{try{if(t!=null)backend.setState(t,Tunnel.State.DOWN,null);}catch(Exception ignored){} activeTunnel=null;connectedAt=0L;main.post(()->{main.removeCallbacks(statsUpdater);showScreen(NAV_HOME);});});
    }

    private void showAdmin() {
        new AlertDialog.Builder(this).setTitle("NazarVPN Admin").setItems(new String[]{"Install Singapore server profile","Install Germany server profile","Install Netherlands server profile"},(d,w)->importConfig(w)).setNegativeButton("Cancel",null).show();
    }
    private void importConfig(int index){selectedFreeIndex=index;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");startActivityForResult(i,REQ_CONFIG);}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode==REQ_VPN){if(resultCode==RESULT_OK&&pendingConnectIndex>=0)connectNow(pendingConnectIndex);else Toast.makeText(this,"VPN permission is required.",Toast.LENGTH_LONG).show();}else if(requestCode==REQ_CONFIG&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null)saveConfig(data.getData(),selectedFreeIndex);}
    private void saveConfig(Uri uri,int index){executor.execute(()->{try(BufferedReader r=new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(uri)))){StringBuilder sb=new StringBuilder();String line;while((line=r.readLine())!=null)sb.append(line).append('\n');String raw=sb.toString();Config.parse(new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString("config_"+keys[index],raw).apply();main.post(()->Toast.makeText(this,freeNames[index]+" server installed",Toast.LENGTH_LONG).show());}catch(Exception e){main.post(()->Toast.makeText(this,"Invalid server profile",Toast.LENGTH_LONG).show());}});}
    private String getStoredConfig(int i){return getSharedPreferences(PREFS,MODE_PRIVATE).getString("config_"+keys[i],null);}
    private void setBusy(boolean busy,String status){if(connectionStatus!=null)connectionStatus.setText(status);if(powerButton!=null)powerButton.setEnabled(!busy);if(powerIcon!=null)powerIcon.setEnabled(!busy);}
    private void openVpnSettings(){try{startActivity(new Intent(Settings.ACTION_VPN_SETTINGS));}catch(Exception e){Toast.makeText(this,"Open Settings > VPN",Toast.LENGTH_LONG).show();}}
    private String readableError(Exception e){String m=e.getMessage();return m==null||m.trim().isEmpty()?e.getClass().getSimpleName():m;}
    private String elapsedText(){if(connectedAt==0L)return "00:00:00";long e=Math.max(0,(System.currentTimeMillis()-connectedAt)/1000L);return String.format("%02d:%02d:%02d",e/3600,(e%3600)/60,e%60);}
    private String humanBytes(long b){if(b<1024)return b+" B";double k=b/1024d;if(k<1024)return String.format("%.1f KB",k);double m=k/1024d;if(m<1024)return String.format("%.1f MB",m);return String.format("%.2f GB",m/1024d);}

    private TextView section(String s){TextView t=text(s,11,Color.rgb(115,126,140),true);t.setPadding(dp(4),dp(4),0,dp(6));return t;}
    private TextView text(String v,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(v);t.setTextSize(sp);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private TextView pill(String v){TextView t=text(v,11,Color.rgb(25,100,180),true);t.setGravity(Gravity.CENTER);t.setBackground(round(Color.rgb(232,244,255),18));return t;}
    private Button button(String v){Button b=new Button(this);b.setText(v);b.setAllCaps(false);b.setTextSize(14);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return b;}
    private void primary(Button b){b.setTextColor(Color.WHITE);b.setBackground(round(Color.rgb(48,111,218),16));}
    private void dark(Button b){b.setTextColor(Color.WHITE);b.setBackground(round(Color.rgb(26,39,60),16));}
    private LinearLayout column(int gap){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);GradientDrawable d=new GradientDrawable();d.setSize(1,dp(gap));d.setColor(Color.TRANSPARENT);l.setDividerDrawable(d);return l;}
    private LinearLayout card(){LinearLayout l=column(4);l.setPadding(dp(18),dp(16),dp(18),dp(16));l.setBackground(round(Color.WHITE,22));l.setElevation(dp(2));return l;}
    private GradientDrawable round(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private LinearLayout.LayoutParams match(){return new LinearLayout.LayoutParams(-1,-2);}
    private LinearLayout.LayoutParams margin(int l,int t,int r,int b){LinearLayout.LayoutParams p=match();p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}

    private final class NazarTunnel implements Tunnel {
        private final String name; NazarTunnel(String n){name=n;} @Override public String getName(){return name;}
        @Override public void onStateChange(State s){if(s==State.DOWN&&activeTunnel==this){activeTunnel=null;connectedAt=0L;main.removeCallbacks(statsUpdater);main.post(()->showScreen(NAV_HOME));}}
    }
    @Override protected void onDestroy(){main.removeCallbacks(statsUpdater);executor.shutdown();super.onDestroy();}
}
