local common = include("nyxclient:ui/screen/common.lua")
local screen = {}

local PANEL_WIDTH = 340
local CRACKED_PANEL_HEIGHT = 214
local CREDENTIAL_PANEL_HEIGHT = 286
local MODAL_PADDING = 20
local FIELD_HEIGHT = 30
local BUTTON_HEIGHT = 30
local CONTROL_RADIUS = 8

local TEXT = 0xFFFFFFFF
local TEXT_MUTED = 0xFFE2E6EF
local TEXT_SUBTLE = 0xFFA8AFBE
local TEXT_DIM = 0xFF687181
local PANEL_BLUR = 0xE6FFFFFF
local PANEL_BACKGROUND = 0xB80A0C12
local PANEL_BORDER = 0x66FFFFFF
local CONTROL_BACKGROUND = 0xAA0E1118
local CONTROL_HOVER = 0xD7191D28
local CONTROL_BORDER = 0x22FFFFFF
local CONTROL_BORDER_HOVER = 0x663D81F7
local ACCENT = 0xFF3D81F7

local selector_hover = 0

local function panel_bounds(state)
    local height = state.requires_credentials and CREDENTIAL_PANEL_HEIGHT or CRACKED_PANEL_HEIGHT
    return {
        x = (state.width - PANEL_WIDTH) * 0.5,
        y = (state.height - height) * 0.5,
        width = PANEL_WIDTH,
        height = height
    }
end

local function render_type_selector(state, x, y, width)
    local active = not state.busy
    local hovered = active and ui.hovered(x, y, width, FIELD_HEIGHT)
    selector_hover = common.animate_exp(
        selector_hover,
        hovered and 1 or 0,
        16,
        state.frame_seconds or 1 / 60
    )

    local fill = ui.mix(CONTROL_BACKGROUND, CONTROL_HOVER, selector_hover)
    local border = ui.mix(CONTROL_BORDER, CONTROL_BORDER_HOVER, selector_hover)
    local value_color = ui.mix(TEXT_MUTED, TEXT, selector_hover)
    local arrow_color = ui.mix(TEXT_SUBTLE, ACCENT, selector_hover)

    ui.text_font("Account type", x, y - 14, 10, TEXT_DIM)
    ui.shadow(x, y, width, FIELD_HEIGHT, CONTROL_RADIUS, 0, 5, 12,
        ui.opacity(0x55000000, 0.45 + selector_hover * 0.28))
    ui.rounded_rect(x, y, width, FIELD_HEIGHT, CONTROL_RADIUS, fill)
    ui.outline(x, y, width, FIELD_HEIGHT, CONTROL_RADIUS, 1, border)

    local value = ui.trim_text("text", state.type_label or "Unknown", 11, width - 42)
    ui.text_font(value, x + 10, y + (FIELD_HEIGHT - ui.font_height("text", 11)) * 0.5, 11, value_color)

    local arrow_x = x + width - 16
    local arrow_y = y + (FIELD_HEIGHT - ui.font_height("text", 14)) * 0.5
    ui.rotate(90, arrow_x, y + FIELD_HEIGHT * 0.5, function()
        ui.text_centered(">", arrow_x, arrow_y, 14, arrow_color)
    end)
    ui.hitbox(x, y, width, FIELD_HEIGHT, "cycle_type", nil, active)
end

function screen.render(state)
    ui.shared_background()
    ui.rect(0, 0, state.width, state.height, 0x77000000)

    local panel = panel_bounds(state)
    ui.shadow(panel.x, panel.y, panel.width, panel.height, 16, 0, 8, 22, 0x77000000)
    ui.panel(panel.x, panel.y, panel.width, panel.height, 16, 16,
        PANEL_BLUR, PANEL_BACKGROUND, 2, PANEL_BORDER)

    local x = panel.x + MODAL_PADDING
    local width = panel.width - MODAL_PADDING * 2
    ui.display_text("Add Account", x, panel.y + 18, 18, TEXT)
    render_type_selector(state, x, panel.y + 56, width)

    if state.needs_name_input then
        ui.input("name", x, panel.y + 100, width, FIELD_HEIGHT,
            state.name or "", "Player name", 16, "confirm", not state.busy, false, "Player name", 10)
    end

    if state.requires_credentials then
        ui.input("uuid", x, panel.y + 144, width, FIELD_HEIGHT,
            state.uuid or "", "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx", 36,
            "confirm", not state.busy, false, "UUID", 10)
        ui.input("access_token", x, panel.y + 188, width, FIELD_HEIGHT,
            state.access_token or "", "Paste access token", 8192,
            "confirm", not state.busy, true, "Access token", 10)
    elseif state.uses_browser_login and (not state.status or state.status == "") then
        ui.text_font(
            ui.trim_text("text", "Browser login will open for Microsoft sign-in.", 10, width),
            x, panel.y + 110, 10, TEXT_SUBTLE
        )
    end

    if state.status and state.status ~= "" then
        ui.text_font(
            ui.trim_text("text", state.status, 10, width),
            x,
            panel.y + panel.height - 62,
            10,
            state.status_error and 0xFFFFA0A0 or TEXT_SUBTLE
        )
    end

    local button_y = panel.y + panel.height - 42
    local gap = 8
    local button_width = (width - gap) * 0.5
    ui.button(state.button_label or "Add", x, button_y, button_width, BUTTON_HEIGHT,
        "confirm", nil, state.valid, true, 11, "add_account:confirm")
    ui.button("Cancel", x + button_width + gap, button_y, button_width, BUTTON_HEIGHT,
        "cancel", nil, true, false, 11, "add_account:cancel")
end

function screen.key_pressed(state, key)
    if key.escape then
        ui.action("cancel")
        return true
    end
    if key.selection and state.valid then
        ui.action("confirm")
        return true
    end
    return false
end

return screen
