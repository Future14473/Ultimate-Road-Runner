package org.firstinspires.ftc.teamcode.RobotParts;

public enum MotorPos {
    frontLeft(0),  frontRight(1), backLeft(2), backRight(3);

    int index;

    MotorPos(int index){
        this.index = index;
    }
}
