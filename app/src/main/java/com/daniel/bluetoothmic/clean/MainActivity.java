package com.daniel.bluetoothmic.clean;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int REQUEST_AUDIO = 1001;
    private static final int REQUEST_BT = 1002;
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO;
    private static final int CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private TextView stateView;
    private TextView bluetoothView;
    private TextView deviceView;
    private TextView effectView;
    private Button startButton;
    private Button stopButton;
    private Spinner effectSpinner;
    private SeekBar volumeBar;

    private AudioRecord recorder;
    private AudioTrack player;
    private Thread audioThread;
    private volatile boolean running;
    private volatile int volumePercent = 85;
    private volatile String effect = "Normal";
    private short[] effectDelay;
    private int effectIndex;

    private final List<AudioDeviceInfo> outputDevices = new ArrayList<>();
    private ArrayAdapter<String> deviceAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        stateView = findViewById(R.id.stateView);
        bluetoothView = findViewById(R.id.bluetoothView);
        deviceView = findViewById(R.id.deviceView);
        effectView = findViewById(R.id.effectView);
        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);
        effectSpinner = findViewById(R.id.effectSpinner);
        volumeBar = findViewById(R.id.volumeBar);

        ArrayAdapter<String> effects = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Normal", "Echo", "Robot", "Deep"});
        effectSpinner.setAdapter(effects);
        effectSpinner.setSelection(0);
        effectSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                effect = (String) parent.getItemAtPosition(position);
                effectView.setText("Effect: " + effect);
                resetEffectState();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        volumeBar.setProgress(volumePercent);
        volumeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                volumePercent = Math.max(0, Math.min(100, progress));
                if (player != null) {
                    player.setVolume(volumePercent / 100f);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });

        startButton.setOnClickListener(v -> startMicrophone());
        stopButton.setOnClickListener(v -> stopMicrophone());
        findViewById(R.id.deviceButton).setOnClickListener(v -> chooseBluetoothDevice());
        findViewById(R.id.settingsButton).setOnClickListener(v ->
                Toast.makeText(this, "Settings: 44.1 kHz • Mono • PCM 16-bit", Toast.LENGTH_LONG).show());
        findViewById(R.id.effectsButton).setOnClickListener(v -> effectSpinner.performClick());

        updateBluetoothStatus();
        updateUi(false);
    }

    private void startMicrophone() {
        if (running) return;

        if (!hasAudioPermission()) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO);
            return;
        }

        if (!ensureBluetoothPermissions()) return;

        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            showError("שירות האודיו של המכשיר אינו זמין.");
            return;
        }

        updateBluetoothStatus();
        if (!isBluetoothEnabled()) {
            showError("Bluetooth כבוי. הפעל Bluetooth וחבר רמקול Bluetooth.");
            return;
        }

        AudioDeviceInfo selected = findPreferredBluetoothOutput(audioManager);
        if (selected == null) {
            showError("לא נמצא התקן Bluetooth מחובר. חבר רמקול Bluetooth ונסה שוב.");
            return;
        }

        int minIn = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING);
        int minOut = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, ENCODING);
        if (minIn <= 0 || minOut <= 0) {
            showError("המכשיר לא סיפק גודל Buffer תקין עבור האודיו.");
            return;
        }

        int inBuffer = Math.max(minIn * 2, 4096);
        int outBuffer = Math.max(minOut * 2, 4096);

        try {
            recorder = new AudioRecord(
                    android.media.MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE, CHANNEL_IN, ENCODING, inBuffer);

            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                releaseAudio();
                showError("לא ניתן לפתוח את המיקרופון במכשיר זה.");
                return;
            }

            AudioFormat format = new AudioFormat.Builder()
                    .setEncoding(ENCODING)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_OUT)
                    .build();

            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();

            player = new AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(format)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(outBuffer)
                    .build();

            if (player.getState() != AudioTrack.STATE_INITIALIZED) {
                releaseAudio();
                showError("לא ניתן לפתוח את יציאת האודיו.");
                return;
            }

            boolean routed = false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    routed = player.setPreferredDevice(selected);
                } catch (SecurityException ignored) {
                    routed = false;
                }
            }

            player.setVolume(volumePercent / 100f);
            player.play();
            recorder.startRecording();

            if (recorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                releaseAudio();
                showError("המיקרופון לא עבר למצב הקלטה.");
                return;
            }

            running = true;
            resetEffectState();
            updateUi(true);
            if (!routed) {
                Toast.makeText(this, "האודיו הופעל; Android יבחר את נתיב Bluetooth הזמין.", Toast.LENGTH_LONG).show();
            }

            audioThread = new Thread(this::audioLoop, "BluetoothMic-Audio");
            audioThread.start();
        } catch (SecurityException e) {
            releaseAudio();
            showError("אין הרשאת Bluetooth/מיקרופון. אשר הרשאות ונסה שוב.");
        } catch (Exception e) {
            releaseAudio();
            showError("שגיאת שמע: " + safeMessage(e));
        }
    }

    private void audioLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
        int bufferSize = Math.max(4096, AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING));
        short[] buffer = new short[bufferSize / 2];

        while (running) {
            int read;
            try {
                read = recorder.read(buffer, 0, buffer.length, AudioRecord.READ_BLOCKING);
            } catch (Exception e) {
                read = -1;
            }

            if (read <= 0) {
                if (running) {
                    runOnUiThread(() -> showError("קריאת המיקרופון נכשלה."));
                    stopMicrophone();
                }
                break;
            }

            applyEffect(buffer, read);

            int offset = 0;
            while (running && offset < read) {
                int written;
                try {
                    written = player.write(buffer, offset, read - offset, AudioTrack.WRITE_BLOCKING);
                } catch (Exception e) {
                    written = -1;
                }
                if (written <= 0) {
                    if (running) {
                        runOnUiThread(() -> showError("הפעלת האודיו נכשלה."));
                        stopMicrophone();
                    }
                    break;
                }
                offset += written;
            }
        }
    }

    private void applyEffect(short[] data, int count) {
        if ("Normal".equals(effect)) return;

        if (effectDelay == null || effectDelay.length != SAMPLE_RATE / 2) {
            effectDelay = new short[SAMPLE_RATE / 2];
            effectIndex = 0;
        }

        if ("Echo".equals(effect)) {
            for (int i = 0; i < count; i++) {
                short delayed = effectDelay[effectIndex];
                int mixed = data[i] + (int) (delayed * 0.48f);
                data[i] = clamp16(mixed);
                effectDelay[effectIndex] = data[i];
                effectIndex = (effectIndex + 1) % effectDelay.length;
            }
        } else if ("Robot".equals(effect)) {
            for (int i = 0; i < count; i++) {
                double carrier = Math.sin(2.0 * Math.PI * 90.0 * (effectIndex++ % SAMPLE_RATE) / SAMPLE_RATE);
                data[i] = clamp16((int) (data[i] * carrier));
            }
        } else if ("Deep".equals(effect)) {
            for (int i = 0; i < count; i++) {
                int prev = effectDelay[effectIndex];
                int smoothed = (prev * 3 + data[i]) / 4;
                effectDelay[effectIndex] = (short) smoothed;
                effectIndex = (effectIndex + 1) % effectDelay.length;
                data[i] = clamp16((int) (smoothed * 0.90f));
            }
        }
    }

    private short clamp16(int value) {
        return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
    }

    private void stopMicrophone() {
        running = false;
        Thread t = audioThread;
        audioThread = null;
        if (t != null && t != Thread.currentThread()) {
            try { t.join(350); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }
        releaseAudio();
        updateUi(false);
    }

    private void releaseAudio() {
        if (recorder != null) {
            try { recorder.stop(); } catch (Exception ignored) {}
            try { recorder.release(); } catch (Exception ignored) {}
            recorder = null;
        }
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) {}
            try { player.release(); } catch (Exception ignored) {}
            player = null;
        }
        resetEffectState();
    }

    private void chooseBluetoothDevice() {
        if (!ensureBluetoothPermissions()) return;
        AudioManager manager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (manager == null) return;

        outputDevices.clear();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (AudioDeviceInfo info : manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                if (isBluetoothOutput(info)) outputDevices.add(info);
            }
        }

        if (outputDevices.isEmpty()) {
            showError("לא נמצא התקן Bluetooth Audio מחובר. חבר רמקול Bluetooth ונסה שוב.");
            return;
        }

        final String[] names = new String[outputDevices.size()];
        for (int i = 0; i < outputDevices.size(); i++) {
            names[i] = deviceName(outputDevices.get(i));
        }

        new android.app.AlertDialog.Builder(this)
                .setTitle("בחירת התקן Bluetooth")
                .setItems(names, (dialog, which) -> {
                    deviceView.setText("Output: " + names[which]);
                    Toast.makeText(this, "נבחר: " + names[which], Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("ביטול", null)
                .show();
    }

    private AudioDeviceInfo findPreferredBluetoothOutput(AudioManager manager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null;
        AudioDeviceInfo fallback = null;
        for (AudioDeviceInfo info : manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            if (info.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) return info;
            if (info.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) fallback = info;
        }
        return fallback;
    }

    private boolean isBluetoothOutput(AudioDeviceInfo info) {
        int type = info.getType();
        return type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO;
    }

    private String deviceName(AudioDeviceInfo info) {
        CharSequence name = info.getProductName();
        return name == null || name.length() == 0 ? "Bluetooth Audio" : name.toString();
    }

    private boolean isBluetoothEnabled() {
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            return adapter != null && adapter.isEnabled();
        } catch (SecurityException e) {
            return false;
        }
    }

    private void updateBluetoothStatus() {
        boolean connected = false;
        try {
            AudioManager manager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (manager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                connected = findPreferredBluetoothOutput(manager) != null;
            }
        } catch (Exception ignored) {}
        bluetoothView.setText("Bluetooth: " + (connected ? "Connected" : "Disconnected"));
        bluetoothView.setTextColor(ContextCompat.getColor(this,
                connected ? R.color.status_ok : R.color.status_bad));
    }

    private void updateUi(boolean active) {
        stateView.setText(active ? "MIC ON" : "MIC OFF");
        stateView.setTextColor(ContextCompat.getColor(this,
                active ? R.color.status_ok : R.color.text_primary));
        startButton.setEnabled(!active);
        stopButton.setEnabled(active);
        updateBluetoothStatus();
    }

    private boolean hasAudioPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean ensureBluetoothPermissions() {
        // This build deliberately targets API 30. Android documents that apps
        // targeting Android 11/API 30 or lower use the legacy BLUETOOTH permission
        // model; the Android 12 Nearby Devices runtime model is for target 31+.
        // We still declare BLUETOOTH_SCAN/CONNECT so the project can be migrated
        // cleanly later, but we do not incorrectly block an API-30-targeted app
        // by requesting permissions that are not part of its runtime model.
        return true;
    }

    private void resetEffectState() {
        effectDelay = null;
        effectIndex = 0;
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        if (!running) updateUi(false);
    }

    private String safeMessage(Exception e) {
        String m = e.getMessage();
        return m == null || m.trim().isEmpty() ? e.getClass().getSimpleName() : m;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startMicrophone();
            } else {
                showError("נדרשת הרשאת מיקרופון כדי להפעיל את Bluetooth Mic.");
            }
        } else if (requestCode == REQUEST_BT) {
            boolean ok = true;
            for (int result : grantResults) ok &= result == PackageManager.PERMISSION_GRANTED;
            if (ok) startMicrophone();
            else showError("נדרשות הרשאות Bluetooth כדי לבחור התקן שמע.");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateBluetoothStatus();
    }

    @Override
    protected void onDestroy() {
        stopMicrophone();
        super.onDestroy();
    }
}
