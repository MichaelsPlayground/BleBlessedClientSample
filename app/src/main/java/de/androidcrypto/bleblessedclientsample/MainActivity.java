package de.androidcrypto.bleblessedclientsample;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import timber.log.Timber;

public class MainActivity extends AppCompatActivity {

    Button scanForDevices, getCurrentTime, setCurrentTimeNotification, unsetCurrentTimeNotification;
    String heartRateMeasurementString;
    com.google.android.material.textfield.TextInputEditText connectedDevice;
    com.google.android.material.textfield.TextInputEditText heartRateMeasurement;
    com.google.android.material.textfield.TextInputEditText currentTime;
    com.google.android.material.textfield.TextInputEditText manufacturerName;
    com.google.android.material.textfield.TextInputEditText requestedModelNumber; // new for write
    Button getModelNumber, setModelNumber; // new for write and read

    BluetoothHandler bluetoothHandler;
    String connectedDeviceFromBluetoothHandler;

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int ACCESS_LOCATION_REQUEST = 2;

    /**
     * Return Intent extra
     */
    public static String EXTRA_DEVICE_ADDRESS = "device_address";
    String macAddressFromScan = ""; // will get filled by Intent from DeviceScanActivity

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        connectedDevice = findViewById(R.id.etMainConnectedDevice);
        heartRateMeasurement = findViewById(R.id.etMainHeartRateMeasurement);
        currentTime = findViewById(R.id.etMainCurrentTime);
        manufacturerName = findViewById(R.id.etMainManufacturerNameMeasurement);
        requestedModelNumber = findViewById(R.id.etMainModelNumberMeasurement);

