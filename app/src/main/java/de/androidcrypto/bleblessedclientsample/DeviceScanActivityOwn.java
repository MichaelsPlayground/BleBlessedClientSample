package de.androidcrypto.bleblessedclientsample;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class DeviceScanActivityOwn extends AppCompatActivity {

    // https://developer.android.com/guide/topics/connectivity/bluetooth/find-ble-devices#java

    private final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private final BluetoothLeScanner bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
    private boolean scanning;
    private Handler handler = new Handler();

    ProgressBar progressBar;
    Button scan, btnReturn;
    ListView listView;
    ArrayAdapter<String> scannedDevicesArrayAdapter;

    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 5000; // 5000 = 5 seconds

    /**
     * Return Intent extra
     */
    public static String EXTRA_DEVICE_ADDRESS = "device_address";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_scan_own);

        progressBar = findViewById(R.id.pbList);
        listView = findViewById(R.id.lvListListView);
        scan = findViewById(R.id.btnListScan);
        btnReturn = findViewById(R.id.btnListReturn);

        // populate the data
        scannedDevicesArrayAdapter = new ArrayAdapter<>(this, R.layout.device_name);
        listView.setAdapter(scannedDevicesArrayAdapter);



        scan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                scannedDevicesArrayAdapter.clear();
                progressBar.setIndeterminate(false);
                progressBar.setVisibility(View.VISIBLE);
                //doDiscovery();
                scanLeDevice();
            }
        });

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @SuppressLint("MissingPermission")
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                progressBar.setIndeterminate(true);
                progressBar.setVisibility(View.GONE);
                System.out.println("This item was clicked: " + i);
                // Cancel discovery because it's costly and we're about to connect
                //mBtAdapter.cancelDiscovery();
                // Get the device MAC address, which is the last 17 chars in the View
                String info = ((TextView) view).getText().toString();
                String address = info.substring(info.length() - 17);
                // check for the text "scanning complete"
                if (address.equalsIgnoreCase("scanning complete")) {
                    System.out.println("do not use this data");
                    address = "";
                }
                System.out.println("*** MAC: " + address);

                // Create the Intent and include the MAC address
                Intent intent = new Intent(DeviceScanActivityOwn.this, MainActivity.class);
                intent.putExtra(EXTRA_DEVICE_ADDRESS, address);
                startActivity(intent);
                finish();



            }
        });


    }

    @SuppressLint("MissingPermission")
    private void scanLeDevice() {
        if (!scanning) {
            // Stops scanning after a predefined scan period.
            handler.postDelayed(new Runnable() {
                @SuppressLint("MissingPermission")
                @Override
                public void run() {
                    scanning = false;
                    bluetoothLeScanner.stopScan(leScanCallback);
                    progressBar.setIndeterminate(true);
                    progressBar.setVisibility(View.GONE);
                }
            }, SCAN_PERIOD);

            scanning = true;
            bluetoothLeScanner.startScan(leScanCallback);
        } else {
            scanning = false;
            bluetoothLeScanner.stopScan(leScanCallback);
        }
    }

    @SuppressLint("MissingPermission")
    // Device scan callback.
    private ScanCallback leScanCallback =
            new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    super.onScanResult(callbackType, result);
                    String deviceInfos =
                            "name: " + result.getDevice().getName()
                            + " type: " + result.getDevice().getType()
                            + " address: " + result.getDevice().getAddress();
                    scannedDevicesArrayAdapter.add(deviceInfos);
                    //leDeviceListAdapter.notifyDataSetChanged();
                }
            };


}