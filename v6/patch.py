from pathlib import Path

activity = Path('engine/main/src/ui/java/de/blinkt/openvpn/activities/NazarVPNActivity.java')
s = activity.read_text()

s = s.replace('import android.app.Activity;', 'import android.app.Activity;\nimport android.Manifest;', 1)
s = s.replace('import android.os.Bundle;', 'import android.os.Bundle;\nimport android.os.Build;', 1)
s = s.replace('import android.util.Base64;', 'import android.util.Base64;\nimport android.content.pm.PackageManager;', 1)

s = s.replace('private static final String VPNGATE="https://www.vpngate.net/api/iphone/";', 'private static final String VPNGATE="https://raw.githubusercontent.com/intod4-source/dawah-erp/nazarvpn-v6-resilient/v5-relays/vpngate.csv";', 1)
s = s.replace('Fast auto-selection • Live relay quality • Optional account', 'One-tap secure connection • Smart global failover • Live relay health')
s = s.replace('Finding fastest server…', 'Finding the fastest secure route…')
s = s.replace('Embedded OpenVPN / Android VpnService', 'OpenVPN engine • Native Android VPN permission • Smart failover')

old_filter = 'List<Relay> filtered=new ArrayList<>();for(Relay x:relays)if(allowedCode(x.countryShort)&&countryEnabled(x.countryShort)&&("AUTO".equals(selectedCode)||selectedCode.equalsIgnoreCase(x.countryShort)))filtered.add(x);if(filtered.isEmpty()&&!"AUTO".equals(selectedCode)){for(Relay x:relays)if(allowedCode(x.countryShort)&&countryEnabled(x.countryShort))filtered.add(x);}'
new_filter = 'List<Relay> filtered=new ArrayList<>();for(Relay x:relays)if(allowedCode(x.countryShort)&&countryEnabled(x.countryShort)&&("AUTO".equals(selectedCode)||selectedCode.equalsIgnoreCase(x.countryShort)))filtered.add(x);if(filtered.isEmpty()&&!"AUTO".equals(selectedCode)){for(Relay x:relays)if(allowedCode(x.countryShort)&&countryEnabled(x.countryShort))filtered.add(x);}if(filtered.isEmpty()&&"AUTO".equals(selectedCode)){for(Relay x:relays)if(x.config!=null&&!x.config.isEmpty())filtered.add(x);}'
if old_filter not in s:
    raise SystemExit('candidate filter pattern not found')
s = s.replace(old_filter, new_filter, 1)
s = s.replace('if(candidates.size()>=6)break;', 'if(candidates.size()>=12)break;', 1)

old_profile = 'String ovpn=new String(Base64.decode(relay.config,Base64.DEFAULT),StandardCharsets.UTF_8);ConfigParser cp=new ConfigParser();cp.parseConfig(new StringReader(ovpn));VpnProfile vp=cp.convertProfile();vp.mName="NazarVPN - "+relay.countryLong+" "+bars(relay.ping);'
new_profile = 'String ovpn=new String(Base64.decode(relay.config,Base64.DEFAULT),StandardCharsets.UTF_8)+"\\nconnect-timeout 6\\nconnect-retry-max 1\\n";ConfigParser cp=new ConfigParser();cp.parseConfig(new StringReader(ovpn));VpnProfile vp=cp.convertProfile();vp.mName="NazarVPN - "+relay.countryLong+" "+bars(relay.ping);vp.mUsername="vpn";vp.mPassword="vpn";'
if old_profile not in s:
    raise SystemExit('profile pattern not found')
s = s.replace(old_profile, new_profile, 1)

old_permission = 'private void requestVpnPermission(){Intent i=VpnService.prepare(this);if(i!=null)startActivityForResult(i,REQ_VPN);else startPending();}'
new_permission = 'private void requestVpnPermission(){Intent i=VpnService.prepare(this);if(i!=null)startActivityForResult(i,REQ_VPN);else{requestNotificationPermissionIfNeeded();startPending();}} private void requestNotificationPermissionIfNeeded(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},502);}'
if old_permission not in s:
    raise SystemExit('permission pattern not found')
