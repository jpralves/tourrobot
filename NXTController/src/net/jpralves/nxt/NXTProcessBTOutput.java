package net.jpralves.nxt;

import java.io.DataOutputStream;
import java.io.IOException;

import android.util.Log;

/**
 * NXTProcessBTOutput Object.
 * 
 * <P>
 * Sends commands to the NXT
 * 
 * @author Joao Alves
 * @version 1.0
 */
public class NXTProcessBTOutput extends Thread {

	private static final String TAG = NXTProcessBTOutput.class.getSimpleName();

	public static final int CMDSETUPROBOT = 1;
	public static final int CMDSETSPEED = 2;
	public static final int CMDGOLEFT = 3;
	public static final int CMDGORIGHT = 4;
	public static final int CMDGOFORWARD = 5;
	public static final int CMDSTOP = 6;

	private DataOutputStream dos;

	private NXTControllerApp app;

	/**
	 * sends STOP command
	 */
	public void primitiveStop() {
		// sendCommand(0, 0);
		sendAtomicCommand(CMDSTOP);
	}

	/**
	 * sends FORWARD command
	 */
	public void primitiveForward() {
		// sendCommand(1, 1);
		sendAtomicCommand(CMDGOFORWARD);
	}

	/**
	 * sends Rotate LEFT command
	 */
	public void primitiveStartLeft() {
		// sendCommand(0, 1);
		sendAtomicCommand(CMDGOLEFT);
	}

	/**
	 * sends Rotate RIGHT command
	 */
	public void primitiveStartRight() {
		// sendCommand(1, 0);
		sendAtomicCommand(CMDGORIGHT);
	}

	/**
	 * sends an atomic command (1 byte command)
	 * 
	 * @param cmd
	 *            the command
	 */
	public void sendAtomicCommand(int cmd) {
		try {
			dos.writeByte(cmd);
			dos.flush();
			app.addTableLong("nxtslave.outbytes", 1);
		} catch (IOException e) {
			if (BuildConfig.DEBUG)
				Log.e(TAG, "sendAtomicCommand - Unable to Write", e);
		}
	}

	/**
	 * Changes the speed of NXT Motors
	 * 
	 * @param newspeed
	 *            the new speed
	 */
	public void sendSpeed(int newspeed) {
		try {
			dos.writeByte(CMDSETSPEED); // Command Type - motor Action
			dos.writeInt(newspeed);
			dos.flush();
			app.addTableLong("nxtslave.outbytes", 1 + 4);
		} catch (IOException e) {
			if (BuildConfig.DEBUG)
				Log.e(TAG, "sendSpeed - Unable to Write", e);
		}
	}

	public NXTProcessBTOutput(NXTControllerApp app, DataOutputStream out) {
		this.dos = out;
		this.app = app;
		this.setDaemon(true);
		this.start();
	}

	public void run() {
	}

	/**
	 * Sends special command to initialize the NXT unit with correct ports
	 * 
	 * @param motorLPos
	 *            port of Left Motor (1,2,3)
	 * @param motorRPos
	 *            port of Right Motor (1,2,3)
	 * @param motorFlags
	 *            motor flags
	 * @param initSpeed
	 *            initial Speed
	 * @param touchPos
	 *            port of touch sensor (1,2,3,4)
	 * @param colorPos
	 *            port of color sensor (1,2,3,4)
	 * @param sonicleftPos
	 *            port of sonic-left sensor (1,2,3,4)
	 * @param sonicrightPos
	 *            port of sonic-right sensor (1,2,3,4)
	 */
	public void setupNXT(int motorLPos, int motorRPos, int motorFlags, int initSpeed,
			int minDistance, int touchPos, int colorPos, int sonicleftPos, int sonicrightPos,
			int sonicfrontPos) {
		// Send the settings of robot
		String signature = "An2NXT10";
		String endsignature = "EOT";
		try {
			dos.writeByte(CMDSETUPROBOT);
			dos.write(signature.getBytes(), 0, signature.length());
			dos.writeByte(motorLPos);
			dos.writeByte(motorRPos);
			dos.writeByte(motorFlags);
			dos.writeInt(initSpeed);
			dos.writeInt(minDistance);
			dos.writeByte(touchPos);
			dos.writeByte(colorPos);
			dos.writeByte(sonicleftPos);
			dos.writeByte(sonicrightPos);
			dos.writeByte(sonicfrontPos);
			dos.write(endsignature.getBytes(), 0, endsignature.length());
			// EOS - End of Setup
			dos.flush();
			app.addTableLong("nxtslave.outbytes", 1 + 8 + 1 + 1 + 1 + 4 + 4 + 1 + 1 + 1 + 1 + 1 + 3);

		} catch (IOException e) {
			if (BuildConfig.DEBUG)
				Log.e(TAG, "setupNXT - Unable to Write", e);
		}
	}

}
