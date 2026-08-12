package de.blinkt.openvpn.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.ConfigParser;
import de.blinkt.openvpn.core.ConnectionStatus;
import de.blinkt.openvpn.core.ProfileManager;
import de.blinkt.openvpn.core.VPNLaunchHelper;
import de.blinkt.openvpn.core.VpnStatus;

public class NazarVPNActivity extends Activity implements VpnStatus.StateListener, VpnStatus.ByteCountListener {
    private static final int REQ_VPN = 301;
    private static final String API = "https://www.vpngate.net/api/iphone/";
    private static final String[] FREE_NAMES = {"Singapore", "Germany", "Netherlands"};
    private static final String[] FREE_FLAGS = {"🇸🇬", "🇩🇪", "🇳🇱"};
    private static final String[] FREE_CODES = {"SG", "DE", "NL"};
    private static final String[] PAID_NAMES = {"United States", "United Kingdom", "France", "Canada"};
    private static final String[] PAID_FLAGS = {"🇺🇸", "🇬🇧", "🇫🇷", "🇨🇦"};
    private static final String[] PREMIUM_NAMES = {"Japan", "UAE", "Australia", "Switzerland"};
    private static final String[] PREMIUM_FLAGS = {"🇯🇵", "🇦🇪", "🇦🇺", "🇨🇭"};

