package net.jpralves.lejos;

import java.io.DataInputStream;
import java.io.IOException;

import lejos.nxt.LCD;

/**
 * NXTInput class - Handles input from BT
 * 
 * @author Joao Alves (jpralves@gmail.com)
 * @version 1.0
 */

public class NXTInput extends Thread {

	private static DataInputStream dis = null;
	private static boolean isShutdown = false;
	private TwoWheelRobot twr = null;
	private NXTRobot slave;
	private String lastCommand = "";

	static final int CMDSETUPROBOT = 1;
	static final int CMDSETSPEED = 2;
	static final int CMDGOLEFT = 3;
	static final int CMDGORIGHT = 4;
	static final int CMDGOFORWARD = 5;
	static final int CMDSTOP = 6;

	public String getLastCommand() {
		return lastCommand;
	}

	public NXTInput(DataInputStream in, TwoWheelRobot twr, NXTRobot slave) {
		NXTInput.dis = in;
		this.twr = twr;
		this.slave = slave;
		this.setDaemon(true);
		this.start();
	}

	public NXTInput(DataInputStream in, NXTRobot slave) {
		NXTInput.dis = in;
		this.slave = slave;
		LCD.drawString("NXTInput Constr.", 0, 2);
		try {
			Thread.sleep(100);
		} catch (InterruptedException e) {
		}
		this.setDaemon(true);
		this.start();
	}

	public void setTwr(TwoWheelRobot twr) {
		this.twr = twr;
	}

	public void goAway() {
		isShutdown = true;
		try {
			Thread.sleep(100);
		} catch (InterruptedException e) {
		}
	}

	public void fromStream(DataInputStream stream) {
		try {

			int command = stream.readByte();
			LCD.drawString("command: "+command+"   ", 0, 3);
			switch (command) {
			case CMDSETUPROBOT: // get Init parameters:
				// read 8 Bytes signature: An2NXT10
				byte[] bsig = new byte[8];
				stream.read(bsig, 0, 8);
				String signature = new String(bsig);
				LCD.drawString("sig: !"+signature+"!   ", 0, 4);
				int motorLPos = stream.readByte();
				int motorRPos = stream.readByte();
				int motorFlags = stream.readByte();
				int initSpeed = stream.readInt();
				int minDistance = stream.readInt();
				int touchPos = stream.readByte();
				int colorPos = stream.readByte();
				int sonicLeftPos = stream.readByte();
				int sonicRightPos = stream.readByte();
				int sonicFrontPos = stream.readByte();
				byte[] bends = new byte[3];
				// EOS - End of Setup
				stream.read(bends, 0, 3);
				String endsignature = new String(bends);
				//LCD.drawString("sig: !"+endsignature+"!   ", 0, 4);				
				lastCommand = "Setup";

				if (signature.equals("An2NXT10") && endsignature.equals("EOT")) {
					// Message OK. Can initialize robot.
					slave.setupRobot(motorLPos, motorRPos, motorFlags,
							initSpeed, minDistance, touchPos, colorPos, sonicLeftPos,
							sonicRightPos, sonicFrontPos);}
					else {
						LCD.drawString("COMANDO INVALIDO", 0, 5);
					}
				
				break;

			case CMDSETSPEED:
				int speed = stream.readInt();
				if (twr != null)
					twr.setSpeed(speed);
				lastCommand = "Set Speed";
				break;
			case CMDGOLEFT:
				if (slave.isRobotState() == NXTRobot.REMOTE) {
					if (twr != null)
						twr.left();
					lastCommand = "Left";
				}
				break;
			case CMDGORIGHT:
				if (slave.isRobotState() == NXTRobot.REMOTE) {
					if (twr != null)
						twr.right();
					lastCommand = "Right";
				}
				break;
			case CMDGOFORWARD:
				if (slave.isRobotState() == NXTRobot.REMOTE) {
					if (twr != null)
						twr.forward();
					lastCommand = "Forward";
				}
				break;
			case CMDSTOP:
				if (slave.isRobotState() == NXTRobot.REMOTE) {
					if (twr != null)
						twr.stop();
					lastCommand = "Stop";
				}
				break;
			}
		} catch (IOException e) {
		}
	}

	public void run() {

		while (!isShutdown) {
			fromStream(dis);
		}
		try {
			dis.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
