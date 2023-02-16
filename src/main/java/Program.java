
import edu.wpi.first.networktables.DoubleSubscriber;
import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.networktables.NetworkTablesJNI;
import edu.wpi.first.util.CombinedRuntimeLoader;

import java.io.IOException;

import edu.wpi.first.cscore.CameraServerJNI;
import edu.wpi.first.math.WPIMathJNI;
import edu.wpi.first.util.WPIUtilJNI;

/**
 * Program
 */
public class Program
{
    public static void main(String[] args) throws Exception
    {
        NetworkTablesJNI.Helper.setExtractOnStaticLoad(false);
        WPIUtilJNI.Helper.setExtractOnStaticLoad(false);
        WPIMathJNI.Helper.setExtractOnStaticLoad(false);
        CameraServerJNI.Helper.setExtractOnStaticLoad(false);

        CombinedRuntimeLoader.loadLibraries(Program.class, "wpiutiljni", "wpimathjni", "ntcorejni", "cscorejnicvstatic");

        NetworkTableInstance inst = NetworkTableInstance.getDefault();
        NetworkTable table = inst.getTable("SmartDashboard");
        DoubleSubscriber nt_lift = table.getDoubleTopic("Lift Height").subscribe(0.0);
        DoubleSubscriber nt_arm = table.getDoubleTopic("Arm Angle").subscribe(0.0);

        inst.startClient4("example client");
        inst.setServer("localhost"); // where TEAM=190, 294, etc, or use inst.setServer("hostname") or similar
        inst.startDSClient(); // recommended if running on DS computer; this gets the robot IP from the DS

        while (true)
        {
            Thread.sleep(1000);

            double lift = nt_lift.get();
            double arm = nt_arm.get();
            System.out.println("Lift: " + lift + " Arm: " + arm);
        }        
    }
}
