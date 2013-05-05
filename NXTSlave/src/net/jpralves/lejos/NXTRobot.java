package net.jpralves.lejos;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import lejos.nxt.Battery;
import lejos.nxt.Button;
import lejos.nxt.ColorSensor;
import lejos.nxt.LCD;
import lejos.nxt.Motor;
import lejos.nxt.NXTRegulatedMotor;
import lejos.nxt.SensorPort;
import lejos.nxt.TouchSensor;
import lejos.nxt.UltrasonicSensor;
import lejos.nxt.comm.Bluetooth;
import lejos.nxt.comm.NXTConnection;

/**
 * NXTRobot class - Creates an Instance of a Robot
 * 
 * @author Joao Alves (jpralves@gmail.com)
 * @version 1.0
 */

public class NXTRobot {

	private static final int STD_MIN = 6100;
	private static final int STD_OK = 6500;
	private static final int STD_MAX = 8000;
	private static final int RECHARGE_MIN = 7100;
	private static final int RECHARGE_OK = 7200;
	private static final int RECHARGE_MAX = 8200;

	private static final int SAFEMINDISTACE = 15;
	private int safeMinDistance = SAFEMINDISTACE;
	private static final int SAFEMINBATTERYPERCENT = 5;
	static final int REMOTE = 0;
	static final int SELF = 1;

	static final int NXTVOLTAGE = 1;
	static final int NXTSPEED = 2;
	static final int NXTSENSORSONICLEFT = 3;
	static final int NXTSENSORSONICRIGHT = 4;
	static final int NXTSENSORSONICFRONT = 5;
	static final int NXTSENSORTOUCH = 6;
	static final int NXTSENSORCOLOR = 7;
	static final int NXTCONTROL = 8;
	static final int NXTLTACHO = 9;
	static final int NXTRTACHO = 10;
	static final int NXTBATTERY = 11;

	static final short BTMSGTYPEFLOAT = 1;
	static final short BTMSGTYPEBYTE = 2;
	static final short BTMSGTYPEINT = 3;
	static final short BTMSGTYPEARRAYINT = 4;

	int avoidMoveDuration = 1000;
	int selfReason = 0;

	private int batteryLow;
	private int batteryOk;
	private int batteryRange;
	private boolean lowVoltage;

	private int robotState = REMOTE;
	private int byteCounter = 0;
	private float sentVoltage = 10000;
	private int sentBatteryLevel = 100;
	private float batteryVoltage = 10000;
	private int[] sentColors = new int[] { 0, 0, 0 };
	private int sentRobotState = -1;
	private int batteryLevel = 100;
	private int distanceLeft = 0;
	private int distanceRight = 0;
	private int distanceFront = 0;
	private int isTouch = -1;
	private int speed = 0;
	private int leftTacho = 0;
	private int rightTacho = 0;
	private NXTConnection connection = null;
	private DataOutputStream dataOut;
	private NXTInput bti;

	private TwoWheelRobot nxtRobot;
	private TouchSensor touch = null;
	private ColorSensor color = null;
	private UltrasonicSensor sonicLeft = null;
	private UltrasonicSensor sonicRight = null;
	private UltrasonicSensor sonicFront = null;

	private boolean configured = false;
	private String strConfiguration = "";

	/**
	 * @return the robotState
	 */
	public int isRobotState() {
		return robotState;
	}

	/**
	 * get the sensor port for a position
	 * 
	 * @param num
	 *            number of the sensorport
	 * @return the sensorport object correspondent to the number
	 */
	public static SensorPort getSensorPort(int num) {
		SensorPort res = SensorPort.S1;

		switch (num) {
		case 1:
			res = SensorPort.S1;
			break;
		case 2:
			res = SensorPort.S2;
			break;
		case 3:
			res = SensorPort.S3;
			break;
		case 4:
			res = SensorPort.S4;
			break;
		}
		return res;
	}

	/**
	 * get the motor for a position
	 * 
	 * @param num
	 *            number of the motor
	 * @return the motor object correspondent to the number
	 */
	public static NXTRegulatedMotor getMotorPort(int num) {
		NXTRegulatedMotor res = Motor.A;

		switch (num) {
		case 1:
			res = Motor.A;
			break;
		case 2:
			res = Motor.B;
			break;
		case 3:
			res = Motor.C;
			break;
		}
		return res;
	}

