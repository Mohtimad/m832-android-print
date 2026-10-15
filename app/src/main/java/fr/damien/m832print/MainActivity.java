package fr.damien.m832print;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Insets;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toolbar;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Printer status and entry point to Android print settings. */
public final class MainActivity extends Activity {
    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final int BLUETOOTH_REQUEST = 1;
    private TextView status;
    private Button refresh;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setDecorFitsSystemWindows(false);

        int pad = Math.round(24 * getResources().getDisplayMetrics().density);
        int headerHeight = Math.round(56 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });

        Toolbar header = new Toolbar(this);
        header.setTitle(R.string.app_name);
        header.setTitleTextColor(Color.BLACK);
        header.setBackgroundColor(Color.WHITE);
        header.setElevation(Math.round(2 * getResources().getDisplayMetrics().density));
        root.addView(header, new LinearLayout.LayoutParams(-1, headerHeight));
        setActionBar(header);

        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(pad, pad, pad, pad);

        TextView help = new TextView(this);
        help.setText(R.string.help_print_pdf);
        help.setTextSize(16);
        help.setPadding(0, pad / 2, 0, pad);
        layout.addView(help);

        status = new TextView(this);
        status.setText(R.string.status_idle);
        status.setTextSize(18);
        status.setTextIsSelectable(true);
        status.setPadding(0, 0, 0, pad / 2);
        layout.addView(status);

        refresh = new Button(this);
        refresh.setText(R.string.refresh_status);
        refresh.setOnClickListener(this::onRefreshClicked);
        layout.addView(refresh);

        Button settings = new Button(this);
        settings.setText(R.string.android_print_settings);
        settings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_PRINT_SETTINGS)));
        layout.addView(settings);
        scroll.addView(layout);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        WindowInsetsController bars = getWindow().getInsetsController();
        if (bars != null) {
            int lightBars = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            bars.setSystemBarsAppearance(lightBars, lightBars);
        }

        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
            onRefreshClicked(null);
        else status.setText(R.string.bluetooth_permission_needed);
    }

    private void onRefreshClicked(View ignored) {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, BLUETOOTH_REQUEST);
            return;
        }
        refresh.setEnabled(false);
        status.setText(R.string.status_reading);
        new Thread(this::readStatus, "m832-status").start();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode != BLUETOOTH_REQUEST) return;
        if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED)
            onRefreshClicked(null);
        else status.setText(R.string.bluetooth_permission_denied);
    }

    private void readStatus() {
        try {
            BluetoothDevice printer = findPrinter();
            ByteArrayOutputStream reply = new ByteArrayOutputStream();
            try (BluetoothSocket socket = printer.createRfcommSocketToServiceRecord(SPP)) {
                socket.connect();
                OutputStream output = socket.getOutputStream();
                output.write(new byte[]{
                        0x1F, 0x11, 0x12, // Cover
                        0x1F, 0x11, 0x07, // Firmware version
                        0x1F, 0x11, 0x11, // Paper
                        0x1F, 0x11, 0x08  // Unconfirmed battery code
                });
                output.flush();
                InputStream input = socket.getInputStream();
                long deadline = android.os.SystemClock.elapsedRealtime() + 2500;
                byte[] buffer = new byte[256];
                while (android.os.SystemClock.elapsedRealtime() < deadline && reply.size() < 256) {
                    int available = input.available();
                    if (available > 0) {
                        int count = input.read(buffer, 0, Math.min(available, buffer.length));
                        if (count > 0) reply.write(buffer, 0, count);
                    } else {
                        Thread.sleep(50);
                    }
                }
            }
            byte[] response = reply.toByteArray();
            String paper = getString(R.string.unknown);
            String batteryCode = getString(R.string.unavailable);
            String cover = getString(R.string.unknown);
            String coverCode = getString(R.string.unavailable);
            StringBuilder hex = new StringBuilder();
            for (byte value : response) hex.append(String.format(Locale.US, "%02X ", value & 0xFF));
            for (int i = 2; i < response.length; i++) {
                if ((response[i - 2] & 0xFF) != 0x1A) continue;
                int kind = response[i - 1] & 0xFF;
                int value = response[i] & 0xFF;
                if (kind == 0x05) {
                    coverCode = String.format(Locale.US, "%02X", value);
                    if (value == 0x98) cover = getString(R.string.cover_closed);
                    if (value == 0x99) cover = getString(R.string.cover_open);
                }
                if (kind == 0x06 && value == 0x89) paper = getString(R.string.paper_present);
                if (kind == 0x06 && value == 0x88) paper = getString(R.string.paper_absent);
                if (kind == 0x04) batteryCode = String.format(Locale.US, "%02X", value);
            }
            if (response.length == 0) show(getString(R.string.printer_no_response));
            else show(getString(R.string.status_summary, cover, coverCode, paper, batteryCode, hex.toString().trim()));
        } catch (Exception error) {
            String detail = error.getMessage() == null ? getString(R.string.unexpected_error) : error.getMessage();
            show(getString(R.string.status_unavailable, detail));
        }
    }

    private BluetoothDevice findPrinter() {
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) throw new IllegalStateException(getString(R.string.bluetooth_disabled));
        Set<BluetoothDevice> bonded = adapter.getBondedDevices();
        for (BluetoothDevice device : bonded) {
            if ("M832".equalsIgnoreCase(device.getName())) return device;
        }
        throw new IllegalStateException(getString(R.string.printer_not_paired));
    }

    private void show(String message) {
        runOnUiThread(() -> {
            status.setText(message);
            refresh.setEnabled(true);
        });
    }
}
