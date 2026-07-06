package io.github.seraphina.nyxclient.ui.clickgui.component;

import io.github.seraphina.nyxclient.ui.clickgui.AbstractComponent;
import io.github.seraphina.nyxclient.utility.Render2DUtility;
import io.github.seraphina.nyxclient.utility.font.FontRenderer;
import io.github.seraphina.nyxclient.value.impl.ColorValue;

import java.awt.Color;

import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;

public class ColorComponent extends AbstractComponent {
    private static final float HEIGHT = 112.0F;
    private static final float PALETTE_H = 70.0F;
    private static final float ALPHA_W = 10.0F;
    private static final float HUE_H = 5.0F;
    private static final float PREVIEW_SIZE = 20.0F;
    private static final float GAP = 6.0F;

    private final ColorValue colorValue;
    private float paletteX;
    private float paletteY;
    private float paletteW;
    private float hueX;
    private float hueY;
    private float hueW;
    private float alphaX;
    private float alphaY;
    private float resetX;
    private float resetY;
    private boolean draggingPalette;
    private boolean draggingHue;
    private boolean draggingAlpha;
    private float resetHoverProgress;
    private float paletteActiveProgress;
    private float hueActiveProgress;
    private float alphaActiveProgress;
    private float visualRed = Float.NaN;
    private float visualGreen = Float.NaN;
    private float visualBlue = Float.NaN;
    private float visualAlpha = Float.NaN;

    public ColorComponent(ColorValue value) {
        super(value);
        this.colorValue = value;
    }

    @Override
    protected void render(int mouseX, int mouseY, float partialTick) {
        FontRenderer labelFont = font(10.0F);
        labelFont.drawString(trimToWidth(labelFont, value.getDisplayName(), width - 20.0F), x, y + 1.0F, TEXT_MUTED);

        float resetSize = 10.0F;
        resetX = x + width - resetSize;
        resetY = y;
        boolean resetHovered = isInside(mouseX, mouseY, resetX, resetY, resetSize, resetSize);
        resetHoverProgress = animate(resetHoverProgress, resetHovered ? 1.0F : 0.0F, 18.0F);
        Render2DUtility.drawRoundedRect(resetX, resetY, resetSize, resetSize, 2.0F,
            Render2DUtility.mix(CONTROL_HOVER, 0xFFE05252, resetHoverProgress));
        font(7.0F).drawCenteredString("R", resetX + resetSize * 0.5F, resetY + centeredTextY(resetSize, font(7.0F)),
            Render2DUtility.mix(TEXT_SUBTLE, TEXT, resetHoverProgress));

        Color color = colorValue.getValue();
        Color visualColor = animatedColor(color);
        float[] hsb = Color.RGBtoHSB(visualColor.getRed(), visualColor.getGreen(), visualColor.getBlue(), null);
        float hue = hsb[0];
        float saturation = hsb[1];
        float brightness = hsb[2];

        paletteX = x;
        paletteY = y + 16.0F;
        float rightToolsWidth = (colorValue.isAllowAlpha() ? ALPHA_W : 0.0F) + GAP + PREVIEW_SIZE;
        paletteW = Math.min(150.0F, Math.max(92.0F, width - rightToolsWidth - 2.0F));

        int pureHue = Color.HSBtoRGB(hue, 1.0F, 1.0F) | 0xFF000000;
        Render2DUtility.drawHorizontalGradientRect(paletteX, paletteY, paletteW, PALETTE_H, 0xFFFFFFFF, pureHue);
        Render2DUtility.drawVerticalGradientRect(paletteX, paletteY, paletteW, PALETTE_H, 0x00000000, 0xFF000000);
        Render2DUtility.drawOutlineRoundedRect(paletteX, paletteY, paletteW, PALETTE_H, 2.0F, 1.0F, BORDER);

        boolean paletteHovered = isInside(mouseX, mouseY, paletteX, paletteY, paletteW, PALETTE_H);
        paletteActiveProgress = animate(paletteActiveProgress, draggingPalette || paletteHovered ? 1.0F : 0.0F, 18.0F);
        float indicatorX = paletteX + paletteW * saturation;
        float indicatorY = paletteY + PALETTE_H * (1.0F - brightness);
        Render2DUtility.drawCircle(indicatorX, indicatorY, 3.0F + paletteActiveProgress * 0.8F, TEXT);
        Render2DUtility.drawOutlineCircle(indicatorX, indicatorY, 4.0F + paletteActiveProgress * 0.8F, 1.0F, 0xB0000000);

        alphaX = paletteX + paletteW + GAP;
        alphaY = paletteY;
        if (colorValue.isAllowAlpha()) {
            Render2DUtility.drawRoundedRect(alphaX, alphaY, ALPHA_W, PALETTE_H, 2.0F, 0xFF282A33);
            Render2DUtility.drawVerticalGradientRect(
                alphaX,
                alphaY,
                ALPHA_W,
                PALETTE_H,
                Render2DUtility.rgba(visualColor.getRed(), visualColor.getGreen(), visualColor.getBlue(), 16),
                Render2DUtility.rgba(visualColor.getRed(), visualColor.getGreen(), visualColor.getBlue(), 255)
            );
            Render2DUtility.drawOutlineRoundedRect(alphaX, alphaY, ALPHA_W, PALETTE_H, 2.0F, 1.0F, BORDER);

            boolean alphaHovered = isInside(mouseX, mouseY, alphaX, alphaY, ALPHA_W, PALETTE_H);
            alphaActiveProgress = animate(alphaActiveProgress, draggingAlpha || alphaHovered ? 1.0F : 0.0F, 18.0F);
            float alphaIndicatorY = alphaY + PALETTE_H * (1.0F - visualColor.getAlpha() / 255.0F);
            Render2DUtility.drawRect(alphaX - 1.0F - alphaActiveProgress, alphaIndicatorY - 1.0F,
                ALPHA_W + 2.0F + alphaActiveProgress * 2.0F, 2.0F, TEXT);
        }

        float previewX = colorValue.isAllowAlpha() ? alphaX : alphaX - GAP;
        float previewY = paletteY + PALETTE_H + 4.0F;
        Render2DUtility.drawRoundedRect(previewX, previewY, PREVIEW_SIZE, PREVIEW_SIZE, 3.0F, colorToArgb(visualColor));
        Render2DUtility.drawOutlineRoundedRect(previewX, previewY, PREVIEW_SIZE, PREVIEW_SIZE, 3.0F, 1.0F, BORDER);

        hueX = paletteX;
        hueY = paletteY + PALETTE_H + 4.0F;
        hueW = paletteW;
        int[] rainbow = {
            0xFFFF0000,
            0xFFFFFF00,
            0xFF00FF00,
            0xFF00FFFF,
            0xFF0000FF,
            0xFFFF00FF,
            0xFFFF0000
        };
        float segmentWidth = hueW / (rainbow.length - 1);
        for (int i = 0; i < rainbow.length - 1; i++) {
            Render2DUtility.drawHorizontalGradientRect(hueX + segmentWidth * i, hueY, segmentWidth + 0.5F, HUE_H, rainbow[i], rainbow[i + 1]);
        }
        Render2DUtility.drawOutlineRoundedRect(hueX, hueY, hueW, HUE_H, 1.0F, 1.0F, BORDER);

        boolean hueHovered = isInside(mouseX, mouseY, hueX, hueY - 3.0F, hueW, HUE_H + 6.0F);
        hueActiveProgress = animate(hueActiveProgress, draggingHue || hueHovered ? 1.0F : 0.0F, 18.0F);
        float hueIndicatorX = hueX + hueW * hue;
        Render2DUtility.drawRect(hueIndicatorX - 1.0F - hueActiveProgress, hueY - 1.0F,
            2.0F + hueActiveProgress * 2.0F, HUE_H + 2.0F, TEXT);
    }

