package io.github.seraphina.nyx.client.module.other;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.PacketEvent;
import io.github.seraphina.nyx.client.events.impl.TickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.player.PlayerUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.EnumValue;
import io.github.seraphina.nyx.client.value.impl.StringValue;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ModuleInfo(name = "nyxclient.module.autologin.name", description = "nyxclient.module.autologin.description", category = Category.OTHER)
public class AutoLogin extends Module {
    public static final AutoLogin INSTANCE = new AutoLogin();

    private static final long COMMAND_COOLDOWN_MS = 2000L;
    private static final Pattern LOGIN_COMMAND_PATTERN = Pattern.compile("(?<![A-Za-z0-9_-])/(?:login|l)(?![A-Za-z0-9_-])", Pattern.CASE_INSENSITIVE);
    private static final Pattern REGISTER_COMMAND_PATTERN = Pattern.compile("(?<![A-Za-z0-9_-])/(?:register|reg)(?![A-Za-z0-9_-])", Pattern.CASE_INSENSITIVE);
    private static final Pattern REGISTER_USAGE_PATTERN = Pattern.compile("(?<![A-Za-z0-9_-])/(?:register|reg)(?![A-Za-z0-9_-])\\s+(\\S+)(?:\\s+(\\S+))?", Pattern.CASE_INSENSITIVE);

    public final StringValue psd = ValueBuild.stringSetting("psd", "nyxclient", this);
    public final EnumValue<RegisterMode> registerMode = ValueBuild.enumSetting("register mode", RegisterMode.AUTO, this);

