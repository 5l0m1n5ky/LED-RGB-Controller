package com.example.rgbcontroller;

import static android.bluetooth.BluetoothProfile.GATT;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.util.Log;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ControllerBLE {

    /*
    Klasa ControllerBLE rozszerza obsługę zleceń zarządzania połączeniem BLE.
    Za pomocą obiektów odpowiednich klas możliwe jest skanowanie, autoryzacja połączenia,
    nawiązywanie i zrywanie komunikacji oraz zestawianie charakterytyk przesyłu danych.
    */
    private static ControllerBLE instance;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothDevice bluetoothDevice;
    private BluetoothGatt bluetoothGatt;
    private BluetoothManager bluetoothManager;
    private BluetoothGattCharacteristic bluetoothGattCharacteristic = null;
    private ArrayList<ControllerListenerRGB> controllerListenerRGB = new ArrayList<>();
    private HashMap<String, BluetoothDevice> devices = new HashMap<>();
    private static final String ServiceUUID = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E";
    private static final String CharacteristicUUID = "6E400002-B5A3-F393-E0A9-E50E24DCCA9E";
    private ControllerBLE(Context context) {
        this.bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
    }
    public static ControllerBLE getInstance(Context context) {
        if (null == instance)
            instance = new ControllerBLE((context));

        return instance;
    }

    public void addBLEControllerListener(ControllerListenerRGB controllerListenerRGB) {
        if (!this.controllerListenerRGB.contains(controllerListenerRGB))
            this.controllerListenerRGB.add(controllerListenerRGB);
    }

    public void removeBLEControllerListener(ControllerListenerRGB controllerListenerRGB) {
        this.controllerListenerRGB.remove(controllerListenerRGB);
    }

    @SuppressLint("MissingPermission")
    public void init() {
        this.devices.clear();
        this.bluetoothLeScanner = this.bluetoothManager.getAdapter().getBluetoothLeScanner();
        bluetoothLeScanner.startScan(scanCallback);
    }

    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            if (!devices.containsKey(device.getAddress()) && isThisTheDevice(device)) {
                deviceFound(device);
                Log.i("[BLE]", " device found");
            }
        }
        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult scanResult : results) {
                BluetoothDevice device = scanResult.getDevice();
                if (!devices.containsKey(device.getAddress()) && isThisTheDevice(device)) {
                    deviceFound(device);
                }
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.i("[BLE]", "scan failed with errorcode: " + errorCode);
        }
    };
    /*
    Poniżej znajduje się sprawdzenie autentyczności urządzenia docelowego BLE.
    */
    @SuppressLint("MissingPermission")
    private boolean isThisTheDevice(BluetoothDevice bluetoothDevice) {
        return null != bluetoothDevice.getName() && bluetoothDevice.getName().startsWith("RGB Controller");
    }

    private void deviceFound(BluetoothDevice bluetoothDevice) {
        this.devices.put(bluetoothDevice.getAddress(), bluetoothDevice);
        fireDeviceFound(bluetoothDevice);
        Log.i("[BLE]", "device found");
    }
    /*
    Połączenie z urządzeniem BLE przerywa skanowanie urządzeń oraz ustanawia serwer połączeniowy GATT.
    */
    @SuppressLint("MissingPermission")
    public void connectToDevice(String address) {
        this.bluetoothDevice = this.devices.get(address);
        this.bluetoothLeScanner.stopScan(this.scanCallback);
        Log.i("[BLE]", "connect to device " + bluetoothDevice.getAddress());
        this.bluetoothGatt = bluetoothDevice.connectGatt(null, false, this.bleConnectCallback);
    }

    private final BluetoothGattCallback bleConnectCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt bluetoothGatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i("[BLE]", "start service discovery " + bluetoothGatt.discoverServices());
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                bluetoothGattCharacteristic = null;
                Log.w("[BLE]", "DISCONNECTED with status " + status);
                fireDisconnected();
            } else {
                Log.i("[BLE]", "unknown state " + newState + " and status " + status);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt bluetoothGatt, int status) {
            if (null == bluetoothGattCharacteristic) {
                for (BluetoothGattService service : bluetoothGatt.getServices()) {
                    if (service.getUuid().toString().equalsIgnoreCase(ServiceUUID)) {
                        List<BluetoothGattCharacteristic> gattCharacteristics = service.getCharacteristics();
                        for (BluetoothGattCharacteristic bgc : gattCharacteristics) {
                            if (bgc.getUuid().toString().equalsIgnoreCase(CharacteristicUUID)) {
                                int chprop = bgc.getProperties();
                                if (((chprop & BluetoothGattCharacteristic.PROPERTY_WRITE) | (chprop & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) > 0) {
                                    bluetoothGattCharacteristic = bgc;
                                    Log.i("[BLE]", "CONNECTED and ready to send");
                                    fireConnected();
                                }
                            }
                        }
                    }
                }
            }
        }
    };

    private void fireDisconnected() {
        for (ControllerListenerRGB ctrlListenerRGB : this.controllerListenerRGB)
            ctrlListenerRGB.BLEControllerDisconnected();

        this.bluetoothDevice = null;
    }

    private void fireConnected() {
        for (ControllerListenerRGB ctrlListenerRGB : this.controllerListenerRGB)
            ctrlListenerRGB.BLEControllerConnected();
    }

    @SuppressLint("MissingPermission")
    private void fireDeviceFound(BluetoothDevice bluetoothDevice) {
        for (ControllerListenerRGB ctrlListenerRGB : this.controllerListenerRGB) {
            ctrlListenerRGB.BLEDeviceFound(bluetoothDevice.getName().trim(), bluetoothDevice.getAddress());
        }
    }

    /*
    sendData() używane jest do wysyłania danych w postaci określonej poprzez UUID charakterystyki.
    Funkcja przyjmuje jako parametr dane typu String, które uprzdnio zostały odpowidnio skonwertowane.
    */
    @SuppressLint("MissingPermission")
    public void sendData(String data) {
        this.bluetoothGattCharacteristic.setValue(data);
        bluetoothGatt.writeCharacteristic(this.bluetoothGattCharacteristic);
        Log.i("BLE send state", " datagram: " + data);
    }

    @SuppressLint("MissingPermission")
    public boolean checkConnectedState() {
        return this.bluetoothManager.getConnectionState(this.bluetoothDevice, GATT) == 2;
    }

    @SuppressLint("MissingPermission")
    public void disconnect() {
        this.bluetoothGatt.disconnect();
    }
}

