package com.example.rgbcontroller;

public interface ControllerListenerRGB {
    public void BLEControllerConnected();
    public void BLEControllerDisconnected();
    public void BLEDeviceFound(String name, String address);
}
