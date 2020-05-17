package com.example.magicinertia;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.databinding.DataBindingUtil;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.example.magicinertia.databinding.ActivityMainBinding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final int ANIMATION_DURATION = 1000;
    private static final String TAG = "MainActivity";
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_FINE_LOCATION = 2;
    public static final String EXTRA_BT_SCAN_RESULTS = "scan results";

    private ValueAnimator backgroundAnimator;
    private Handler mHandler;
    private ActivityMainBinding mBinding;
    final Runnable stopScanning = new Runnable() {
        public void run() {
            stopScan();
        }
    };
    private boolean mScanning = false;

    private Map<String, BluetoothDevice> mScanResults;
    private BluetoothAdapter mBluetoothAdapter;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //this.getSupportActionBar().hide();
        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        final ConstraintLayout innerLayout = findViewById(R.id.inner_layout);

        setupBT();

        backgroundAnimator = ValueAnimator.ofInt(0, 0xFF);
        backgroundAnimator.setDuration(ANIMATION_DURATION);
        backgroundAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                //innerLayout.setBackgroundColor(0xFFFFFFFF);
                innerLayout.setBackgroundColor((((int)animation.getAnimatedValue()) << 24) | 0x00FFFFFF);
            }
        });
        backgroundAnimator.addListener(new AnimatorListenerAdapter()
        {
            @Override
            public void onAnimationEnd(Animator animation)
            {
                startScan();
            }
        });

        innerLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!backgroundAnimator.isRunning())
                   backgroundAnimator.start();
                startScan();

            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        final ConstraintLayout innerLayout = findViewById(R.id.inner_layout);
        innerLayout.setBackgroundColor(0x00FFFFFF);

        mHandler = new Handler();

        if (!hasPermissions()) {
            finish();
            return;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if(mHandler != null)
            mHandler.removeCallbacksAndMessages(null);

        stopScan();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(receiver);
    }

    private void stopScan() {
        if(mScanning)
            mBluetoothAdapter.cancelDiscovery();
        mScanning = false;

        scanComplete();
    }

    private void scanComplete() {
        if (mScanResults.isEmpty()) {
            Toast toast = Toast.makeText(getApplicationContext(), "No devices found", Toast.LENGTH_SHORT);
            toast.show();
            return;
        } else {
            for (String deviceAddress : mScanResults.keySet()) {
                Log.d(TAG, "Found device: " + deviceAddress);
            }

            //If successful, start new activity
            mHandler = new Handler();
            mHandler.removeCallbacksAndMessages(null);

            Intent intent = new Intent(MainActivity.this, DeviceListActivity.class);
            intent.putExtra(EXTRA_BT_SCAN_RESULTS, (HashMap)mScanResults);
            MainActivity.this.startActivity(intent);
            this.overridePendingTransition(0, 0);
            //this.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }
    }

    private boolean hasPermissions() {
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            requestBluetoothEnable();
            Toast toast = Toast.makeText(getApplicationContext(), "Bluetooth Adapter not enabled", Toast.LENGTH_LONG);
            toast.show();
            return false;

        } else if (!hasLocationPermissions()) {
            requestLocationPermission();
            Toast toast = Toast.makeText(getApplicationContext(), "Please enable location permissions", Toast.LENGTH_LONG);
            toast.show();
            return false;
        }
        return true;
    }

    private boolean hasLocationPermissions() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
    private void requestLocationPermission() {
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_FINE_LOCATION);
    }

    private void requestBluetoothEnable() {
        if (!mBluetoothAdapter.isEnabled()) {
            //Ask user to enable BT adapter
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    private void setupBT() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            // Device doesn't support Bluetooth
            Toast toast = Toast.makeText(getApplicationContext(), "Bluetooth is not supported on this device", Toast.LENGTH_SHORT);
        }

        while(!hasPermissions())    {}

        // Register for broadcasts when a device is discovered.
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(receiver, filter);
    }

    // Create a BroadcastReceiver for ACTION_FOUND.
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Discovery has found a device. Get the BluetoothDevice
                // object and its info from the Intent.
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress(); // MAC address

                if(deviceName != null && deviceName.equals("HC-06"))
                    mScanResults.put(deviceHardwareAddress, device);
            }
        }
    };

    private void startScan() {
        mScanResults = new HashMap<>();

        //Query paired devices
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();

        if (pairedDevices.size() > 0) {
            // There are paired devices. Get the name and address of each paired device.
            for (BluetoothDevice device : pairedDevices) {
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress(); // MAC address
                Log.d(TAG, "DEVICE ADDRESS: " + deviceHardwareAddress);

                mScanResults.put(deviceHardwareAddress, device);
            }
        }

        mScanning = mBluetoothAdapter.startDiscovery();

        if(mScanning) {
            mHandler.postDelayed(stopScanning, 1000);
        }

    }

}