s = s.replace(old_permission, new_permission, 1)
s = s.replace('if(result==RESULT_OK)startPending();else failConnect("VPN permission is required.");', 'if(result==RESULT_OK){requestNotificationPermissionIfNeeded();startPending();}else failConnect("VPN permission is required.");', 1)

old_fetch = 'private List<Relay> fetchRelays(boolean warm) throws Exception{if(!relayCache.isEmpty()&&System.currentTimeMillis()-lastRelayFetch<CACHE_MS)return new ArrayList<>(relayCache);HttpURLConnection c=(HttpURLConnection)new URL(VPNGATE).openConnection();c.setConnectTimeout(6000);c.setReadTimeout(9000);c.setRequestProperty("User-Agent","NazarVPN/4.0 Android");if(c.getResponseCode()!=200)throw new Exception("VPN directory unavailable");List<Relay> out=new ArrayList<>();try(BufferedReader br=new BufferedReader(new InputStreamReader(c.getInputStream(),StandardCharsets.UTF_8))){String line;while((line=br.readLine())!=null){if(line.startsWith("*")||line.startsWith("#")||line.trim().isEmpty())continue;String[] p=line.split(",",15);if(p.length<15)continue;try{Relay r=new Relay();r.host=p[0];r.ip=p[1];r.score=parseLong(p[2]);r.ping=(int)parseLong(p[3]);r.speed=parseLong(p[4]);r.countryLong=p[5];r.countryShort=p[6];r.config=p[14];if(!r.config.isEmpty())out.add(r);}catch(Exception ignored){}}}finally{c.disconnect();}relayCache=out;lastRelayFetch=System.currentTimeMillis();bestByCountry.clear();for(Relay r:out){Relay old=bestByCountry.get(r.countryShort);if(old==null||rank(r)>rank(old))bestByCountry.put(r.countryShort,r);}return new ArrayList<>(out);}'
new_fetch = '''private List<Relay> fetchRelays(boolean warm) throws Exception{if(!relayCache.isEmpty()&&System.currentTimeMillis()-lastRelayFetch<CACHE_MS)return new ArrayList<>(relayCache);Exception last=null;String[] sources={SB+"/functions/v1/nazarvpn-relays",VPNGATE};for(String src:sources){HttpURLConnection c=null;try{c=(HttpURLConnection)new URL(src).openConnection();c.setConnectTimeout(5000);c.setReadTimeout(7000);c.setRequestProperty("User-Agent","NazarVPN/6.0 Android");if(src.contains("supabase.co"))c.setRequestProperty("apikey",KEY);if(c.getResponseCode()!=200)throw new Exception("HTTP "+c.getResponseCode());List<Relay> out=parseRelayStream(c.getInputStream());if(out.size()>=1){storeRelays(out);return new ArrayList<>(out);}}catch(Exception e){last=e;}finally{if(c!=null)c.disconnect();}}try(InputStream in=getAssets().open("vpngate.csv")){List<Relay> out=parseRelayStream(in);if(!out.isEmpty()){storeRelays(out);return new ArrayList<>(out);}}catch(Exception e){last=e;}throw new Exception("VPN directory unavailable"+(last==null?"":" — "+last.getClass().getSimpleName()));} private List<Relay> parseRelayStream(InputStream in)throws Exception{List<Relay> out=new ArrayList<>();try(BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String line;while((line=br.readLine())!=null){if(line.startsWith("*")||line.startsWith("#")||line.trim().isEmpty())continue;String[] p=line.split(",",15);if(p.length<15)continue;try{Relay r=new Relay();r.host=p[0];r.ip=p[1];r.score=parseLong(p[2]);r.ping=(int)parseLong(p[3]);r.speed=parseLong(p[4]);r.countryLong=p[5];r.countryShort=p[6];r.config=p[14];if(!r.config.isEmpty())out.add(r);}catch(Exception ignored){}}}return out;} private void storeRelays(List<Relay> out){relayCache=out;lastRelayFetch=System.currentTimeMillis();bestByCountry.clear();for(Relay r:out){Relay old=bestByCountry.get(r.countryShort);if(old==null||rank(r)>rank(old))bestByCountry.put(r.countryShort,r);}}'''
if old_fetch not in s:
    raise SystemExit('relay fetch pattern not found')
