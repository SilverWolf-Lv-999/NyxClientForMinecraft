package io.github.seraphina.nyxclient.ui.clickgui.component;

import io.github.seraphina.nyxclient.ui.clickgui.AbstractComponent;
import io.github.seraphina.nyxclient.utility.Render2DUtility;
import io.github.seraphina.nyxclient.utility.font.FontRenderer;
import io.github.seraphina.nyxclient.value.impl.StringValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_END;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_HOME;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;

public class StringComponent extends AbstractComponent {
    private static final float INPUT_HEIGHT = 20.0F;

    private final StringValue stringValue;
    private float inputX;
    private float inputY;
    private float inputWidth;
    private boolean focused;
    private boolean selectedAll;
    private int cursor;

    public StringComponent(StringValue value) {
        super(value);
        this.stringValue = value;
        this.cursor = safeValue().length();
    }

    @Override
    protected void render(int mouseX, int mouseY, float partialTick) {
        inputWidth = Math.min(Math.max(112.0F, width * 0.52F), Math.max(54.0F, width - 64.0F));
        inputX = x + width - inputWidth;
        inputY = y + 5.0F;

        drawLabel(inputWidth + 12.0F);
        boolean hovered = isInside(mouseX, mouseY, inputX, inputY, inputWidth, INPUT_HEIGHT);
        Render2DUtility.drawRoundedRect(inputX, inputY, inputWidth, INPUT_HEIGHT, 4.0F, focused ? CONTROL_HOVER : CONTROL_BACKGROUND);
        Render2DUtility.drawOutlineRoundedRect(inputX, inputY, inputWidth, INPUT_HEIGHT, 4.0F, 1.0F, focused ? ACCENT : hovered ? BORDER : BORDER_SOFT);

        FontRenderer inputFont = font(9.0F);
        String text = safeValue();
        cursor = Math.max(0, Math.min(cursor, text.length()));
        String displayText = displayText(inputFont, text, inputWidth - 12.0F);

        if (selectedAll && focused) {
            Render2DUtility.drawRoundedRect(inputX + 5.0F, inputY + 4.0F, Math.max(2.0F, inputWidth - 10.0F), INPUT_HEIGHT - 8.0F, 2.0F, 0x443D81F7);
        }

        inputFont.drawString(displayText, inputX + 6.0F, inputY + centeredTextY(INPUT_HEIGHT, inputFont), focused ? TEXT : TEXT_SUBTLE);
        if (focused && shouldDrawCursor()) {
            float cursorX = cursorX(inputFont, text, displayText);
            Render2DUtility.drawRect(cursorX, inputY + 4.0F, 1.0F, INPUT_HEIGHT - 8.0F, TEXT);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }

        focused = isInside(mouseX, mouseY, inputX, inputY, inputWidth, INPUT_HEIGHT);
        selectedAll = false;
        if (focused) {
            cursor = cursorAt(mouseX);
        }
        return focused;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!focused) {
            return false;
        }

        if (event.isSelectAll()) {
            selectedAll = true;
            cursor = safeValue().length();
            return true;
        }
        if (event.isCopy()) {
            setClipboard(safeValue());
            return true;
        }
        if (event.isCut()) {
            setClipboard(safeValue());
            setText("");
            return true;
        }
        if (event.isPaste()) {
            insert(getClipboard());
            return true;
        }

        switch (event.key()) {
            case GLFW_KEY_BACKSPACE -> {
                delete(-1);
                return true;
            }
            case GLFW_KEY_DELETE -> {
                delete(1);
                return true;
            }
            case GLFW_KEY_HOME -> {
                cursor = 0;
                selectedAll = false;
                return true;
            }
            case GLFW_KEY_END -> {
                cursor = safeValue().length();
                selectedAll = false;
                return true;
            }
            case GLFW_KEY_ENTER, GLFW_KEY_KP_ENTER, GLFW_KEY_ESCAPE -> {
                blur();
                return true;
            }
            default -> {
                if (event.isLeft()) {
                    cursor = Math.max(0, cursor - 1);
                    selectedAll = false;
                    return true;
                }
                if (event.isRight()) {
                    cursor = Math.min(safeValue().length(), cursor + 1);
                    selectedAll = false;
                    return true;
                }
                return false;
            }
        }
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!focused || !event.isAllowedChatCharacter()) {
            return false;
        }

        insert(event.codepointAsString());
        return true;
    }

    @Override
    public void blur() {
        focused = false;
        selectedAll = false;
    }

    private void insert(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }

        String current = safeValue();
        if (selectedAll) {
            current = "";
            cursor = 0;
        }

        cursor = Math.max(0, Math.min(cursor, current.length()));
        setText(current.substring(0, cursor) + text + current.substring(cursor));
        cursor += text.length();
        selectedAll = false;
    }

    private void delete(int direction) {
        String current = safeValue();
        if (selectedAll) {
            setText("");
            return;
        }

        if (direction < 0 && cursor > 0) {
            int previous = current.offsetByCodePoints(cursor, -1);
            setText(current.substring(0, previous) + current.substring(cursor));
            cursor = previous;
        } else if (direction > 0 && cursor < current.length()) {
            int next = current.offsetByCodePoints(cursor, 1);
            setText(current.substring(0, cursor) + current.substring(next));
        }
    }

    private void setText(String text) {
        stringValue.setValue(text);
        cursor = Math.max(0, Math.min(cursor, safeValue().length()));
        selectedAll = false;
    }

    private int cursorAt(double mouseX) {
        FontRenderer inputFont = font(9.0F);
        String text = safeValue();
        float localX = (float)mouseX - inputX - 6.0F;
        int best = 0;
        for (int i = 1; i <= text.length(); i++) {
            if (inputFont.getStringWidth(text.substring(0, i)) <= localX) {
                best = i;
            }
        }
        return Math.max(0, Math.min(text.length(), best));
    }

    private float cursorX(FontRenderer renderer, String text, String displayText) {
        if (!displayText.equals(text)) {
            return inputX + inputWidth - 6.0F;
        }

        return inputX + 6.0F + renderer.getStringWidth(text.substring(0, Math.max(0, Math.min(cursor, text.length()))));
    }

    private String displayText(FontRenderer renderer, String text, float maxWidth) {
        if (renderer.getStringWidth(text) <= maxWidth) {
            return text;
        }

        String suffix = "...";
        int start = 0;
        while (start < text.length() && renderer.getStringWidth(suffix + text.substring(start)) > maxWidth) {
            start++;
        }
        return suffix + text.substring(start);
    }

    private String safeValue() {
        String value = stringValue.getValue();
        return value == null ? "" : value;
    }

    private boolean shouldDrawCursor() {
        return (System.currentTimeMillis() / 500L) % 2L == 0L;
    }

    private String getClipboard() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null ? "" : minecraft.keyboardHandler.getClipboard();
    }

    private void setClipboard(String text) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.keyboardHandler.setClipboard(text);
        }
    }
}
