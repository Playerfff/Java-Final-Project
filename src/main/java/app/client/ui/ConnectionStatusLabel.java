package app.client.ui;

import javax.swing.*;
import java.awt.*;
import java.util.Timer;
import java.util.TimerTask;

public class ConnectionStatusLabel extends JLabel {
    private Color statusColor = new Color(220, 53, 69); // Red
    private String statusText = "Disconnected";
    private Timer blinkTimer = null;
    private boolean blinkState = false;

    public ConnectionStatusLabel() {
        super("");
        setPreferredSize(new Dimension(140, 30));
        setFont(new Font("SansSerif", Font.BOLD, 12));
        setForeground(Color.WHITE);
        setHorizontalAlignment(SwingConstants.CENTER);
    }

    public void setConnected() {
        stopBlinking();
        statusColor = new Color(40, 167, 69); // Green
        statusText = "Connected";
        repaint();
    }

    public void setDisconnected() {
        stopBlinking();
        statusColor = new Color(220, 53, 69); // Red
        statusText = "Disconnected";
        repaint();
    }

    public void startReconnecting() {
        if (blinkTimer != null) return; // Already blinking
        statusText = "Reconnecting...";
        statusColor = new Color(255, 193, 7); // Yellow/Orange

        blinkTimer = new Timer(true);
        blinkTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                blinkState = !blinkState;
                repaint();
            }
        }, 0, 500);
    }

    private void stopBlinking() {
        if (blinkTimer != null) {
            blinkTimer.cancel();
            blinkTimer = null;
        }
        blinkState = true; // Always visible when not blinking
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw pill background
        if (blinkTimer == null || blinkState) {
            g2.setColor(statusColor);
        } else {
            g2.setColor(statusColor.darker());
        }
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);

        // Draw text centered
        g2.setColor(Color.WHITE);
        FontMetrics fm = g2.getFontMetrics();
        int x = (getWidth() - fm.stringWidth(statusText)) / 2;
        int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
        g2.drawString(statusText, x, y);

        g2.dispose();
    }
}