s = s.replace(old_fetch, new_fetch, 1)

s = s.replace('throw new Exception("No compatible live relay found")', 'throw new Exception("No usable secure route is available right now")', 1)
s = s.replace('Could not find a live VPN relay.\\n\\n', 'Unable to establish a secure route.\\n\\n', 1)
activity.write_text(s)

manifest = Path('engine/main/src/ui/AndroidManifest.xml')
m = manifest.read_text().replace('android:name=".activities.MainActivity"', 'android:name=".activities.NazarVPNActivity"', 1)
if 'android.permission.POST_NOTIFICATIONS' not in m:
    pos = m.find('>', m.find('<manifest')) + 1
    m = m[:pos] + '\n    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />' + m[pos:]
m = m.replace('android:icon="@mipmap/ic_launcher"', 'android:icon="@drawable/nazarvpn_icon"')
m = m.replace('android:roundIcon="@mipmap/ic_launcher_round"', 'android:roundIcon="@drawable/nazarvpn_icon"')
manifest.write_text(m)

drawable = Path('engine/main/src/ui/res/drawable')
drawable.mkdir(parents=True, exist_ok=True)
drawable.joinpath('nazarvpn_icon.xml').write_text('''<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108"><path android:fillColor="#071A3D" android:pathData="M54,4 C75,4 94,13 101,22 L96,68 C93,87 76,99 54,105 C32,99 15,87 12,68 L7,22 C14,13 33,4 54,4 Z"/><path android:fillColor="#0D6EFD" android:pathData="M54,12 C72,12 87,19 93,26 L89,64 C87,78 74,89 54,96 C34,89 21,78 19,64 L15,26 C21,19 36,12 54,12 Z"/><path android:fillColor="#27D3FF" android:pathData="M54,21 C68,21 80,26 84,31 L81,60 C79,71 69,80 54,86 C39,80 29,71 27,60 L24,31 C28,26 40,21 54,21 Z"/><path android:fillColor="#FFFFFF" android:pathData="M43,48 L43,42 C43,35 48,30 55,30 C62,30 67,35 67,42 L67,48 L72,48 C74,48 76,50 76,52 L76,68 C76,71 74,73 71,73 L39,73 C36,73 34,71 34,68 L34,52 C34,50 36,48 38,48 Z M49,48 L61,48 L61,42 C61,38 59,36 55,36 C51,36 49,38 49,42 Z"/><path android:fillColor="#B9F4FF" android:pathData="M30,25 C40,18 60,15 77,21 C63,17 47,20 36,28 C31,32 27,36 24,41 L24,32 C25,29 27,27 30,25 Z"/></vector>''')

gradle = Path('engine/main/build.gradle.kts')
g = gradle.read_text().replace('defaultConfig {', 'defaultConfig {\n        applicationId = "com.nazarvpn.app"', 1).replace('versionName = "0.7.64"', 'versionName = "6.0.0"', 1)
gradle.write_text(g)

for f in Path('engine/main/src').rglob('strings.xml'):
    try:
        t = f.read_text().replace('<string name="app">OpenVPN for Android</string>', '<string name="app">NazarVPN</string>').replace('<string name="app_name">OpenVPN for Android</string>', '<string name="app_name">NazarVPN</string>')
        f.write_text(t)
    except Exception:
        pass

gp = Path('engine/gradle.properties')
txt = gp.read_text() if gp.exists() else ''
lines = [x for x in txt.splitlines() if not x.startswith('org.gradle.jvmargs=') and not x.startswith('org.gradle.workers.max=')]
lines += ['org.gradle.jvmargs=-Xmx5g -XX:MaxMetaspaceSize=1024m -Dfile.encoding=UTF-8', 'org.gradle.workers.max=2', 'org.gradle.parallel=false']
gp.write_text('\n'.join(lines) + '\n')
