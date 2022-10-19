package de.androidcrypto.bleblessedclientsample;

import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE;
import static com.welie.blessed.BluetoothBytesParser.FORMAT_SINT16;
import static com.welie.blessed.BluetoothBytesParser.FORMAT_UINT16;
import static com.welie.blessed.BluetoothBytesParser.FORMAT_UINT8;
import static com.welie.blessed.BluetoothBytesParser.bytes2String;
import static java.lang.Math.abs;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.welie.blessed.BluetoothBytesParser;
import com.welie.blessed.BluetoothCentralManager;
import com.welie.blessed.BluetoothCentralManagerCallback;
import com.welie.blessed.BluetoothPeripheral;
import com.welie.blessed.BluetoothPeripheralCallback;
import com.welie.blessed.BondState;
import com.welie.blessed.ConnectionPriority;
import com.welie.blessed.GattStatus;
import com.welie.blessed.HciStatus;
import com.welie.blessed.ScanFailure;
import com.welie.blessed.WriteType;

import org.jetbrains.annotations.NotNull;

import java.nio.ByteOrder;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;

import timber.log.Timber;

class BluetoothHandler {

    // Intent constants
    public static final String CONNECTED_DEVICE_ACTION = "androidcrypto.connected.device";
    public static final String CONNECTED_DEVICE_EXTRA = "androidcrypto.connected.device.extra";
    public static final String MEASUREMENT_HEART_BEAT_RATE = "androidcrypto.measurement.heartbeatrate";
    public static final String MEASUREMENT_HEART_BEAT_RATE_EXTRA = "androidcrypto.measurement.heartbeatrate.extra";
    public static final String MEASUREMENT_EXTRA_PERIPHERAL = "androidcrypto.measurement.peripheral";
    public static final String MEASUREMENT_CURRENT_TIME = "androidcrypto.measurement.currenttime";
    public static final String MEASUREMENT_CURRENT_TIME_EXTRA = "androidcrypto.measurement.currenttime.extra";
    public static final String MEASUREMENT_MANUFACTURER_NAME = "androidcrypto.measurement.manufacturername";
    public static final String MEASUREMENT_MANUFACTURER_NAME_EXTRA = "androidcrypto.measurement.manufacturername.extra";

    // UUIDs for the Heart Rate service (HRS)
    private static final UUID HEART_BEAT_SERVICE_UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb");
    private static final UUID HEART_BEAT_RATE_MEASUREMENT_CHARACTERISTIC_UUID = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb");

    // UUIDs for the Device Information service (DIS)
    private static final UUID DEVICE_INFORMATION_SERVICE_UUID = UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb");
    private static final UUID MANUFACTURER_NAME_CHARACTERISTIC_UUID = UUID.fromString("00002A29-0000-1000-8000-00805f9b34fb");
    private static final UUID MODEL_NUMBER_CHARACTERISTIC_UUID = UUID.fromString("00002A24-0000-1000-8000-00805f9b34fb");

    // UUIDs for the Current Time service (CTS)
    private static final UUID CURRENT_TIME_SERVICE_UUID = UUID.fromString("00001805-0000-1000-8000-00805f9b34fb");
    private static final UUID CURRENT_TIME_CHARACTERISTIC_UUID = UUID.fromString("00002A2B-0000-1000-8000-00805f9b34fb");

    // UUIDs for the Battery Service (BAS)
    private static final UUID BATTERY_SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb");
    private static final UUID BATTERY_LEVEL_CHARACTERISTIC_UUID = UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb");

    // Local variables
    public BluetoothCentralManager central;
    private static BluetoothHandler instance = null;
    private final Context context;
    String mMacAddress;
    private final Handler handler = new Handler();
    private int currentTimeCounter = 0;

    private void printToast(String message) {
        Toast.makeText(context,
                        message,
                        Toast.LENGTH_SHORT)
                .show();
    }

    public void readCurrentTime(String macAddress) {
        // get the peripheral from mainActivity and call to read the data, in the callback they were send back to main
        BluetoothPeripheral bluetoothPeripheral = central.getPeripheral(macAddress);
        bluetoothPeripheral.readCharacteristic(CURRENT_TIME_SERVICE_UUID, CURRENT_TIME_CHARACTERISTIC_UUID);
    }