        // get the connectedDevice in case of "back key" pushed
        if (connectedDeviceFromBluetoothHandler != null) {
            connectedDevice.setText(connectedDeviceFromBluetoothHandler);
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        registerReceiver(connectedDeviceDataReceiver, new IntentFilter((BluetoothHandler.CONNECTED_DEVICE_ACTION)));
        registerReceiver(locationServiceStateReceiver, new IntentFilter((LocationManager.MODE_CHANGED_ACTION)));
        registerReceiver(heartRateDataReceiver, new IntentFilter( BluetoothHandler.MEASUREMENT_HEART_BEAT_RATE ));
        registerReceiver(currentTimeDataReceiver, new IntentFilter(BluetoothHandler.MEASUREMENT_CURRENT_TIME));
        registerReceiver(manufacturerNameDataReceiver, new IntentFilter(BluetoothHandler.MEASUREMENT_MANUFACTURER_NAME));
        registerReceiver(modelNumberDataReceiver, new IntentFilter(BluetoothHandler.MEASUREMENT_MODEL_NUMBER));

        scanForDevices = findViewById(R.id.btnMainScan);
        scanForDevices.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, DeviceScanActivityOwn.class);
                startActivity(intent);
            }
        });

        // receive the address from DeviceListOwnActivity, if we receive data run the connection part
        Intent incommingIntent = getIntent();
        Bundle extras = incommingIntent.getExtras();
        if (extras != null) {
            macAddressFromScan = extras.getString(EXTRA_DEVICE_ADDRESS); // retrieve the data using keyName
            System.out.println("Main received data: " + macAddressFromScan);
            try {
                if (!macAddressFromScan.equals("")) {
                    Log.i("Main", "selected MAC: " + macAddressFromScan);
                    initBluetoothHandler(macAddressFromScan);
                }
            } catch (NullPointerException e) {
                // do nothing, there are just no data
            }
        }

        Button getCurrentTime = findViewById(R.id.btnMainGetCurrentTime);
        getCurrentTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (bluetoothHandler != null) {
                    Log.i("Main", "readCurrentTime started");
                    bluetoothHandler.readCurrentTime(macAddressFromScan);
                }

            }
        });

        Button setCurrentTimeNotification = findViewById(R.id.btnMainSetCurrentTimeNotification);
        setCurrentTimeNotification.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (bluetoothHandler != null) {
                    Log.i("Main", "readCurrentTime started");
                    bluetoothHandler.setCurrentTimeNotification(macAddressFromScan, true);
                }

            }
        });

        Button unsetCurrentTimeNotification = findViewById(R.id.btnMainUnsetCurrentTimeNotification);
        unsetCurrentTimeNotification.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (bluetoothHandler != null) {
                    Log.i("Main", "readCurrentTime started");
                    bluetoothHandler.setCurrentTimeNotification(macAddressFromScan, false);
                }

            }
        });

        Button readModelNumberString = findViewById(R.id.btnMainGetModelNumber);
        readModelNumberString.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (bluetoothHandler != null) {
                    Log.i("Main", "readModelNumberString started");
                    bluetoothHandler.readModelNumber(macAddressFromScan);
                }

            }
        });

        Button writeModelNumberString = findViewById(R.id.btnMainSetModelNumber);
        writeModelNumberString.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (bluetoothHandler != null) {
                    String newModelNumber = requestedModelNumber.getText().toString();
                    if (newModelNumber.equals("")) return;
                    Log.i("Main", "writeModelNumberString started");
                    bluetoothHandler.writeModelNumber(macAddressFromScan, newModelNumber);
                }

            }
        });
    }

    private void printToast(String message) {
        Toast.makeText(getApplicationContext(),
                        message,
                        Toast.LENGTH_SHORT)
                .show();
    }

    /**
     * section for (runtime) permissions
     */

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] missingPermissions = getMissingPermissions(getRequiredPermissions());
            if (missingPermissions.length > 0) {
                requestPermissions(missingPermissions, ACCESS_LOCATION_REQUEST);
            } else {
                permissionsGranted();
            }
        }
    }

    private String[] getMissingPermissions(String[] requiredPermissions) {
        List<String> missingPermissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (String requiredPermission : requiredPermissions) {
                if (getApplicationContext().checkSelfPermission(requiredPermission) != PackageManager.PERMISSION_GRANTED) {
                    missingPermissions.add(requiredPermission);
                }
            }
        }
        return missingPermissions.toArray(new String[0]);
    }

    private String[] getRequiredPermissions() {
        int targetSdkVersion = getApplicationInfo().targetSdkVersion;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && targetSdkVersion >= Build.VERSION_CODES.S) {
            return new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT};
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && targetSdkVersion >= Build.VERSION_CODES.Q) {
            return new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
        } else return new String[]{Manifest.permission.ACCESS_COARSE_LOCATION};
    }

    private void permissionsGranted() {
        // Check if Location services are on because they are required to make scanning work for SDK < 31
        int targetSdkVersion = getApplicationInfo().targetSdkVersion;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && targetSdkVersion < Build.VERSION_CODES.S) {
            if (checkLocationServices()) {
                //initBluetoothHandler(); // don't start here, use the initBluetoothHandler from selectedDevice
                //printToast("start scan to proceed");
            }
        } else {
            //initBluetoothHandler(); // don't start here, use the initBluetoothHandler from selectedDevice
            //printToast("start scan to proceed");
        }
    }

    private boolean areLocationServicesEnabled() {
        LocationManager locationManager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            Timber.e("could not get location manager");
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return locationManager.isLocationEnabled();
        } else {
            boolean isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

            return isGpsEnabled || isNetworkEnabled;
        }
    }

    private boolean checkLocationServices() {
        if (!areLocationServicesEnabled()) {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Location services are not enabled")
                    .setMessage("Scanning for Bluetooth peripherals requires locations services to be enabled.") // Want to enable?
                    .setPositiveButton("Enable", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.cancel();
                            startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                        }
                    })
                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // if this button is clicked, just close
                            // the dialog box and do nothing
                            dialog.cancel();
                        }
                    })
                    .create()
                    .show();
            return false;
        } else {
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // Check if all permission were granted
        boolean allGranted = true;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (allGranted) {
            permissionsGranted();
        } else {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Permission is required for scanning Bluetooth peripherals")
                    .setMessage("Please grant permissions")
                    .setPositiveButton("Retry", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.cancel();
                            checkPermissions();
                        }
                    })
                    .create()
                    .show();
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    protected void onResume() {
        super.onResume();

        if (getBluetoothManager().getAdapter() != null) {
            if (!isBluetoothEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            } else {
                checkPermissions();
            }
        } else {
            Timber.e("This device has no Bluetooth hardware");
        }
    }

    private boolean isBluetoothEnabled() {
        BluetoothAdapter bluetoothAdapter = getBluetoothManager().getAdapter();
        if(bluetoothAdapter == null) return false;

        return bluetoothAdapter.isEnabled();
    }

    private void initBluetoothHandler(String macAddress)
    {
        bluetoothHandler = BluetoothHandler.getInstance(getApplicationContext(), macAddress);
    }

    @NotNull
    private BluetoothManager getBluetoothManager() {
        return Objects.requireNonNull((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE),"cannot get BluetoothManager");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(heartRateDataReceiver);
        unregisterReceiver(locationServiceStateReceiver);
        unregisterReceiver(currentTimeDataReceiver);
        unregisterReceiver(connectedDeviceDataReceiver);
        unregisterReceiver(manufacturerNameDataReceiver);
        unregisterReceiver(modelNumberDataReceiver);
    }

    /**
     * section for broadcast receiver = all included ble services need one
     */

    private final BroadcastReceiver locationServiceStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action != null && action.equals(LocationManager.MODE_CHANGED_ACTION)) {
                boolean isEnabled = areLocationServicesEnabled();
                Timber.i("Location service state changed to: %s", isEnabled ? "on" : "off");
                checkPermissions();
            }
        }
    };

    private final BroadcastReceiver connectedDeviceDataReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String connectedDeviceString = intent.getStringExtra(BluetoothHandler.CONNECTED_DEVICE_EXTRA);
            if (connectedDeviceString == null) return;
            connectedDevice.setText(connectedDeviceString);
        }
    };

    private final BroadcastReceiver heartRateDataReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            HeartRateMeasurement measurement = (HeartRateMeasurement) intent.getSerializableExtra(BluetoothHandler.MEASUREMENT_HEART_BEAT_RATE_EXTRA);
            if (measurement == null) return;
            heartRateMeasurementString = String.format(Locale.ENGLISH, "%d bpm", measurement.pulse);
            heartRateMeasurement.setText(heartRateMeasurementString);
        }
    };

    private final BroadcastReceiver currentTimeDataReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String currentTimeString = intent.getStringExtra(BluetoothHandler.MEASUREMENT_CURRENT_TIME_EXTRA);
            if (currentTimeString == null) return;
            connectedDeviceFromBluetoothHandler = currentTimeString;
            currentTime.setText(currentTimeString);
        }
    };

    private final BroadcastReceiver manufacturerNameDataReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String manufacturerNameString = intent.getStringExtra(BluetoothHandler.MEASUREMENT_MANUFACTURER_NAME_EXTRA);
            if (manufacturerNameString == null) return;
            manufacturerName.setText(manufacturerNameString);
        }
    };

    private final BroadcastReceiver modelNumberDataReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String dataString = intent.getStringExtra(BluetoothHandler.MEASUREMENT_MODEL_NUMBER_EXTRA);
            if (dataString == null) return;
            requestedModelNumber.setText(dataString);
        }
    };
}