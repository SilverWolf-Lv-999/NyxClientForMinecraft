package io.github.seraphina.nyx.client.module.visual.hud.component;

import io.github.seraphina.nyx.client.manager.FontManager;
import io.github.seraphina.nyx.client.manager.HUDManager;
import io.github.seraphina.nyx.client.manager.NotificationManager;
import io.github.seraphina.nyx.client.module.visual.hud.HUD;
import io.github.seraphina.nyx.client.ui.Notification;
import io.github.seraphina.nyx.client.ui.UIComponent;
import io.github.seraphina.nyx.client.utility.MathUtility;
import io.github.seraphina.nyx.client.utility.Render2DUtility;
import io.github.seraphina.nyx.client.utility.font.FontRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.AABB;

import java.util.List;

public final class NotificationComponent implements UIComponent<HUD> {
    private static final String ID = "notification";
    private static final String PREVIEW_TEXT = "Module Nyx | Enabled";
    private static final float WIDTH_MIN = 96.0F;
    private static final float WIDTH_MAX = 330.0F;
    private static final float HEIGHT = 26.0F;
    private static final float GAP = 5.0F;
    private static final float RADIUS = 6.0F;
    private static final float SCREEN_MARGIN = 12.0F;
    private static final float TEXT_PADDING_LEFT = 16.0F;
    private static final float TEXT_PADDING_RIGHT = 10.0F;
    private static final float SLIDE_DISTANCE = 18.0F;
    private static final int BACKGROUND = 0xDD0C0D11;
    private static final int BORDER = 0x26FFFFFF;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int SHADOW = 0x90000000;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public boolean isVisible() {
        return HUD.INSTANCE.notification.getValue();
    }

    @Override
    public float getDefaultX() {
        if (mc.getWindow() == null) {
            return 8.0F;
        }
        return Math.max(8.0F, mc.getWindow().getGuiScaledWidth() - stackWidth(notificationsForBounds()) - SCREEN_MARGIN);
    }

    @Override
    public float getDefaultY() {
        return SCREEN_MARGIN;
    }

    @Override
    public void render(GuiGraphics graphics, float partialTicks, float scale) {
        List<Notification> notifications = NotificationManager.getNotifications();
        if (notifications.isEmpty()) {
            return;
        }

        FontRenderer font = font();
        float stackWidth = stackWidth(font, notifications);
        float stackHeight = stackHeight(notifications.size());
        Placement placement = placement();
        long now = System.currentTimeMillis();

        for (int index = 0; index < notifications.size(); index++) {
            Notification notification = notifications.get(index);
            float progress = notification.getVisibilityProgress(now);
            if (progress <= 0.01F) {
                continue;
            }

            String text = notification.getText();
            float width = notificationWidth(font, text);
            float x = xFor(placement, stackWidth, width);
            float y = yFor(placement, stackHeight, index);

            if (placement == Placement.CENTER) {
                float itemScale = 0.84F + 0.16F * progress;
                float centerX = x + width * 0.5F;
                float centerY = y + HEIGHT * 0.5F;
                Render2DUtility.withScale(itemScale, itemScale, centerX, centerY,
                    () -> renderNotification(font, notification, x, y, width, progress));
            } else {
                float offset = (1.0F - progress) * SLIDE_DISTANCE;
                renderNotification(
                    font,
                    notification,
                    x + placement.horizontalDirection * offset,
                    y + placement.verticalDirection * offset,
                    width,
                    progress
                );
            }
        }
    }

    @Override
    public AABB getBoundingBox() {
        List<Notification> notifications = notificationsForBounds();
        return new AABB(
            0.0D,
            0.0D,
            0.0D,
            stackWidth(notifications),
            stackHeight(notifications.size()),
            1.0D
        );
    }

    private void renderNotification(FontRenderer font, Notification notification, float x, float y, float width, float opacity) {
        int shadow = Render2DUtility.applyOpacity(SHADOW, opacity);
        int background = Render2DUtility.applyOpacity(BACKGROUND, opacity);
        int border = Render2DUtility.applyOpacity(BORDER, opacity);
        int accent = Render2DUtility.applyOpacity(accentColor(notification.getState()), opacity);
        int text = Render2DUtility.applyOpacity(TEXT, opacity);

        Render2DUtility.drawDropShadow(x, y, width, HEIGHT, RADIUS, 0.0F, 2.0F, 10.0F, shadow);
        Render2DUtility.drawRoundedRect(x, y, width, HEIGHT, RADIUS, background);
        Render2DUtility.drawOutlineRoundedRect(x, y, width, HEIGHT, RADIUS, 1.0F, border);
        Render2DUtility.drawRoundedRect(x + 6.0F, y + 7.0F, 3.0F, HEIGHT - 14.0F, 1.5F, accent);

        float textY = y + centeredTextY(HEIGHT, font) - 0.5F;
        float textWidth = width - TEXT_PADDING_LEFT - TEXT_PADDING_RIGHT;
        font.drawString(trimToWidth(font, notification.getText(), textWidth), x + TEXT_PADDING_LEFT, textY, text);
    }