    public void setCurrentTimeNotification(String macAddress, boolean status) {
        // get the peripheral from mainActivity and call to read the data, in the callback they were send back to main
        BluetoothPeripheral bluetoothPeripheral = central.getPeripheral(macAddress);
        bluetoothPeripheral.setNotify(CURRENT_TIME_SERVICE_UUID, CURRENT_TIME_CHARACTERISTIC_UUID, status);
    }

    // Callback for peripherals
    private final BluetoothPeripheralCallback peripheralCallback = new BluetoothPeripheralCallback() {
        @Override
        public void onServicesDiscovered(@NotNull BluetoothPeripheral peripheral) {
            // Request a higher MTU, iOS always asks for 185
            peripheral.requestMtu(185);

            // Request a new connection priority
            peripheral.requestConnectionPriority(ConnectionPriority.HIGH);

            // Read manufacturer and model number from the Device Information Service
            peripheral.readCharacteristic(DEVICE_INFORMATION_SERVICE_UUID, MANUFACTURER_NAME_CHARACTERISTIC_UUID);
            peripheral.readCharacteristic(DEVICE_INFORMATION_SERVICE_UUID, MODEL_NUMBER_CHARACTERISTIC_UUID);

            // Turn on notifications for Current Time Service and write it if possible
            BluetoothGattCharacteristic currentTimeCharacteristic = peripheral.getCharacteristic(CURRENT_TIME_SERVICE_UUID, CURRENT_TIME_CHARACTERISTIC_UUID);
            if (currentTimeCharacteristic != null) {

                peripheral.setNotify(currentTimeCharacteristic, true);

                // If it has the write property we write the current time
                if ((currentTimeCharacteristic.getProperties() & PROPERTY_WRITE) > 0) {
                    // Write the current time unless it is an Omron device
                    /*
                    if (!isOmronBPM(peripheral.getName())) {
                        BluetoothBytesParser parser = new BluetoothBytesParser();
                        parser.setCurrentTime(Calendar.getInstance());
                        peripheral.writeCharacteristic(currentTimeCharacteristic, parser.getValue(), WriteType.WITH_RESPONSE);
                    }*/
                }
            }

            // Try to turn on notifications for other characteristics
            peripheral.readCharacteristic(BATTERY_SERVICE_UUID, BATTERY_LEVEL_CHARACTERISTIC_UUID);
            peripheral.setNotify(HEART_BEAT_SERVICE_UUID, HEART_BEAT_RATE_MEASUREMENT_CHARACTERISTIC_UUID, true);
        }

        @Override
        public void onNotificationStateUpdate(@NotNull BluetoothPeripheral peripheral, @NotNull BluetoothGattCharacteristic characteristic, @NotNull GattStatus status) {
            if (status == GattStatus.SUCCESS) {
                final boolean isNotifying = peripheral.isNotifying(characteristic);
                Timber.i("SUCCESS: Notify set to '%s' for %s", isNotifying, characteristic.getUuid());
            } else {
                Timber.e("ERROR: Changing notification state failed for %s (%s)", characteristic.getUuid(), status);
            }
        }

        @Override
        public void onCharacteristicWrite(@NotNull BluetoothPeripheral peripheral, @NotNull byte[] value, @NotNull BluetoothGattCharacteristic characteristic, @NotNull GattStatus status) {
            if (status == GattStatus.SUCCESS) {
                Timber.i("SUCCESS: Writing <%s> to <%s>", bytes2String(value), characteristic.getUuid());
            } else {
                Timber.i("ERROR: Failed writing <%s> to <%s> (%s)", bytes2String(value), characteristic.getUuid(), status);
            }
        }

        @Override
        public void onDescriptorRead(@NonNull BluetoothPeripheral peripheral, @NonNull byte[] value, @NonNull BluetoothGattDescriptor descriptor, @NonNull GattStatus status) {
            super.onDescriptorRead(peripheral, value, descriptor, status);
            if (status != GattStatus.SUCCESS) return;
        }

        @Override
        public void onCharacteristicUpdate(@NotNull BluetoothPeripheral peripheral, @NotNull byte[] value, @NotNull BluetoothGattCharacteristic characteristic, @NotNull GattStatus status) {
            if (status != GattStatus.SUCCESS) return;

            UUID characteristicUUID = characteristic.getUuid();
            BluetoothBytesParser parser = new BluetoothBytesParser(value);

            if (characteristicUUID.equals(HEART_BEAT_RATE_MEASUREMENT_CHARACTERISTIC_UUID)) {
                HeartRateMeasurement measurement = new HeartRateMeasurement(value);
                Intent intent = new Intent(MEASUREMENT_HEART_BEAT_RATE);
                intent.putExtra(MEASUREMENT_HEART_BEAT_RATE_EXTRA, measurement);
                sendMeasurement(intent, peripheral);
                Timber.d("%s", measurement);
            /*
            } else if (characteristicUUID.equals(UUID.fromString("00001805-0000-1000-8000-00805f9b34fb"))) {

                Date currentTime = parser.getDateTime();
                System.out.println("*** received time: " + currentTime);
                Timber.i("Received device time: %s", currentTime);
            */
            } else if (characteristicUUID.equals(CURRENT_TIME_CHARACTERISTIC_UUID)) {
                Date currentTime = parser.getDateTime();
                //Log.i("BH", "currentTimne: " + currentTime);
                Timber.i("Received device time: %s", currentTime);
                Intent intent = new Intent(MEASUREMENT_CURRENT_TIME);
                intent.putExtra(MEASUREMENT_CURRENT_TIME_EXTRA, currentTime.toString());
                sendMeasurement(intent, peripheral);
                Timber.d("%s", currentTime);

            } else if (characteristicUUID.equals(BATTERY_LEVEL_CHARACTERISTIC_UUID)) {
                int batteryLevel = parser.getIntValue(FORMAT_UINT8);
                Timber.i("Received battery level %d%%", batteryLevel);
            } else if (characteristicUUID.equals(MANUFACTURER_NAME_CHARACTERISTIC_UUID)) {
                String manufacturer = parser.getStringValue(0);
                Timber.i("Received manufacturer: %s", manufacturer);
                Intent intent = new Intent(MEASUREMENT_MANUFACTURER_NAME);
                intent.putExtra(MEASUREMENT_MANUFACTURER_NAME_EXTRA, manufacturer);
                sendMeasurement(intent, peripheral);
            } else if (characteristicUUID.equals(MODEL_NUMBER_CHARACTERISTIC_UUID)) {
                String modelNumber = parser.getStringValue(0);
                Timber.i("Received modelnumber: %s", modelNumber);
            }
        }

        @Override
        public void onMtuChanged(@NotNull BluetoothPeripheral peripheral, int mtu, @NotNull GattStatus status) {
            Timber.i("new MTU set: %d", mtu);
        }

        private void sendMeasurement(@NotNull Intent intent, @NotNull BluetoothPeripheral peripheral ) {
            intent.putExtra(MEASUREMENT_EXTRA_PERIPHERAL, peripheral.getAddress());
            context.sendBroadcast(intent);
        }
    };