	public boolean sendFloat(int method, Float value) {
		boolean done = false;
		try {
			dataOut.writeShort(method);
			dataOut.writeByte(BTMSGTYPEFLOAT);
			dataOut.writeFloat(value);
			dataOut.flush();
			byteCounter += 2 + 1 + 4;
			done = true;
		} catch (IOException e) {
		}
		return done;
	}

	public boolean sendInt(int method, int value) {
		boolean done = false;
		try {
			dataOut.writeShort(method);
			dataOut.writeByte(BTMSGTYPEINT);
			dataOut.writeInt(value);
			dataOut.flush();
			byteCounter += 2 + 1 + 4;
			done = true;
		} catch (IOException e) {
		}
		return done;
	}

	public boolean sendByte(int method, int value) {
		boolean done = false;
		try {
			dataOut.writeShort(method);
			dataOut.writeByte(BTMSGTYPEBYTE);
			dataOut.writeByte(value);
			dataOut.flush();
			byteCounter += 2 + 1 + 1;
			done = true;
		} catch (IOException e) {
		}
		return done;
	}

	public boolean sendInts(int method, int len, int values[]) {
		boolean done = false;
		try {
			dataOut.writeShort(method);
			dataOut.writeByte(BTMSGTYPEARRAYINT);
			dataOut.writeByte(len);
			for (int i = 0; i < len; i++)
				dataOut.writeInt(values[i]);
			dataOut.flush();
			byteCounter += 2 + 1 + 1 + 4 * len;
			done = true;
		} catch (IOException e) {
		}
		return done;
	}

	/**
	 * Calc the state of the battery charge.
	 * 
	 * @param mV
	 *            Current reading
	 * @return current charge level 0-100%
	 */
	private int calcBatteryLevel(int mV) {
		int val = ((mV - batteryLow) * 100 + batteryRange / 2) / batteryRange;
		if (val < 0)
			val = 0;
		if (val > 100)
			val = 100;
		return val;
	}

	/**
	 * Update the battery status display. Low battery state is shown by flashing
	 * the icon.
	 */
	private void updateBattery() {
		// Handle the battery display.
		int mV = Battery.getVoltageMilliVolt();
		batteryVoltage = Battery.getVoltage();
		if (mV <= batteryLow)
			lowVoltage = true;
		else if (mV >= batteryOk)
			lowVoltage = false;
		if (lowVoltage)
			batteryLevel = 0;

		else
			batteryLevel = calcBatteryLevel(mV);
	}

	/**
	 * Initialize the battery state
	 * 
	 * @param low
	 *            Low voltage limit
	 * @param ok
	 *            Voltage that is OK (not low).
	 * @param high
	 *            100% charged level.
	 */
	private void initBattery(int low, int ok, int high) {
		batteryLow = low;
		batteryOk = ok;
		batteryRange = high - low;
	}

	public void setupRobot(int motorLPos, int motorRPos, int motorFlags,
			int initSpeed, int minDistance, int touchPos, int colorPos,
			int sonicleftPos, int sonicrightPos, int sonicfrontPos) {
		nxtRobot = new TwoWheelRobot(getMotorPort(motorLPos),
				getMotorPort(motorRPos), initSpeed, motorFlags);

		if (touchPos != 0)
			touch = new TouchSensor(getSensorPort(touchPos));

		if (colorPos != 0)
			color = new ColorSensor(getSensorPort(colorPos));
		if (sonicfrontPos != 0) {
			sonicFront = new UltrasonicSensor(getSensorPort(sonicfrontPos));
			sonicFront.continuous();
		} else {
			if (sonicleftPos != 0) {
				sonicLeft = new UltrasonicSensor(getSensorPort(sonicleftPos));
				sonicLeft.continuous();
			}
			if (sonicrightPos != 0) {
				sonicRight = new UltrasonicSensor(getSensorPort(sonicrightPos));
				sonicRight.continuous();
			}
		}
		strConfiguration = motorLPos + " " + motorRPos + " -- " + sonicleftPos
				+ sonicrightPos + sonicfrontPos + touchPos + colorPos;

		if (Battery.isRechargeable())
			initBattery(RECHARGE_MIN, RECHARGE_OK, RECHARGE_MAX);
		else
			initBattery(STD_MIN, STD_OK, STD_MAX);

		safeMinDistance = minDistance;
		configured = true;
	}