    private Placement placement() {
        if (mc.getWindow() == null) {
            return Placement.LEFT_TOP;
        }

        AABB bounds = HUDManager.getDisplayBounds(this);
        float centerX = (float)((bounds.minX + bounds.maxX) * 0.5D);
        float centerY = (float)((bounds.minY + bounds.maxY) * 0.5D);
        float screenWidth = mc.getWindow().getGuiScaledWidth();
        float screenHeight = mc.getWindow().getGuiScaledHeight();
        float middleX = screenWidth * 0.5F;
        float middleY = screenHeight * 0.5F;

        boolean nearCenterX = Math.abs(centerX - middleX) <= screenWidth * 0.18F;
        boolean nearCenterY = Math.abs(centerY - middleY) <= screenHeight * 0.22F;
        if (nearCenterX && nearCenterY) {
            return Placement.CENTER;
        }

        boolean right = centerX >= middleX;
        boolean bottom = centerY >= middleY;
        if (right) {
            return bottom ? Placement.RIGHT_BOTTOM : Placement.RIGHT_TOP;
        }
        return bottom ? Placement.LEFT_BOTTOM : Placement.LEFT_TOP;
    }

    private float xFor(Placement placement, float stackWidth, float width) {
        if (placement == Placement.RIGHT_TOP || placement == Placement.RIGHT_BOTTOM) {
            return stackWidth - width;
        }
        if (placement == Placement.CENTER) {
            return (stackWidth - width) * 0.5F;
        }
        return 0.0F;
    }

    private float yFor(Placement placement, float stackHeight, int index) {
        if (placement == Placement.LEFT_BOTTOM || placement == Placement.RIGHT_BOTTOM) {
            return stackHeight - HEIGHT - index * (HEIGHT + GAP);
        }
        return index * (HEIGHT + GAP);
    }

    private List<Notification> notificationsForBounds() {
        List<Notification> notifications = NotificationManager.getNotifications();
        if (notifications.isEmpty()) {
            return List.of(new Notification(Notification.State.MODULE_ENABLED, PREVIEW_TEXT));
        }
        return notifications;
    }

    private float stackWidth(List<Notification> notifications) {
        return stackWidth(font(), notifications);
    }

    private float stackWidth(FontRenderer font, List<Notification> notifications) {
        float width = notificationWidth(font, PREVIEW_TEXT);
        for (Notification notification : notifications) {
            width = Math.max(width, notificationWidth(font, notification.getText()));
        }
        return width;
    }

    private float stackHeight(int count) {
        int safeCount = Math.max(1, count);
        return safeCount * HEIGHT + (safeCount - 1) * GAP;
    }

    private float notificationWidth(FontRenderer font, String text) {
        float maxWidth = maxNotificationWidth();
        return MathUtility.clamp(font.getStringWidth(text) + TEXT_PADDING_LEFT + TEXT_PADDING_RIGHT, WIDTH_MIN, maxWidth);
    }

    private float maxNotificationWidth() {
        if (mc.getWindow() == null) {
            return WIDTH_MAX;
        }
        return Math.max(WIDTH_MIN, Math.min(WIDTH_MAX, mc.getWindow().getGuiScaledWidth() - SCREEN_MARGIN * 2.0F));
    }

    private int accentColor(Notification.State state) {
        return switch (state) {
            case MODULE_ENABLED -> 0xFF53E08C;
            case MODULE_DISABLED -> 0xFFFF6373;
            case DEBUG_MSG -> 0xFF3D81F7;
        };
    }

    private FontRenderer font() {
        return FontManager.getAppleTextRenderer(12.0F);
    }

    private static String trimToWidth(FontRenderer renderer, String text, float maxWidth) {
        if (renderer.getStringWidth(text) <= maxWidth) {
            return text;
        }

        String ellipsis = "...";
        float ellipsisWidth = renderer.getStringWidth(ellipsis);
        if (ellipsisWidth >= maxWidth) {
            return ellipsis;
        }

        int end = text.length();
        while (end > 0 && renderer.getStringWidth(text.substring(0, end)) + ellipsisWidth > maxWidth) {
            end--;
        }
        return text.substring(0, Math.max(0, end)) + ellipsis;
    }

    private static float centeredTextY(float height, FontRenderer renderer) {
        return (height - renderer.getLineHeight()) * 0.5F;
    }

    private enum Placement {
        LEFT_TOP(-1.0F, -1.0F),
        RIGHT_TOP(1.0F, -1.0F),
        LEFT_BOTTOM(-1.0F, 1.0F),
        RIGHT_BOTTOM(1.0F, 1.0F),
        CENTER(0.0F, 0.0F);

        private final float horizontalDirection;
        private final float verticalDirection;

        Placement(float horizontalDirection, float verticalDirection) {
            this.horizontalDirection = horizontalDirection;
            this.verticalDirection = verticalDirection;
        }
    }
}
