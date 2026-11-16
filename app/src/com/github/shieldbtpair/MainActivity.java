package com.github.shieldbtpair;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Pairs a Bluetooth device that the Android TV "Add accessory" screen refuses
 * to list. Either pick the device from the list, or pass its address:
 *
 *   adb shell am start -n com.github.shieldbtpair/.MainActivity -e addr AA:BB:CC:DD:EE:FF
 *
 * Optional: -e transport le|classic  (default: LE unless the device is known as Classic/Dual).
 *
 * Everything is also written to logcat under the tag "BtPair".
 */
public class MainActivity extends Activity {
    private static final String TAG = "BtPair";
    private static final String HID_STATE = "android.bluetooth.input.profile.action.CONNECTION_STATE_CHANGED";
    private static final String EXTRA_REASON = "android.bluetooth.device.extra.REASON";
    private static final int TRANSPORT_LE = 2;
    private static final int MAX_TRIES = 12;
    private static final long RETRY_MS = 8000;
    private static final int REQ_LOCATION = 1;

    private static final class Found {
        BluetoothDevice device;
        String name;
        int rssi = Short.MIN_VALUE;
    }

    private final Handler handler = new Handler();
    private final List<Found> found = new ArrayList<Found>();
    private final List<String> rows = new ArrayList<String>();
    private ArrayAdapter<String> listAdapter;
    private TextView status;
    private TextView logView;
    private BluetoothAdapter adapter;
    private BluetoothDevice target;
    private String transport = "auto";
    private boolean scanning;
    private boolean registered;
    private int tries;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();

        adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            setStatus("Bluetooth is off or not available.");
            return;
        }

        IntentFilter f = new IntentFilter();
        f.addAction(BluetoothDevice.ACTION_FOUND);
        f.addAction(BluetoothDevice.ACTION_NAME_CHANGED);
        f.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        f.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        f.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        f.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        f.addAction(HID_STATE);
        registerReceiver(receiver, f);
        registered = true;

        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (adapter != null && adapter.isEnabled()) handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        String addr = intent.getStringExtra("addr");
        String tr = intent.getStringExtra("transport");
        transport = tr == null ? "auto" : tr.trim().toLowerCase(Locale.ROOT);
        if (addr != null) {
            addr = addr.trim().toUpperCase(Locale.ROOT);
            if (!BluetoothAdapter.checkBluetoothAddress(addr)) {
                setStatus("Invalid address: " + addr + "  (expected AA:BB:CC:DD:EE:FF)");
                return;
            }
            pair(adapter.getRemoteDevice(addr));
        } else {
            startScan();
        }
    }

    // ---------------------------------------------------------------- UI

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(60, 40, 60, 40);

        status = new TextView(this);
        status.setTextSize(20);
        root.addView(status);

        Button rescan = new Button(this);
        rescan.setText("Scan again");
        rescan.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { target = null; startScan(); }
        });
        root.addView(rescan, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        ListView list = new ListView(this);
        listAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, rows);
        list.setAdapter(listAdapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                if (pos >= 0 && pos < found.size()) pair(found.get(pos).device);
            }
        });
        root.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 3f));

        logView = new TextView(this);
        logView.setTextSize(14);
        root.addView(logView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 2f));

        setContentView(root);
    }

    private void setStatus(String s) {
        Log.i(TAG, s);
        status.setText(s);
    }

    private void log(String s) {
        Log.i(TAG, s);
        String line = new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date()) + "  " + s + "\n";
        CharSequence old = logView.getText();
        // keep the newest lines on top so they stay visible on a TV
        logView.setText(line + (old.length() > 1500 ? old.subSequence(0, 1500) : old));
    }

    // ---------------------------------------------------------------- scan

    private void startScan() {
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            setStatus("Location permission is required by Android to list nearby Bluetooth devices.");
            requestPermissions(new String[] {android.Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION);
            return;
        }
        handler.removeCallbacks(retryTask);
        found.clear();
        refreshRows();
        scanning = true;
        adapter.cancelDiscovery();
        boolean ok = adapter.startDiscovery();
        setStatus(ok
                ? "Scanning ... put your device into pairing mode now, then select it below."
                : "Could not start the Bluetooth scan.");
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code != REQ_LOCATION) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            startScan();
        } else {
            setStatus("No permission, no device list. Start with  -e addr AA:BB:CC:DD:EE:FF  instead.");
        }
    }

    private void onFound(BluetoothDevice d, String name, int rssi) {
        Found e = null;
        for (Found x : found) if (x.device.equals(d)) { e = x; break; }
        if (e == null) { e = new Found(); e.device = d; found.add(e); }
        if (name == null) name = d.getName();
        if (name != null) e.name = name;
        if (rssi != Short.MIN_VALUE) e.rssi = rssi;
        // named devices first, then strongest signal
        Collections.sort(found, new Comparator<Found>() {
            public int compare(Found a, Found b) {
                if ((a.name == null) != (b.name == null)) return a.name == null ? 1 : -1;
                return b.rssi - a.rssi;
            }
        });
        refreshRows();
    }

    private void refreshRows() {
        rows.clear();
        for (Found e : found) {
            rows.add((e.name == null ? "(no name)" : e.name) + "   " + e.device.getAddress()
                    + "   " + typeName(e.device)
                    + (e.rssi == Short.MIN_VALUE ? "" : "   " + e.rssi + " dBm")
                    + (e.device.getBondState() == BluetoothDevice.BOND_BONDED ? "   [paired]" : ""));
        }
        listAdapter.notifyDataSetChanged();
    }

    private static String typeName(BluetoothDevice d) {
        switch (d.getType()) {
            case BluetoothDevice.DEVICE_TYPE_LE: return "LE";
            case BluetoothDevice.DEVICE_TYPE_CLASSIC: return "Classic";
            case BluetoothDevice.DEVICE_TYPE_DUAL: return "Dual";
            default: return "?";
        }
    }

    // ---------------------------------------------------------------- pair

    private void pair(BluetoothDevice d) {
        scanning = false;
        target = d;
        tries = 0;
        String n = d.getName();
        setStatus("Pairing " + (n == null ? "" : n + "  ") + d.getAddress()
                + " ... keep the device in pairing mode.");
        attempt();
    }

    private void attempt() {
        if (target == null) return;
        handler.removeCallbacks(retryTask);
        int st = target.getBondState();
        if (st == BluetoothDevice.BOND_BONDED) {
            setStatus("PAIRED: " + target.getAddress() + ". You can close this app and uninstall it.");
            return;
        }
        if (st != BluetoothDevice.BOND_BONDING) {
            tries++;
            adapter.cancelDiscovery();
            boolean ok;
            String how;
            // A device that only speaks Bluetooth LE must be bonded over LE.
            // For an address we have never seen, the stack cannot know that.
            int type = target.getType();
            boolean classic = "classic".equals(transport)
                    || (!"le".equals(transport)
                        && (type == BluetoothDevice.DEVICE_TYPE_CLASSIC || type == BluetoothDevice.DEVICE_TYPE_DUAL));
            if (classic) {
                ok = target.createBond();
                how = "createBond()";
            } else {
                try {
                    Method m = BluetoothDevice.class.getMethod("createBond", int.class);
                    ok = (Boolean) m.invoke(target, TRANSPORT_LE);
                    how = "createBond(LE)";
                } catch (Throwable t) {
                    ok = target.createBond();
                    how = "createBond() [fallback]";
                }
            }
            log("Attempt " + tries + "/" + MAX_TRIES + ": " + how + " = " + ok);
        }
        if (tries < MAX_TRIES) {
            handler.postDelayed(retryTask, RETRY_MS);
        } else {
            setStatus("Giving up after " + MAX_TRIES + " attempts. Select the device again to retry.");
        }
    }

    private final Runnable retryTask = new Runnable() {
        public void run() { attempt(); }
    };

    // ---------------------------------------------------------------- events

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context ctx, Intent i) {
            String a = i.getAction();
            if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(a)) {
                if (scanning) adapter.startDiscovery();
                return;
            }
            BluetoothDevice d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (d == null) return;
            if (BluetoothDevice.ACTION_FOUND.equals(a) || BluetoothDevice.ACTION_NAME_CHANGED.equals(a)) {
                if (scanning) {
                    onFound(d, i.getStringExtra(BluetoothDevice.EXTRA_NAME),
                            i.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE));
                }
                return;
            }
            if (target == null || !d.equals(target)) return;
            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(a)) {
                int st = i.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);
                if (st == BluetoothDevice.BOND_BONDED) {
                    handler.removeCallbacks(retryTask);
                    log("Bonded.");
                    setStatus("PAIRED: " + target.getAddress() + ". You can close this app and uninstall it.");
                    refreshRows();
                } else if (st == BluetoothDevice.BOND_BONDING) {
                    log("Bonding ...");
                } else {
                    log("Not bonded (reason " + i.getIntExtra(EXTRA_REASON, -1) + "), will retry.");
                }
            } else if (HID_STATE.equals(a)) {
                int st = i.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
                log("Input device state: " + st + (st == 2 ? " (connected)" : ""));
            } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(a)) {
                log("Radio link up.");
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(a)) {
                log("Radio link down.");
            }
        }
    };

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        scanning = false;
        if (registered) unregisterReceiver(receiver);
        if (adapter != null) {
            try { adapter.cancelDiscovery(); } catch (Throwable t) { /* ignore */ }
        }
        super.onDestroy();
    }
}
