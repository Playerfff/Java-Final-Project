package app.client;

import app.client.DPIUtil;

import javax.swing.SwingUtilities;

public class ClientApp {
    public static void main(String[] args) {
        DPIUtil.autoScale();

        String host = "localhost";
        int port = 5555;
        if (args.length >= 1) host = args[0];
        if (args.length >= 2) port = Integer.parseInt(args[1]);
        final String h = host;
        final int p = port;
        SwingUtilities.invokeLater(() -> new SchedulerGUI(h, p).setVisible(true));
    }
}
