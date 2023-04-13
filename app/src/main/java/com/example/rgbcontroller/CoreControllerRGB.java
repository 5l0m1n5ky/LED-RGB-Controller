package com.example.rgbcontroller;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;


public class CoreControllerRGB extends AppCompatActivity implements ControllerListenerRGB {

    /*
    Klasa CoreControllerRGB stanowi rdzeń aplikacji aplikacji.
    Obsługuje zdarzenia zaintegrowane z systemem Android oraz zleca obsługę połączenia Bluetooth Low Energy.
    Jej istotną częścią jest kontrola zmiennych globalnych używanych w procesie wysyłania danych.
    */
    public final int ringCenterX = 540;
    public final int ringCenterY = 1270;
    public final int maxCircleRadius = 430;
    public final int minCircleRadius = 300;
    private static boolean connected = false;

    ImageView rgbRing;
    ImageView ringSlider;
    ImageView switchOnOff;
    ImageView switchOnOffGlow;
    ImageView btIndicator;
    ImageView btIndicatorGlow;
    SeekBar brightnessBar;
    SeekBar speedBar;
    Switch fadeSwitch;
    Switch flashSwitch;
    TextView brightnessLabel;
    TextView speedLabel;
    Bitmap bitmap;
    private ControllerBLE controllerBLE;
    private TransmitterBLE transmitterBLE;
    protected String deviceAddress;
    public static String switchOnOffState = "0";
    public static String fadeState = "0";
    public static String flashState = "0";
    public static String brightnessLevel = "64";
    public static String speedLevel = "32";
    public static String redChannel = "00";
    public static String greenChannel = "00";
    public static String blueChannel = "FF";


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        /*
        Inicjalizacja kontrukcji aplikacji
        */

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.controllerBLE = ControllerBLE.getInstance(this);
        this.transmitterBLE = new TransmitterBLE(this.controllerBLE);

        /*
        Wywołanie metody sprawdzającej przydzielone aplikacji uprawnienia
        W razie braku niezbędnych uprawnień, aplikacja prosi użytkownika o ich przydzielenie.
        */
        checkPermissions();

        /*
        Inicjalizacja komponentów logicznych i graficznych aplikacji.
        */
        rgbRing = findViewById(R.id.colorRing);
        ringSlider = findViewById(R.id.colorRingSlider);
        switchOnOff = findViewById(R.id.button);
        switchOnOffGlow = findViewById(R.id.buttonGlow);
        btIndicator = findViewById(R.id.btIndicator);
        btIndicatorGlow = findViewById(R.id.btIndicatorGlow);
        brightnessBar = findViewById(R.id.brightnessBar);
        speedBar = findViewById(R.id.speedBar);
        fadeSwitch = findViewById(R.id.fadeSwitch);
        flashSwitch = findViewById(R.id.flashSwitch);
        brightnessLabel = findViewById(R.id.brightnessLabel);
        speedLabel = findViewById(R.id.speedLabel);

        switchOnOffGlow.setEnabled(false);
        switchOnOffGlow.setVisibility(View.INVISIBLE);
        btIndicatorGlow.setVisibility(View.INVISIBLE);
        brightnessBar.setProgress(100);
        speedBar.setProgress(50);

        /*
        Metoda wyłącza dostęp do części steruących dostępnych dla użytkownika
        w celu zapobiegania wystapienia błędów obsługi danych. Aktywacja nastąpi
        w momencie nawiązania komunikacji z urządzeniem BLE.
        */
        disableControl();

