package net.jpralves.nxt;

import java.io.DataInputStream;
import java.io.IOException;

import android.util.Log;

/**
 * NXTProcessBTInput Object.
 * 
 * <P>
 * Processes input comming from NXT
 * 
 * @author Joao Alves
 * @version 1.0
 */
public class NXTProcessBTInput extends Thread {

	private static final String TAG = NXTProcessBTInput.class.getSimpleName();

	public static final int NXTVOLTAGE = 1;
	public static final int NXTSPEED = 2;
	public static final int NXTSENSORSONICLEFT = 3;
	public static final int NXTSENSORSONICRIGHT = 4;
	public static final int NXTSENSORSONICFRONT = 5;
	public static final int NXTSENSORTOUCH = 6;
	public static final int NXTSENSORCOLOR = 7;
	public static final int NXTCONTROL = 8;
	public static final int NXTLTACHO = 9;
	public static final int NXTRTACHO = 10;
	public static final int NXTBATTERY = 11;

	static final short BTMSGTYPEFLOAT = 1;
	static final short BTMSGTYPEBYTE = 2;
	static final short BTMSGTYPEINT = 3;
	static final short BTMSGTYPEARRAYINT = 4;

	private NXTControllerApp app = null;
	private static DataInputStream dis = null;
	private static boolean isShutdown = false;

	private class Msg {
		public int method;
		public byte len;
		public byte typearg;
		public int[] args;
		public float argfloat;
		public int argint;
		public byte argbyte;
	}

	public NXTProcessBTInput(NXTControllerApp app, DataInputStream in) {
		NXTProcessBTInput.dis = in;
		this.app = app;
		this.setDaemon(true);
		this.start();
	}

	/**
	 * Handles the messages got from NXT
	 * <p>
	 * registers information in NXTControllerApp for later retrieval
	 * 
	 * @param msg
	 *            the message
	 */
	private void handleBTMessage(Msg msg) {
		// put your methods here
		switch (msg.method) {
		case NXTVOLTAGE:
			if (BuildConfig.DEBUG)
				Log.i(TAG, "BTin: nxt.voltage = " + msg.argfloat);
			app.setTableFloat("nxt.voltage", msg.argfloat);
			break;
		case NXTSPEED:
			if (BuildConfig.DEBUG)
				Log.i(TAG, "BTin: nxt.speed = " + msg.argint);
			app.setTableInt("nxt.speed", msg.argint);
			break;
		case NXTSENSORSONICLEFT:
			if (BuildConfig.DEBUG)
				Log.i(TAG, "BTin: nxt.sensor.ultrasonic-left = " + msg.argint);
			app.setTableInt("nxt.sensor.ultrasonic-left", msg.argint);
			break;
		case NXTSENSORSONICRIGHT:
			if (BuildConfig.DEBUG)
				Log.i(TAG, "BTin: nxt.sensor.ultrasonic-right = " + msg.argint);
			app.setTableInt("nxt.sensor.ultrasonic-right", msg.argint);
			break;
		case NXTSENSORSONICFRONT:
			if (BuildConfig.DEBUG)
				Log.i(TAG, "BTin: nxt.sensor.ultrasonic-front = " + msg.argint);
			app.setTableInt("nxt.sensor.ultrasonic-front", msg.argint);
			break;
		case NXTSENSORTOUCH:
			if (BuildConfig.DEBUG)
				Log.i(TAG, "BTin: nxt.sensor.touch = " + msg.argbyte);
			app.setTableInt("nxt.sensor.touch", msg.argbyte);
			break;
		case NXTSENSORCOLOR:
			if (BuildConfig.DEBUG)
				Log.i(TAG, "BTin: nxt.sensor.color = " + msg.args[0] + ", " + msg.args[1] + ", "
						+ msg.args[2]);
			app.setTableInt("nxt.sensor.color.red", msg.args[0]);
			app.setTableInt("nxt.sensor.color.green", msg.args[1]);
			app.setTableInt("nxt.sensor.color.blue", msg.args[2]);
			break;
		case NXTCONTROL:
			if (BuildConfig.DEBUG)
				Log.i(TAG, "BTin: nxt.control = " + msg.argbyte);
			app.setTableInt("nxt.control", msg.argbyte);
			break;
		case NXTLTACHO:
			if (BuildConfig.DEBUG)
				Log.i(TAG, "BTin: nxt.motor.lefttacho = " + msg.argint);
			app.setTableInt("nxt.motor.lefttacho", msg.argint);
			break;
		case NXTRTACHO:
			if (BuildConfig.DEBUG)
				Log.i(TAG, "BTin: nxt.motor.righttacho = " + msg.argint);
			app.setTableInt("nxt.motor.righttacho", msg.argint);
			break;
		case NXTBATTERY:
			if (BuildConfig.DEBUG)
				Log.i(TAG, "BTin: nxt.battery = " + msg.argbyte);
			app.setTableInt("nxt.battery", msg.argbyte);
			break;
		default:
			Log.e(TAG, "Invalid value - method=" + msg.method);
		}
	}

	/**
	 * formats message from BT stream
	 * 
	 * @param stream
	 *            input stream
	 * @return the message
	 */
	private Msg fromStream(DataInputStream stream) {
		Msg msg = new Msg();
		try {
			msg.method = stream.readShort();
			msg.typearg = stream.readByte();
			app.addTableLong("nxtslave.inbytes", 2 + 1);
			switch (msg.typearg) {
			case BTMSGTYPEFLOAT:
				msg.argfloat = stream.readFloat();
				app.addTableLong("nxtslave.inbytes", 4);
				break;
			case BTMSGTYPEBYTE:
				msg.argbyte = stream.readByte();
				app.addTableLong("nxtslave.inbytes", 1);
				break;
			case BTMSGTYPEINT:
				msg.argint = stream.readInt();
				app.addTableLong("nxtslave.inbytes", 4);
				break;
			case BTMSGTYPEARRAYINT:
				msg.len = stream.readByte();
				if (msg.len > 0) {
					msg.args = new int[msg.len];
					for (int i = 0; i < msg.len; i++) {
						msg.args[i] = stream.readInt();
					}
					app.addTableLong("nxtslave.inbytes", 1 + 4 * msg.len);
				}
				break;
			default:
				Log.e(TAG, "Invalid BTIN message type = " + msg.typearg);
			}

		} catch (IOException e) {
			if (BuildConfig.DEBUG)
				Log.e(TAG, e.getMessage(), e);
		}
		return msg;
	}

	/**
	 * while running decodes message from stream and handles it
	 * 
	 */
	public void run() {
		while (!isShutdown) {
			Msg msg = fromStream(dis);
			handleBTMessage(msg);
		}
	}
}