    private volatile String pendingCommand;
    private volatile Action pendingAction;
    private volatile Action lastSentAction;
    private volatile long lastSentAt;

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        resetState();
    }

    @EventTarget
    public void onPacketReceive(PacketEvent.Receive event) {
        String message = chatMessage(event.getPacket());
        if (message == null || message.isBlank()) {
            return;
        }

        AuthCommand command = authCommand(message);
        if (command != null) {
            queue(command);
        }
    }

    @EventTarget
    public void onPostTick(TickEvent.Post event) {
        String command = pendingCommand;
        Action action = pendingAction;
        if (command == null) {
            return;
        }

        if (!canSendCommand()) {
            pendingCommand = null;
            pendingAction = null;
            return;
        }

        pendingCommand = null;
        pendingAction = null;
        PlayerUtility.runCmd(command);
        lastSentAction = action;
        lastSentAt = System.currentTimeMillis();
    }

    private void resetState() {
        pendingCommand = null;
        pendingAction = null;
        lastSentAction = null;
        lastSentAt = 0L;
    }

    private String chatMessage(Packet<?> packet) {
        if (packet instanceof ClientboundSystemChatPacket systemChatPacket) {
            return systemChatPacket.content().getString();
        }

        if (packet instanceof ClientboundDisguisedChatPacket disguisedChatPacket) {
            return disguisedChatPacket.message().getString();
        }

        return null;
    }

    private AuthCommand authCommand(String message) {
        String password = password();
        if (password.isBlank()) {
            return null;
        }

        String normalizedMessage = message.toLowerCase(Locale.ROOT);
        CommandMatch login = firstCommand(message, LOGIN_COMMAND_PATTERN);
        CommandMatch register = firstCommand(message, REGISTER_COMMAND_PATTERN);

        login = isUsableCommand(login, normalizedMessage) ? login : null;
        register = isUsableCommand(register, normalizedMessage) ? register : null;

        if (login == null && register == null) {
            return null;
        }

        if (register != null && (login == null || register.index() < login.index())) {
            return registerCommand(register.command(), password, message);
        }

        return new AuthCommand(Action.LOGIN, login.command() + " " + password);
    }

    private String password() {
        String password = psd.getValue();
        return password == null ? "" : password.trim();
    }

    private AuthCommand registerCommand(String command, String password, String message) {
        if (shouldUseConfirmPassword(message)) {
            return new AuthCommand(Action.REGISTER, command + " " + password + " " + password);
        }

        return new AuthCommand(Action.REGISTER, command + " " + password);
    }

    private boolean shouldUseConfirmPassword(String message) {
        return switch (registerMode.getValue()) {
            case SINGLE_PASSWORD -> false;
            case DOUBLE_PASSWORD -> true;
            case AUTO -> inferConfirmPassword(message);
        };
    }

    private boolean inferConfirmPassword(String message) {
        Matcher matcher = REGISTER_USAGE_PATTERN.matcher(message);
        while (matcher.find()) {
            String firstArg = normalizeUsageToken(matcher.group(1));
            String secondArg = normalizeUsageToken(matcher.group(2));
            if (!secondArg.isEmpty() && !isUsageSeparator(secondArg)) {
                return true;
            }

            if (!firstArg.isEmpty() && !isUsageSeparator(firstArg)) {
                return false;
            }
        }

        String normalizedMessage = message.toLowerCase(Locale.ROOT);
        if (normalizedMessage.contains("confirm")
                || normalizedMessage.contains("repeat")
                || normalizedMessage.contains("again")
                || message.contains("确认")
                || message.contains("重复")
                || message.contains("再次")
                || message.contains("两次")) {
            return true;
        }

        return true;
    }

    private void queue(AuthCommand command) {
        long now = System.currentTimeMillis();
        if (pendingCommand != null) {
            return;
        }

        if (lastSentAction == command.action()
                && now - lastSentAt < COMMAND_COOLDOWN_MS) {
            return;
        }

        pendingAction = command.action();
        pendingCommand = command.command();
    }

    private boolean canSendCommand() {
        return mc.player != null && mc.level != null && mc.player.connection != null;
    }

    private static CommandMatch firstCommand(String message, Pattern pattern) {
        Matcher matcher = pattern.matcher(message);
        if (!matcher.find()) {
            return null;
        }

        return new CommandMatch(matcher.group().toLowerCase(Locale.ROOT), matcher.start());
    }

    private static boolean isUsableCommand(CommandMatch command, String normalizedMessage) {
        return command != null
                && ((!command.command().equals("/l") && !command.command().equals("/reg")) || hasAuthContext(normalizedMessage));
    }

    private static boolean hasAuthContext(String normalizedMessage) {
        return normalizedMessage.contains("password")
                || normalizedMessage.contains("pass")
                || normalizedMessage.contains("login")
                || normalizedMessage.contains("log in")
                || normalizedMessage.contains("register")
                || normalizedMessage.contains("auth")
                || normalizedMessage.contains("密码")
                || normalizedMessage.contains("登录")
                || normalizedMessage.contains("登陆")
                || normalizedMessage.contains("登入")
                || normalizedMessage.contains("注册")
                || normalizedMessage.contains("认证");
    }

    private static String normalizeUsageToken(String token) {
        if (token == null) {
            return "";
        }

        int start = 0;
        int end = token.length();
        while (start < end && isUsageTokenPunctuation(token.charAt(start))) {
            start++;
        }
        while (end > start && isUsageTokenPunctuation(token.charAt(end - 1))) {
            end--;
        }

        return token.substring(start, end).toLowerCase(Locale.ROOT);
    }

    private static boolean isUsageTokenPunctuation(char ch) {
        return ch == '<'
                || ch == '>'
                || ch == '['
                || ch == ']'
                || ch == '('
                || ch == ')'
                || ch == '{'
                || ch == '}'
                || ch == '"'
                || ch == '\''
                || ch == '`'
                || ch == ','
                || ch == '.'
                || ch == ':'
                || ch == ';'
                || ch == '|'
                || ch == '，'
                || ch == '。'
                || ch == '：'
                || ch == '；'
                || ch == '！'
                || ch == '!'
                || ch == '?'
                || ch == '？'
                || ch == '“'
                || ch == '”'
                || ch == '‘'
                || ch == '’';
    }

    private static boolean isUsageSeparator(String token) {
        return token.isBlank()
                || token.startsWith("/")
                || token.equals("or")
                || token.equals("and")
                || token.equals("或")
                || token.equals("或者")
                || token.equals("和");
    }

    public enum RegisterMode {
        AUTO("Auto"),
        SINGLE_PASSWORD("SinglePassword"),
        DOUBLE_PASSWORD("DoublePassword");

        private final String name;

        RegisterMode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private record CommandMatch(String command, int index) {
    }

    private enum Action {
        LOGIN,
        REGISTER
    }

    private record AuthCommand(Action action, String command) {
    }
}