	private boolean sendValues(float newVoltage, int newBatteryLevel,
			int newDistanceLeft, int newDistanceRight, int newDistanceFront,
			int newIsTouch, int[] newColors, int newspeed, int newLeftTacho,
			int newRightTacho) {

		boolean result = true;

		if (sentVoltage != newVoltage) { 
			sentVoltage = newVoltage;
			result &= sendFloat(NXTVOLTAGE, sentVoltage);
		}
		if (sentBatteryLevel != newBatteryLevel) { 
			sentBatteryLevel = newBatteryLevel;
			result &= sendByte(NXTBATTERY, sentBatteryLevel);
		}

		if (result && distanceFront != newDistanceFront) {
			distanceFront = newDistanceFront;
			result &= sendByte(NXTSENSORSONICFRONT, distanceFront);
		}

		if (result && distanceLeft != newDistanceLeft) {
			distanceLeft = newDistanceLeft;
			result &= sendInt(NXTSENSORSONICLEFT, distanceLeft);
		}
		if (result && distanceRight != newDistanceRight) {
			distanceRight = newDistanceRight;
			result &= sendInt(NXTSENSORSONICRIGHT, distanceRight);
		}
		if (result && isTouch != newIsTouch) {
			isTouch = newIsTouch;
			result &= sendInt(NXTSENSORTOUCH, newIsTouch);
		}
		if (result
				&& (sentColors[0] != newColors[0]
						|| sentColors[1] != newColors[1] || sentColors[2] != newColors[2])) {
			sentColors[0] = newColors[0];
			sentColors[1] = newColors[1];
			sentColors[2] = newColors[2];
			result &= sendInts(NXTSENSORCOLOR, 3, sentColors);
		}
		if (result && sentRobotState != robotState) {
			sentRobotState = robotState;
			result &= sendByte(NXTCONTROL, robotState);
		}
		if (result && speed != newspeed) {
			speed = newspeed;
			result &= sendInt(NXTSPEED, speed);
		}
		if (result && Math.abs(leftTacho - newLeftTacho) > 5) { // Reduce Noise
			leftTacho = newLeftTacho;
			result &= sendInt(NXTLTACHO, newLeftTacho);
		}
		if (result && Math.abs(rightTacho - newRightTacho) > 5) { // Reduce
																	// Noise
			rightTacho = newRightTacho;
			result &= sendInt(NXTRTACHO, newRightTacho);
		}
		return !result;
	}

	private void displayInfo() {
		LCD.drawString("Bat:" + batteryLevel + "%   ", 0, 0);
		if (sonicFront != null) {
			LCD.drawString("DF:" + distanceFront + "  ", 0, 1);
		} else {
			LCD.drawString("DL:" + distanceLeft + "  ", 0, 1);
			LCD.drawString("DR:" + distanceRight + "  ", 0, 2);
		}
		LCD.drawString("T:" + isTouch, 0, 3);
		LCD.drawString("State:"
				+ (robotState == SELF ? "Self  " + selfReason : "Remote"), 0, 4);
		LCD.drawString(bti.getLastCommand() + "      ", 0, 5);
		LCD.drawString("Count: " + byteCounter, 0, 6);
	}

