package com.example.magicinertia;

import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.example.magicinertia.databinding.ActivityDeviceListBinding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class DeviceListActivity extends AppCompatActivity {

    public static final String EXTRA_DEVICE_ADDRESS = "device address";
    public static final String EXTRA_BT_SCAN_RESULTS = "scan results";
    private static final int ANIMATION_DURATION = 600;
    private ValueAnimator transparencyAnimator;
    private ActivityDeviceListBinding mBinding;

    //Bluetooth stuff
    private Map<String, BluetoothDevice> mScanResults;
    private ArrayList<String> bluetoothAddresses;
    private ArrayList<BluetoothDevice> bluetoothDevices;

    //List stuff
    private int numDevices = 1;
    private int currentDevice = 0;  //Index from 0
    private int direction = 0;

    @Override
    @SuppressWarnings("unchecked")  //For HashMap cast
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.overridePendingTransition(0, 0);
        setContentView(R.layout.activity_device_list);
        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_device_list);


        //Retrieve devices from previous scan
        Intent intent = getIntent();
        mScanResults = (HashMap<String, BluetoothDevice>)intent.getSerializableExtra(MainActivity.EXTRA_BT_SCAN_RESULTS);
        bluetoothAddresses = new ArrayList<>();
        bluetoothDevices = new ArrayList<>();
        for(String deviceAddress : mScanResults.keySet()) {
            bluetoothAddresses.add(deviceAddress);
            bluetoothDevices.add(mScanResults.get(deviceAddress));
        }

        //Device numbers
        numDevices = bluetoothDevices.size();
        mBinding.deviceNumber.setText((currentDevice + 1) + "/" + numDevices);
        mBinding.deviceAddress.setText(bluetoothAddresses.get(currentDevice));

        transparencyAnimator = ValueAnimator.ofInt(0xFF, 0);
        transparencyAnimator.setDuration(ANIMATION_DURATION);
        transparencyAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                setTransparencyOfAll((int)animation.getAnimatedValue());
            }
        });

        transparencyAnimator.addListener(new AnimatorListenerAdapter()
        {
            @Override
            public void onAnimationRepeat(Animator animation)
            {
                changeDevice();
            }
        });

        mBinding.rightButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!transparencyAnimator.isRunning()) {
                    transparencyAnimator.start();
                    transparencyAnimator.setRepeatCount(1);
                    transparencyAnimator.setRepeatMode(ValueAnimator.REVERSE);
                }

                direction = 1;
            }
        });

        mBinding.leftButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!transparencyAnimator.isRunning()) {
                    transparencyAnimator.start();
                    transparencyAnimator.setRepeatCount(1);
                    transparencyAnimator.setRepeatMode(ValueAnimator.REVERSE);
                }

                direction = -1;
            }
        });

        mBinding.buttonConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startGraphActivity();
            }
        });
    }

    private void setTransparencyOfAll(int transparency) {
        int color = (transparency << 24) | 0x004A4A4A;
        mBinding.buttonConnect.setTextColor((transparency << 24) | 0x00FFFFFF);
        mBinding.buttonConnect.getBackground().setAlpha(transparency);
        mBinding.deviceAddress.setTextColor(color);
        mBinding.deviceFoundText.setTextColor(color);
        mBinding.deviceImage.setImageAlpha(transparency);
        mBinding.deviceName.setTextColor(color);
        mBinding.deviceNumber.setTextColor(color);
        mBinding.leftButton.setImageAlpha(transparency);
        mBinding.rightButton.setImageAlpha(transparency);
    }

    private void changeDevice() {
        currentDevice += direction + numDevices;
        currentDevice %= numDevices;
        String deviceAddress = bluetoothAddresses.get(currentDevice);

        mBinding.deviceNumber.setText((currentDevice+1) + "/" + numDevices);
        mBinding.deviceName.setText(mScanResults.get(deviceAddress).getName());
        mBinding.deviceAddress.setText(deviceAddress);

        direction = 0;
    }

    private void startGraphActivity() {
        //Intent intent = new Intent(DeviceListActivity.this, SampleGraphActivity.class);
        //DeviceListActivity.this.startActivity(intent);

        String currentDeviceAddress = bluetoothAddresses.get(currentDevice);

        Intent intent = new Intent(DeviceListActivity.this, GraphActivity.class);
        intent.putExtra(EXTRA_BT_SCAN_RESULTS, (HashMap)mScanResults);
        intent.putExtra(EXTRA_DEVICE_ADDRESS, currentDeviceAddress);
        DeviceListActivity.this.startActivity(intent);
    }
}
