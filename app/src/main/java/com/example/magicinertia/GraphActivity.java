package com.example.magicinertia;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.databinding.DataBindingUtil;

import com.example.magicinertia.databinding.ActivityGraphBinding;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class GraphActivity extends AppCompatActivity implements PopupMenu.OnMenuItemClickListener {

    private String TAG = "fGraph activity";

    private ActivityGraphBinding mBinding;
    private final Handler mHandler = new Handler();

    //This handler does Bluetooth data reception.
    private final Handler btHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case MessageConstants.MESSAGE_READ:
                    byte[] writeBuf = (byte[]) msg.obj;

                    int begin = 0;
                    int end = (int)msg.arg1;

                    String writeMessage = new String(writeBuf);
                    writeMessage = writeMessage.substring(begin, end);

                    //Log.d(TAG, writeMessage);
                    //voltageBuffer = 0x00FF & writeBuf [0];

                    //For every byte received, handle
                    for(int i = begin; i < end; i++){
                        byte currentByte = (byte)(0x00FF & writeBuf[i]);
                        //Log.d(TAG, String.valueOf(currentByte));

                        //Check flag for start of message
                        if(currentByte == Flags.START) {
                            //Log.d(TAG, " START");
                            currentByteInMessage = 0;
                            curState = States.WAITING_FOR_ID;
                        } else if (curState == States.WAITING_FOR_ID) {
                            curState = States.STARTED;
                            //Log.d(TAG, "Flag: " + currentByte);

                            //Checking what ID flag was sent.
                            switch(currentByte){
                                case Flags.PHASE:
                                    currentMessageType = Flags.PHASE;
                                    break;

                                case Flags.VOLTAGE:
                                    currentMessageType = Flags.VOLTAGE;
                                    break;

                                case Flags.FREQUENCY:
                                    currentMessageType = Flags.FREQUENCY;
                                    break;

                                case Flags.POWER:
                                    currentMessageType = Flags.POWER;
                                    break;

                                default:
                                    curState = States.WAITING_FOR_START;
                                    break;


                            }
                        } else if (curState == States.STARTED && currentByteInMessage <= 3){
                            //If not start of message, add to message
                            //Place byte into message buffer
                            currentMessage[currentByteInMessage] = currentByte;

                            //If message buffer is full, convert byte array into float and put in voltageBuffer
                            if(currentByteInMessage == 3) {
                                curState = States.WAITING_FOR_START;

                                //Convert byte array into float
                                //Big Endian Conversion
                                float f = ByteBuffer.wrap(currentMessage).order(ByteOrder.BIG_ENDIAN).getFloat();
                                messagesReceived ++;
                                Log.d(TAG, "Full float: " + String.valueOf(f));

                                //Put in buffer to plot if enough chars have been received
                                //Eventually change to different buffers for different data
                                //if(messagesReceived % (BAUD_RATE / 32 * SAMPLE_PERIOD) == 0) {
                                    hasData = true;
                                    switch(currentMessageType) {
                                        case Flags.FREQUENCY:
                                            dataBuffer[0][numInBuffer[0]] = f;
                                            numInBuffer[0]++;
                                            break;
                                        case Flags.VOLTAGE:
                                            dataBuffer[1][numInBuffer[1]] = f;
                                            numInBuffer[1]++;
                                            break;
                                        case Flags.PHASE:
                                            dataBuffer[2][numInBuffer[2]] = f;
                                            numInBuffer[2]++;
                                            break;
                                        case Flags.POWER:
                                            dataBuffer[3][numInBuffer[3]] = f;
                                            numInBuffer[3]++;
                                            break;
                                    }
                                    //Log.d(TAG, "Full float: " + String.valueOf(f));

                                //}
                            }
                            currentByteInMessage ++;
                        }
                    }


                    //Log.d(TAG, writeMessage);
                    break;

                case MessageConstants.MESSAGE_TOAST:
                    String text = (String) msg.obj;
                    if(text.equals("Connected!")) {
                        mConnected = true;
                    }
                    Toast toast = Toast.makeText(getApplicationContext(),text, Toast.LENGTH_SHORT);
                    toast.show();
                    break;
            }


        }
    };

    //private ArrayList<Float> voltageBuffer = new ArrayList<>();
    private static final int BUFFER_SIZE = 1024;
    private static final int BAUD_RATE = 9600;
    private static final float SAMPLE_PERIOD = 0.01f;
    private byte currentMessage[] = new byte[4];                                                    //The current float message in bytes
    private int currentByteInMessage = 5;
    private int currentMessageType = Flags.FREQUENCY;
    private float dataBuffer[][] = new float[4][BUFFER_SIZE];                                         //Holding floats until ready to plot
    private int numInBuffer[] = {0, 0, 0, 0};
    private int messagesReceived = 0;
    private int curState = States.WAITING_FOR_START;
    private boolean hasData = false;
    private boolean mConnected = false;

    private Runnable mTimer1;
    private LineGraphSeries<DataPoint> fSeries;
    private LineGraphSeries<DataPoint> vSeries;
    private LineGraphSeries<DataPoint> phSeries;
    private LineGraphSeries<DataPoint> poSeries;
    private double graphXValue[] = {0d, 0d, 0d, 0d};
    long tStart[] = {0, 0, 0, 0};

    //Sim data
    private boolean simData = false;
    private float lastFreqValue = 60;
    private float lastVoltValue = 120;
    private float lastPhaseValue = 0;
    private float lastPowerValue = 10;


    private GraphView fGraph;
    private GraphView vGraph;
    private GraphView phGraph;
    private GraphView poGraph;

    //Date Storage
    private boolean storeData = false;
    private String fileName;
    private String path = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "Magic_Inertia";

    //BT Stuff
    private String deviceAddress = "";
    private Map<String, BluetoothDevice> mScanResults;
    private BluetoothDevice mBluetoothDevice;
    private ConnectThread mConnectThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sample_graph);
        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_graph);


        invalidateOptionsMenu();

        //Retrieve devices from previous scan
        Intent intent = getIntent();
        mScanResults = (HashMap<String, BluetoothDevice>)intent.getSerializableExtra(DeviceListActivity.EXTRA_BT_SCAN_RESULTS);
        deviceAddress = (String)intent.getSerializableExtra(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        mBluetoothDevice = mScanResults.get(deviceAddress);
        mBinding.deviceAddress.setText(deviceAddress);

        fGraph = mBinding.freqGraph;
        vGraph = mBinding.voltGraph;
        phGraph = mBinding.phaseGraph;
        poGraph = mBinding.powerGraph;

        fSeries = new LineGraphSeries<>();
        vSeries = new LineGraphSeries<>();
        phSeries = new LineGraphSeries<>();
        poSeries = new LineGraphSeries<>();
        setUpGraph(fGraph, fSeries, 59, 61, false, "Frequency (f)");
        setUpGraph(vGraph, vSeries, 110, 130, false, "Voltage (V)");
        setUpGraph(phGraph, phSeries, -10, 10, false, "Phase (degrees)");
        setUpGraph(poGraph, poSeries, 0, 130, true, "Power (W)");


        //Try Connecting
        mConnectThread = new ConnectThread(mBluetoothDevice);
        mConnectThread.start();

        //Set record text invisible
        mBinding.reccordText.setVisibility(View.GONE);
    }

    @Override
    public void onResume() {
        super.onResume();

        for(int i = 0; i < 4; i ++)
            tStart[i] = System.currentTimeMillis();



        mTimer1 = new Runnable() {
            @Override
            public void run() {
                //graphLastXValue += 1d;

                //Real BT data received, here we plot it in our graphs.
                if(hasData && !simData) {
                    for(int r = 0; r < 4; r++) {
                        //Loop through all buffers
                        for (int i = 0; i < numInBuffer[r]; i++) {
                            //Log.d(TAG, String.valueOf(voltageBuffer[i]));
                            switch (r) {
                                case 0:
                                    fSeries.appendData(new DataPoint(graphXValue[r], dataBuffer[r][i]), true, 2000);
                                    break;
                                case 1:
                                    vSeries.appendData(new DataPoint(graphXValue[r], dataBuffer[r][i]), true, 2000);
                                    break;
                                case 2:
                                    phSeries.appendData(new DataPoint(graphXValue[r], dataBuffer[r][i]), true, 2000);
                                    break;
                                case 3:
                                    poSeries.appendData(new DataPoint(graphXValue[r], dataBuffer[r][i]), true, 2000);
                                    break;
                            }

                            //Use the real time from phone clock as x value
                            long tEnd = System.currentTimeMillis();
                            long delta = (tEnd - tStart[r]);
                            graphXValue[r] += (float) (delta / 1000.0);
                            tStart[r] = tEnd;
                            //Reset hasData flag (will wait until more data has arrived ovre BT)
                            hasData = false;
                        }
                        //No available data in buffer after plotting
                        numInBuffer[r] = 0;
                    }
                } else if (simData) {       //Here, we use random data form (not real BT signal)
                    String data[] = new String[5];
                    //Loop over for each graph
                    for(int r = 0; r < 4; r++) {
                        switch (r) {
                            case 0:
                                lastFreqValue += getRandom(1000, -500, 10000);
                                lastFreqValue = min(max(59, lastFreqValue), 61);
                                data[1] = Float.toString(lastFreqValue);
                                fSeries.appendData(new DataPoint(graphXValue[r], lastFreqValue), true, 2000);
                                break;
                            case 1:
                                lastVoltValue += getRandom(1000, -500, 3000);
                                lastVoltValue = min(max(110, lastVoltValue), 130);
                                data[2] = Float.toString(lastVoltValue);
                                vSeries.appendData(new DataPoint(graphXValue[r], lastVoltValue), true, 2000);
                                break;
                            case 2:
                                lastPhaseValue += getRandom(1000, -500, 2000);
                                lastPhaseValue = min(max(-10, lastPhaseValue), 10);
                                data[3] = Float.toString(lastPhaseValue);
                                phSeries.appendData(new DataPoint(graphXValue[r], lastPhaseValue), true, 2000);
                                break;
                            case 3:
                                lastPowerValue += getRandom(1000, -500, 1000);
                                lastPowerValue = min(max(5, lastPowerValue), 110);
                                data[0] = Double.toString(graphXValue[3]);
                                data[4] = Float.toString(lastPowerValue);
                                poSeries.appendData(new DataPoint(graphXValue[r], lastPowerValue), true, 2000);
                                break;
                        }
                        //Use real clock as x value
                        long tEnd = System.currentTimeMillis();
                        long delta = (tEnd - tStart[r]);
                        graphXValue[r] += (float) (delta / 1000.0);
                        tStart[r] = tEnd;
                    }

                    //If recording data, save into CSV file. Only added for the simulated data, should be simple to add for real BT tranmsitted data too.
                    if(storeData)
                        writeCSV(data, 4);
                }

                //Wait
                mHandler.postDelayed(this, (long) (20));
            }
        };
        mHandler.postDelayed(mTimer1, 100);
    }

    @Override
    public void onPause() {
        mHandler.removeCallbacks(mTimer1);
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(mConnected)
            mConnectThread.cancel();
    }

    private static final float durationOfGraph = 60;
    private void setUpGraph(GraphView g, LineGraphSeries<DataPoint> s, float minY, float maxY, boolean showXAxis, String yAxis) {
        g.addSeries(s);
        g.getViewport().setXAxisBoundsManual(true);
        g.getViewport().setYAxisBoundsManual(true);
        g.getGridLabelRenderer().setVerticalLabelsColor(Color.GRAY);
        g.getGridLabelRenderer().setHorizontalLabelsColor(Color.GRAY);
        g.getViewport().setMinX(0);
        g.getViewport().setMaxX(durationOfGraph);
        g.getViewport().setMinY(minY);
        g.getViewport().setMaxY(maxY);

        GridLabelRenderer gridLabel = g.getGridLabelRenderer();

        if(showXAxis) {
            g.getGridLabelRenderer().setHorizontalLabelsVisible(true);
            gridLabel.setHorizontalAxisTitle("Time (s)");
        } else
            g.getGridLabelRenderer().setHorizontalLabelsVisible(false);

        g.getGridLabelRenderer().setVerticalLabelsVisible(true);
        gridLabel.setVerticalAxisTitle(yAxis);

        //g.getViewport().setMinY(minY);
        //g.getViewport().setMaxY( maxY);
    }
