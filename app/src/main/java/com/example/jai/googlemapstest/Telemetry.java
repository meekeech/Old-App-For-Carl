package com.example.jai.googlemapstest;

/**
 * Created by Jai on 2/21/2016.
 */

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbManager;
import android.os.Environment;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.widget.Toast;

import com.ftdi.j2xx.D2xxManager;
import com.ftdi.j2xx.FT_Device;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class Telemetry extends FragmentActivity {

    FT_Device ftDev;
    public static D2xxManager ftD2xx = null;

    // Buffer Read
    public int READ_BUFFER_SIZE = 56;
    public int port = 0;
    public byte[] USBBuffer;
    public final byte XON = 0x11;    /* Resume transmission */
    public final byte XOFF = 0x13;    /* Pause transmission */
    public static final int READBUF_SIZE  = 256;
    public int mReadSize = 0;
    public boolean mThreadIsStopped = true;

    // Thread for handling data
    Handler mHandler = new Handler();

    protected double heightoffset = 0;

    // Serial communication (usb radio)
    private final int BAUD = 57600;
    private final byte[] drop = {(byte) '0'};
    private final byte[] load = {(byte) '1'};
    private final byte[] request = {(byte) 'r'};

    // Initialize buffers for incoming transmission
    private byte[] rbuf  = new byte[READBUF_SIZE];
    private char[] rchar = new char[READBUF_SIZE];

    // Read variables
    public double planeLong;
    public double planeLat;
    public double planeAlt;
    public double planeSpeed;
    public double planeHeading;
    public double planeYaw;
    public double planePitch;
    public double planeRoll;
    public boolean payload;
    public boolean dropLoadToggle;

    Context global_context;
    protected String TAG = "TAG";
    protected IntentFilter filter;
    protected BroadcastReceiver mUsbReceiver;
    protected boolean telemetryOpen = false;

    protected BufferedWriter bfWriter;
    boolean firstline;

    long startTime, currentTime;

    public Telemetry(Context context) {
        this.planeLong = 0;
        this.planeLat = 0;
        this.planeAlt = 0;
        this.planeSpeed = 0;
        this.planeHeading = 0;
        this.planeYaw = 0;
        this.planePitch = 0;
        this.planeRoll = 0;
        this.payload = true;
        this.dropLoadToggle = false;
        this.global_context = context;
        this.firstline = true;
        startTime = System.currentTimeMillis();
        currentTime = 0;

        mUsbReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                    // never come here(when attached, go to onNewIntent)
                    openDevice();
                } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                    closeDevice();
                }
            }
        };

        Log.v(TAG, global_context.toString());

        // Initialize USB socket
        try {
            ftD2xx = D2xxManager.getInstance(global_context);
        }
        catch (D2xxManager.D2xxException e) {
            Log.e("FTDI_HT", "getInstance fail!!");
        }

        // Establish USB buffer
        USBBuffer = new byte[READ_BUFFER_SIZE];

        // Stuff for the USB socket
        filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);

        fileShit();

        global_context.registerReceiver(mUsbReceiver, filter);
        Log.v("TELE", "Telemetry established" );
    }

    /**
     * Destroy all humans. Close the activity and the USB socket.
     */
    protected void fileShit() {
        File fileDir = new File(Environment.getExternalStorageDirectory().getAbsolutePath());
        File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+File.separator+"FileLog.csv");

        if(!file.exists()){
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if(file.exists()){
            try {
                FileWriter fileWriter = new FileWriter(file);
                bfWriter = new BufferedWriter(fileWriter);
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "Telemetry Destroyed!");
        mThreadIsStopped = true;
        global_context.unregisterReceiver(mUsbReceiver);
    }

    public void closeDevice() {
        mThreadIsStopped = true;
        telemetryOpen = false;
        //updateView(false);
        if(ftDev != null) {
            ftDev.close();
            try {
                bfWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        openDevice();
    };

    void setConfig() {
        ftDev.setBitMode((byte) 0, D2xxManager.FT_BITMODE_RESET); // reset to UART mode for 232 devices
        ftDev.setBaudRate(BAUD);
        ftDev.setDataCharacteristics(D2xxManager.FT_DATA_BITS_8, D2xxManager.FT_STOP_BITS_1,
                D2xxManager.FT_PARITY_NONE);
        ftDev.setFlowControl(D2xxManager.FT_FLOW_RTS_CTS, XON, XOFF);
    }

    protected void openDevice() {
        if(ftDev != null) {
            if(ftDev.isOpen()) {
                if(mThreadIsStopped) {
                    //updateView(true);
                    setConfig();
                    ftDev.purge((byte) (D2xxManager.FT_PURGE_TX | D2xxManager.FT_PURGE_RX));
                    ftDev.restartInTask();
                    new Thread(mLoop).start();

                }
                return;
            }
        }

        int devCount = 0;
        devCount = ftD2xx.createDeviceInfoList(global_context);

        Log.d(TAG, "Device number : " + Integer.toString(devCount));

        D2xxManager.FtDeviceInfoListNode[] deviceList = new D2xxManager.FtDeviceInfoListNode[devCount];
        ftD2xx.getDeviceInfoList(devCount, deviceList);

        if(devCount <= 0) {
            return;
        }

        if(ftDev == null) {
            ftDev = ftD2xx.openByIndex(global_context, 0);
        } else {
            synchronized (ftDev) {
                ftDev = ftD2xx.openByIndex(global_context, 0);
            }
        }

        if(ftDev.isOpen()) {
            if(mThreadIsStopped) {
                setConfig();
                ftDev.purge((byte) (D2xxManager.FT_PURGE_TX | D2xxManager.FT_PURGE_RX));
                ftDev.restartInTask();
                new Thread(mLoop).start();
            }
        }
    }

    private Runnable mLoop = new Runnable() {
        @Override
        public void run() {

            int i;
            int readSize;
            mThreadIsStopped = false;

            while(true) {
                if(mThreadIsStopped) {
                    break;
                }

                synchronized (ftDev) {

                    // Check drop condition
                    if(dropLoadToggle) {
                        payloadToggle(payload);
                        dropLoadToggle = false;
                    }

                    // Request telemetry from plane
                    if(!ftDev.isOpen()) {
                        Log.e(TAG, "onClickWrite : Device is not open");
                        return;
                    }

                    // Writing r to the buffer
                    if (ftD2xx.createDeviceInfoList(global_context) >= 1) {
                        ftDev.setLatencyTimer((byte) 16);
                        ftDev.write(request, request.length);
                    } else {
                        //Toast.makeText(global_context, "USB Lost", Toast.LENGTH_SHORT).show();
                        closeDevice();
                    }

                    try{
                        Thread.sleep(500);
                    }catch(Exception e)
                    {
                        Log.e("TEL", "Error trying to sleep");
                    }

                    // Size of buffer
                    readSize = ftDev.getQueueStatus();

                    // If elements are present in the buffer
                    if(readSize > 40) {
                        mReadSize = readSize;

                        if(mReadSize > READBUF_SIZE) {
                            mReadSize = READBUF_SIZE;
                        }

                        ftDev.read(rbuf, mReadSize);

                        for(i=0; i<mReadSize; i++) {
                            rchar[i] = (char)rbuf[i];
                        }

                        // Parse incoming string and set telemetry coordinates
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {

                                // Copy in buffer to string
                                String telemetryBuffer = String.copyValueOf(rchar, 0, mReadSize);

                                // Remove beginning/end carriages '$' and '#'
                                String filteredBuffer = telemetryBuffer.substring(telemetryBuffer.lastIndexOf("$") + 1);
                                String[] splitBuffer  = filteredBuffer.split("#");
                                filteredBuffer = splitBuffer[0];
                                //Toast.makeText(global_context,filteredBuffer, Toast.LENGTH_SHORT).show();

                                // Parse incoming data by ',' delimiter and update
                                String telemetry[] = filteredBuffer.split("[,]");
                                if (telemetry.length >= 8) {updateTelemetry(telemetry);}

                                setTimerToDrop();
                            }
                        });

                    } // end of if(readSize>0)
                } // end of synchronized
            }
        }
    };

    /**
     * Sets up the USB socket if not already opened. Checks for connectivity
     * and then opens 'port 0' since there is only one USB port on the
     * device. This step is critical for communication with the plane.
     */
    protected void setUpUsbIfNeeded() {
        // Check if already connected
        if (telemetryOpen) {
            String msg = "Port("+port+") is already opened.";
            Toast.makeText(global_context, msg, Toast.LENGTH_SHORT).show();
            return;
        }

        // Check whether there is a device plugged in
        if (ftD2xx.createDeviceInfoList(global_context) < 1) {
            String msg = "Connect the USB radio";
            Toast.makeText(global_context, msg, Toast.LENGTH_SHORT).show();
            return;
        }

        // Open the device on port 0 (USB radio by default)
        ftDev = ftD2xx.openByIndex(global_context, 0);

        // Check for successful connection
        if (ftDev == null) {
            String msg = "Connect the USB radio";
            Toast.makeText(global_context, msg, Toast.LENGTH_SHORT).show();
            return;
        }

        setConfig();
        Toast.makeText(global_context, "Connected", Toast.LENGTH_SHORT).show();
        openDevice();
        telemetryOpen = true;
    }

    private void updateTelemetry(String[] telemetry)
    {
        planeSpeed =  Double.valueOf(telemetry[0]);             // airspeed
        planeHeading = Double.valueOf(telemetry[1]);            // heading
        planeLong = Double.valueOf(telemetry[3]);               // long
        planeLat = Double.valueOf(telemetry[4]);                // lat
        planeAlt = Double.valueOf(telemetry[2]);                // altitude
        planePitch = Double.valueOf(telemetry[6]);              // pitch
        planeRoll = Double.valueOf(telemetry[7]);               // roll
        currentTime = System.currentTimeMillis() - startTime;   // time

        String data = Long.toString(currentTime) + " " + Double.toString(planeSpeed) + " " + Double.toString(planeHeading) + " " + Double.toString(planeLong) + " " + Double.toString(planeLat) + " " + Double.toString(planeAlt) + " " + Double.toString(planePitch) + " " + Double.toString(planeRoll) + "\n";
        if (firstline) {
            try {
                bfWriter.write("New Flight");
                firstline = false;
            } catch (IOException e){
                e.printStackTrace();
            }
        }

        try {
            bfWriter.write(data);
        } catch (IOException e){
            e.printStackTrace();
        }

        Log.v(TAG, "Plane Latitude  : " + Double.toString(planeLat));
        Log.v(TAG, "Plane Longitude : "+ Double.toString(planeLong));

        return;
    }

    private void setTimerToDrop(){
        return;
    }

    public void payloadToggle(boolean dropLoad)
    {

        if(ftDev == null){
            return;
        }

        if (!ftDev.isOpen()) {
            String msg = "Device not open!";
            Toast.makeText(global_context, msg, Toast.LENGTH_SHORT).show();
            return;
        }


        synchronized (ftDev) {
            if(!ftDev.isOpen()) {
                Log.e(TAG, "onClickWrite : Device is not open");
                return;
            }

            ftDev.setLatencyTimer((byte)16);

            //payload toggle command logic
            if (dropLoad) {
                ftDev.write(drop, drop.length);
                Log.v(TAG,"DROPPED");
                payload = false;
            }
            else {
                ftDev.write(load, load.length);
                Log.v(TAG, "LOADED");
                payload = true;
            }
        }
    }
    ///////////////////////////////////////////////////////////////////////
}