    private static final int NAV_HOME=0, NAV_LOCATIONS=1, NAV_PREMIUM=2, NAV_SETTINGS=3;
    private FrameLayout content;
    private LinearLayout navBar;
    private TextView statusText, timerText, trafficText, powerIcon, locationText;
    private Button powerButton;
    private int selected = 0;
    private boolean connected = false;
    private boolean connecting = false;
    private long connectedAt = 0;
    private long bytesIn = 0, bytesOut = 0;
    private VpnProfile pendingProfile;
    private String activeCountry = null;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private final Runnable clock = new Runnable() {
        @Override public void run() {
            if (!connected || connectedAt == 0) return;
            long s = Math.max(0, (System.currentTimeMillis()-connectedAt)/1000);
            if (timerText != null) timerText.setText(String.format(Locale.US,"%02d:%02d:%02d",s/3600,(s%3600)/60,s%60));
            if (trafficText != null) trafficText.setText(human(bytesIn)+" ↓   "+human(bytesOut)+" ↑");
            main.postDelayed(this,1000);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        selected = getSharedPreferences("nazarvpn",MODE_PRIVATE).getInt("selected",0);
        setContentView(buildShell());
        showScreen(NAV_HOME);
    }

    @Override protected void onResume() {
        super.onResume();
        VpnStatus.addStateListener(this);
        VpnStatus.addByteCountListener(this);
    }

    @Override protected void onPause() {
        VpnStatus.removeStateListener(this);
        VpnStatus.removeByteCountListener(this);
        super.onPause();
    }

    private View buildShell() {
        LinearLayout shell=new LinearLayout(this); shell.setOrientation(LinearLayout.VERTICAL); shell.setBackgroundColor(Color.rgb(246,248,252));
        content=new FrameLayout(this); shell.addView(content,new LinearLayout.LayoutParams(-1,0,1f));
        navBar=new LinearLayout(this); navBar.setOrientation(LinearLayout.HORIZONTAL); navBar.setPadding(dp(8),dp(7),dp(8),dp(8)); navBar.setBackgroundColor(Color.WHITE);
        addNav("⌂","Home",NAV_HOME); addNav("◎","Locations",NAV_LOCATIONS); addNav("♛","Premium",NAV_PREMIUM); addNav("⚙","Settings",NAV_SETTINGS);
        shell.addView(navBar,new LinearLayout.LayoutParams(-1,dp(74))); return shell;
    }

    private void addNav(String icon,String label,int id) {
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setGravity(Gravity.CENTER);
        TextView i=text(icon,22,Color.rgb(50,65,85),true); i.setGravity(Gravity.CENTER); TextView l=text(label,11,Color.rgb(50,65,85),false); l.setGravity(Gravity.CENTER);
        box.addView(i,new LinearLayout.LayoutParams(-1,dp(30))); box.addView(l,new LinearLayout.LayoutParams(-1,dp(22))); box.setOnClickListener(v->showScreen(id));
        navBar.addView(box,new LinearLayout.LayoutParams(0,-1,1f));
    }

    private void showScreen(int id) {
        content.removeAllViews();
        if(id==NAV_HOME) content.addView(buildHome()); else if(id==NAV_LOCATIONS) content.addView(buildLocations()); else if(id==NAV_PREMIUM) content.addView(buildPremium()); else content.addView(buildSettings());
    }

    private View buildHome() {
        ScrollView s=new ScrollView(this); LinearLayout r=column(); r.setPadding(dp(22),dp(22),dp(22),dp(28)); s.addView(r);
        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); TextView brand=text("NazarVPN",28,Color.rgb(15,40,72),true); top.addView(brand,new LinearLayout.LayoutParams(0,dp(50),1f)); top.addView(pill("FREE"),new LinearLayout.LayoutParams(dp(64),dp(34))); r.addView(top,match());
        TextView tag=text("Private. Fast. One tap away.",14,Color.rgb(105,116,132),false); tag.setPadding(0,0,0,dp(18)); r.addView(tag,match());
        LinearLayout loc=card(); loc.addView(text("CURRENT LOCATION",11,Color.rgb(119,130,145),true),match()); locationText=text(FREE_FLAGS[selected]+"  "+FREE_NAMES[selected],20,Color.rgb(24,36,52),true); loc.addView(locationText,match()); TextView ch=text("Change ›",14,Color.rgb(42,110,205),true); ch.setGravity(Gravity.RIGHT); ch.setOnClickListener(v->showScreen(NAV_LOCATIONS)); loc.addView(ch,match()); r.addView(loc,margin(0,0,0,22));
        LinearLayout hero=card(); hero.setGravity(Gravity.CENTER_HORIZONTAL); hero.setPadding(dp(22),dp(26),dp(22),dp(26));
        String st=connected?"Protected":(connecting?"Connecting…":"Not connected"); statusText=text(st,17,connected?Color.rgb(20,145,86):Color.rgb(112,124,140),true); statusText.setGravity(Gravity.CENTER); hero.addView(statusText,match());
        powerIcon=text(connected?"✓":"⏻",56,Color.WHITE,true); powerIcon.setGravity(Gravity.CENTER); powerIcon.setBackground(circle(connected?Color.rgb(24,171,101):Color.rgb(48,111,218))); powerIcon.setOnClickListener(v->toggle()); LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(dp(128),dp(128)); pp.setMargins(0,dp(20),0,dp(18)); hero.addView(powerIcon,pp);
        powerButton=button(connected?"Disconnect":(connecting?"Connecting…":"Quick Connect")); primary(powerButton); powerButton.setEnabled(!connecting); powerButton.setOnClickListener(v->toggle()); hero.addView(powerButton,new LinearLayout.LayoutParams(-1,dp(54)));
        timerText=text(connected?elapsed():"00:00:00",18,Color.rgb(35,47,62),true); timerText.setGravity(Gravity.CENTER); timerText.setPadding(0,dp(18),0,dp(4)); hero.addView(timerText,match()); trafficText=text(human(bytesIn)+" ↓   "+human(bytesOut)+" ↑",13,Color.rgb(115,126,142),false); trafficText.setGravity(Gravity.CENTER); hero.addView(trafficText,match()); r.addView(hero,margin(0,0,0,18));
        LinearLayout note=card(); note.addView(text("Free community network",17,Color.rgb(27,37,53),true),match()); note.addView(text("Free connections use VPN Gate volunteer relay servers. Availability can change automatically.",13,Color.rgb(102,112,128),false),margin(0,6,0,0)); r.addView(note,match());
        if(connected) main.post(clock); return s;
    }

    private View buildLocations() {
        ScrollView s=new ScrollView(this); LinearLayout r=column(); r.setPadding(dp(20),dp(22),dp(20),dp(28)); s.addView(r); r.addView(text("Locations",28,Color.rgb(20,35,55),true),match()); TextView sub=text("Choose your virtual location",14,Color.rgb(110,120,135),false); sub.setPadding(0,dp(4),0,dp(16)); r.addView(sub,match());
        r.addView(section("FREE LOCATIONS"),match()); for(int i=0;i<FREE_NAMES.length;i++) r.addView(locationRow(FREE_FLAGS[i],FREE_NAMES[i],"Free",i,false),margin(0,5,0,5));
        r.addView(section("PAID LOCATIONS"),margin(0,14,0,0)); for(int i=0;i<PAID_NAMES.length;i++) r.addView(locationRow(PAID_FLAGS[i],PAID_NAMES[i],"Paid",-1,true),margin(0,5,0,5));
        r.addView(section("PREMIUM LOCATIONS"),margin(0,14,0,0)); for(int i=0;i<PREMIUM_NAMES.length;i++) r.addView(locationRow(PREMIUM_FLAGS[i],PREMIUM_NAMES[i],"Premium",-1,true),margin(0,5,0,5)); return s;
    }

