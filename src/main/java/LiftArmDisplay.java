
import edu.wpi.first.networktables.BooleanSubscriber;
import edu.wpi.first.networktables.DoubleSubscriber;
import edu.wpi.first.networktables.StringSubscriber;
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

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import edu.wpi.first.cscore.CameraServerJNI;
import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.WPIMathJNI;
import edu.wpi.first.util.WPIUtilJNI;

/** Basic display of lift and arm
 *
 *  Listens to network table for lift and arm settings,
 *  be it simulated or real robot,
 *  and provides simple representation.
 */
public class LiftArmDisplay implements Runnable
{
    // JPanel for our custom graphics
    private static class RobotDisplay extends JPanel
    {
        private double lift_extension = 0.0, arm_angle = -90.0, intake_angle = 90.0;
        private boolean arm_extended = false;

        // Set lift etc. and request redraw
        public void set(double lift_extension, double arm_angle,  boolean arm_extended, double intake_angle)
        {
            this.lift_extension = lift_extension;
            this.arm_angle = arm_angle;
            this.arm_extended = arm_extended;
            this.intake_angle = intake_angle;
            repaint();
        }

        // helper for rotating (x, y) by angle, getting the new x
        private static double rotate_x(double x, double y, double angle)
        {
            double rad = Math.toRadians(angle);
            return x * Math.cos(rad) - y * Math.sin(rad);
        }

        // helper for rotating (x, y) by angle, getting the new y
        private static double rotate_y(double x, double y, double angle)
        {
            double rad = Math.toRadians(angle);
            return x * Math.sin(rad) + y * Math.cos(rad);
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            // Long ago, AWT actually used Graphics2D, but API still passes Graphics
            Graphics2D g2 = (Graphics2D) g;

            // How large is the panel?
            Rectangle bounds = getBounds();
            // Clear everything
            g2.clearRect(0, 0, bounds.width, bounds.height);

            // Scaling from robot to screen
            int base_x = bounds.width / 6;
            int base_y = bounds.height-5;
            double pixel_per_meter = Math.max(bounds.width, bounds.height) / 2.0;

            // Draw Lift
            double lift_angle = Math.toRadians(60.0);
            double lift_length = 0.8 + lift_extension;
            int robot_edge = base_x + (int) (0.8 * Math.cos(lift_angle) * pixel_per_meter);
            int lift_top_x = base_x + (int) (lift_length * Math.cos(lift_angle) * pixel_per_meter);
            int lift_top_y = base_y - (int) (lift_length * Math.sin(lift_angle) * pixel_per_meter);
            g2.setColor(Color.BLUE);
            g2.setStroke(new BasicStroke(10));
            g.drawLine(0, base_y, robot_edge, base_y);
            g.drawLine(base_x, base_y, lift_top_x, lift_top_y);

            // Draw Intake
            double intake_length = 0.2;
            int intake_x = robot_edge + (int) (Math.cos(Math.toRadians(intake_angle)) * intake_length * pixel_per_meter);
            int intake_y = base_y     - (int) (Math.sin(Math.toRadians(intake_angle)) * intake_length * pixel_per_meter);
            g2.setColor(Color.GREEN);
            g.drawLine(robot_edge, base_y, intake_x, intake_y);

            // Draw Arm at end of Lift
            double arm_length = arm_extended ? 0.5 : 0.3;
            int arm_x = lift_top_x + (int) (Math.cos(Math.toRadians(arm_angle)) * arm_length * pixel_per_meter);
            int arm_y = lift_top_y - (int) (Math.sin(Math.toRadians(arm_angle)) * arm_length * pixel_per_meter);
            g2.setColor(Color.RED);
            g.drawLine(lift_top_x, lift_top_y, arm_x, arm_y);

            // Draw Grabber at end of Arm
            double grab_size = 0.15 * pixel_per_meter;
            g2.setColor(Color.ORANGE);
            g.drawLine(arm_x, arm_y,
                       (int) (arm_x + rotate_x(grab_size, -grab_size/2, -arm_angle)),
                       (int) (arm_y + rotate_y(grab_size, -grab_size/2, -arm_angle)));
            g.drawLine(arm_x, arm_y,
                       (int) (arm_x + rotate_x(grab_size, +grab_size/2, -arm_angle)),
                       (int) (arm_y + rotate_y(grab_size, +grab_size/2, -arm_angle)));

            // If intake is out front, pretend we're in front of nodes so draw those
            if (intake_angle > 110.0)
            {
                g2.setColor(Color.DARK_GRAY);
                int node_start = robot_edge + 10;
                int node_width = (int)(0.25 * pixel_per_meter);
                int level1 = (int)(0.25 * pixel_per_meter);
                int level2 = (int)(0.50 * pixel_per_meter);
                // Floor node
                g.drawRect(node_start, bounds.height-2, node_width, 2);
                // Mid node
                g.drawRect(node_start + node_width, bounds.height-level1, node_width, level1);
                // Upper/far node
                g.drawRect(node_start + 2*node_width, bounds.height-level2, node_width, level2);
    
                // Rods sticking out of mid and far node
                g2.setColor(Color.GRAY);
                int rod_height = node_width;
                int rod_x = node_start + node_width + node_width/2;
                g2.drawLine(rod_x, bounds.height-level1, rod_x, bounds.height-level1-rod_height);
                rod_x = node_start + 2*node_width + node_width/2;
                g2.drawLine(rod_x, bounds.height-level2, rod_x, bounds.height-level2-rod_height);
            }
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
            StringSubscriber nt_mode = table.getStringTopic("Mode").subscribe("?");
            DoubleSubscriber nt_lift = table.getDoubleTopic("Lift Height").subscribe(0.0);
            DoubleSubscriber nt_arm = table.getDoubleTopic("Arm Angle").subscribe(0.0);
            BooleanSubscriber nt_ext = table.getBooleanTopic("Arm Extended").subscribe(false);
            DoubleSubscriber nt_intake = table.getDoubleTopic("Intake Angle").subscribe(90.0);

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
                double intake = nt_intake.get();

                // Must update display on UI thread
                SwingUtilities.invokeAndWait(() ->
                {
                    info.setText(String.format("%s Mode - Lift %5.2f m, Arm %s at %4.1f deg, Intake at %4.1f deg",
                                               nt_mode.get(),
                                               lift,
                                               extended ? "out" : "in ",
                                               arm,
                                               intake));
                    display.set(lift, arm, extended, intake);
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
        // Create GUI
        final JFrame frame = new JFrame("Lift/Arm Sim");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        display = new RobotDisplay();
        frame.getContentPane().add(display, BorderLayout.CENTER);

        info = new JLabel("XXXXXXXXXXXXXXXXXXXXX");
        info.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.GRAY, 2, true),
                                                          BorderFactory.createEmptyBorder(5, 5, 5, 5)));
        frame.getContentPane().add(info, BorderLayout.SOUTH);

        frame.setBounds(10, 10, 800, 800);
        frame.setVisible(true);

        // Start worker which reads network table and updates display
        Thread worker = new Thread(new LiftArmDisplay());
        worker.setDaemon(true);
        worker.start();

        // We actually don't quit when leaving main()
        // because the GUI and worker keep running...
    }
}
