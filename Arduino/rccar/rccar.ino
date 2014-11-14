#include "DualVNH5019MotorShield.h"
 
DualVNH5019MotorShield md;

void setup() {
  Serial.begin(19200);
  Serial.setTimeout(3000);
  md.init();
}

void stopMotors() {
  md.setSpeeds(0, 0);
}

void loop() {
  int i;
  int dataPos;
  byte receivedData[12];
  
  int bytesReceived = Serial.readBytes(receivedData, 8);
  if (bytesReceived < 8) {
    // Incomplete or missing communications.
    // Stop motors and wait for valid message.
    stopMotors();
    return;
  }
  
  // Copy first 4 bytes to end of array, to make it easier
  // to search for the sync bytes.
  for (i = 0; i < 4; i++) {
    receivedData[8 + i] = receivedData[i];
  }
  
  // Find sync bytes.
  for (i = 0; i < 8; i++) {
    if (receivedData[i] == 0x7f &&
        receivedData[i+1] == 0x7f &&
        receivedData[i+2] == 0x80 &&
        receivedData[i+3] == 0x80) {
      // Found sync bytes.
      dataPos = (i+4)%8;
      break;
    }
  }
  
  if (i == 8) {
    // Sync bytes not found. Wait for a valid message.
    stopMotors();
    return;
  }
  
  // Valid message found. Decode it and send to motor.
  short leftPos = receivedData[dataPos] |
      (((short)receivedData[dataPos+1]) << 8);
  short rightPos = receivedData[dataPos+2] |
      (((short)receivedData[dataPos+3]) << 8);
  md.setSpeeds(rightPos, leftPos);
}

