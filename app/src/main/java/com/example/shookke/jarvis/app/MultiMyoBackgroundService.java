package com.example.shookke.jarvis.app;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import com.example.shookke.jarvis.R;
import com.example.shookke.jarvis.ble.BluetoothLeUart;
import com.thalmic.myo.AbstractDeviceListener;
import com.thalmic.myo.DeviceListener;
import com.thalmic.myo.Hub;
import com.thalmic.myo.Myo;
import com.thalmic.myo.Pose;

import java.nio.ByteBuffer;
import java.util.ArrayList;

//import com.example.shookke.jarvis.ble.BluetoothLeUart;

public class MultiMyoBackgroundService extends Service implements BluetoothLeUart.Callback{
    public static final String TAG = "MultiMyoBackground";
    private BluetoothLeUart uart;


    private Toast mToast;

    public MultiMyoBackgroundService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        return null;
    }
    // We store each Myo object that we attach to in this list, so that we can keep track of the order we've seen
    // each Myo and give it a unique short identifier (see onAttach() and identifyMyo() below).
    private ArrayList<Myo> mKnownMyos = new ArrayList<Myo>();
    private MyoAdapter mAdapter;
    private DeviceListener mListener = new AbstractDeviceListener() {
        // Every time the SDK successfully attaches to a Myo armband, this function will be called.
        //
        // You can rely on the following rules:
        //  - onAttach() will only be called once for each Myo device
        //  - no other events will occur involving a given Myo device before onAttach() is called with it
        //
        // If you need to do some kind of per-Myo preparation before handling events, you can safely do it in onAttach().
        @Override
        public void onAttach(Myo myo, long timestamp) {
            // The object for a Myo is unique - in other words, it's safe to compare two Myo references to
            // see if they're referring to the same Myo.
            // Add the Myo object to our list of known Myo devices. This list is used to implement identifyMyo() below so
            // that we can give each Myo a nice short identifier.
            mKnownMyos.add(myo);
            // Now that we've added it to our list, get our short ID for it and print it out.
            Log.i(TAG, "Attached to " + myo.getMacAddress() + ", now known as Myo " + identifyMyo(myo) + ".");
        }
        @Override
        public void onConnect(Myo myo, long timestamp) {
            mAdapter.setMessage(myo, "Myo " + identifyMyo(myo) + " has connected.");
        }
        @Override
        public void onDisconnect(Myo myo, long timestamp) {
            mAdapter.setMessage(myo, "Myo " + identifyMyo(myo) + " has disconnected.");
        }
        @Override
        public void onPose(Myo myo, long timestamp, Pose pose) {

            if (pose != null) {
                switch (pose) {
                    case FIST:
                        sendPoseEvent(1);
                        break;
                    case WAVE_OUT:
                        sendPoseEvent(2);
                        break;
                    case WAVE_IN:
                        sendPoseEvent(3);
                        break;
                    case FINGERS_SPREAD:
                        sendPoseEvent(4);
                        break;
                    case DOUBLE_TAP:
                        sendPoseEvent(5);
                        break;
                    case REST:
                        sendPoseEvent(6);
                        break;
                    case UNKNOWN:
                        break;
                }
            }

        }

        private void sendPoseEvent(int tag) {
            String data = "!B" + tag;
            ByteBuffer buffer = ByteBuffer.allocate(data.length()).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            buffer.put(data.getBytes());
            sendDataWithCRC(buffer.array());
        }

    };
    @Override
    public void onCreate() {
        super.onCreate();
        intUart();

        intMyo();

    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        // We don't want any callbacks when the Activity is gone, so unregister the listener.
        Hub.getInstance().removeListener(mListener);
        // Shutdown the Hub. This will disconnect any Myo devices that are connected.
        Hub.getInstance().shutdown();
        //mBluetoothGatt.disconnect();

        uart.unregisterCallback(this);
        uart.disconnect();

    }
    // This is a utility function implemented for this sample that maps a Myo to a unique ID starting at 1.
    // It does so by looking for the Myo object in mKnownMyos, which onAttach() adds each Myo into as it is attached.
    private int identifyMyo(Myo myo) {
        return mKnownMyos.indexOf(myo) + 1;
    }



    @Override
    public void onConnected(BluetoothLeUart uart) {
        System.out.println("connection successful!");

    }

    @Override
    public void onConnectFailed(BluetoothLeUart uart) {
        System.out.println("connection failed");
    }

    @Override
    public void onDisconnected(BluetoothLeUart uart) {
        System.out.println("connection disconnected");
    }

    @Override
    public void onReceive(BluetoothLeUart uart, BluetoothGattCharacteristic rx) {

    }

    @Override
    public void onDeviceFound(BluetoothDevice device) {

    }

    @Override
    public void onDeviceInfoAvailable() {

    }


    private class MyoAdapter extends ArrayAdapter<String> {
        public MyoAdapter(Context context, int count) {
            super(context, android.R.layout.simple_list_item_1);
            // Initialize adapter with items for each expected Myo.
            for (int i = 0; i < count; i++) {
                add(getString(R.string.waiting_message));
            }
        }
        public void setMessage(Myo myo, String message) {
            // identifyMyo returns IDs starting at 1, but the adapter indices start at 0.
            int index = identifyMyo(myo) - 1;
            // Replace the message.
            //remove(getItem(index));
            //message = "Myo " + index + message;
            showToast(message);
        }
    }

    private void showToast(String text) {
        Log.w(TAG, text);
        if (mToast == null) {
            mToast = Toast.makeText(this, text, Toast.LENGTH_SHORT);
        } else {
            mToast.setText(text);
        }
        mToast.show();
    }


    // Send data to UART and add a byte with a custom CRC
    protected void sendDataWithCRC(byte[] data) {

        // Calculate checksum
        byte checksum = 0;
        for (int i = 0; i < data.length; i++) {
            checksum += data[i];
        }
        checksum = (byte) (~checksum);       // Invert

        // Add crc to data
        byte dataCrc[] = new byte[data.length + 1];
        System.arraycopy(data, 0, dataCrc, 0, data.length);
        dataCrc[data.length] = checksum;
        String str = new String(dataCrc);
        System.out.println(str);
        // Send it
        //Log.d(TAG, "Send to UART: " + BleUtils.bytesToHexWithSpaces(dataCrc));
        uart.send(dataCrc);
    }

    protected void intMyo() {
        Hub hub = Hub.getInstance();
        if (!hub.init(this)) {
            // We can't do anything with the Myo device if the Hub can't be initialized, so exit.
            Toast.makeText(this, "Couldn't initialize Hub", Toast.LENGTH_SHORT).show();
        }
        // Disable standard Myo locking policy. All poses will be delivered.
        hub.setLockingPolicy(Hub.LockingPolicy.NONE);
        final int attachingCount = 2;
        // Set the maximum number of simultaneously attached Myos to 2.
        hub.setMyoAttachAllowance(attachingCount);
        Log.i(TAG, "Attaching to " + attachingCount + " Myo armbands.");
        // attachToAdjacentMyos() attaches to Myo devices that are physically very near to the Bluetooth radio
        // until it has attached to the provided count.
        // DeviceListeners attached to the hub will receive onAttach() events once attaching has completed.
        hub.attachToAdjacentMyos(attachingCount);
        // Next, register for DeviceListener callbacks.
        hub.addListener(mListener);
        // Attach an adapter to the ListView for showing the state of each Myo.
        mAdapter = new MyoAdapter(this, attachingCount);
    }

    protected void intUart() {
        uart = new BluetoothLeUart(this);
        uart.registerCallback(this);
        uart.connectFirstAvailable();

        // First, we initialize the Hub singleton.

        if(uart.isConnected()){
            System.out.println("connection established");
        }
    }


}

