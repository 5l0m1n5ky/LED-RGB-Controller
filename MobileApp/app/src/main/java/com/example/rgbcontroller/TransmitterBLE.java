package com.example.rgbcontroller;

public class TransmitterBLE {

    /*
    Klasa TransmitterBLE obsługuje tę część aplikacji, która odpowiedzialna jest za walidację danych w odpowiedniej postaci.
    createDataFrame() używane jest do zgromadzenia wszystkich zmiennych globalych, które zostają zintegrowane w postać jednej zmiennej
    typu String. Klasa zawiera także w sobie wywołanie funkcji obsługi transmitowania danych do urządzenia BLE - kontrolera taśmy RGB.
    */
    private ControllerBLE controllerBLE;

    public TransmitterBLE(ControllerBLE controllerBLE) {
        this.controllerBLE = controllerBLE;
    }
    public void createDataFrame(String switchOnOffState, String fadeState, String flashState, String brightnessLevel, String speedLevel, String redChannel, String greenChannel, String blueChannel) {
        String data = String.join("", switchOnOffState, fadeState, flashState, brightnessLevel, speedLevel, redChannel, greenChannel, blueChannel);

        transmitData(data);
    }

    public void transmitData(String data) {
        this.controllerBLE.sendData(data);
    }
}
