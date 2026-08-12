package com.nazarvpn.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.wireguard.android.backend.GoBackend;
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

    private final String[] countries = {
            "Germany 🇩🇪 — FREE",
            "Singapore 🇸🇬 — FREE",
            "Netherlands 🇳🇱 — FREE",
            "United States 🇺🇸 — PAID 🔒",
            "United Kingdom 🇬🇧 — PAID 🔒",
            "Canada 🇨🇦 — PREMIUM 🔒",
            "Japan 🇯🇵 — PREMIUM 🔒",
            "UAE 🇦🇪 — PREMIUM 🔒"
    };

    private final String[] keys = {"de", "sg", "nl"};
    private final String[] tunnelNames = {"nazar_de", "nazar_sg", "nazar_nl"};

    private Spinner countrySpinner;
    private TextView statusText;
    private TextView configText;
    private Button connectButton;
    private Button importButton;
    private int selectedFreeIndex = 0;
    private int pendingConnectIndex = -1;
    private GoBackend backend;
    private NazarTunnel activeTunnel;
    private Config activeConfig;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        backend = new GoBackend(getApplicationContext());
        setContentView(buildUi());
        refreshConfigState();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(28));
        root.setBackgroundColor(Color.rgb(247, 249, 252));
        scroll.addView(root);

        TextView title = text("NazarVPN", 34, Color.rgb(16, 70, 120), true);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title, matchWrap());

        TextView subtitle = text("Secure WireGuard VPN for Android", 15, Color.DKGRAY, false);
        subtitle.setGravity(Gravity.CENTER_HORIZONTAL);
        subtitle.setPadding(0, dp(4), 0, dp(24));
        root.addView(subtitle, matchWrap());

        statusText = text("Disconnected", 20, Color.rgb(180, 45, 45), true);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(dp(12), dp(16), dp(12), dp(16));
        root.addView(statusText, matchWrap());

        TextView serverLabel = text("Choose server", 16, Color.BLACK, true);
        serverLabel.setPadding(0, dp(18), 0, dp(8));
        root.addView(serverLabel, matchWrap());

        countrySpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, countries);
        countrySpinner.setAdapter(adapter);
        countrySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 2) {
                    countrySpinner.setSelection(selectedFreeIndex);
                    showPlans();
                    return;
                }
                selectedFreeIndex = position;
                refreshConfigState();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        root.addView(countrySpinner, matchWrap());

        configText = text("Server config not installed", 14, Color.DKGRAY, false);
        configText.setPadding(0, dp(12), 0, dp(10));
        root.addView(configText, matchWrap());

        importButton = button("Import WireGuard server config");
        importButton.setOnClickListener(v -> importConfig());
        root.addView(importButton, matchWrap());

        connectButton = button("CONNECT");
        LinearLayout.LayoutParams connectLp = matchWrap();
        connectLp.setMargins(0, dp(14), 0, dp(10));
        connectButton.setLayoutParams(connectLp);
        connectButton.setMinHeight(dp(58));
        connectButton.setTextSize(19);
        connectButton.setOnClickListener(v -> toggleConnection());
        root.addView(connectButton);

        Button plans = button("Upgrade / Paid Service");
        plans.setOnClickListener(v -> showPlans());
        LinearLayout.LayoutParams planLp = matchWrap();
        planLp.setMargins(0, dp(12), 0, 0);
        plans.setLayoutParams(planLp);
        root.addView(plans);

        TextView info = text(
                "Free plan: 3 countries\nPaid plan: Rs 1,000 — more countries\nPremium plan: Rs 5,000 — all countries + advanced features\n\nEasyPaisa: 03434710858\n\nSecurity: NazarVPN uses the official WireGuard Android tunnel engine. A country can connect only after a valid NazarVPN WireGuard server configuration for that country is installed.",
                14, Color.DKGRAY, false);
        info.setPadding(0, dp(22), 0, 0);
        root.addView(info, matchWrap());

        return scroll;
    }

    private void toggleConnection() {
        if (activeTunnel != null) {
            disconnect();
            return;
        }
        String config = getStoredConfig(selectedFreeIndex);
        if (config == null || config.trim().isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Server configuration required")
                    .setMessage("This country does not yet have a WireGuard server configuration. Import the .conf file created by your NazarVPN server before connecting.")
                    .setPositiveButton("Import config", (d, w) -> importConfig())
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }
        pendingConnectIndex = selectedFreeIndex;
        Intent permission = VpnService.prepare(this);
        if (permission != null) {
            startActivityForResult(permission, REQ_VPN);
        } else {
            connectNow(pendingConnectIndex);
        }
    }

    private void connectNow(int index) {
        setBusy("Connecting…");
        executor.execute(() -> {
            try {
                String raw = getStoredConfig(index);
                Config config = Config.parse(new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
                NazarTunnel tunnel = new NazarTunnel(tunnelNames[index]);
                backend.setState(tunnel, Tunnel.State.UP, config);
                activeTunnel = tunnel;
                activeConfig = config;
                main.post(() -> {
                    statusText.setText("Connected — " + countries[index].replace(" — FREE", ""));
                    statusText.setTextColor(Color.rgb(22, 135, 67));
                    connectButton.setEnabled(true);
                    connectButton.setText("DISCONNECT");
                    countrySpinner.setEnabled(false);
                    importButton.setEnabled(false);
                });
            } catch (Exception e) {
                activeTunnel = null;
                activeConfig = null;
                main.post(() -> {
                    statusText.setText("Connection failed");
                    statusText.setTextColor(Color.rgb(180, 45, 45));
                    connectButton.setEnabled(true);
                    connectButton.setText("CONNECT");
                    Toast.makeText(MainActivity.this, readableError(e), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void disconnect() {
        setBusy("Disconnecting…");
        NazarTunnel tunnel = activeTunnel;
        executor.execute(() -> {
            try {
                if (tunnel != null) backend.setState(tunnel, Tunnel.State.DOWN, null);
            } catch (Exception ignored) { }
            activeTunnel = null;
            activeConfig = null;
            main.post(() -> {
                statusText.setText("Disconnected");
                statusText.setTextColor(Color.rgb(180, 45, 45));
                connectButton.setEnabled(true);
                connectButton.setText("CONNECT");
                countrySpinner.setEnabled(true);
                importButton.setEnabled(true);
            });
        });
    }

    private void setBusy(String text) {
        statusText.setText(text);
        statusText.setTextColor(Color.rgb(130, 100, 20));
        connectButton.setEnabled(false);
    }

    private void importConfig() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQ_CONFIG);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_VPN) {
            if (resultCode == RESULT_OK && pendingConnectIndex >= 0) connectNow(pendingConnectIndex);
            else Toast.makeText(this, "VPN permission was not granted.", Toast.LENGTH_LONG).show();
            return;
        }
        if (requestCode == REQ_CONFIG && resultCode == RESULT_OK && data != null && data.getData() != null) {
            readAndSaveConfig(data.getData(), selectedFreeIndex);
        }
    }

    private void readAndSaveConfig(Uri uri, int index) {
        executor.execute(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(uri)))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line).append('\n');
                String raw = sb.toString();
                Config.parse(new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("config_" + keys[index], raw).apply();
                main.post(() -> {
                    refreshConfigState();
                    Toast.makeText(this, "Valid WireGuard config installed for " + countries[index].replace(" — FREE", ""), Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                main.post(() -> Toast.makeText(this, "Invalid WireGuard config: " + readableError(e), Toast.LENGTH_LONG).show());
            }
        });
    }

    private String getStoredConfig(int index) {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getString("config_" + keys[index], null);
    }

    private void refreshConfigState() {
        if (configText == null) return;
        String config = getStoredConfig(selectedFreeIndex);
        if (config == null || config.trim().isEmpty()) {
            configText.setText("Server config: NOT INSTALLED");
            configText.setTextColor(Color.rgb(170, 80, 25));
        } else {
            configText.setText("Server config: READY ✓");
            configText.setTextColor(Color.rgb(22, 135, 67));
        }
    }

    private void showPlans() {
        new AlertDialog.Builder(this)
                .setTitle("NazarVPN Plans")
                .setMessage("PAID — Rs 1,000\n• More countries\n• Faster server pool\n• Priority support\n\nPREMIUM — Rs 5,000\n• All available countries\n• Priority servers\n• Advanced connection options\n• Premium support\n\nSend payment to EasyPaisa:\n03434710858\n\nAfter payment, account activation should be verified by the NazarVPN administrator before premium servers are enabled.")
                .setPositiveButton("OK", null)
                .show();
    }

    private String readableError(Exception e) {
        String m = e.getMessage();
        if (m == null || m.trim().isEmpty()) return e.getClass().getSimpleName();
        return m;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        return b;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private final class NazarTunnel implements Tunnel {
        private final String name;
        NazarTunnel(String name) { this.name = name; }
        @Override public String getName() { return name; }
        @Override public void onStateChange(State newState) {
            if (newState == State.DOWN && activeTunnel == this) {
                main.post(() -> {
                    activeTunnel = null;
                    activeConfig = null;
                    statusText.setText("Disconnected");
                    statusText.setTextColor(Color.rgb(180, 45, 45));
                    connectButton.setText("CONNECT");
                    connectButton.setEnabled(true);
                    countrySpinner.setEnabled(true);
                    importButton.setEnabled(true);
                });
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