    @Override
    public float getHeight() {
        return HEIGHT;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }

        if (isInside(mouseX, mouseY, resetX, resetY, 10.0F, 10.0F)) {
            colorValue.setValue(colorValue.getDefaultValue());
            return true;
        }
        if (isInside(mouseX, mouseY, paletteX, paletteY, paletteW, PALETTE_H)) {
            draggingPalette = true;
            updatePalette(mouseX, mouseY);
            return true;
        }
        if (colorValue.isAllowAlpha() && isInside(mouseX, mouseY, alphaX, alphaY, ALPHA_W, PALETTE_H)) {
            draggingAlpha = true;
            updateAlpha(mouseY);
            return true;
        }
        if (isInside(mouseX, mouseY, hueX, hueY, hueW, HUE_H)) {
            draggingHue = true;
            updateHue(mouseX);
            return true;
        }
        return isInside(mouseX, mouseY, x, y, width, HEIGHT);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = draggingPalette || draggingHue || draggingAlpha;
        draggingPalette = false;
        draggingHue = false;
        draggingAlpha = false;
        return handled;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingPalette) {
            updatePalette(mouseX, mouseY);
            return true;
        }
        if (draggingHue) {
            updateHue(mouseX);
            return true;
        }
        if (draggingAlpha) {
            updateAlpha(mouseY);
            return true;
        }
        return false;
    }

    @Override
    public void blur() {
        draggingPalette = false;
        draggingHue = false;
        draggingAlpha = false;
    }

    private void updatePalette(double mouseX, double mouseY) {
        float saturation = clamp((float)((mouseX - paletteX) / paletteW), 0.0F, 1.0F);
        float brightness = 1.0F - clamp((float)((mouseY - paletteY) / PALETTE_H), 0.0F, 1.0F);
        Color current = colorValue.getValue();
        float[] hsb = Color.RGBtoHSB(current.getRed(), current.getGreen(), current.getBlue(), null);
        setColorFromHsb(hsb[0], saturation, brightness, current.getAlpha());
    }

    private void updateHue(double mouseX) {
        float hue = clamp((float)((mouseX - hueX) / hueW), 0.0F, 1.0F);
        Color current = colorValue.getValue();
        float[] hsb = Color.RGBtoHSB(current.getRed(), current.getGreen(), current.getBlue(), null);
        setColorFromHsb(hue, hsb[1], hsb[2], current.getAlpha());
    }

    private void updateAlpha(double mouseY) {
        float alpha = 1.0F - clamp((float)((mouseY - alphaY) / PALETTE_H), 0.0F, 1.0F);
        Color current = colorValue.getValue();
        colorValue.setValue(new Color(current.getRed(), current.getGreen(), current.getBlue(), Math.round(alpha * 255.0F)));
    }

    private void setColorFromHsb(float hue, float saturation, float brightness, int alpha) {
        int rgb = Color.HSBtoRGB(hue, saturation, brightness);
        colorValue.setValue(new Color((rgb >>> 16) & 0xFF, (rgb >>> 8) & 0xFF, rgb & 0xFF, alpha));
    }

    private Color animatedColor(Color target) {
        visualRed = animate(visualRed, target.getRed(), 18.0F);
        visualGreen = animate(visualGreen, target.getGreen(), 18.0F);
        visualBlue = animate(visualBlue, target.getBlue(), 18.0F);
        visualAlpha = animate(visualAlpha, target.getAlpha(), 18.0F);
        return new Color(Math.round(visualRed), Math.round(visualGreen), Math.round(visualBlue), Math.round(visualAlpha));
    }
}
