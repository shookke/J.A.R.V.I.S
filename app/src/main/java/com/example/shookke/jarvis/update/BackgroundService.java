package com.example.shookke.jarvis.update;

import android.app.Service;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import com.example.shookke.jarvis.R;
import com.example.shookke.jarvis.ble.BleManager;
import com.example.shookke.jarvis.ble.BleUtils;
import com.thalmic.myo.AbstractDeviceListener;
import com.thalmic.myo.DeviceListener;
import com.thalmic.myo.Hub;
import com.thalmic.myo.Myo;
import com.thalmic.myo.Pose;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class BackgroundService extends Service implements BleManager.BleManagerListener {
    public static final String TAG = "BackgroundService";

    // Service Constants
    public static final String UUID_SERVICE = "6e400001-b5a3-f393-e0a9-e50e24dcca9e";
    public static final String UUID_RX = "6e400003-b5a3-f393-e0a9-e50e24dcca9e";
    public static final String UUID_TX = "6e400002-b5a3-f393-e0a9-e50e24dcca9e";
    public static final String UUID_DFU = "00001530-1212-EFDE-1523-785FEABCD123";
    public static final int kTxMaxCharacters = 20;


    protected BleManager mBleManager;
    protected BluetoothGattService mUartService;

    private Toast mToast;

    // Classes that inherit from AbstractDeviceListener can be used to receive events from Myo devices.
    // If you do not override an event, the default behavior is to do nothing.
    private DeviceListener mListener = new AbstractDeviceListener() {
        @Override
        public void onConnect(Myo myo, long timestamp) {
            showToast(getString(R.string.connected));
        }

        @Override
        public void onDisconnect(Myo myo, long timestamp) {
            showToast(getString(R.string.disconnected));
        }

        // onPose() is called whenever the Myo detects that the person wearing it has changed their pose, for example,
        // making a fist, or not making a fist anymore.
        @Override
        public void onPose(Myo myo, long timestamp, Pose pose) {
            // Show the name of the pose in a toast.
            //showToast(getString(R.string.pose, pose.toString()));

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

    // region Send Data to UART
    /*
    protected void sendData(String text) {
        final byte[] value = text.getBytes(Charset.forName("UTF-8"));
        sendData(value);
    }
    */

    protected void sendData(byte[] data) {
        if (mUartService != null) {
            // Split the value into chunks (UART service has a maximum number of characters that can be written )
            for (int i = 0; i < data.length; i += kTxMaxCharacters) {
                final byte[] chunk = Arrays.copyOfRange(data, i, Math.min(i + kTxMaxCharacters, data.length));
                mBleManager.writeService(mUartService, UUID_TX, chunk);
            }
        } else {
            Log.w(TAG, "Uart Service not discovered. Unable to send data");
        }
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

        // Send it
        Log.d(TAG, "Send to UART: " + BleUtils.bytesToHexWithSpaces(dataCrc));
        sendData(dataCrc);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // First, we initialize the Hub singleton with an application identifier.
        Hub hub = Hub.getInstance();
        if (!hub.init(this, getPackageName())) {
            showToast("Couldn't initialize Hub");
            stopSelf();
            return;
        }

        // Disable standard Myo locking policy. All poses will be delivered.
        hub.setLockingPolicy(Hub.LockingPolicy.NONE);

        // Next, register for DeviceListener callbacks.
        hub.addListener(mListener);

        // Finally, scan for Myo devices and connect to the first one found that is very near.
        hub.attachToAdjacentMyo();


        /*
        // Initializes Bluetooth adapter.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        //Get the device by its serial number
        BluetoothDevice bdDevice = mBluetoothAdapter.getRemoteDevice(getString(R.string.device));

        //for ble connection
        bdDevice.connectGatt(getApplicationContext(), true, mGattCallback);
        */

        mBleManager.connect(getString(R.string.device));

    }

    private void connect(String device) {
        boolean isConnecting = mBleManager.connect(this, device);
        System.out.println(isConnecting);
    }

    @Override
    public void onConnected() {

    }

    @Override
    public void onConnecting() {

    }

    @Override
    public void onDisconnected() {
        Log.d(TAG, "Disconnected. Back to previous activity");
        //finish();
    }

    @Override
    public void onServicesDiscovered() {
        mUartService = mBleManager.getGattService(UUID_SERVICE);

        mBleManager.enableNotification(mUartService, UUID_RX, true);
    }

    @Override
    public void onDataAvailable(BluetoothGattCharacteristic characteristic) {

    }

    @Override
    public void onDataAvailable(BluetoothGattDescriptor descriptor) {


    }

    @Override
    public void onReadRemoteRssi(int rssi) {

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // We don't want any callbacks when the Service is gone, so unregister the listener.
        Hub.getInstance().removeListener(mListener);

        Hub.getInstance().shutdown();
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



    private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            //Connection established
            if (status == BluetoothGatt.GATT_SUCCESS
                    && newState == BluetoothProfile.STATE_CONNECTED) {

                //Discover services
                gatt.discoverServices();

            } else if (status == BluetoothGatt.GATT_SUCCESS
                    && newState == BluetoothProfile.STATE_DISCONNECTED) {

                //Handle a disconnect event

            }
        }
    };







}
