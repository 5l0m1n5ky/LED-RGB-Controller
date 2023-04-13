
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <stdlib.h>
#include <string>
#include <iostream>


BLECharacteristic *pCharacteristic;
bool deviceConnected = false;

#define LED 2

int redDataValue = 0;
int greenDataValue = 0;
int blueDataValue = 0;

int redChannelValue = 0;
int greenChannelValue = 0;
int blueChannelValue = 0;

int brightnessDuty = 255;
int speedDuty = 127;

String switchState = "0";
String fadeState = "0";
String flashState = "0";

int brighntessLevel = 100;
int speedLevel = 50;

const int frequency = 1500;
#define redChannel 1
#define greenChannel 2
#define blueChannel 3
const int resolution = 8;

#define redPin 5
#define greenPin 3
#define bluePin 15

const int minFlashDelay = 40;
const int maxFlashDelay = 2000;

const int minFadeDelay = 1;
const int maxFadeDelay = 30;

long timeStamp = 0;

int speedFadeDuty = 0;
int speedFlashDuty = 0;

boolean switchCheck = false;

// ++++++++++++++++++++++++

long int fadeInitTime;
long int flashInitTime;

// ++++++++++++++++++++++++



#define SERVICE_UUID "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
#define CHARACTERISTIC_UUID_RX "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"

class MyServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer *pServer) {
    deviceConnected = true;
  };

  void onDisconnect(BLEServer *pServer) {
    deviceConnected = false;
  }
};

class MyCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *pCharacteristic) {
    std::string rxValue = pCharacteristic->getValue();

    String valueRX = rxValue.c_str();

    if (rxValue.length() > 0) {

      switchState = valueRX.substring(0, 1);
      fadeState = valueRX.substring(1, 2);
      flashState = valueRX.substring(2, 3);
      brighntessLevel = hexToDec(valueRX.substring(3, 5));
      speedLevel = hexToDec(valueRX.substring(5, 7));
      redDataValue = hexToDec(valueRX.substring(7, 9));
      greenDataValue = hexToDec(valueRX.substring(9, 11));
      blueDataValue = hexToDec(valueRX.substring(11, 13));

      brightnessDuty = map(brighntessLevel, 0, 100, 1, 255);
      speedFadeDuty = map(speedLevel, 0, 100, maxFadeDelay, minFadeDelay);
      speedFlashDuty = map(speedLevel, 0, 100, maxFlashDelay, minFlashDelay);

      redChannelValue = map(redDataValue, 0, 255, 0, brightnessDuty);
      greenChannelValue = map(greenDataValue, 0, 255, 0, brightnessDuty);
      blueChannelValue = map(blueDataValue, 0, 255, 0, brightnessDuty);


      Serial.println("switchState " + switchState);
      Serial.println("fadeState " + fadeState);
      Serial.println("flashState  " + flashState);
      Serial.println("brighntessLevel " + String(brighntessLevel));
      Serial.println("speedLevel  " + String(speedLevel));
      Serial.println("redDataValue  " + String(redDataValue));
      Serial.println("greenDataValue  " + String(greenDataValue));
      Serial.println("blueDataValue " + String(blueDataValue));

      Serial.println("brightnessDuty  " + String(brightnessDuty));
      Serial.println("speedFadeDuty " + String(speedFadeDuty));
      Serial.println("speedFlashDuty  " + String(speedFlashDuty));
      Serial.println("redChannelValue " + String(redChannelValue));
      Serial.println("greenChannelValue " + String(greenChannelValue));
      Serial.println("blueChannelValue  " + String(blueChannelValue));
    }
  }
  unsigned int hexToDec(String hexData) {

    char hex[hexData.length()];
    hexData.toCharArray(hex, hexData.length() + 1);
    unsigned int decimalValue = (int)strtol(hex, NULL, 16);

    return decimalValue;
  }
};

int mapValue(int value){
  return map(value, 0, 255, 0, brightnessDuty);
}

void setColor(int valueRed, int valueGreen, int valueBlue) {

  ledcWrite(redChannel, valueRed);
  ledcWrite(greenChannel, valueGreen);
  ledcWrite(blueChannel, valueBlue);
}

boolean controllerDelay(int delayTime) {

  if ((millis() - timeStamp) >= delayTime) {
    timeStamp = millis();
    return true;
  } else {
    return false;
  }
}

void fade() {

  static unsigned int rgbColour[3] = { 255, 0, 0 };
  static int incrementColourIndex = 1;
  static int decrementColourIndex = 0;
  static int i = -1;

  i++;
  if (i > 255) {
    i = 0;
    decrementColourIndex++;
    if (decrementColourIndex > 2) {
      decrementColourIndex = 0;
    }
    if (decrementColourIndex == 2) {
      incrementColourIndex = 0;
    } else {
      incrementColourIndex = decrementColourIndex + 1;
    }
  }

  rgbColour[decrementColourIndex] -= 1;
  rgbColour[incrementColourIndex] += 1;

  setColor(mapValue(rgbColour[0]), mapValue(rgbColour[1]), mapValue(rgbColour[2]));
  fadeInitTime = millis() + speedFadeDuty;
}

boolean isflashEnabled() {

  if (millis() >= flashInitTime) {
    flashInitTime = millis() + speedFlashDuty;
    return true;
  } else {
    return false;
  }
}

void setup() {
  Serial.begin(115200);

  pinMode(LED, OUTPUT);
  pinMode(redPin, OUTPUT);
  pinMode(greenPin, OUTPUT);
  pinMode(bluePin, OUTPUT);

  ledcAttachPin(redPin, redChannel);
  ledcAttachPin(greenPin, greenChannel);
  ledcAttachPin(bluePin, blueChannel);

  ledcSetup(redChannel, frequency, resolution);
  ledcSetup(greenChannel, frequency, resolution);
  ledcSetup(blueChannel, frequency, resolution);

  // Create the BLE Device
  BLEDevice::init("RGB Controller");  // Give it a name

  // Create the BLE Server
  BLEServer *pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  // Create the BLE Service
  BLEService *pService = pServer->createService(SERVICE_UUID);

  BLECharacteristic *pCharacteristic = pService->createCharacteristic(CHARACTERISTIC_UUID_RX, BLECharacteristic::PROPERTY_WRITE);

  pCharacteristic->setCallbacks(new MyCallbacks());

  // Start the service
  pService->start();

  // Start advertising
  pServer->getAdvertising()->start();
  Serial.println("Waiting a client connection to notify...");

  // +++++++++++++++++++++++

  fadeInitTime = millis();
  flashInitTime = millis();

  // +++++++++++++++++++++++
}

void loop() {

  if (switchState == "1") {
    // Serial.println("switch ON state");
    switchCheck = true;
  } else {
    switchCheck = false;
  }

  if (fadeState == "1" && switchCheck) {

    if (millis() >= fadeInitTime) {
      fade();
    }

  } else if (flashState == "1" && switchCheck) {

    if (isflashEnabled()) {

      setColor(mapValue(random(0, 3) * 64), mapValue(random(0, 3) * 64), mapValue(random(0, 3) * 64));

    }

  } else if (switchCheck) {
    
    setColor(redChannelValue, greenChannelValue, blueChannelValue);
   
  } else if (!switchCheck){

    setColor(0, 0, 0);

  }


  if (!deviceConnected) {
    digitalWrite(LED, HIGH);
    delay(250);
    digitalWrite(LED, LOW);
    delay(250);
  }
}
