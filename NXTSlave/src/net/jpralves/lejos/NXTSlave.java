package net.jpralves.lejos;

/**
 * NXTSlave class - Main Class entry point for program
 * 
 * @author Joao Alves (jpralves@gmail.com)
 * @version 1.0
 */

public class NXTSlave {

	public static void main(String[] args) {
		NXTRobot robot = new NXTRobot();
		robot.go();
	}
}
