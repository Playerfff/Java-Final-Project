package app.client;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.util.Enumeration;

public class DPIUtil {
    private static float scaleFactor = 1.0f;

    static {
        int dpi = Toolkit.getDefaultToolkit().getScreenResolution();
        scaleFactor = dpi / 96.0f;
        if (scaleFactor < 1.0f) scaleFactor = 1.0f;
    }

    public static float getScaleFactor() {
        return scaleFactor;
    }

    public static void autoScale() {
        if (scaleFactor <= 1.0f) return;

        System.out.println("High DPI detected. Scaling factor: " + scaleFactor);

        Enumeration<Object> keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            Object value = UIManager.get(key);

            if (value instanceof FontUIResource) {
                FontUIResource orig = (FontUIResource) value;
                FontUIResource scaled = new FontUIResource(orig.deriveFont(orig.getSize2D() * scaleFactor));
                UIManager.put(key, scaled);
            }
        }
    }

    public static int scale(int size) {
        return Math.round(size * scaleFactor);
    }
}