    private View locationRow(String flag,String name,String plan,int idx,boolean locked) {
        LinearLayout row=card(); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(16),dp(9),dp(16),dp(9)); row.addView(text(flag,28,Color.BLACK,false),new LinearLayout.LayoutParams(dp(54),dp(54))); row.addView(text(name+"\n"+plan,15,Color.rgb(30,42,59),true),new LinearLayout.LayoutParams(0,dp(54),1f)); TextView e=text(locked?"🔒":(idx==selected?"✓":"›"),18,locked?Color.GRAY:Color.rgb(36,111,210),true); e.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL); row.addView(e,new LinearLayout.LayoutParams(dp(44),dp(54))); row.setOnClickListener(v->{if(locked)showScreen(NAV_PREMIUM);else{selected=idx;getSharedPreferences("nazarvpn",MODE_PRIVATE).edit().putInt("selected",idx).apply();showScreen(NAV_HOME);}}); return row;
    }

    private View buildPremium() {
        ScrollView s=new ScrollView(this); LinearLayout r=column(); r.setPadding(dp(20),dp(22),dp(20),dp(30)); s.addView(r); r.addView(text("NazarVPN Premium",28,Color.rgb(20,35,55),true),match()); TextView sub=text("Choose a plan that fits your needs.",14,Color.rgb(110,120,135),false); sub.setPadding(0,dp(4),0,dp(16)); r.addView(sub,match()); r.addView(plan("PAID","Rs 1,000","More countries\nPriority servers\nFaster support",false),margin(0,4,0,8)); r.addView(plan("PREMIUM","Rs 5,000","All available countries\nPriority routing\nAdvanced privacy controls\nPremium support",true),margin(0,4,0,12)); LinearLayout pay=card(); pay.addView(text("EasyPaisa",17,Color.rgb(28,39,54),true),match()); pay.addView(text("03434710858",24,Color.rgb(24,122,77),true),margin(0,8,0,4)); pay.addView(text("Send payment and contact NazarVPN support for activation.",13,Color.rgb(103,113,129),false),match()); r.addView(pay,match()); return s;
    }

    private View plan(String name,String price,String features,boolean dark) { LinearLayout c=card(); if(dark)c.setBackground(round(Color.rgb(23,36,58),22)); int tc=dark?Color.WHITE:Color.rgb(25,36,52), sc=dark?Color.rgb(205,214,230):Color.rgb(100,111,126); c.addView(text(name,13,dark?Color.rgb(122,190,255):Color.rgb(42,109,201),true),match()); c.addView(text(price,29,tc,true),margin(0,6,0,12)); c.addView(text(features,14,sc,false),margin(0,0,0,16)); Button b=button("Get "+name); if(dark)primary(b); else dark(b); b.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(name+" activation").setMessage("Send "+price+" to EasyPaisa 03434710858, then contact NazarVPN support with payment confirmation.").setPositiveButton("OK",null).show()); c.addView(b,new LinearLayout.LayoutParams(-1,dp(48))); return c; }

    private View buildSettings() { ScrollView s=new ScrollView(this); LinearLayout r=column(); r.setPadding(dp(20),dp(22),dp(20),dp(28)); s.addView(r); r.addView(text("Settings",28,Color.rgb(20,35,55),true),match()); TextView sub=text("Privacy and connection preferences",14,Color.rgb(110,120,135),false); sub.setPadding(0,dp(4),0,dp(16)); r.addView(sub,match()); r.addView(setting("⚡","Quick Connect","Automatically selects a live relay","ON"),margin(0,5,0,5)); r.addView(setting("🛡","VPN engine","OpenVPN 3 / Android VpnService","OVPN"),margin(0,5,0,5)); r.addView(setting("🌐","Free network","VPN Gate volunteer relays","LIVE"),margin(0,5,0,5)); r.addView(setting("?","Support","EasyPaisa / activation assistance","›"),margin(0,5,0,5)); return s; }
    private View setting(String icon,String title,String desc,String end){LinearLayout row=card();row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(16),dp(10),dp(16),dp(10));row.addView(text(icon,22,Color.rgb(45,91,155),false),new LinearLayout.LayoutParams(dp(46),dp(52)));row.addView(text(title+"\n"+desc,14,Color.rgb(35,46,61),true),new LinearLayout.LayoutParams(0,dp(52),1f));TextView e=text(end,12,Color.rgb(83,96,114),true);e.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);row.addView(e,new LinearLayout.LayoutParams(dp(62),dp(52)));return row;}

    private void toggle() { if(connected){disconnect();return;} if(connecting)return; connecting=true; showScreen(NAV_HOME); worker.execute(this::prepareRelayAndConnect); }

    private void prepareRelayAndConnect() {
        try {
            List<Relay> relays=fetchRelays();
            if(relays.isEmpty()) throw new Exception("No free relays available right now");
            String desired=FREE_CODES[selected];
            List<Relay> preferred=new ArrayList<>(), fallback=new ArrayList<>();
            for(Relay r:relays){ if(desired.equalsIgnoreCase(r.countryShort)) preferred.add(r); else fallback.add(r); }
            Comparator<Relay> cmp=(a,b)->Long.compare(b.score,a.score); Collections.sort(preferred,cmp); Collections.sort(fallback,cmp);
            List<Relay> candidates=new ArrayList<>(); candidates.addAll(preferred); candidates.addAll(fallback);
            Exception last=null;
            for(int i=0;i<Math.min(8,candidates.size());i++) {
                try {
                    Relay relay=candidates.get(i); String ovpn=new String(Base64.decode(relay.config,Base64.DEFAULT),StandardCharsets.UTF_8);
                    ConfigParser cp=new ConfigParser(); cp.parseConfig(new StringReader(ovpn)); VpnProfile vp=cp.convertProfile(); vp.mName="NazarVPN Free - "+relay.countryLong; ProfileManager.setTemporaryProfile(this,vp); pendingProfile=vp; activeCountry=relay.countryLong;
                    main.post(this::requestVpnPermission); return;
                } catch(Exception e){last=e;}
            }
            throw last==null?new Exception("No compatible relay profile found"):last;
        } catch(Exception e) {
            main.post(()->{connecting=false;showScreen(NAV_HOME);new AlertDialog.Builder(this).setTitle("Could not connect").setMessage("NazarVPN could not find a compatible free relay. Check your internet and try again.\n\n"+safe(e)).setPositiveButton("OK",null).show();});
        }
    }

    private List<Relay> fetchRelays() throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(API).openConnection(); c.setConnectTimeout(12000); c.setReadTimeout(15000); c.setRequestProperty("User-Agent","NazarVPN/3.0 Android"); c.setInstanceFollowRedirects(true);
        if(c.getResponseCode()!=200) throw new Exception("Relay directory unavailable (HTTP "+c.getResponseCode()+")");
        List<Relay> out=new ArrayList<>(); try(BufferedReader br=new BufferedReader(new InputStreamReader(c.getInputStream(),StandardCharsets.UTF_8))){String line;while((line=br.readLine())!=null){if(line.startsWith("*")||line.startsWith("#")||line.trim().isEmpty())continue;String[] p=line.split(",",15);if(p.length<15)continue;try{Relay r=new Relay();r.host=p[0];r.ip=p[1];r.score=Long.parseLong(p[2]);r.countryLong=p[5];r.countryShort=p[6];r.config=p[14];if(!r.config.isEmpty())out.add(r);}catch(Exception ignored){}}} finally {c.disconnect();} return out;
    }

    private void requestVpnPermission() { Intent i=VpnService.prepare(this); if(i!=null)startActivityForResult(i,REQ_VPN); else startPendingProfile(); }
    private void startPendingProfile() { if(pendingProfile==null){connecting=false;showScreen(NAV_HOME);return;} try{VPNLaunchHelper.startOpenVpn(pendingProfile,getApplicationContext(),"NazarVPN Quick Connect",true); statusTextIf("Connecting…");}catch(Exception e){connecting=false;showScreen(NAV_HOME);Toast.makeText(this,safe(e),Toast.LENGTH_LONG).show();} }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode==REQ_VPN){if(resultCode==RESULT_OK)startPendingProfile();else{connecting=false;showScreen(NAV_HOME);Toast.makeText(this,"VPN permission is required",Toast.LENGTH_LONG).show();}}}

    private void disconnect(){try{Intent i=new Intent(this,de.blinkt.openvpn.activities.DisconnectVPN.class);startActivity(i);}catch(Exception e){Toast.makeText(this,"Could not disconnect",Toast.LENGTH_SHORT).show();}}

    @Override public void updateState(String state,String logmessage,int localizedResId,ConnectionStatus level,Intent intent){runOnUiThread(()->{if(level==ConnectionStatus.LEVEL_CONNECTED){connected=true;connecting=false;if(connectedAt==0)connectedAt=System.currentTimeMillis();showScreen(NAV_HOME);}else if(level==ConnectionStatus.LEVEL_NOTCONNECTED||level==ConnectionStatus.LEVEL_AUTH_FAILED){connected=false;connecting=false;connectedAt=0;bytesIn=bytesOut=0;main.removeCallbacks(clock);showScreen(NAV_HOME);}else if(level==ConnectionStatus.LEVEL_CONNECTING_NO_SERVER_REPLY_YET||level==ConnectionStatus.LEVEL_CONNECTING_SERVER_REPLIED){connecting=true;statusTextIf("Connecting…");}});}
    @Override public void setConnectedVPN(String uuid){}
    @Override public void updateByteCount(long in,long out,long diffIn,long diffOut){bytesIn=in;bytesOut=out;runOnUiThread(()->{if(trafficText!=null)trafficText.setText(human(bytesIn)+" ↓   "+human(bytesOut)+" ↑");});}

    private void statusTextIf(String s){if(statusText!=null)statusText.setText(s);if(powerButton!=null)powerButton.setText(s);}
    private String elapsed(){if(connectedAt==0)return"00:00:00";long s=(System.currentTimeMillis()-connectedAt)/1000;return String.format(Locale.US,"%02d:%02d:%02d",s/3600,(s%3600)/60,s%60);}
    private String human(long b){if(b<1024)return b+" B";double k=b/1024d;if(k<1024)return String.format(Locale.US,"%.1f KB",k);double m=k/1024d;if(m<1024)return String.format(Locale.US,"%.1f MB",m);return String.format(Locale.US,"%.2f GB",m/1024d);}
    private String safe(Exception e){String m=e.getMessage();return m==null||m.trim().isEmpty()?e.getClass().getSimpleName():m;}
    private TextView text(String v,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(v);t.setTextSize(sp);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private TextView pill(String v){TextView t=text(v,11,Color.rgb(25,100,180),true);t.setGravity(Gravity.CENTER);t.setBackground(round(Color.rgb(232,244,255),18));return t;}
    private TextView section(String v){TextView t=text(v,11,Color.rgb(115,126,140),true);t.setPadding(dp(4),dp(4),0,dp(6));return t;}
    private Button button(String v){Button b=new Button(this);b.setText(v);b.setAllCaps(false);b.setTextSize(14);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return b;}
    private void primary(Button b){b.setTextColor(Color.WHITE);b.setBackground(round(Color.rgb(48,111,218),16));}
    private void dark(Button b){b.setTextColor(Color.WHITE);b.setBackground(round(Color.rgb(26,39,60),16));}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout card(){LinearLayout l=column();l.setPadding(dp(18),dp(16),dp(18),dp(16));l.setBackground(round(Color.WHITE,22));l.setElevation(dp(2));return l;}
    private GradientDrawable round(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private GradientDrawable circle(int color){GradientDrawable g=new GradientDrawable();g.setShape(GradientDrawable.OVAL);g.setColor(color);return g;}
    private LinearLayout.LayoutParams match(){return new LinearLayout.LayoutParams(-1,-2);}
    private LinearLayout.LayoutParams margin(int l,int t,int r,int b){LinearLayout.LayoutParams p=match();p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    @Override protected void onDestroy(){main.removeCallbacks(clock);worker.shutdownNow();super.onDestroy();}
    private static class Relay{String host,ip,countryLong,countryShort,config;long score;}
}