//////////////////////////////////////////////////////////STATES/////////////////////////////////////////////////////////////////////////////////////////
    private interface States {
        public static final int WAITING_FOR_START = -99;
        public static final int WAITING_FOR_ID = -98;
        public static final int STARTED = -97;

    }

////////////////////////////////////////////////////////////Flags////////////////////////////////////////////////////////////////////////////////////////
    private interface Flags {
        public static final int START = -1;
        public static final int PHASE = -2;
        public static final int VOLTAGE = -3;
        public static final int FREQUENCY = -4;
        public static final int POWER = -5;
    }

////////////////////////////////////////////////////////////Message Constants////////////////////////////////////////////////////////////////////////////

    // Defines several constants used when transmitting messages between the
    // service and the UI.
    private interface MessageConstants {
        public static final int MESSAGE_READ = 0;
        public static final int MESSAGE_WRITE = 1;
        public static final int MESSAGE_TOAST = 2;
        public static final int MESSAGE_CONNECTED = 3;
        public static final int MESSAGE_CONNECT_FAIL = 4;

        // ... (Add other message types here as needed.)
    }


///////////////////////////////////////////////////////////////////Connect Thread///////////////////////////////////////////////////////////////////////////

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

        private ConnectedThread mConnectedThread;

        public ConnectThread(BluetoothDevice device) {
            BluetoothSocket tmp = null;
            mmDevice = device;
            try {
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) { }
            mmSocket = tmp;
        }
        public void run() {
            //Already cancel discovery after scan stop
            //mBluetoothAdapter.cancelDiscovery();
            try {
                Log.d(TAG, "Trying to connect...");
                mmSocket.connect();
            } catch (IOException connectException) {
                Message cntMsg = btHandler.obtainMessage(
                        MessageConstants.MESSAGE_TOAST, -1, -1,
                        "Connection failed, please try again");
                cntMsg.sendToTarget();
                Log.d(TAG, "Connect Exception");
                try {
                    mmSocket.close();
                } catch (IOException closeException) { }
                return;
            }

            mConnectedThread = new ConnectedThread(mmSocket);
            mConnectedThread.start();
        }
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }

    ////////////////////////////////////////Connected Thread/////////////////////////////////////////////////////////////////////////////////////////
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private byte[] mmBuffer; // mmBuffer store for the stream

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }
        public void run() {
            Log.d(TAG, "Connected Thread Running");
            Message cntMsg = btHandler.obtainMessage(
                    MessageConstants.MESSAGE_TOAST, -1, -1,
                    "Connected!");
            cntMsg.sendToTarget();


            mmBuffer = new byte[1024];
            int numBytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs.
            while (true) {
                try {
                    // Read from the InputStream.
                    numBytes = mmInStream.read(mmBuffer);
                    // Send the obtained bytes to the UI activity.
                    Message readMsg = btHandler.obtainMessage(
                            MessageConstants.MESSAGE_READ, numBytes, -1,
                            mmBuffer);
                    readMsg.sendToTarget();
                } catch (IOException e) {
                    Log.d(TAG, "Input stream was disconnected", e);
                    break;
                }
            }
        }
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) { }
        }
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }

    //////////////////////////////////////////SAVING TO MEMORY/////////////////////////////////////
    private void toggleRecord(MenuItem menuItem) {
        if(!storeData) {
            storeData = true;

            //Set File Name to current time
            fileName = Calendar.getInstance().getTime().toString() + ".csv";

            //Check Permission
            while(!isStoragePermissionGranted());

            //Create Folder
            createFolder();

            mBinding.reccordText.setVisibility(View.VISIBLE);

        } else {
            storeData = false;

            mBinding.reccordText.setVisibility(View.GONE);
        }
    }

    public  boolean isStoragePermissionGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                Log.v(TAG,"Permission is granted");
                return true;
            } else {

                Log.v(TAG,"Permission is revoked");
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                return false;
            }
        }
        else { //permission is automatically granted on sdk<23 upon installation
            Log.v(TAG,"Permission is granted");
            return true;
        }
    }

    private void createFolder() {
        File folder = new File(path);
        boolean success = true;
        if (!folder.exists()) {
            success = folder.mkdirs();
        }
        if (success) {
            Log.d(TAG, "Successfully created new folder / already exists");
        } else {
            Log.d(TAG, "Failed to create new folder");
        }
    }

    private void writeCSV(String data[], int lenData) {
        Log.d(TAG, "Writing to CSV");
        String filePath = path + File.separator + fileName;
        File f = new File(filePath);
        CSVWriter writer;
        FileWriter mFileWriter;

        try {
            // File exist
            if(f.exists()&&!f.isDirectory())
            {
                mFileWriter = new FileWriter(filePath, true);
                writer = new CSVWriter(mFileWriter);
            }
            else
            {
                writer = new CSVWriter(new FileWriter(filePath));
            }

            writer.writeNext(data);

            writer.close();
            Log.d(TAG, "SUCCESSFUL WRITE");
        } catch (IOException e) {
            Log.d(TAG, e.toString());
        }
    }

    //////////////////////////////////////////User Interface//////////////////////////////////////////
    public void showOptions(View v) {
        PopupMenu popup = new PopupMenu(this, v);
        popup.setOnMenuItemClickListener(this);
        popup.inflate(R.menu.popup_menu);

        MenuItem recordMenuItem = popup.getMenu().findItem(R.id.record);
        if(storeData) {
            recordMenuItem.setTitle("Stop recording");
        }
        else {
            recordMenuItem.setTitle("Record");
        }


        popup.show();
    }


    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        switch(menuItem.getItemId()) {
            case R.id.simulate:
                if(simData)
                    simData = false;
                else
                    simData = true;
                return true;
            case R.id.record:
                toggleRecord(menuItem);
                return true;
            default:
                return false;
        }
    }

    //////////////////////////////SIMULATION///////////////////////////////////////////////////////
    private static float getRandom(int range, int offset, float k) {
        Random random = new Random();

        return (float)(random.nextInt(range) + offset) / k;
    }

}