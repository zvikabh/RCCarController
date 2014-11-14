#include "DualVNH5019MotorShield.h"
 
DualVNH5019MotorShield md;
int speedMove = 100;
int speedTurn = 300;

void setup()
{
  Serial.begin(19200);
  Serial.println("Dual VNH5019 Motor Shield");
  md.init();
}
 
void loop()
{
  Serial.println("Forward");
  md.setSpeeds(speedMove, speedMove);
  delay(3000);
  Serial.println("Turn Right");
  md.setSpeeds(-speedTurn, speedTurn);
  delay(1500);
  Serial.println("Reverse");
  md.setSpeeds(-speedMove, -speedMove);
  delay(3000);
  Serial.println("Turn Left");
  md.setSpeeds(speedTurn, -speedTurn);
  delay(1500);
}

