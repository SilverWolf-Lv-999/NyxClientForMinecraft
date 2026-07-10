package io.github.seraphina.nyx.client.manager;

import io.github.seraphina.nyx.client.ui.Notification;
import io.github.seraphina.nyx.client.utility.IMinecraft;

import java.util.ArrayList;
import java.util.List;

public final class NotificationManager implements IMinecraft {
    private static final int MAX_NOTIFICATIONS = 8;
    private static final List<Notification> NOTIFICATIONS = new ArrayList<>();

    private NotificationManager() {
    }

    public static synchronized void pushNotification(Notification notification) {
        if (notification == null || mc.player == null) {
            return;
        }

        long now = System.currentTimeMillis();
        removeExpired(now);
        NOTIFICATIONS.add(0, notification);

        while (NOTIFICATIONS.size() > MAX_NOTIFICATIONS) {
            NOTIFICATIONS.remove(NOTIFICATIONS.size() - 1);
        }
    }

    public static void pushModule(String moduleName, boolean enabled) {
        pushNotification(Notification.module(moduleName, enabled));
    }

    public static void pushDebug(String message) {
        pushNotification(Notification.debug(message));
    }

    public static synchronized List<Notification> getNotifications() {
        removeExpired(System.currentTimeMillis());
        return List.copyOf(NOTIFICATIONS);
    }

    public static synchronized void clear() {
        NOTIFICATIONS.clear();
    }

    private static void removeExpired(long now) {
        NOTIFICATIONS.removeIf(notification -> notification.isExpired(now));
    }
}
