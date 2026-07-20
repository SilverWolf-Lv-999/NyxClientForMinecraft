package io.github.seraphina.nyx.client.ui.clickgui.component;

import io.github.seraphina.nyx.client.ui.clickgui.AbstractComponent;
import io.github.seraphina.nyx.client.utility.Render2DUtility;
import io.github.seraphina.nyx.client.utility.font.FontRenderer;
import io.github.seraphina.nyx.client.value.impl.ColorValue;

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
    private float resetSize = 10.0F;
    private float paletteHeight = PALETTE_H;
    private float alphaWidth = ALPHA_W;
    private float hueHeight = HUE_H;
    private float previewSize = PREVIEW_SIZE;
    private float gap = GAP;
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
        boolean compact = compactLayout();
        float labelHeight = compact ? 14.0F : 16.0F;
        resetSize = compact ? 8.0F : 10.0F;
        paletteHeight = compact ? 46.0F : PALETTE_H;
        alphaWidth = compact ? 7.0F : ALPHA_W;
        hueHeight = compact ? 4.0F : HUE_H;
        previewSize = compact ? 14.0F : PREVIEW_SIZE;
        gap = compact ? 4.0F : GAP;

        FontRenderer labelFont = font(compact ? 7.0F : 10.0F);
        labelFont.drawString(trimToWidth(labelFont, value.getDisplayName(), width - resetSize - 6.0F), x,
            y + centeredTextY(labelHeight, labelFont), compact ? 0xCCFFFFFF : TEXT_MUTED);

        resetX = x + width - resetSize;
        resetY = y + (labelHeight - resetSize) * 0.5F;
        boolean resetHovered = isInside(mouseX, mouseY, resetX, resetY, resetSize, resetSize);
        resetHoverProgress = animate(resetHoverProgress, resetHovered ? 1.0F : 0.0F, 18.0F);
        Render2DUtility.drawRoundedRect(resetX, resetY, resetSize, resetSize, 2.0F,
            Render2DUtility.mix(CONTROL_HOVER, 0xFFE05252, resetHoverProgress));
        FontRenderer resetFont = compact ? boldFont(5.5F) : font(7.0F);
        resetFont.drawCenteredString("R", resetX + resetSize * 0.5F, resetY + centeredTextY(resetSize, resetFont),
            Render2DUtility.mix(TEXT_SUBTLE, TEXT, resetHoverProgress));

        Color color = colorValue.getValue();
        Color visualColor = animatedColor(color);
        float[] hsb = Color.RGBtoHSB(visualColor.getRed(), visualColor.getGreen(), visualColor.getBlue(), null);
        float hue = hsb[0];
        float saturation = hsb[1];
        float brightness = hsb[2];

        paletteX = x;
        paletteY = y + labelHeight;
        float alphaToolsWidth = colorValue.isAllowAlpha() ? alphaWidth + gap : 0.0F;
        paletteW = Math.min(150.0F, Math.max(compact ? 36.0F : 48.0F, width - alphaToolsWidth));

        int pureHue = Color.HSBtoRGB(hue, 1.0F, 1.0F) | 0xFF000000;
        Render2DUtility.drawHorizontalGradientRect(paletteX, paletteY, paletteW, paletteHeight, 0xFFFFFFFF, pureHue);
        Render2DUtility.drawVerticalGradientRect(paletteX, paletteY, paletteW, paletteHeight, 0x00000000, 0xFF000000);
        Render2DUtility.drawOutlineRoundedRect(paletteX, paletteY, paletteW, paletteHeight, 2.0F, 1.0F, BORDER);

        boolean paletteHovered = isInside(mouseX, mouseY, paletteX, paletteY, paletteW, paletteHeight);
        paletteActiveProgress = animate(paletteActiveProgress, draggingPalette || paletteHovered ? 1.0F : 0.0F, 18.0F);
        float indicatorX = paletteX + paletteW * saturation;
        float indicatorY = paletteY + paletteHeight * (1.0F - brightness);
        float indicatorRadius = compact ? 2.0F : 3.0F;
        Render2DUtility.drawCircle(indicatorX, indicatorY, indicatorRadius + paletteActiveProgress * 0.6F, TEXT);
        Render2DUtility.drawOutlineCircle(indicatorX, indicatorY, indicatorRadius + 1.0F + paletteActiveProgress * 0.6F,
            1.0F, 0xB0000000);

        alphaX = paletteX + paletteW + gap;
        alphaY = paletteY;
        if (colorValue.isAllowAlpha()) {
            Render2DUtility.drawRoundedRect(alphaX, alphaY, alphaWidth, paletteHeight, 2.0F, 0xFF282A33);
            Render2DUtility.drawVerticalGradientRect(
                alphaX,
                alphaY,
                alphaWidth,
                paletteHeight,
                Render2DUtility.rgba(visualColor.getRed(), visualColor.getGreen(), visualColor.getBlue(), 16),
                Render2DUtility.rgba(visualColor.getRed(), visualColor.getGreen(), visualColor.getBlue(), 255)
            );
            Render2DUtility.drawOutlineRoundedRect(alphaX, alphaY, alphaWidth, paletteHeight, 2.0F, 1.0F, BORDER);

            boolean alphaHovered = isInside(mouseX, mouseY, alphaX, alphaY, alphaWidth, paletteHeight);
            alphaActiveProgress = animate(alphaActiveProgress, draggingAlpha || alphaHovered ? 1.0F : 0.0F, 18.0F);
            float alphaIndicatorY = alphaY + paletteHeight * (1.0F - visualColor.getAlpha() / 255.0F);
            Render2DUtility.drawRect(alphaX - 1.0F - alphaActiveProgress, alphaIndicatorY - 1.0F,
                alphaWidth + 2.0F + alphaActiveProgress * 2.0F, 2.0F, TEXT);
        }

        float previewX = x + width - previewSize;
        float previewY = paletteY + paletteHeight + (compact ? 3.0F : 4.0F);
        Render2DUtility.drawRoundedRect(previewX, previewY, previewSize, previewSize, compact ? 2.0F : 3.0F,
            colorToArgb(visualColor));
        Render2DUtility.drawOutlineRoundedRect(previewX, previewY, previewSize, previewSize, compact ? 2.0F : 3.0F,
            1.0F, BORDER);

        hueX = paletteX;
        hueY = previewY;
        hueW = Math.max(24.0F, previewX - gap - hueX);
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
            Render2DUtility.drawHorizontalGradientRect(hueX + segmentWidth * i, hueY, segmentWidth + 0.5F,
                hueHeight, rainbow[i], rainbow[i + 1]);
        }
        Render2DUtility.drawOutlineRoundedRect(hueX, hueY, hueW, hueHeight, 1.0F, 1.0F, BORDER);

        boolean hueHovered = isInside(mouseX, mouseY, hueX, hueY - 3.0F, hueW, hueHeight + 6.0F);
        hueActiveProgress = animate(hueActiveProgress, draggingHue || hueHovered ? 1.0F : 0.0F, 18.0F);
        float hueIndicatorX = hueX + hueW * hue;
        Render2DUtility.drawRect(hueIndicatorX - 1.0F - hueActiveProgress, hueY - 1.0F,
            2.0F + hueActiveProgress * 2.0F, hueHeight + 2.0F, TEXT);
    }

    @Override
    public float getHeight() {
        return compactLayout() ? 78.0F : HEIGHT;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }

        if (isInside(mouseX, mouseY, resetX, resetY, resetSize, resetSize)) {
            colorValue.setValue(colorValue.getDefaultValue());
            return true;
        }
        if (isInside(mouseX, mouseY, paletteX, paletteY, paletteW, paletteHeight)) {
            draggingPalette = true;
            updatePalette(mouseX, mouseY);
            return true;
        }
        if (colorValue.isAllowAlpha() && isInside(mouseX, mouseY, alphaX, alphaY, alphaWidth, paletteHeight)) {
            draggingAlpha = true;
            updateAlpha(mouseY);
            return true;
        }
        if (isInside(mouseX, mouseY, hueX, hueY, hueW, hueHeight)) {
            draggingHue = true;
            updateHue(mouseX);
            return true;
        }
        return isInside(mouseX, mouseY, x, y, width, getHeight());
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
        float brightness = 1.0F - clamp((float)((mouseY - paletteY) / paletteHeight), 0.0F, 1.0F);
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
        float alpha = 1.0F - clamp((float)((mouseY - alphaY) / paletteHeight), 0.0F, 1.0F);
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
