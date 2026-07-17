package io.github.seraphina.nyx.client.ui.alt;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;
import io.github.seraphina.nyx.client.alt.AltManager;
import io.github.seraphina.nyx.client.alt.MicrosoftAuth;
import io.github.seraphina.nyx.client.manager.FontManager;
import io.github.seraphina.nyx.client.ui.LuaScreen;
import io.github.seraphina.nyx.client.ui.mainui.MainUI;
import io.github.seraphina.nyx.client.utility.Render2DUtility;
import io.github.seraphina.nyx.client.utility.font.FontRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;
import org.luaj.vm2.LuaValue;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static io.github.seraphina.nyx.client.utility.MathUtility.*;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_END;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_F5;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_HOME;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;

public final class AltManagerScreen extends LuaScreen {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT);

    private static final float DEFAULT_FRAME_SECONDS = 1.0F / 60.0F;
    private static final float MAX_FRAME_SECONDS = 1.0F / 20.0F;
    private static final float TRANSITION_SECONDS = 0.92F;
    private static final float HOVER_SPEED = 16.0F;
    private static final float SCROLL_STEP = 38.0F;
    private static final float FLIP_DEGREES = 180.0F;
    private static final float FLIP_EDGE_MIN_SCALE = 0.075F;

    private static final float MAIN_PANEL_MAX_WIDTH = 301.0F;
    private static final float MAIN_PANEL_MIN_WIDTH = 175.0F;
    private static final float MAIN_PANEL_MAX_HEIGHT = 312.0F;
    private static final float MAIN_PANEL_MIN_HEIGHT = 180.0F;
    private static final float PANEL_MAX_WIDTH = 620.0F;
    private static final float PANEL_MIN_WIDTH = 320.0F;
    private static final float PANEL_MAX_HEIGHT = 388.0F;
    private static final float PANEL_MIN_HEIGHT = 184.0F;
    private static final float TARGET_PANEL_WIDTH_SCALE = 0.8F;
    private static final float TARGET_PANEL_HEIGHT_SCALE = 1.1F;
    private static final float PANEL_RADIUS = 18.0F;
    private static final float PANEL_BLUR_RADIUS = 18.0F;
    private static final float PANEL_BORDER_WIDTH = 3.0F;
    private static final float PANEL_PADDING = 16.0F;
    private static final float HEADER_HEIGHT = 58.0F;
    private static final float ROW_HEIGHT = 58.0F;
    private static final float ROW_GAP = 8.0F;
    private static final float ICON_SIZE = 42.0F;
    private static final float ACTION_BUTTON_HEIGHT = 30.0F;
    private static final float ACTION_BUTTON_GAP = 8.0F;
    private static final float ACTION_BUTTON_TOP_GAP = 14.0F;

    private static final float MAIN_BUTTON_HEIGHT = 34.0F;
    private static final float MAIN_BUTTON_MIN_HEIGHT = 28.0F;
    private static final float MAIN_BUTTON_GAP = 10.0F;
    private static final float MAIN_BUTTON_MIN_GAP = 7.0F;
    private static final float MAIN_BUTTON_MAX_INSET = 32.0F;
    private static final float MAIN_BUTTON_MIN_INSET = 18.0F;

    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_MUTED = 0xFFE2E6EF;
    private static final int TEXT_SUBTLE = 0xFFA8AFBE;
    private static final int TEXT_DIM = 0xFF687181;
    private static final int TEXT_DISABLED = 0xFF687181;
    private static final int TITLE_SHADOW = 0xAA000000;
    private static final int PANEL_BLUR = 0xE6FFFFFF;
    private static final int PANEL_BACKGROUND = 0xB80A0C12;
    private static final int PANEL_BORDER = 0x66FFFFFF;
    private static final int ROW_BACKGROUND = 0x9913161E;
    private static final int ROW_HOVER = 0xBB1A1E29;
    private static final int ROW_SELECTED = 0x993D81F7;
    private static final int CONTROL_BACKGROUND = 0xAA0E1118;
    private static final int CONTROL_HOVER = 0xD7191D28;
    private static final int CONTROL_BORDER = 0x22FFFFFF;
    private static final int CONTROL_BORDER_HOVER = 0x663D81F7;
    private static final int ACCENT = 0xFF3D81F7;

    private static final String MAIN_TITLE = "Nyx Client";
    private static final String ALT_TITLE = "Alt Manager";
    private static final String[] MAIN_BUTTON_LABELS = {"Single Player", "Muti Player", "Alt Manager", "Option", "Exit"};

    private final Screen lastScreen;
    private final List<AccountEntry> entries = new ArrayList<>();
    private final List<ActionButton> actionButtons = new ArrayList<>();

    private @Nullable String selectedKey;
    private AltManager.Account deletingAccount;
    private int selectedIndex = -1;
    private float scroll;
    private float maxScroll;
    private long lastFrameNanos;
    private float frameSeconds = DEFAULT_FRAME_SECONDS;
    private float transitionProgress;
    private boolean exiting;
    private boolean switchingBack;
    private boolean accountsLoaded;

    public AltManagerScreen(Screen lastScreen) {
        super("nyxclient:ui/screen/altmanager.lua", Component.literal(ALT_TITLE));
        this.lastScreen = lastScreen;
        initActionButtons();
    }

    public static void open(Screen lastScreen) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.setScreen(new AltManagerScreen(lastScreen));
        }
    }

    @Override
    protected void init() {
        super.init();
        this.lastFrameNanos = 0L;
        if (!this.accountsLoaded) {
            reloadAccounts();
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        updateFrameTime();
        updateTransition();
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.switchingBack) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return super.keyPressed(event);
    }

    @Override
    protected void appendLuaState(Map<String, Object> state) {
        List<Map<String, Object>> accounts = new ArrayList<>();
        for (int index = 0; index < this.entries.size(); index++) {
            AccountEntry entry = this.entries.get(index);
            AltManager.Account account = entry.account;
            Map<String, Object> accountState = new LinkedHashMap<>();
            accountState.put("index", index + 1);
            accountState.put("name", account.getName());
            accountState.put("type_line", accountTypeLine(account));
            accountState.put("info_line", accountInfoLine(account));
            accountState.put("current", AltManager.isCurrent(account));
            accountState.put("selected", index == this.selectedIndex);
            accounts.add(accountState);
        }

        Minecraft minecraft = Minecraft.getInstance();
        String currentName = minecraft == null || minecraft.getUser() == null ? "" : minecraft.getUser().getName();
        state.put("accounts", accounts);
        state.put("loaded", this.accountsLoaded);
        state.put("interactive", isInteractive());
        state.put("transition_progress", this.transitionProgress);
        state.put("exiting", this.exiting);
        state.put("current_name", currentName);
        state.put("login_label", loginButtonLabel());
        state.put("can_login", selectedAccount() != null && !AltManager.isCurrent(selectedAccount()));
        state.put("can_delete", selectedAccount() != null);
    }

    @Override
    protected boolean onLuaAction(String action, LuaValue payload) {
        if (!isInteractive() && !action.equals("back")) {
            return true;
        }

        return switch (action) {
            case "select" -> {
                selectAccount(payload.checkint() - 1);
                yield true;
            }
            case "login" -> {
                loginSelectedAccount();
                yield true;
            }
            case "login_index" -> {
                selectAccount(payload.checkint() - 1);
                loginSelectedAccount();
                yield true;
            }
            case "add" -> {
                openAddAccount();
                yield true;
            }
            case "delete" -> {
                deleteSelectedAccount();
                yield true;
            }
            case "refresh" -> {
                reloadAccounts();
                yield true;
            }
            case "back" -> {
                beginBackTransition();
                yield true;
            }
            default -> false;
        };
    }

    @Override
    protected void renderLuaCustom(String name, LuaValue[] args) {
        if (!name.equals("account_icon") || args.length < 5) {
            return;
        }
        int index = args[0].checkint() - 1;
        if (index < 0 || index >= this.entries.size()) {
            return;
        }
        renderAccountIcon(
            this.entries.get(index),
            (float)args[1].checkdouble(),
            (float)args[2].checkdouble(),
            (float)args[3].checkdouble(),
            (float)args[4].checkdouble()
        );
    }

    @Override
    public void onClose() {
        beginBackTransition();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        MainUI.pauseSharedBackgroundPlayback();
        super.removed();
    }

    private void initActionButtons() {
        this.actionButtons.clear();
        this.actionButtons.add(new ActionButton(this::loginButtonLabel, () -> selectedAccount() != null, this::loginSelectedAccount));
        this.actionButtons.add(new ActionButton("Add", () -> true, this::openAddAccount));
        this.actionButtons.add(new ActionButton("Delete", () -> selectedAccount() != null, this::deleteSelectedAccount));
        this.actionButtons.add(new ActionButton("Refresh", () -> true, this::reloadAccounts));
        this.actionButtons.add(new ActionButton("Back", () -> true, this::beginBackTransition));
    }

    private String loginButtonLabel() {
        AltManager.Account account = selectedAccount();
        return account != null && AltManager.isCurrent(account) ? "Current" : "Login";
    }

    private void reloadAccounts() {
        String previousKey = currentSelectionKey();
        AltManager.load();

        this.entries.clear();
        for (AltManager.Account account : AltManager.getAccounts()) {
            this.entries.add(new AccountEntry(account));
        }

        this.accountsLoaded = true;
        this.selectedIndex = -1;

        String preferredKey = previousKey != null ? previousKey : this.selectedKey;
        if (preferredKey != null) {
            for (int i = 0; i < this.entries.size(); i++) {
                if (preferredKey.equals(this.entries.get(i).key())) {
                    this.selectedIndex = i;
                    break;
                }
            }
        }

        if (this.selectedIndex < 0 && !this.entries.isEmpty()) {
            this.selectedIndex = 0;
        }

        this.selectedKey = currentSelectionKey();
        this.scroll = clamp(this.scroll, 0.0F, this.maxScroll);
    }

    private void selectAccount(int index) {
        if (index < 0 || index >= this.entries.size()) {
            return;
        }

        this.selectedIndex = index;
        this.selectedKey = this.entries.get(index).key();
    }

    private AltManager.Account selectedAccount() {
        if (this.selectedIndex < 0 || this.selectedIndex >= this.entries.size()) {
            return null;
        }
        return this.entries.get(this.selectedIndex).account;
    }

    private @Nullable String currentSelectionKey() {
        AltManager.Account account = selectedAccount();
        return account == null ? null : keyFor(account);
    }

    private void loginSelectedAccount() {
        AltManager.Account account = selectedAccount();
        if (account == null) {
            return;
        }

        if (AltManager.login(account)) {
            this.selectedKey = keyFor(account);
            reloadAccounts();
        }
    }

    private void openAddAccount() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.setScreen(new AddAccountScreen(this));
        }
    }

    private void deleteSelectedAccount() {
        Minecraft minecraft = Minecraft.getInstance();
        AltManager.Account account = selectedAccount();
        if (minecraft == null || account == null) {
            return;
        }

        this.deletingAccount = account;
        minecraft.setScreen(new ConfirmScreen(
            this::deleteAccountCallback,
            Component.literal("Delete account"),
            Component.literal("Remove " + account.getName() + " from Alt Manager?"),
            Component.literal("Delete"),
            CommonComponents.GUI_CANCEL
        ));
    }

    private void deleteAccountCallback(boolean confirmed) {
        if (confirmed && this.deletingAccount != null) {
            AltManager.remove(this.deletingAccount);
            this.selectedKey = null;
        }

        this.deletingAccount = null;
        reloadAccounts();
        returnToThisScreen();
    }

    private void returnToThisScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.setScreen(this);
        }
    }

    private void beginBackTransition() {
        if (this.exiting || this.switchingBack) {
            return;
        }
        this.exiting = true;
    }

    private void updateFrameTime() {
        long now = System.nanoTime();
        if (this.lastFrameNanos == 0L) {
            this.frameSeconds = DEFAULT_FRAME_SECONDS;
        } else {
            this.frameSeconds = clamp((now - this.lastFrameNanos) / 1_000_000_000.0F, 0.0F, MAX_FRAME_SECONDS);
        }
        this.lastFrameNanos = now;
    }

    private void updateTransition() {
        float delta = this.frameSeconds / TRANSITION_SECONDS;
        this.transitionProgress = clamp(this.transitionProgress + (this.exiting ? -delta : delta), 0.0F, 1.0F);

        if (this.exiting && this.transitionProgress <= 0.0F && !this.switchingBack) {
            this.switchingBack = true;
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null) {
                minecraft.setScreen(this.lastScreen);
            }
        }
    }

    private boolean isInteractive() {
        return !this.exiting && this.transitionProgress >= 0.985F;
    }

    private void renderFlippingPanel(PanelBounds panel, int mouseX, int mouseY) {
        float flipProgress = easeInOutCubic(panelProgress());
        if (flipProgress <= 0.001F) {
            renderPanelSurface(panel, true);
            renderMainButtonGhosts(panel, 1.0F);
            return;
        }
        if (flipProgress >= 0.999F) {
            renderPanelSurface(panel, true);
            renderAccountArea(panel, mouseX, mouseY, 1.0F);
            return;
        }

        float degrees = flipProgress * FLIP_DEGREES;
        float projectionScale = Render2DUtility.verticalFlipScale(degrees);
        if (projectionScale <= FLIP_EDGE_MIN_SCALE) {
            renderFlipEdge(panel);
            return;
        }

        boolean backFace = Render2DUtility.isVerticalFlipBackFace(degrees);
        float faceAlpha = easeOutCubic(clamp((projectionScale - FLIP_EDGE_MIN_SCALE) / (1.0F - FLIP_EDGE_MIN_SCALE), 0.0F, 1.0F));
        Render2DUtility.withVerticalPerspectiveFlip(degrees, panel.centerX(), panel.centerY(), panel.width, FLIP_EDGE_MIN_SCALE, () -> {
            renderPanelSurface(panel, false);
            renderFlipShade(panel, degrees);
            if (backFace) {
                Render2DUtility.withHorizontalVertexReflection(panel.centerX(), () ->
                    renderAccountArea(panel, mouseX, mouseY, faceAlpha)
                );
            } else {
                renderMainButtonGhosts(panel, faceAlpha);
            }
        });
    }

    private void renderPanelSurface(PanelBounds panel, boolean blurSurface) {
        if (blurSurface) {
            Render2DUtility.drawGaussianBlurredPanel(
                panel.x,
                panel.y,
                panel.width,
                panel.height,
                PANEL_RADIUS,
                PANEL_BLUR_RADIUS,
                PANEL_BLUR,
                PANEL_BACKGROUND,
                PANEL_BORDER_WIDTH,
                PANEL_BORDER
            );
            return;
        }

        Render2DUtility.drawRoundedRect(panel.x, panel.y, panel.width, panel.height, PANEL_RADIUS, PANEL_BACKGROUND);
        Render2DUtility.drawOutlineRoundedRect(panel.x, panel.y, panel.width, panel.height, PANEL_RADIUS, PANEL_BORDER_WIDTH, PANEL_BORDER);
    }

    private void renderFlipShade(PanelBounds panel, float degrees) {
        float shadeAlpha = (float)Math.sin(Math.toRadians(degrees)) * 0.34F;
        if (shadeAlpha <= 0.001F) {
            return;
        }

        Render2DUtility.drawRoundedHorizontalGradientRect(
            panel.x,
            panel.y,
            panel.width,
            panel.height,
            PANEL_RADIUS,
            Render2DUtility.applyOpacity(0x99000000, shadeAlpha),
            Render2DUtility.applyOpacity(0x22000000, shadeAlpha)
        );
    }

    private void renderFlipEdge(PanelBounds panel) {
        float edgeWidth = Math.max(2.0F, PANEL_BORDER_WIDTH);
        float x = panel.centerX() - edgeWidth * 0.5F;
        Render2DUtility.drawDropShadow(x, panel.y, edgeWidth, panel.height, edgeWidth * 0.5F, 0.0F, 5.0F, 16.0F, 0x77000000);
        Render2DUtility.drawRoundedRect(x, panel.y, edgeWidth, panel.height, edgeWidth * 0.5F, PANEL_BACKGROUND);
        Render2DUtility.drawRoundedRect(x + edgeWidth * 0.5F - 0.5F, panel.y + PANEL_RADIUS, 1.0F,
            Math.max(0.0F, panel.height - PANEL_RADIUS * 2.0F), 0.5F, PANEL_BORDER);
    }

    private void renderTitles(PanelBounds mainPanel, PanelBounds currentPanel) {
        FontRenderer mainTitleFont = displayFont(26.0F);
        float mainTitleAlpha = 1.0F - easeOutCubic(phase(0.12F, 0.42F, this.transitionProgress));
        drawCenteredTitle(mainTitleFont, MAIN_TITLE, mainPanel.centerX(), Math.max(8.0F, mainPanel.y - 44.0F), mainTitleAlpha);

        FontRenderer altTitleFont = displayFont(24.0F);
        float altTitleAlpha = easeOutCubic(phase(0.54F, 0.88F, this.transitionProgress));
        drawCenteredTitle(altTitleFont, ALT_TITLE, currentPanel.centerX(), Math.max(8.0F, currentPanel.y - 38.0F), altTitleAlpha);
    }

    private void drawCenteredTitle(FontRenderer font, String text, float centerX, float y, float alpha) {
        if (alpha <= 0.001F) {
            return;
        }

        font.drawCenteredString(text, centerX + 1.0F, y + 1.0F, Render2DUtility.applyOpacity(TITLE_SHADOW, alpha));
        font.drawCenteredString(text, centerX, y, Render2DUtility.applyOpacity(TEXT, alpha));
    }

    private void renderMainButtonGhosts(PanelBounds panel, float alpha) {
        if (alpha <= 0.001F) {
            return;
        }

        float inset = clamp(panel.width * 0.13F, MAIN_BUTTON_MIN_INSET, MAIN_BUTTON_MAX_INSET);
        float buttonWidth = Math.max(1.0F, panel.width - inset * 2.0F);
        float buttonHeight = Math.max(1.0F, clamp(panel.height * 0.11F, MAIN_BUTTON_MIN_HEIGHT, MAIN_BUTTON_HEIGHT));
        float buttonGap = clamp(panel.height * 0.032F, MAIN_BUTTON_MIN_GAP, MAIN_BUTTON_GAP);
        float totalHeight = MAIN_BUTTON_LABELS.length * buttonHeight + (MAIN_BUTTON_LABELS.length - 1) * buttonGap;
        float buttonY = panel.y + (panel.height - totalHeight) * 0.5F;

        for (String label : MAIN_BUTTON_LABELS) {
            float x = panel.centerX() - buttonWidth * 0.5F;
            renderGhostButton(label, x, buttonY, buttonWidth, buttonHeight, alpha);
            buttonY += buttonHeight + buttonGap;
        }
    }

    private void renderGhostButton(String label, float x, float y, float width, float height, float alpha) {
        if (width <= 1.0F || height <= 1.0F || alpha <= 0.001F) {
            return;
        }

        Render2DUtility.drawDropShadow(x, y, width, height, 8.0F, 0.0F, 5.0F, 12.0F, Render2DUtility.applyOpacity(0x55000000, alpha * 0.55F));
        Render2DUtility.drawRoundedRect(x, y, width, height, 8.0F, Render2DUtility.applyOpacity(CONTROL_BACKGROUND, alpha));
        Render2DUtility.drawOutlineRoundedRect(x, y, width, height, 8.0F, 1.0F, Render2DUtility.applyOpacity(CONTROL_BORDER, alpha));

        FontRenderer font = textFont(12.0F);
        font.drawCenteredString(
            trimToWidth(font, label, width - 18.0F),
            x + width * 0.5F,
            y + (height - font.getLineHeight()) * 0.5F,
            Render2DUtility.applyOpacity(TEXT_MUTED, alpha)
        );
    }

    private void renderAccountArea(PanelBounds panel, int mouseX, int mouseY, float faceAlpha) {
        if (faceAlpha <= 0.001F) {
            return;
        }

        float listProgress = listProgress();
        if (listProgress <= 0.001F) {
            return;
        }

        float alpha = easeOutCubic(listProgress) * faceAlpha;
        renderPanelHeader(panel, alpha);

        float listX = panel.x + PANEL_PADDING;
        float listY = panel.y + HEADER_HEIGHT;
        float listWidth = panel.width - PANEL_PADDING * 2.0F;
        float listHeight = Math.max(0.0F, panel.height - HEADER_HEIGHT - PANEL_PADDING);
        updateScrollLimit(listHeight);

        Render2DUtility.withClip(listX, listY, listWidth, listHeight, () -> {
            if (!this.accountsLoaded) {
                renderCenteredStatus("Loading accounts...", listX, listY, listWidth, listHeight, alpha);
            } else if (this.entries.isEmpty()) {
                renderCenteredStatus("No accounts found", listX, listY, listWidth, listHeight, alpha);
            } else {
                renderAccountRows(listX, listY, listWidth, listHeight, mouseX, mouseY, alpha, listProgress);
            }
        });

        renderScrollBar(listX, listY, listWidth, listHeight, alpha);
    }

    private void renderPanelHeader(PanelBounds panel, float alpha) {
        FontRenderer heading = displayFont(17.0F);
        FontRenderer meta = textFont(10.0F);
        String count = this.entries.size() == 1 ? "1 saved account" : this.entries.size() + " saved accounts";
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.getUser() != null) {
            count += " - Current: " + minecraft.getUser().getName();
        }

        float x = panel.x + PANEL_PADDING;
        heading.drawString("Accounts", x, panel.y + 18.0F, Render2DUtility.applyOpacity(TEXT, alpha));
        meta.drawString(trimToWidth(meta, count, panel.width - PANEL_PADDING * 2.0F), x, panel.y + 39.0F, Render2DUtility.applyOpacity(TEXT_DIM, alpha));
    }

    private void renderAccountRows(float listX, float listY, float listWidth, float listHeight, int mouseX, int mouseY, float alpha, float listProgress) {
        FontRenderer nameFont = textFont(12.0F);
        FontRenderer metaFont = textFont(9.0F);
        FontRenderer infoFont = textFont(9.0F);
        for (int i = 0; i < this.entries.size(); i++) {
            float rowY = stackedItemY(listY, i, ROW_HEIGHT, ROW_GAP, this.scroll);
            if (rowY > listY + listHeight || rowY + ROW_HEIGHT < listY) {
                continue;
            }

            float rowProgress = clamp(listProgress * 1.18F - i * 0.045F, 0.0F, 1.0F);
            rowProgress = easeOutCubic(rowProgress);
            if (rowProgress <= 0.001F) {
                continue;
            }

            AccountEntry entry = this.entries.get(i);
            AltManager.Account account = entry.account;
            boolean selected = i == this.selectedIndex;
            boolean current = AltManager.isCurrent(account);
            boolean hovered = isInteractive() && isInside(mouseX, mouseY, listX, rowY, listWidth, ROW_HEIGHT);
            float visibleWidth = listWidth * rowProgress;
            float rowX = listX + (listWidth - visibleWidth) * 0.5F;
            float rowAlpha = alpha * rowProgress;
            int fill = selected ? ROW_SELECTED : Render2DUtility.mix(ROW_BACKGROUND, ROW_HOVER, hovered ? 1.0F : 0.0F);
            int border = selected || current ? ACCENT : CONTROL_BORDER;

            Render2DUtility.drawRoundedRect(rowX, rowY, visibleWidth, ROW_HEIGHT, 8.0F, Render2DUtility.applyOpacity(fill, rowAlpha));
            Render2DUtility.drawOutlineRoundedRect(rowX, rowY, visibleWidth, ROW_HEIGHT, 8.0F, 1.0F, Render2DUtility.applyOpacity(border, rowAlpha));

            if (visibleWidth < 78.0F) {
                continue;
            }

            float iconX = rowX + 9.0F;
            float iconY = rowY + (ROW_HEIGHT - ICON_SIZE) * 0.5F;
            renderAccountIcon(entry, iconX, iconY, ICON_SIZE, rowAlpha);

            float textX = iconX + ICON_SIZE + 12.0F;
            float textRight = rowX + visibleWidth - 12.0F;
            float textWidth = Math.max(1.0F, textRight - textX);
            nameFont.drawString(trimToWidth(nameFont, account.getName(), textWidth), textX, rowY + 10.0F,
                Render2DUtility.applyOpacity(TEXT, rowAlpha));
            metaFont.drawString(trimToWidth(metaFont, accountTypeLine(account), textWidth), textX, rowY + 29.0F,
                Render2DUtility.applyOpacity(TEXT_DIM, rowAlpha));
            infoFont.drawString(trimToWidth(infoFont, accountInfoLine(account), textWidth), textX, rowY + 43.0F,
                Render2DUtility.applyOpacity(current ? 0xFFD7E6FF : TEXT_SUBTLE, rowAlpha));
        }
    }

    private void renderAccountIcon(AccountEntry entry, float x, float y, float size, float alpha) {
        Render2DUtility.drawRoundedRect(x, y, size, size, 6.0F, Render2DUtility.applyOpacity(0xFF252A35, alpha));
        Render2DUtility.drawPlayerHead(entry.skin(), x, y, size, 6.0F, Render2DUtility.applyOpacity(0xFFFFFFFF, alpha));
        Render2DUtility.drawOutlineRoundedRect(x, y, size, size, 6.0F, 1.0F, Render2DUtility.applyOpacity(0x44FFFFFF, alpha));
    }

    private void renderCenteredStatus(String message, float x, float y, float width, float height, float alpha) {
        FontRenderer font = textFont(12.0F);
        font.drawCenteredString(
            trimToWidth(font, message, width - 24.0F),
            x + width * 0.5F,
            y + (height - font.getLineHeight()) * 0.5F,
            Render2DUtility.applyOpacity(TEXT_DIM, alpha)
        );
    }

    private void renderScrollBar(float listX, float listY, float listWidth, float listHeight, float alpha) {
        if (this.maxScroll <= 0.001F || this.entries.isEmpty()) {
            return;
        }

        float contentHeight = stackedContentHeight(this.entries.size(), ROW_HEIGHT, ROW_GAP);
        float barHeight = clamp(listHeight * (listHeight / Math.max(listHeight, contentHeight)), 24.0F, listHeight);
        float barY = listY + (listHeight - barHeight) * (this.scroll / this.maxScroll);
        Render2DUtility.drawRoundedRect(
            listX + listWidth - 4.0F,
            barY,
            3.0F,
            barHeight,
            1.5F,
            Render2DUtility.applyOpacity(0x66FFFFFF, alpha)
        );
    }

    private void renderUserCardTransition() {
        float cardWidth = MainUI.sharedUserCardWidth(this.width);
        float hiddenOffset = -cardWidth - 28.0F;
        float offsetX;
        float alpha;

        if (this.exiting) {
            float progress = phase(0.0F, 0.42F, 1.0F - this.transitionProgress);
            float eased = easeOutBack(progress);
            offsetX = lerp(hiddenOffset, 0.0F, eased);
            alpha = clamp(progress * 1.4F, 0.0F, 1.0F);
        } else {
            float progress = phase(0.0F, 0.42F, this.transitionProgress);
            if (progress <= 0.22F) {
                offsetX = lerp(0.0F, 12.0F, easeOutCubic(progress / 0.22F));
            } else {
                offsetX = lerp(12.0F, hiddenOffset, easeInCubic((progress - 0.22F) / 0.78F));
            }
            alpha = 1.0F - easeOutCubic(phase(0.52F, 1.0F, progress));
        }

        if (alpha > 0.001F) {
            MainUI.renderSharedUserCard(this.width, this.height, offsetX, alpha);
        }
    }

    private void layoutActionButtons(PanelBounds panel) {
        if (this.actionButtons.isEmpty()) {
            return;
        }

        float y = panel.y + panel.height + ACTION_BUTTON_TOP_GAP;
        int firstRowCount = Math.min(3, this.actionButtons.size());
        int secondRowCount = this.actionButtons.size() - firstRowCount;
        float rowOneWidth = (panel.width - ACTION_BUTTON_GAP * (firstRowCount - 1)) / firstRowCount;

        for (int i = 0; i < firstRowCount; i++) {
            ActionButton button = this.actionButtons.get(i);
            button.setBounds(panel.x + i * (rowOneWidth + ACTION_BUTTON_GAP), y, rowOneWidth, ACTION_BUTTON_HEIGHT);
        }

        if (secondRowCount <= 0) {
            return;
        }

        float rowTwoY = y + ACTION_BUTTON_HEIGHT + ACTION_BUTTON_GAP;
        float rowTwoWidth = (panel.width - ACTION_BUTTON_GAP * (secondRowCount - 1)) / secondRowCount;
        float totalWidth = rowTwoWidth * secondRowCount + ACTION_BUTTON_GAP * (secondRowCount - 1);
        float rowTwoX = panel.x + (panel.width - totalWidth) * 0.5F;
        for (int i = 0; i < secondRowCount; i++) {
            ActionButton button = this.actionButtons.get(i + firstRowCount);
            button.setBounds(rowTwoX + i * (rowTwoWidth + ACTION_BUTTON_GAP), rowTwoY, rowTwoWidth, ACTION_BUTTON_HEIGHT);
        }
    }

    private void renderActionButtons(int mouseX, int mouseY) {
        float alpha;
        float scale;
        if (this.exiting) {
            float exit = 1.0F - this.transitionProgress;
            if (exit < 0.18F) {
                scale = 1.0F + 0.08F * easeOutCubic(exit / 0.18F);
                alpha = 1.0F;
            } else {
                float shrink = easeInCubic(phase(0.18F, 0.50F, exit));
                scale = 1.08F * (1.0F - shrink);
                alpha = 1.0F - shrink;
            }
        } else {
            float buttonProgress = easeOutBack(phase(0.72F, 1.0F, this.transitionProgress));
            scale = 0.86F + 0.14F * buttonProgress;
            alpha = buttonProgress;
        }

        if (alpha <= 0.001F || scale <= 0.001F) {
            return;
        }

        for (ActionButton button : this.actionButtons) {
            renderActionButton(button, mouseX, mouseY, alpha, scale);
        }
    }

    private void renderActionButton(ActionButton button, int mouseX, int mouseY, float alpha, float scale) {
        boolean active = button.active();
        boolean hovered = isInteractive() && active && button.contains(mouseX, mouseY);
        button.hoverProgress = animateExp(button.hoverProgress, hovered ? 1.0F : 0.0F, HOVER_SPEED, this.frameSeconds);

        float opacity = alpha * (active ? 1.0F : 0.48F);
        int fill = Render2DUtility.mix(CONTROL_BACKGROUND, CONTROL_HOVER, button.hoverProgress);
        int border = Render2DUtility.mix(CONTROL_BORDER, CONTROL_BORDER_HOVER, button.hoverProgress);
        int color = active ? Render2DUtility.mix(TEXT_MUTED, TEXT, button.hoverProgress) : TEXT_DISABLED;

        Render2DUtility.withScale(scale, scale, button.centerX(), button.centerY(), () -> {
            Render2DUtility.drawDropShadow(button.x, button.y, button.width, button.height, 8.0F, 0.0F, 5.0F, 12.0F,
                Render2DUtility.applyOpacity(0x55000000, opacity * (0.55F + button.hoverProgress * 0.45F)));
            Render2DUtility.drawRoundedRect(button.x, button.y, button.width, button.height, 8.0F, Render2DUtility.applyOpacity(fill, opacity));
            Render2DUtility.drawOutlineRoundedRect(button.x, button.y, button.width, button.height, 8.0F, 1.0F,
                Render2DUtility.applyOpacity(border, opacity));

            FontRenderer font = textFont(11.0F);
            font.drawCenteredString(
                trimToWidth(font, button.label(), button.width - 16.0F),
                button.centerX(),
                button.y + (button.height - font.getLineHeight()) * 0.5F,
                Render2DUtility.applyOpacity(color, opacity)
            );
        });
    }

    private String accountTypeLine(AltManager.Account account) {
        String type = accountTypeLabel(account.getType());
        UUID uuid = account.getUuidValue();
        return uuid == null ? type : type + " - " + uuid.toString().substring(0, 8);
    }

    private String accountInfoLine(AltManager.Account account) {
        if (AltManager.isCurrent(account)) {
            return "Current account";
        }

        long lastUsed = account.getLastUsed();
        if (lastUsed <= 0L) {
            return "Never used";
        }

        ZonedDateTime time = ZonedDateTime.ofInstant(Instant.ofEpochMilli(lastUsed), ZoneId.systemDefault());
        return "Last used " + DATE_FORMAT.withLocale(Locale.getDefault()).format(time);
    }

    private int accountRowAt(double mouseX, double mouseY) {
        PanelBounds panel = currentPanelBounds(mainPanelBounds(), targetPanelBounds());
        float listX = panel.x + PANEL_PADDING;
        float listY = panel.y + HEADER_HEIGHT;
        float listWidth = panel.width - PANEL_PADDING * 2.0F;
        float listHeight = Math.max(0.0F, panel.height - HEADER_HEIGHT - PANEL_PADDING);
        if (!isInside(mouseX, mouseY, listX, listY, listWidth, listHeight)) {
            return -1;
        }

        for (int i = 0; i < this.entries.size(); i++) {
            float rowY = stackedItemY(listY, i, ROW_HEIGHT, ROW_GAP, this.scroll);
            if (isInside(mouseX, mouseY, listX, rowY, listWidth, ROW_HEIGHT)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isInsideList(double mouseX, double mouseY) {
        PanelBounds panel = currentPanelBounds(mainPanelBounds(), targetPanelBounds());
        float listX = panel.x + PANEL_PADDING;
        float listY = panel.y + HEADER_HEIGHT;
        float listWidth = panel.width - PANEL_PADDING * 2.0F;
        float listHeight = Math.max(0.0F, panel.height - HEADER_HEIGHT - PANEL_PADDING);
        return isInside(mouseX, mouseY, listX, listY, listWidth, listHeight);
    }

    private void updateScrollLimit(float listHeight) {
        this.maxScroll = Math.max(0.0F, stackedContentHeight(this.entries.size(), ROW_HEIGHT, ROW_GAP) - listHeight);
        this.scroll = clamp(this.scroll, 0.0F, this.maxScroll);
    }

    private PanelBounds mainPanelBounds() {
        float maxWidth = Math.max(1.0F, Math.min(MAIN_PANEL_MAX_WIDTH, this.width - 32.0F));
        float minWidth = Math.min(MAIN_PANEL_MIN_WIDTH, maxWidth);
        float panelWidth = clamp(this.width * 0.336F, minWidth, maxWidth);

        float maxHeight = Math.max(1.0F, Math.min(MAIN_PANEL_MAX_HEIGHT, this.height - 32.0F));
        float minHeight = Math.min(MAIN_PANEL_MIN_HEIGHT, maxHeight);
        float panelHeight = clamp(this.height * 0.432F, minHeight, maxHeight);

        return new PanelBounds((this.width - panelWidth) * 0.5F, (this.height - panelHeight) * 0.5F, panelWidth, panelHeight);
    }

    private PanelBounds targetPanelBounds() {
        float maxWidth = Math.max(1.0F, Math.min(PANEL_MAX_WIDTH, this.width - 42.0F));
        float minWidth = Math.min(PANEL_MIN_WIDTH, maxWidth);
        float basePanelWidth = clamp(this.width * 0.74F, minWidth, maxWidth);
        float panelWidth = Math.max(1.0F, basePanelWidth * TARGET_PANEL_WIDTH_SCALE);

        float controlsHeight = ACTION_BUTTON_HEIGHT * 2.0F + ACTION_BUTTON_GAP;
        float availableHeight = Math.max(1.0F, this.height - controlsHeight - ACTION_BUTTON_TOP_GAP - 76.0F);
        float maxHeight = Math.max(1.0F, Math.min(PANEL_MAX_HEIGHT, availableHeight));
        float minHeight = Math.min(PANEL_MIN_HEIGHT, maxHeight);
        float basePanelHeight = clamp(this.height * 0.58F, minHeight, maxHeight);
        float panelHeight = Math.min(availableHeight, basePanelHeight * TARGET_PANEL_HEIGHT_SCALE);
        float y = Math.max(44.0F, (this.height - panelHeight - controlsHeight - ACTION_BUTTON_TOP_GAP - 18.0F) * 0.5F);

        return new PanelBounds((this.width - panelWidth) * 0.5F, y, panelWidth, panelHeight);
    }

    private PanelBounds currentPanelBounds(PanelBounds from, PanelBounds to) {
        float progress = easeInOutCubic(panelProgress());
        return new PanelBounds(
            lerp(from.x, to.x, progress),
            lerp(from.y, to.y, progress),
            lerp(from.width, to.width, progress),
            lerp(from.height, to.height, progress)
        );
    }

    private float panelProgress() {
        return phase(0.24F, 0.74F, this.transitionProgress);
    }

    private float listProgress() {
        return phase(0.56F, 0.96F, this.transitionProgress);
    }

    private void playClickSound() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    private FontRenderer displayFont(float size) {
        return FontManager.getAppleDisplayRenderer(size);
    }

    private FontRenderer textFont(float size) {
        return FontManager.getAppleTextRenderer(size);
    }

    private String trimToWidth(FontRenderer renderer, String text, float maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0.0F) {
            return "";
        }
        if (renderer.getStringWidth(text) <= maxWidth) {
            return text;
        }

        String suffix = "...";
        float suffixWidth = renderer.getStringWidth(suffix);
        if (suffixWidth > maxWidth) {
            return "";
        }

        int end = text.length();
        while (end > 0 && renderer.getStringWidth(text.substring(0, end)) + suffixWidth > maxWidth) {
            end--;
        }
        return text.substring(0, Math.max(0, end)) + suffix;
    }

    private static String keyFor(AltManager.Account account) {
        UUID uuid = account.getUuidValue();
        return uuid != null ? uuid.toString() : account.getName().toLowerCase(Locale.ROOT);
    }

    private static boolean isValidOfflineName(String name) {
        return name != null && !name.isBlank() && StringUtil.isValidPlayerName(name);
    }

    private static String accountTypeLabel(AltManager.AccountType type) {
        if (type == null) {
            return "Unknown";
        }

        String raw = type.getName();
        if (raw == null || raw.isBlank()) {
            raw = type.name();
        }

        String[] words = raw.replace('_', ' ').replace('-', ' ').strip().toLowerCase(Locale.ROOT).split("\\s+");
        StringBuilder label = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }

            if (!label.isEmpty()) {
                label.append(' ');
            }
            label.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }

        return label.isEmpty() ? type.name() : label.toString();
    }

    private static UUID parseUuidInput(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String uuid = value.strip();
        if (uuid.length() == 32) {
            uuid = uuid.substring(0, 8) + "-"
                + uuid.substring(8, 12) + "-"
                + uuid.substring(12, 16) + "-"
                + uuid.substring(16, 20) + "-"
                + uuid.substring(20);
        }

        try {
            return UUID.fromString(uuid);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    @Override
    protected void renderBlurredBackground(GuiGraphics guiGraphics) {
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    private static final class AddAccountScreen extends LuaScreen {
        private static final int PANEL_WIDTH = 340;
        private static final int CRACKED_PANEL_HEIGHT = 214;
        private static final int CREDENTIAL_PANEL_HEIGHT = 286;
        private static final int NAME_MAX_LENGTH = 16;
        private static final int UUID_MAX_LENGTH = 36;
        private static final int ACCESS_TOKEN_MAX_LENGTH = 8192;
        private static final float MODAL_PADDING = 20.0F;
        private static final float FIELD_HEIGHT = 30.0F;
        private static final float BUTTON_HEIGHT = 30.0F;
        private static final float CONTROL_RADIUS = 8.0F;

        private final AltManagerScreen parent;
        private final TypeSelector typeSelector = new TypeSelector();
        private final TextInput nameInput = new TextInput("Player name", "Player name", NAME_MAX_LENGTH);
        private final TextInput uuidInput = new TextInput("UUID", "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx", UUID_MAX_LENGTH);
        private final TextInput accessTokenInput = new TextInput("Access token", "Paste access token", ACCESS_TOKEN_MAX_LENGTH);
        private final ModalButton addButton = new ModalButton(this::addButtonLabel);
        private final ModalButton cancelButton = new ModalButton("Cancel");
        private AltManager.AccountType selectedType = AltManager.AccountType.CRACKED;
        private @Nullable String statusMessage;
        private boolean statusError;
        private boolean microsoftLoginInProgress;
        private long lastFrameNanos;
        private float frameSeconds = DEFAULT_FRAME_SECONDS;

        private AddAccountScreen(AltManagerScreen parent) {
            super("nyxclient:ui/screen/addaccount.lua", Component.literal("Add Account"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            super.init();
            this.lastFrameNanos = 0L;
        }

        @Override
        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            updateFrameTime();
            super.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            return super.mouseClicked(event, doubleClick);
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            return super.keyPressed(event);
        }

        @Override
        public boolean charTyped(CharacterEvent event) {
            return super.charTyped(event);
        }

        @Override
        protected void appendLuaState(Map<String, Object> state) {
            state.put("type_label", typeButtonLabel());
            state.put("requires_credentials", requiresCredentials());
            state.put("uses_browser_login", usesBrowserLogin());
            state.put("needs_name_input", needsNameInput());
            state.put("name", this.nameInput.value());
            state.put("uuid", this.uuidInput.value());
            state.put("access_token", this.accessTokenInput.value());
            state.put("valid", hasValidInput());
            state.put("button_label", addButtonLabel());
            state.put("status", this.statusMessage == null ? "" : this.statusMessage);
            state.put("status_error", this.statusError);
            state.put("busy", this.microsoftLoginInProgress);
        }

        @Override
        protected boolean onLuaAction(String action, LuaValue payload) {
            return switch (action) {
                case "cycle_type" -> {
                    cycleType();
                    yield true;
                }
                case "confirm" -> {
                    confirm();
                    yield true;
                }
                case "cancel" -> {
                    returnToParent();
                    yield true;
                }
                default -> false;
            };
        }

        @Override
        protected void onLuaInputChanged(String id, String value) {
            switch (id) {
                case "name" -> this.nameInput.setValue(value);
                case "uuid" -> this.uuidInput.setValue(value);
                case "access_token" -> this.accessTokenInput.setValue(value);
                default -> {
                    return;
                }
            }
            clearStatus();
        }

        @Override
        public void onClose() {
            returnToParent();
        }

        @Override
        public boolean shouldCloseOnEsc() {
            return false;
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        private void cycleType() {
            AltManager.AccountType[] types = AltManager.AccountType.values();
            if (types.length == 0) {
                return;
            }

            int index = 0;
            for (int i = 0; i < types.length; i++) {
                if (types[i] == this.selectedType) {
                    index = i;
                    break;
                }
            }

            this.selectedType = types[(index + 1) % types.length];
            clearStatus();
            syncCredentialFields();
            focusInput(needsNameInput() ? this.nameInput : null);
        }

        private String typeButtonLabel() {
            return accountTypeLabel(this.selectedType);
        }

        private boolean requiresCredentials() {
            return this.selectedType != AltManager.AccountType.CRACKED && !usesBrowserLogin();
        }

        private boolean usesBrowserLogin() {
            return this.selectedType == AltManager.AccountType.MICROSOFT;
        }

        private boolean needsNameInput() {
            return this.selectedType == AltManager.AccountType.CRACKED || requiresCredentials();
        }

        private boolean hasValidInput() {
            if (usesBrowserLogin()) {
                return !this.microsoftLoginInProgress;
            }

            if (!isValidOfflineName(currentName())) {
                return false;
            }

            if (!requiresCredentials()) {
                return true;
            }

            return parseUuidInput(currentUuid()) != null && !currentAccessToken().isBlank();
        }

        private String currentName() {
            return this.nameInput.value().strip();
        }

        private String currentUuid() {
            return this.uuidInput.value().strip();
        }

        private String currentAccessToken() {
            return this.accessTokenInput.value().strip();
        }

        private String addButtonLabel() {
            if (!usesBrowserLogin()) {
                return "Add";
            }

            return this.microsoftLoginInProgress ? "Waiting..." : "Login";
        }

        private void confirm() {
            if (usesBrowserLogin()) {
                startMicrosoftAuth();
                return;
            }

            String name = currentName();
            if (!isValidOfflineName(name)) {
                setStatus("Use a 1-16 character Minecraft name.", true);
                return;
            }

            AltManager.Account account;
            if (this.selectedType == AltManager.AccountType.CRACKED) {
                account = AltManager.Account.cracked(name);
            } else {
                UUID uuid = parseUuidInput(currentUuid());
                if (uuid == null) {
                    setStatus("Enter a valid UUID.", true);
                    return;
                }

                String accessToken = currentAccessToken();
                if (accessToken.isBlank()) {
                    setStatus("Access token is required.", true);
                    return;
                }

                account = new AltManager.Account(this.selectedType, name, uuid, accessToken);
                if (!account.isValid()) {
                    setStatus("Account details are incomplete.", true);
                    return;
                }
            }

            AltManager.add(account);
            this.parent.selectedKey = keyFor(account);
            this.parent.reloadAccounts();
            returnToParent();
        }

        private void startMicrosoftAuth() {
            if (this.microsoftLoginInProgress) {
                return;
            }

            this.microsoftLoginInProgress = true;
            setStatus("Opening Microsoft login...", false);
            MicrosoftAuth.login(
                url -> {
                    copyLoginUrl(url);
                    setStatus("Browser opened. Complete Microsoft sign-in.", false);
                },
                account -> {
                    this.microsoftLoginInProgress = false;
                    AltManager.add(account);
                    this.parent.selectedKey = keyFor(account);
                    this.parent.reloadAccounts();
                    returnToParent();
                },
                error -> {
                    this.microsoftLoginInProgress = false;
                    setStatus(error == null || error.isBlank() ? "Microsoft login failed." : error, true);
                }
            );
        }

        private void setStatus(String message, boolean error) {
            this.statusMessage = message;
            this.statusError = error;
        }

        private void clearStatus() {
            this.statusMessage = null;
            this.statusError = false;
        }

        private void copyLoginUrl(String url) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null && url != null && !url.isBlank()) {
                minecraft.keyboardHandler.setClipboard(url);
            }
        }

        private void returnToParent() {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null) {
                minecraft.setScreen(this.parent);
            }
        }

        private void updateFrameTime() {
            long now = System.nanoTime();
            if (this.lastFrameNanos == 0L) {
                this.frameSeconds = DEFAULT_FRAME_SECONDS;
            } else {
                this.frameSeconds = clamp((now - this.lastFrameNanos) / 1_000_000_000.0F, 0.0F, MAX_FRAME_SECONDS);
            }
            this.lastFrameNanos = now;
        }

        private void layoutControls() {
            syncCredentialFields();

            float panelX = (this.width - PANEL_WIDTH) * 0.5F;
            float panelY = (this.height - panelHeight()) * 0.5F;
            float controlX = panelX + MODAL_PADDING;
            float controlWidth = PANEL_WIDTH - MODAL_PADDING * 2.0F;
            this.typeSelector.setBounds(controlX, panelY + 56.0F, controlWidth, FIELD_HEIGHT);
            this.nameInput.setBounds(controlX, panelY + 100.0F, controlWidth, FIELD_HEIGHT);

            if (requiresCredentials()) {
                this.uuidInput.setBounds(controlX, panelY + 144.0F, controlWidth, FIELD_HEIGHT);
                this.accessTokenInput.setBounds(controlX, panelY + 188.0F, controlWidth, FIELD_HEIGHT);
            }

            float buttonY = panelY + panelHeight() - 42.0F;
            float buttonGap = 8.0F;
            float buttonWidth = (controlWidth - buttonGap) * 0.5F;
            this.addButton.setBounds(controlX, buttonY, buttonWidth, BUTTON_HEIGHT);
            this.cancelButton.setBounds(controlX + buttonWidth + buttonGap, buttonY, buttonWidth, BUTTON_HEIGHT);
        }

        private void syncCredentialFields() {
            boolean nameInputVisible = needsNameInput();
            this.nameInput.setVisible(nameInputVisible);
            if (!nameInputVisible) {
                this.nameInput.setFocused(false);
            }

            boolean credentials = requiresCredentials();
            this.uuidInput.setVisible(credentials);
            this.accessTokenInput.setVisible(credentials);
            if (!credentials) {
                this.uuidInput.setFocused(false);
                this.accessTokenInput.setFocused(false);
            }
        }

        private int panelHeight() {
            return requiresCredentials() ? CREDENTIAL_PANEL_HEIGHT : CRACKED_PANEL_HEIGHT;
        }

        private void focusInput(@Nullable TextInput target) {
            this.nameInput.setFocused(target == this.nameInput);
            this.uuidInput.setFocused(target == this.uuidInput && this.uuidInput.isVisible());
            this.accessTokenInput.setFocused(target == this.accessTokenInput && this.accessTokenInput.isVisible());
        }

        private void blurInputs() {
            focusInput(null);
        }

        private @Nullable TextInput focusedInput() {
            if (this.nameInput.isFocused()) {
                return this.nameInput;
            }
            if (this.uuidInput.isFocused()) {
                return this.uuidInput;
            }
            if (this.accessTokenInput.isFocused()) {
                return this.accessTokenInput;
            }

            return null;
        }

        private @Nullable TextInput inputAt(double mouseX, double mouseY) {
            if (this.nameInput.contains(mouseX, mouseY)) {
                return this.nameInput;
            }
            if (this.uuidInput.contains(mouseX, mouseY)) {
                return this.uuidInput;
            }
            if (this.accessTokenInput.contains(mouseX, mouseY)) {
                return this.accessTokenInput;
            }

            return null;
        }

        @Override
        protected void renderBlurredBackground(GuiGraphics guiGraphics) {
        }

        @Override
        public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        }

        private static String trimToWidthStatic(FontRenderer renderer, String text, float maxWidth) {
            if (text == null || text.isEmpty() || maxWidth <= 0.0F) {
                return "";
            }
            if (renderer.getStringWidth(text) <= maxWidth) {
                return text;
            }

            String suffix = "...";
            float suffixWidth = renderer.getStringWidth(suffix);
            if (suffixWidth > maxWidth) {
                return "";
            }

            int end = text.length();
            while (end > 0 && renderer.getStringWidth(text.substring(0, end)) + suffixWidth > maxWidth) {
                end--;
            }
            return text.substring(0, Math.max(0, end)) + suffix;
        }

        private static float centeredTextY(float height, FontRenderer renderer) {
            return (height - renderer.getLineHeight()) * 0.5F;
        }

        private static final class TypeSelector {
            private float x;
            private float y;
            private float width;
            private float height;
            private float hoverProgress;

            private void setBounds(float x, float y, float width, float height) {
                this.x = x;
                this.y = y;
                this.width = width;
                this.height = height;
            }

            private void render(int mouseX, int mouseY, float frameSeconds, String value) {
                FontRenderer labelFont = FontManager.getAppleTextRenderer(10.0F);
                FontRenderer valueFont = FontManager.getAppleTextRenderer(11.0F);
                boolean hovered = contains(mouseX, mouseY);
                this.hoverProgress = animateExp(this.hoverProgress, hovered ? 1.0F : 0.0F, HOVER_SPEED, frameSeconds);
                int fill = Render2DUtility.mix(CONTROL_BACKGROUND, CONTROL_HOVER, this.hoverProgress);
                int border = Render2DUtility.mix(CONTROL_BORDER, CONTROL_BORDER_HOVER, this.hoverProgress);

                labelFont.drawString("Account type", this.x, this.y - 14.0F, TEXT_DIM);
                Render2DUtility.drawDropShadow(this.x, this.y, this.width, this.height, CONTROL_RADIUS, 0.0F, 5.0F, 12.0F,
                    Render2DUtility.applyOpacity(0x55000000, 0.45F + this.hoverProgress * 0.28F));
                Render2DUtility.drawRoundedRect(this.x, this.y, this.width, this.height, CONTROL_RADIUS, fill);
                Render2DUtility.drawOutlineRoundedRect(this.x, this.y, this.width, this.height, CONTROL_RADIUS, 1.0F, border);

                valueFont.drawString(trimToWidthStatic(valueFont, value, this.width - 42.0F),
                    this.x + 10.0F, this.y + centeredTextY(this.height, valueFont), Render2DUtility.mix(TEXT_MUTED, TEXT, this.hoverProgress));

                FontRenderer arrowFont = FontManager.getAppleTextRenderer(14.0F);
                Render2DUtility.withRotation(90.0F, this.x + this.width - 16.0F, this.y + this.height * 0.5F, () -> {
                    arrowFont.drawCenteredString(">", this.x + this.width - 16.0F,
                        this.y + (this.height - arrowFont.getLineHeight()) * 0.5F,
                        Render2DUtility.mix(TEXT_SUBTLE, ACCENT, this.hoverProgress));
                });
            }

            private boolean contains(double mouseX, double mouseY) {
                return isInside(mouseX, mouseY, this.x, this.y, this.width, this.height);
            }
        }

        private static final class ModalButton {
            private final Supplier<String> label;
            private float x;
            private float y;
            private float width;
            private float height;
            private float hoverProgress;

            private ModalButton(String label) {
                this(() -> label);
            }

            private ModalButton(Supplier<String> label) {
                this.label = label;
            }

            private void setBounds(float x, float y, float width, float height) {
                this.x = x;
                this.y = y;
                this.width = width;
                this.height = height;
            }

            private void render(int mouseX, int mouseY, float frameSeconds, boolean active) {
                boolean hovered = active && contains(mouseX, mouseY);
                this.hoverProgress = animateExp(this.hoverProgress, hovered ? 1.0F : 0.0F, HOVER_SPEED, frameSeconds);
                float opacity = active ? 1.0F : 0.45F;
                int fill = Render2DUtility.mix(CONTROL_BACKGROUND, CONTROL_HOVER, this.hoverProgress);
                int border = Render2DUtility.mix(CONTROL_BORDER, CONTROL_BORDER_HOVER, this.hoverProgress);
                int color = active ? Render2DUtility.mix(TEXT_MUTED, TEXT, this.hoverProgress) : TEXT_DISABLED;

                Render2DUtility.drawDropShadow(this.x, this.y, this.width, this.height, CONTROL_RADIUS, 0.0F, 5.0F, 12.0F,
                    Render2DUtility.applyOpacity(0x55000000, opacity * (0.45F + this.hoverProgress * 0.35F)));
                Render2DUtility.drawRoundedRect(this.x, this.y, this.width, this.height, CONTROL_RADIUS, Render2DUtility.applyOpacity(fill, opacity));
                Render2DUtility.drawOutlineRoundedRect(this.x, this.y, this.width, this.height, CONTROL_RADIUS, 1.0F,
                    Render2DUtility.applyOpacity(border, opacity));

                FontRenderer font = FontManager.getAppleTextRenderer(11.0F);
                font.drawCenteredString(trimToWidthStatic(font, this.label.get(), this.width - 14.0F),
                    this.x + this.width * 0.5F,
                    this.y + centeredTextY(this.height, font),
                    Render2DUtility.applyOpacity(color, opacity));
            }

            private boolean contains(double mouseX, double mouseY) {
                return isInside(mouseX, mouseY, this.x, this.y, this.width, this.height);
            }
        }

        private static final class TextInput {
            private final String label;
            private final String placeholder;
            private final int maxLength;
            private String value = "";
            private float x;
            private float y;
            private float width;
            private float height;
            private boolean visible = true;
            private boolean focused;
            private boolean selectedAll;
            private int cursor;
            private float hoverProgress;
            private float focusProgress;
            private float selectionProgress;

            private TextInput(String label, String placeholder, int maxLength) {
                this.label = label;
                this.placeholder = placeholder;
                this.maxLength = maxLength;
            }

            private void setBounds(float x, float y, float width, float height) {
                this.x = x;
                this.y = y;
                this.width = width;
                this.height = height;
            }

            private void render(int mouseX, int mouseY, float frameSeconds) {
                if (!this.visible) {
                    return;
                }

                FontRenderer labelFont = FontManager.getAppleTextRenderer(10.0F);
                FontRenderer valueFont = FontManager.getAppleTextRenderer(10.0F);
                boolean hovered = contains(mouseX, mouseY);
                this.hoverProgress = animateExp(this.hoverProgress, hovered ? 1.0F : 0.0F, HOVER_SPEED, frameSeconds);
                this.focusProgress = animateExp(this.focusProgress, this.focused ? 1.0F : 0.0F, HOVER_SPEED, frameSeconds);
                this.selectionProgress = animateExp(this.selectionProgress, this.focused && this.selectedAll ? 1.0F : 0.0F, HOVER_SPEED, frameSeconds);
                int fill = Render2DUtility.mix(CONTROL_BACKGROUND, CONTROL_HOVER, Math.max(this.hoverProgress * 0.65F, this.focusProgress));
                int border = Render2DUtility.mix(CONTROL_BORDER, CONTROL_BORDER_HOVER, Math.max(this.hoverProgress, this.focusProgress));

                labelFont.drawString(this.label, this.x, this.y - 14.0F, Render2DUtility.mix(TEXT_DIM, TEXT_SUBTLE, this.focusProgress));
                Render2DUtility.drawDropShadow(this.x, this.y, this.width, this.height, CONTROL_RADIUS, 0.0F, 5.0F, 12.0F,
                    Render2DUtility.applyOpacity(0x55000000, 0.36F + Math.max(this.hoverProgress, this.focusProgress) * 0.26F));
                Render2DUtility.drawRoundedRect(this.x, this.y, this.width, this.height, CONTROL_RADIUS, fill);
                Render2DUtility.drawOutlineRoundedRect(this.x, this.y, this.width, this.height, CONTROL_RADIUS, 1.0F, border);

                if (this.selectionProgress > 0.001F) {
                    Render2DUtility.drawRoundedRect(this.x + 7.0F, this.y + 6.0F, Math.max(2.0F, this.width - 14.0F), this.height - 12.0F, 3.0F,
                        Render2DUtility.applyOpacity(ACCENT, this.selectionProgress * 0.36F));
                }

                String text = this.value.isEmpty() ? this.placeholder : this.value;
                String displayText = displayText(valueFont, text, this.width - 18.0F);
                int color = this.value.isEmpty() ? TEXT_DIM : Render2DUtility.mix(TEXT_MUTED, TEXT, this.focusProgress);
                valueFont.drawString(displayText, this.x + 9.0F, this.y + centeredTextY(this.height, valueFont), color);

                if (this.focused && !this.selectedAll && shouldDrawCursor()) {
                    float cursorX = cursorX(valueFont, displayText);
                    Render2DUtility.drawRect(cursorX, this.y + 7.0F, 1.0F, this.height - 14.0F,
                        Render2DUtility.applyOpacity(TEXT, Math.max(0.55F, this.focusProgress)));
                }
            }

            private boolean keyPressed(KeyEvent event) {
                if (!this.focused) {
                    return false;
                }

                if (event.isSelectAll()) {
                    this.selectedAll = true;
                    this.cursor = this.value.length();
                    return true;
                }
                if (event.isCopy()) {
                    setClipboard(this.value);
                    return true;
                }
                if (event.isCut()) {
                    setClipboard(this.value);
                    setValue("");
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
                        this.cursor = 0;
                        this.selectedAll = false;
                        return true;
                    }
                    case GLFW_KEY_END -> {
                        this.cursor = this.value.length();
                        this.selectedAll = false;
                        return true;
                    }
                    default -> {
                        if (event.isLeft()) {
                            this.cursor = Math.max(0, this.cursor - 1);
                            this.selectedAll = false;
                            return true;
                        }
                        if (event.isRight()) {
                            this.cursor = Math.min(this.value.length(), this.cursor + 1);
                            this.selectedAll = false;
                            return true;
                        }
                        return false;
                    }
                }
            }

            private boolean charTyped(CharacterEvent event) {
                if (!this.focused || !event.isAllowedChatCharacter()) {
                    return false;
                }

                insert(event.codepointAsString());
                return true;
            }

            private void insert(String text) {
                if (text == null || text.isEmpty()) {
                    return;
                }

                String cleanText = text.replace("\r", "").replace("\n", "");
                if (cleanText.isEmpty()) {
                    return;
                }

                String current = this.selectedAll ? "" : this.value;
                int safeCursor = this.selectedAll ? 0 : Math.max(0, Math.min(this.cursor, current.length()));
                int room = Math.max(0, this.maxLength - current.length());
                if (room <= 0) {
                    return;
                }

                if (cleanText.length() > room) {
                    cleanText = cleanText.substring(0, room);
                }

                setValue(current.substring(0, safeCursor) + cleanText + current.substring(safeCursor));
                this.cursor = safeCursor + cleanText.length();
                this.selectedAll = false;
            }

            private void delete(int direction) {
                if (this.selectedAll) {
                    setValue("");
                    return;
                }

                if (direction < 0 && this.cursor > 0) {
                    int previous = this.value.offsetByCodePoints(this.cursor, -1);
                    setValue(this.value.substring(0, previous) + this.value.substring(this.cursor));
                    this.cursor = previous;
                } else if (direction > 0 && this.cursor < this.value.length()) {
                    int next = this.value.offsetByCodePoints(this.cursor, 1);
                    setValue(this.value.substring(0, this.cursor) + this.value.substring(next));
                }
            }

            private void setValue(String value) {
                this.value = value == null ? "" : value;
                if (this.value.length() > this.maxLength) {
                    this.value = this.value.substring(0, this.maxLength);
                }
                this.cursor = Math.max(0, Math.min(this.cursor, this.value.length()));
                this.selectedAll = false;
            }

            private void placeCursor(double mouseX) {
                this.selectedAll = false;
                if (!this.visible) {
                    this.cursor = 0;
                    return;
                }

                FontRenderer valueFont = FontManager.getAppleTextRenderer(10.0F);
                String displayText = displayText(valueFont, this.value, this.width - 18.0F);
                if (!displayText.equals(this.value)) {
                    this.cursor = this.value.length();
                    return;
                }

                float localX = (float)mouseX - this.x - 9.0F;
                int best = 0;
                for (int i = 1; i <= this.value.length(); i++) {
                    if (valueFont.getStringWidth(this.value.substring(0, i)) <= localX) {
                        best = i;
                    }
                }
                this.cursor = Math.max(0, Math.min(this.value.length(), best));
            }

            private String displayText(FontRenderer renderer, String text, float maxWidth) {
                if (text == null || text.isEmpty()) {
                    return "";
                }

                String candidate = text.length() > 128 ? text.substring(text.length() - 128) : text;
                if (renderer.getStringWidth(candidate) <= maxWidth) {
                    return candidate;
                }

                String suffix = "...";
                int start = 0;
                while (start < candidate.length() && renderer.getStringWidth(suffix + candidate.substring(start)) > maxWidth) {
                    start++;
                }
                return suffix + candidate.substring(Math.min(start, candidate.length()));
            }

            private float cursorX(FontRenderer renderer, String displayText) {
                if (!displayText.equals(this.value)) {
                    return this.x + this.width - 9.0F;
                }

                int safeCursor = Math.max(0, Math.min(this.cursor, this.value.length()));
                return this.x + 9.0F + renderer.getStringWidth(this.value.substring(0, safeCursor));
            }

            private String value() {
                return this.value;
            }

            private void setFocused(boolean focused) {
                this.focused = focused;
                if (focused) {
                    this.cursor = this.value.length();
                } else {
                    this.selectedAll = false;
                }
            }

            private boolean isFocused() {
                return this.focused && this.visible;
            }

            private void setVisible(boolean visible) {
                this.visible = visible;
            }

            private boolean isVisible() {
                return this.visible;
            }

            private boolean contains(double mouseX, double mouseY) {
                return this.visible && isInside(mouseX, mouseY, this.x, this.y, this.width, this.height);
            }

            private boolean shouldDrawCursor() {
                return (System.currentTimeMillis() / 500L) % 2L == 0L;
            }

            private static String getClipboard() {
                Minecraft minecraft = Minecraft.getInstance();
                return minecraft == null ? "" : minecraft.keyboardHandler.getClipboard();
            }

            private static void setClipboard(String text) {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft != null) {
                    minecraft.keyboardHandler.setClipboard(text);
                }
            }
        }
    }

    private final class AccountEntry {
        private final AltManager.Account account;
        private final GameProfile profile;
        private final PlayerSkin fallbackSkin;
        private volatile Supplier<PlayerSkin> skinLookup;

        private AccountEntry(AltManager.Account account) {
            this.account = account;
            this.profile = new GameProfile(account.getUuidValue(), account.getName());
            this.fallbackSkin = DefaultPlayerSkin.get(this.profile);

            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null) {
                this.skinLookup = () -> this.fallbackSkin;
            } else {
                this.skinLookup = () -> this.fallbackSkin;
                loadSkin(minecraft);
            }
        }

        private String key() {
            return keyFor(this.account);
        }

        private PlayerSkin skin() {
            try {
                PlayerSkin skin = this.skinLookup.get();
                return skin == null ? this.fallbackSkin : skin;
            } catch (RuntimeException exception) {
                return this.fallbackSkin;
            }
        }

        private void loadSkin(Minecraft minecraft) {
            if (this.account.getType() != AltManager.AccountType.MICROSOFT) {
                this.skinLookup = minecraft.getSkinManager().createLookup(this.profile, false);
                return;
            }

            CompletableFuture.supplyAsync(
                () -> minecraft.services().sessionService().fetchProfile(this.profile.id(), true)
            ).thenAccept(result -> applyFetchedProfile(minecraft, result)).exceptionally(exception -> null);
        }

        private void applyFetchedProfile(Minecraft minecraft, @Nullable ProfileResult result) {
            if (result == null || result.profile() == null) {
                return;
            }

            this.skinLookup = minecraft.getSkinManager().createLookup(result.profile(), false);
        }
    }

    private final class ActionButton {
        private final Supplier<String> label;
        private final BooleanSupplier active;
        private final Runnable action;
        private float x;
        private float y;
        private float width;
        private float height;
        private float hoverProgress;

        private ActionButton(String label, BooleanSupplier active, Runnable action) {
            this(() -> label, active, action);
        }

        private ActionButton(Supplier<String> label, BooleanSupplier active, Runnable action) {
            this.label = label;
            this.active = active;
            this.action = action;
        }

        private void setBounds(float x, float y, float width, float height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        private String label() {
            return this.label.get();
        }

        private boolean active() {
            return this.active.getAsBoolean();
        }

        private boolean contains(double mouseX, double mouseY) {
            return isInside(mouseX, mouseY, this.x, this.y, this.width, this.height);
        }

        private float centerX() {
            return this.x + this.width * 0.5F;
        }

        private float centerY() {
            return this.y + this.height * 0.5F;
        }
    }

    private record PanelBounds(float x, float y, float width, float height) {
        private float centerX() {
            return this.x + this.width * 0.5F;
        }

        private float centerY() {
            return this.y + this.height * 0.5F;
        }
    }
}