    // Callback for central
    private final BluetoothCentralManagerCallback bluetoothCentralManagerCallback = new BluetoothCentralManagerCallback() {

        @Override
        public void onConnectedPeripheral(@NotNull BluetoothPeripheral peripheral) {
            Timber.i("connected to '%s'", peripheral.getName());
            String data = "connected to:" + peripheral.getAddress() + " (" + peripheral.getName() + ")";
            Intent intent = new Intent(CONNECTED_DEVICE_ACTION);
            intent.putExtra(CONNECTED_DEVICE_EXTRA, data);
            context.sendBroadcast(intent);
        }

        @Override
        public void onConnectionFailed(@NotNull BluetoothPeripheral peripheral, final @NotNull HciStatus status) {
            Timber.e("connection '%s' failed with status %s", peripheral.getName(), status);
            String data = "connection failed with:" + peripheral.getAddress() + " (" + peripheral.getName() + ")";
            Intent intent = new Intent(CONNECTED_DEVICE_ACTION);
            intent.putExtra(CONNECTED_DEVICE_EXTRA, data);
            context.sendBroadcast(intent);
        }

        @Override
        public void onDisconnectedPeripheral(@NotNull final BluetoothPeripheral peripheral, final @NotNull HciStatus status) {
            Timber.i("disconnected '%s' with status %s", peripheral.getName(), status);
            String data = "disconnected from:" + peripheral.getAddress() + " (" + peripheral.getName() + ")";
            Intent intent = new Intent(CONNECTED_DEVICE_ACTION);
            intent.putExtra(CONNECTED_DEVICE_EXTRA, data);
            context.sendBroadcast(intent);

            // Reconnect to this device when it becomes available again
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    central.autoConnectPeripheral(peripheral, peripheralCallback);
                }
            }, 5000);
        }

        @Override
        public void onDiscoveredPeripheral(@NotNull BluetoothPeripheral peripheral, @NotNull ScanResult scanResult) {
            Timber.i("Found peripheral '%s'", peripheral.getName());
            //printToast("found peripheral: " + peripheral.getName());
            central.stopScan();

            if (peripheral.getName().contains("Contour") && peripheral.getBondState() == BondState.NONE) {
                // Create a bond immediately to avoid double pairing popups
                central.createBond(peripheral, peripheralCallback);
            } else {
                central.connectPeripheral(peripheral, peripheralCallback);
            }
        }

        @Override
        public void onBluetoothAdapterStateChanged(int state) {
            Timber.i("bluetooth adapter changed state to %d", state);
            if (state == BluetoothAdapter.STATE_ON) {
                // Bluetooth is on now, start scanning again
                // Scan for peripherals with a certain service UUIDs
                //central.startPairingPopupHack();
                //startScan();
                startScan(null);
            }
        }

        @Override
        public void onScanFailed(@NotNull ScanFailure scanFailure) {
            Timber.i("scanning failed with error %s", scanFailure);
            //printToast("scanning failed with error " + scanFailure);
        }
    };

    public static synchronized BluetoothHandler getInstance2(Context context) {
        if (instance == null) {
            instance = new BluetoothHandler(context.getApplicationContext(), null);
        }
        return instance;
    }

    public static synchronized BluetoothHandler getInstance(Context context, String macAddress) {
        if (instance == null) {
            instance = new BluetoothHandler(context.getApplicationContext(), macAddress);
        }
        return instance;
    }

    private BluetoothHandler(Context context, String macAddress) {

        this.context = context;
        this.mMacAddress = macAddress; // may be null !

        printToast("BH constructor MAC: " + macAddress);


        // reset the complete handler if macAddress is provided
        if ((macAddress != null) & (!macAddress.equals(""))) {

        }

        // Plant a tree
        //Timber.plant(new Timber.DebugTree());

        // Create BluetoothCentral
        central = new BluetoothCentralManager(context, bluetoothCentralManagerCallback, new Handler());

        // Scan for peripherals with a certain service UUIDs
        //central.startPairingPopupHack();
        //printToast("now startScan with mMacAddress: " + mMacAddress);
        startScan(mMacAddress);
    }

    private void startScan(String macAddress) {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                //central.scanForPeripheralsWithServices(new UUID[]{BLP_SERVICE_UUID, HTS_SERVICE_UUID, HRS_SERVICE_UUID, PLX_SERVICE_UUID, WSS_SERVICE_UUID, GLUCOSE_SERVICE_UUID});
                //central.scanForPeripherals();
                printToast("BH handler.postDelayed macAddress: " + macAddress);
                if (macAddress == null) {
                    printToast("BH macAddress == null");
                    central.scanForPeripheralsWithServices(new UUID[]{HEART_BEAT_SERVICE_UUID, BATTERY_SERVICE_UUID});
                } else {
                    //printToast("BH macAddress NOT null");
                    BluetoothPeripheral peripheral = central.getPeripheral(macAddress);
                    printToast("found peripheral: " + peripheral.getName());
                    central.stopScan();

                    if (peripheral.getName().contains("Contour") && peripheral.getBondState() == BondState.NONE) {
                        // Create a bond immediately to avoid double pairing popups
                        central.createBond(peripheral, peripheralCallback);
                    } else {
                        printToast("BH central.connectPeripheral");
                        central.connectPeripheral(peripheral, peripheralCallback);
                    }
                }
            }
        },1000);
    }
}
