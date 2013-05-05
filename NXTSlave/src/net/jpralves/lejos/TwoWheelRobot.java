package net.jpralves.lejos;

import java.io.File;

import lejos.nxt.NXTRegulatedMotor;
import lejos.nxt.Sound;

/**
 * Two Wheel Robot - exposes primitives to handle this robot
 * 
 * @author Joao Alves (jpralves@gmail.com)
 * @version 1.0
 */
public class TwoWheelRobot {

	private static final long TACHOTIMEOUT = 500;
	/**
	 * left motor object
	 */
	private NXTRegulatedMotor leftMotor;
	/**
	 * right motor object
	 */
	private NXTRegulatedMotor rightMotor;

	private long tachoCounter = 0;
	private int motorDirection;
	private int speed = 0;

	public static final int FLOATFLAG = 0x01;
	public static final int DIRECTION = 0x02;

	/**
	 * Two Wheel Robot constructor
	 * 
	 * @param left
	 *            left motor object
	 * @param right
	 *            right motor object
	 * @param speed
	 *            speed
	 * @param flags
	 * 			 Special flags of motors:
	 * 				0x01 - FLOAT
	 * 				0x02 - Direction
	 */
	public TwoWheelRobot(NXTRegulatedMotor left, NXTRegulatedMotor right,
			int speed, int flags) {
		int direction = 0;
		this.leftMotor = left;
		this.rightMotor = right;
		if ((flags & FLOATFLAG) != 0) {
			leftMotor.flt();
			rightMotor.flt();
		}
		if ((flags & DIRECTION) != 0) {
			direction = 1;
			leftMotor.flt();
			rightMotor.flt();
		}
		
		setSpeed(speed);
		this.motorDirection = direction;
	}

	public void changemotors(int motorL, int motorR) {
		leftMotor.setSpeed(speed * motorL);
		rightMotor.setSpeed(speed * motorR);
	}

	/**
	 * puts both motors running forward
	 */
	public void forward() {
		if (motorDirection == 0) {
			leftMotor.forward();
			rightMotor.forward();
		} else {
			leftMotor.backward();
			rightMotor.backward();
		}
		// Delay.msDelay(100);
		// stop();
	}

	/**
	 * puts both motors running backward
	 */
	public void backward() {
		if (motorDirection == 0) {
			leftMotor.backward();
			rightMotor.backward();
		} else {
			leftMotor.forward();
			rightMotor.forward();
		}
	}

	/**
	 * puts the right motor running forward and stops left motor
	 */
	public void left() {
		if (motorDirection == 0) {
			rightMotor.forward();
			leftMotor.stop();
		} else {
			rightMotor.backward();
			leftMotor.stop();
		}

		// Delay.msDelay(100);
		// stop();
	}

	/**
	 * puts the left motor running forward and stops right motor
	 */
	public void right() {
		if (motorDirection == 0) {
			leftMotor.forward();
			rightMotor.stop();
		} else {
			leftMotor.backward();
			rightMotor.stop();
		}
		// Delay.msDelay(100);
		// stop();
	}

	/**
	 * puts the left motor running in reverse direction and stops right motor
	 */
	public void backleft() {
		if (motorDirection == 0) {
			rightMotor.stop();
			leftMotor.backward();
		} else {
			rightMotor.stop();
			leftMotor.forward();

		}
	}

	/**
	 * puts the right motor running in reverse direction and stops left motor
	 */
	public void backright() {
		if (motorDirection == 0) {
			leftMotor.stop();
			rightMotor.backward();
		} else {
			leftMotor.stop();
			rightMotor.forward();
		}
	}

	/**
	 * stops both motors
	 */
	public void stop() {
		leftMotor.stop();
		rightMotor.stop();
	}

	/**
	 * gets the tach counter of the left motor
	 * 
	 * @return the left motor tach counter
	 */
	public int getLeftTachoCount() {
		return Math.abs(leftMotor.getTachoCount());
	}

	/**
	 * gets the tach counter of the right motor
	 * 
	 * @return the right motor tach counter
	 */
	public int getRightTachoCount() {
		return Math.abs(rightMotor.getTachoCount());
	}

	/**
	 * plays the horn.wav in the speaker
	 */
	public void pressHorn() {
		File f = new File("horn.wav");
		Sound.playSample(f, 100);
	}

	public int getSpeed() {
		return speed;
	}

	/**
	 * Changes the speed of both motors
	 * 
	 * @param speed
	 *            new speed
	 */
	public void setSpeed(int speed) {
		this.speed = speed;
		leftMotor.setSpeed(speed);
		rightMotor.setSpeed(speed);
	}

	/**
	 * sleeps for a period of time
	 * 
	 * @param time
	 *            sleep time in milliseconds
	 */
	public void sleep(long time) {
		tachoCounter += time;
		if (tachoCounter > TACHOTIMEOUT) {
			tachoCounter = 0;
		}
		try {
			Thread.sleep(time);
		} catch (InterruptedException e) {
		}
	}

	public boolean isMoving() {
		return leftMotor.isMoving() || rightMotor.isMoving();
	}
}