        switchOnOff.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                /*
                Kontrola reakcji wcśnięcia przycisku włączania. Grafika przycisku bez poświetlenia
                zostaje podmieniona na wersję z poświetleniem, zostaje zdefiniowana ramka danych do wysłania.
                */
                switchOnOffState = "1";
                transmitterBLE.createDataFrame(switchOnOffState, fadeState, flashState, brightnessLevel, speedLevel, redChannel, greenChannel, blueChannel);
                switchOnOffGlow.setVisibility(View.VISIBLE);
                switchOnOffGlow.bringToFront();
                switchOnOff.setEnabled(false);
                switchOnOffGlow.setEnabled(true);
            }
        });

        switchOnOffGlow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                /*
                Kontrola reakcji odcśnięcia przycisku włączania. Grafika przycisku z podświetleniem
                zostaje podmieniona na wersję bez poświetlenia, zostaje zdefiniowana ramka danych do wysłania.
                */
                switchOnOffState = "0";
                transmitterBLE.createDataFrame(switchOnOffState, fadeState, flashState, brightnessLevel, speedLevel, redChannel, greenChannel, blueChannel);
                switchOnOff.setEnabled(true);
                switchOnOffGlow.setEnabled(false);
                switchOnOffGlow.setVisibility(View.INVISIBLE);
                switchOnOff.bringToFront();
            }
        });

        brightnessBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean b) {
                /*
                Metoda typu Listener odpowiedzialna za odczyt stanu suwaka.
                Funkcja checkLevel() kontroluje stałą długość ramki dodając "0"
                przed zapis heksadecymalny w określonych przypadkach.
                */
                brightnessLevel = checkLevel(Integer.toHexString(progress));
                transmitterBLE.createDataFrame(switchOnOffState, fadeState, flashState, brightnessLevel, speedLevel, redChannel, greenChannel, blueChannel);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        speedBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean b) {
                speedLevel = checkLevel(Integer.toHexString(progress));
                transmitterBLE.createDataFrame(switchOnOffState, fadeState, flashState, brightnessLevel, speedLevel, redChannel, greenChannel, blueChannel);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        fadeSwitch.setOnClickListener(new View.OnClickListener() {
            /*
            Reakacja na zdarzenie wywołane przez przełącznik efektu świetlengo
            nadpisuje dane, tworzy ramkę oraz wyklucza drugi z efektów aby uniknąć
            sytuacji aktywnych dwóch efektów w tym samym momencie
            */
            @Override
            public void onClick(View view) {
                if (fadeSwitch.isChecked()) {
                    fadeState = "1";
                } else {
                    fadeState = "0";
                }
                flashState = "0";
                transmitterBLE.createDataFrame(switchOnOffState, fadeState, flashState, brightnessLevel, speedLevel, redChannel, greenChannel, blueChannel);
                flashSwitch.setChecked(false);
            }
        });

        flashSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (flashSwitch.isChecked()) {
                    flashState = "1";
                } else {
                    flashState = "0";
                }
                fadeState = "0";
                transmitterBLE.createDataFrame(switchOnOffState, fadeState, flashState, brightnessLevel, speedLevel, redChannel, greenChannel, blueChannel);
                fadeSwitch.setChecked(false);
            }
        });
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        /*
        Metoda typu onTouchEvent zbiera informację o współrzędnych miejsca dotyku ekranu.
        */

        int xCoordinate = (int) event.getX();
        int yCoordinate = (int) event.getY();

        double angle = 0.0;

        /*
        Poniżej wykonywane jest geometryczne wyznaczenie odlegości punktu dotyku od środka pierścienia kontroli RGB.
        Następnie wyznaczany jest kąt za pomocą trygonometrycznej konwersji. Informacja o koncie i odległości od środka
        okręgu wykorzystywana jest do obrotu wskaźnika koloru oraz pobrania próbki składowych RGB.
        */
        double radius = Math.sqrt(Math.pow(Math.abs(xCoordinate - ringCenterX), 2) + Math.pow(Math.abs(yCoordinate - ringCenterY), 2));

        if (radius <= maxCircleRadius && radius > minCircleRadius) {
            if (xCoordinate >= ringCenterX && yCoordinate >= ringCenterY) {
                angle = 180 - Math.toDegrees(Math.asin(Math.abs(xCoordinate - ringCenterX) / radius));
            } else if (xCoordinate < ringCenterX && yCoordinate >= ringCenterY) {
                angle = Math.toDegrees(Math.asin(Math.abs(xCoordinate - ringCenterX) / radius)) + 180;
            } else if (xCoordinate < ringCenterX && yCoordinate < ringCenterY) {
                angle = 360 - Math.toDegrees(Math.asin(Math.abs(xCoordinate - ringCenterX) / radius));
            } else if (xCoordinate >= ringCenterX && yCoordinate < ringCenterY) {
                angle = Math.toDegrees(Math.asin(Math.abs(xCoordinate - ringCenterX) / radius));
            }
            ringSlider.setRotation((float) angle);
            getColorCode(xCoordinate, yCoordinate);
        }
        return false;
    }

    public void getColorCode(int xCoordinate, int yCoordinate) {

        bitmap = ((BitmapDrawable) rgbRing.getDrawable()).getBitmap();
        int y = bitmap.getHeight();
        int x = bitmap.getWidth();

        int rangeMinX = ringCenterX - maxCircleRadius;
        int rangeMaxX = ringCenterX + maxCircleRadius;
        int rangeMinY = ringCenterY - maxCircleRadius;
        int rangeMaxY = ringCenterY + maxCircleRadius;

        int scaleMin = 0;
        int scaleMax = 606;

        int xScaled = ((scaleMax - scaleMin) * (xCoordinate - rangeMinX)) / (rangeMaxX - rangeMinX);
        int yScaled = ((scaleMax - scaleMin) * (yCoordinate - rangeMinY)) / (rangeMaxY - rangeMinY);

        int pixel = bitmap.getPixel(xScaled, yScaled);

        /*
        Pobrana informacja o kolorze wystepuje w postaci typu Integer. Poniżej wartość składowych
        jest nadpisywana w zmiennych z korekcją długości ramki danych checkHex oraz konwersji Int -> Hex String.
        */
        redChannel = checkHex(Integer.toHexString(Color.red(pixel)));
        greenChannel = checkHex(Integer.toHexString(Color.green(pixel)));
        blueChannel = checkHex(Integer.toHexString(Color.blue(pixel)));

        if (connected) {
            transmitterBLE.createDataFrame(switchOnOffState, fadeState, flashState, brightnessLevel, speedLevel, redChannel, greenChannel, blueChannel);
        }

    }

    private String checkHex(String hexValue) {
        String fixedStringValue = "";
        if (hexValue.length() == 1) {
            fixedStringValue = String.join("", "0", hexValue);
        } else {
            fixedStringValue = hexValue;
        }
        return fixedStringValue;
    }

    private String checkLevel(String levelValue) {
        String fixedlevelValue = "";
        if (levelValue.length() == 1) {
            fixedlevelValue = String.join("", "0", levelValue);
        } else {
            fixedlevelValue = levelValue;
        }
        return fixedlevelValue;
    }



    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.BLUETOOTH_CONNECT, android.Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.NEARBY_WIFI_DEVICES}, 42);
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    /*
    Metoda onStart() wywoływana jest przy każdym uruchomieniu aplikacji zgodnie zaraz po metodzie onCreate().
    Poniższe działanie dotyczy sprawdzenia statusu włączenia modułu Bluetooth oraz ewentualną prośbę o jego
    uruchomienie.
    */
    protected void onStart() {
        super.onStart();
        if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
            Intent enableBTIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBTIntent, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        this.deviceAddress = null;
        this.controllerBLE = ControllerBLE.getInstance(this);
        this.controllerBLE.addBLEControllerListener(this);

        /*
        Włączony Bluetooth umożliwia inicjalizację połączenia metodą init().
        */

        if (BluetoothAdapter.getDefaultAdapter().isEnabled()) {
            Log.i("===[BLE]===", "Searching for RGB Controller...");
            this.controllerBLE.init();
        } else {
            Log.i("===========INIT STATUS===========", "Init() not executable");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        /*
        onPause() wywoływane jest po wyjściu z aplikacji - wyłączony zostaje listener BLE.
        */

        this.controllerBLE.removeBLEControllerListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        /*
        onDestroy() wywoływane jest po usunięciu aplikacji z pamięci RAM - wyłączona zostaje funkcjonalność BLE.
        */

        controllerBLE.disconnect();
    }

    @Override
    public void BLEControllerConnected() {
        /*
        Za pomocą nowego wątku następuje aktywacja elementów strujących do dyspozcyji użytkownika.
        Wyświetlana jest kontrolka połączenia BT.
        */
        Log.i("[BLE]", "Connected");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                switchOnOff.setEnabled(true);
                ringSlider.setEnabled(true);
                rgbRing.setEnabled(true);
                fadeSwitch.setEnabled(true);
                flashSwitch.setEnabled(true);
                brightnessBar.setEnabled(true);
                speedBar.setEnabled(true);
                btIndicatorGlow.setVisibility(View.VISIBLE);
                connected = true;
            }
        });
    }

    @Override
    public void BLEControllerDisconnected() {
        /*
        Nowo utworzony wątek służy do zerwania komunikacji BT, dezaktywacji elementów sterujących dla użytkownika oraz
        aktualizacji stanu kontrolki połączenia.
        */
        Log.i("[BLE]", "Disconnected");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                disableControl();
                btIndicatorGlow.setVisibility(View.INVISIBLE);
            }
        });
        btIndicatorGlow.setVisibility(View.INVISIBLE);
    }

    @Override
    /*
    Informacja o wykrytym docelowym urządzeniu BLE jest nadpisywana w zmiennej oraz przekazywana do przetworzenia połączenia.
    */
    public void BLEDeviceFound(String name, String address) {
        Log.i("[BLE]", "Device " + name + " found with address " + address);
        this.deviceAddress = address;
        controllerBLE.connectToDevice(deviceAddress);
    }

    private void disableControl() {
        switchOnOff.setEnabled(false);
        switchOnOffGlow.setEnabled(false);
        ringSlider.setEnabled(false);
        rgbRing.setEnabled(false);
        fadeSwitch.setEnabled(false);
        flashSwitch.setEnabled(false);
        brightnessBar.setEnabled(false);
        speedBar.setEnabled(false);
    }
}



