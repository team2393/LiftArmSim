
import edu.wpi.first.networktables.BooleanSubscriber;
import edu.wpi.first.networktables.DoubleSubscriber;
import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.networktables.NetworkTablesJNI;
import edu.wpi.first.util.CombinedRuntimeLoader;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.BasicStroke;
import java.awt.Rectangle;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import edu.wpi.first.cscore.CameraServerJNI;
import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.WPIMathJNI;
import edu.wpi.first.util.WPIUtilJNI;

/** Basic display if lift and arm
 *
 *  Listens to network table for lift and arm settings,
 *  be it simulated or real robot,
 *  and provides simple representation.
 */
public class LiftArmDisplay implements Runnable
{
    private static class RobotDisplay extends JPanel
    {
        private double lift_extension = 0.0, arm_angle = -90.0;
        private boolean arm_extended = false;

        public void set(double lift_extension, double arm_angle,  boolean arm_extended)
        {
            this.lift_extension = lift_extension;
            this.arm_angle = arm_angle;
            this.arm_extended = arm_extended;
            repaint();
        }

        private static double rotate_x(double x, double y, double angle)
        {
            double rad = Math.toRadians(angle);
            return x * Math.cos(rad) - y * Math.sin(rad);
        }

        private static double rotate_y(double x, double y, double angle)
        {
            double rad = Math.toRadians(angle);
            return x * Math.sin(rad) + y * Math.cos(rad);
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            Graphics2D g2 = (Graphics2D) g;
            Rectangle bounds = getBounds();

            int base_x = bounds.width / 6;
            int base_y = bounds.height;

            // Scaling from robot to screen
            double pixel_per_meter = Math.min(bounds.width, bounds.height) / 3.0;

            double lift_angle = Math.toRadians(60.0);
            double lift_length = 1.5 + lift_extension;
            int lift_top_x = base_x + (int) (lift_length * Math.cos(lift_angle) * pixel_per_meter);
            int lift_top_y = base_y - (int) (lift_length * Math.sin(lift_angle) * pixel_per_meter);

            g2.setColor(Color.BLUE);
            g2.setStroke(new BasicStroke(10));
            g.drawLine(base_x, base_y, lift_top_x, lift_top_y);

            double arm_length = arm_extended ? 0.8 : 0.4;
            int arm_x = lift_top_x + (int) (Math.cos(Math.toRadians(arm_angle)) * arm_length * pixel_per_meter);
            int arm_y = lift_top_y - (int) (Math.sin(Math.toRadians(arm_angle)) * arm_length * pixel_per_meter);

            g2.setColor(Color.RED);
            g.drawLine(lift_top_x, lift_top_y, arm_x, arm_y);

            double grab_size = 0.2 * pixel_per_meter;
            g2.setColor(Color.YELLOW);
            g.drawLine(arm_x, arm_y,
                       (int) (arm_x + rotate_x(grab_size, -grab_size/2, -arm_angle)),
                       (int) (arm_y + rotate_y(grab_size, -grab_size/2, -arm_angle)));
            g.drawLine(arm_x, arm_y,
                       (int) (arm_x + rotate_x(grab_size, +grab_size/2, -arm_angle)),
                       (int) (arm_y + rotate_y(grab_size, +grab_size/2, -arm_angle)));
        }
    };

    private static JLabel info;
    private static RobotDisplay display;

    @Override
    public void run()
    {
        try
        {
            // Network table setup, see
            // https://docs.wpilib.org/en/stable/docs/software/networktables/client-side-program.html
            NetworkTablesJNI.Helper.setExtractOnStaticLoad(false);
            WPIUtilJNI.Helper.setExtractOnStaticLoad(false);
            WPIMathJNI.Helper.setExtractOnStaticLoad(false);
            CameraServerJNI.Helper.setExtractOnStaticLoad(false);

            CombinedRuntimeLoader.loadLibraries(LiftArmDisplay.class, "wpiutiljni", "wpimathjni", "ntcorejni", "cscorejnicvstatic");

            NetworkTableInstance inst = NetworkTableInstance.getDefault();
            NetworkTable table = inst.getTable("SmartDashboard");
            DoubleSubscriber nt_lift = table.getDoubleTopic("Lift Height").subscribe(0.0);
            DoubleSubscriber nt_arm = table.getDoubleTopic("Arm Angle").subscribe(0.0);
            BooleanSubscriber nt_ext = table.getBooleanTopic("Arm Extended").subscribe(false);

            inst.startClient4("example client");
            inst.setServer("localhost"); // where TEAM=190, 294, etc, or use inst.setServer("hostname") or similar
            inst.startDSClient(); // recommended if running on DS computer; this gets the robot IP from the DS

            while (true)
            {
                // Update display with readings from NT at 10 Hz
                Thread.sleep(100);
                double lift = nt_lift.get();
                double arm = nt_arm.get();
                boolean extended = nt_ext.get();
                // System.out.println("Lift: " + lift + " Arm: " + arm);

                // Must update on UI thread
                SwingUtilities.invokeAndWait(() ->
                {
                    info.setText(String.format("Lift: %5.2f m, Arm %s at %4.1f deg",
                                               lift,
                                               extended ? "out" : "in ",
                                               arm));
                    display.set(lift, arm, extended);
                });
            }        
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
    }


    public static void main(String[] args) throws Exception
    {

        final JFrame frame = new JFrame("Lift/Arm Sim");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        display = new RobotDisplay();
        frame.getContentPane().add(display, BorderLayout.CENTER);

        info = new JLabel("XXXXXXXXXXXXXXXXXXXXX");
        frame.getContentPane().add(info, BorderLayout.SOUTH);

        frame.setBounds(10, 10, 600, 800);
        frame.setVisible(true);

        Thread worker = new Thread(new LiftArmDisplay());
        worker.setDaemon(true);
        worker.start();
    }
}
