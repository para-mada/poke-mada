package net.paramada.pokemada;

import javax.imageio.ImageIO;
import java.awt.AWTException;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/** Displays native Windows notifications through a persistent system-tray icon. */
final class WindowsNotificationService implements AutoCloseable {
    private static final System.Logger LOGGER = System.getLogger(WindowsNotificationService.class.getName());
    private static final String ICON_RESOURCE = "/net/paramada/pokemada/assets/master-v-emblem.png";

    private TrayIcon trayIcon;
    private boolean initializationAttempted;

    synchronized void show(String title, String message) {
        TrayIcon icon = trayIcon();
        if (icon == null) return;
        icon.displayMessage(title, message, TrayIcon.MessageType.INFO);
        LOGGER.log(System.Logger.Level.INFO,
                "Windows notification requested: title={0}, message={1}", title, message);
    }

    private TrayIcon trayIcon() {
        if (trayIcon != null || initializationAttempted) return trayIcon;
        initializationAttempted = true;
        if (!System.getProperty("os.name", "").toLowerCase().contains("windows")) {
            LOGGER.log(System.Logger.Level.INFO, "Native Windows notifications skipped on this operating system");
            return null;
        }
        if (!SystemTray.isSupported()) {
            LOGGER.log(System.Logger.Level.WARNING, "System tray is unavailable; Windows notifications are disabled");
            return null;
        }

        try (InputStream stream = WindowsNotificationService.class.getResourceAsStream(ICON_RESOURCE)) {
            if (stream == null) throw new IOException("Missing notification icon: " + ICON_RESOURCE);
            BufferedImage image = ImageIO.read(stream);
            if (image == null) throw new IOException("Unsupported notification icon: " + ICON_RESOURCE);
            TrayIcon icon = new TrayIcon(image, "Master V Tournament");
            icon.setImageAutoSize(true);
            SystemTray.getSystemTray().add(icon);
            trayIcon = icon;
            LOGGER.log(System.Logger.Level.INFO, "Windows notification service initialized");
            return icon;
        } catch (AWTException | IOException | RuntimeException exception) {
            LOGGER.log(System.Logger.Level.WARNING, "Could not initialize Windows notifications", exception);
            return null;
        }
    }

    @Override
    public synchronized void close() {
        if (trayIcon == null) return;
        SystemTray.getSystemTray().remove(trayIcon);
        trayIcon = null;
    }
}
