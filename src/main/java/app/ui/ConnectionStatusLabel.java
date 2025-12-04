package app.ui;

import javax.swing.*;
import java.awt.*;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Simple connection status label; supports a blinking reconnect animation.
 */
public class ConnectionStatusLabel extends JLabel {
    private final Color GREEN = new Color(0,128,0);
    private final Color RED = Color.RED;
    private final Color ORANGE = new Color(255,140,0);
    private Timer blinkTimer = null;
    private volatile boolean blinking = false;

    public ConnectionStatusLabel() {
        super("🔴 Disconnected");
        setForeground(RED);
        setFont(getFont().deriveFont(Font.BOLD));
    }

    public void setConnected() {
        stopBlinking();
        setText("🟢 Connected");
        setForeground(GREEN);
    }

    public void setDisconnected() {
        stopBlinking();
        setText("🔴 Disconnected");
        setForeground(RED);
    }

    public void startReconnecting() {
        stopBlinking();
        blinking = true;
        setText("🟠 Reconnecting...");
        blinkTimer = new Timer(true);
        blinkTimer.scheduleAtFixedRate(new TimerTask() {
            boolean toggle = false;
            @Override
            public void run() {
                SwingUtilities.invokeLater(() -> {
                    if (!blinking) return;
                    setForeground(toggle ? ORANGE : RED);
                    toggle = !toggle;
                });
            }
        }, 0, 500);
    }

    public void stopBlinking() {
        blinking = false;
        if (blinkTimer != null) { blinkTimer.cancel(); blinkTimer = null; }
    }
}
