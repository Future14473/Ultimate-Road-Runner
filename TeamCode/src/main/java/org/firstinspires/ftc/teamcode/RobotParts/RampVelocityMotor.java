package org.firstinspires.ftc.teamcode.RobotParts;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;

public class RampVelocityMotor {
    DcMotorEx self;
    double myVelocity = 0;

    public RampVelocityMotor(DcMotorEx me){
        self = me;
        self.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        self.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
    }

    public void setVelocity(double vel){
        myVelocity += (vel - myVelocity)/1.3;
        self.setVelocity(myVelocity);
    }
}