	private boolean setupConnections() {
		boolean done = false;

		LCD.drawString("waiting for BT", 0, 1);
		while (!Button.ESCAPE.isDown() && !done) {
			connection = Bluetooth.waitForConnection(0, NXTConnection.PACKET);
			if (connection != null)
				done = true;
		}
		if (done) {

			// waitForConnection();

			LCD.drawString("Connected BT", 0, 1);

			dataOut = connection.openDataOutputStream();
			DataInputStream dataIn = connection.openDataInputStream();

			bti = new NXTInput(dataIn, this);

			// bti = new NXTInput(dataIn, nxtRobot, this);
			LCD.drawString("Wait for Config", 0, 1);

			done = false;
			while (!Button.ESCAPE.isDown() && !done) {
				if (configured)
					done = true;
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					done = true;
					e.printStackTrace();
				}
			}
		}
		return configured;
	}

	public void go() {

		//
		if (setupConnections()) {
			bti.setTwr(nxtRobot);
			LCD.clear();
			boolean done = false;
			while (!Button.ESCAPE.isDown() && !done) {
				done = sendValues(batteryVoltage, batteryLevel,
						getDistanceSonicLeft(), getDistanceSonicRight(),
						getDistanceSonicFront(), getTouchPressed(),
						getColors(), nxtRobot.getSpeed(),
						nxtRobot.getLeftTachoCount(),
						nxtRobot.getRightTachoCount());
				updateBattery();
				displayInfo();
				validateConditions();
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					done = true;
					e.printStackTrace();
				}
			}
		}
		bti.goAway();
	}

	private int[] getColors() {
		if (color != null)
			return new int[] { color.getColor().getRed(),
					color.getColor().getGreen(), color.getColor().getBlue() };
		else
			return new int[] { 0, 0, 0 };

	}

	private int getTouchPressed() {
		if (touch != null) {
			return (touch.isPressed() ? 1 : 0);
		} else
			return 0;
	}

	private int getDistanceSonic(UltrasonicSensor sensor) {
		if (sensor != null)
			return sensor.getDistance();
		else
			return -1;
	}

	private int getDistanceSonicFront() {
		return getDistanceSonic(sonicFront);
	}

	private int getDistanceSonicLeft() {
		return getDistanceSonic(sonicLeft);
	}

	private int getDistanceSonicRight() {
		return getDistanceSonic(sonicRight);
	}

	private void validateConditions() {

		if (batteryLevel < SAFEMINBATTERYPERCENT) {
			robotState = SELF;
			selfReason = 1;
			batteryLowLevelAction();
			return;
		}

		if (sonicFront != null) {
			// 1 Front sensor configuration
			if (distanceFront < safeMinDistance) {
				robotState = SELF;
				selfReason = 2;
				lowDistanceBothAction();
				return;
			}

		} else {
			// 2 Sensors - Left and Right

			if (distanceLeft < safeMinDistance + 5
					&& distanceRight < safeMinDistance + 5) {
				robotState = SELF;
				selfReason = 2;
				lowDistanceBothAction();
				return;
			}

			if (distanceLeft < safeMinDistance) {
				robotState = SELF;
				selfReason = 3;
				lowDistanceLeftAction();
				return;
			}

			if (distanceRight < safeMinDistance) {
				robotState = SELF;
				selfReason = 4;
				lowDistanceRightAction();
				return;
			}
		}
		if (isTouch == 1) {
			robotState = SELF;
			selfReason = 5;
			touchSensorAction();
			return;
		}
		selfReason = 0;
		robotState = REMOTE;
	}

	private void touchSensorAction() {
		nxtRobot.backward();
		try {
			Thread.sleep(avoidMoveDuration); // Depends on Speed
		} catch (InterruptedException e) {
		}
	}

	private void lowDistanceBothAction() {
		if (nxtRobot.isMoving()) {
			nxtRobot.stop();
			nxtRobot.pressHorn();
		} else {

			nxtRobot.backward();
			try {
				Thread.sleep(avoidMoveDuration); // Depends on Speed
			} catch (InterruptedException e) {
			}
			nxtRobot.stop();
		}
	}

	private void lowDistanceRightAction() {
		if (nxtRobot.isMoving()) {
			nxtRobot.stop();
			nxtRobot.pressHorn();
		} else {

			nxtRobot.backleft();
			try {
				Thread.sleep(avoidMoveDuration); // Depends on Speed
			} catch (InterruptedException e) {
			}
			nxtRobot.stop();
		}
	}

	private void lowDistanceLeftAction() {
		if (nxtRobot.isMoving()) {
			nxtRobot.stop();
			nxtRobot.pressHorn();
		} else {

			nxtRobot.backright();
			try {
				Thread.sleep(avoidMoveDuration); // Depends on Speed
			} catch (InterruptedException e) {
			}
			nxtRobot.stop();
		}

	}

	private void batteryLowLevelAction() {
	}
}
