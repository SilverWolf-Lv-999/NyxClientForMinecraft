local screen = {}

local HEADER_HEIGHT = 20
local MODULE_HEIGHT = 20
local VALUE_PADDING = 6
local PANEL_RADIUS = 6

local SCREEN_DIM = 0x00000000
local PANEL_BACKGROUND = 0xFF171717
local MODULE_HOVER = 0x16FFFFFF
local SETTINGS_BACKGROUND = 0xFF202020
local TEXT = 0xFFFFFFFF
local TEXT_MUTED = 0x99FFFFFF
local TEXT_DIM = 0x66FFFFFF
local BORDER = 0x18FFFFFF
local ACCENT = 0xFF2DE8CA

local module_hover = {}
local tooltip = nil

local function clamp(value, minimum, maximum)
    return math.max(minimum, math.min(maximum, value))
end

local function lerp(from, to, progress)
    return from + (to - from) * progress
end

local function animate_exp(current, target, speed, frame_seconds)
    return target + (current - target) * math.exp(-speed * (frame_seconds or 1 / 60))
end

local function hovered(state, x, y, width, height)
    local mouse_x = state.mouse_x or 0
    local mouse_y = state.mouse_y or 0
    return mouse_x >= x and mouse_x < x + width and mouse_y >= y and mouse_y < y + height
end

local function actual(state, value)
    return value * (state.global_scale or 1)
end

local function hitbox(state, x, y, width, height, action, payload, active, capture)
    ui.hitbox(actual(state, x), actual(state, y), actual(state, width), actual(state, height),
        action, payload, active, capture == true)
end

local function scroll_area(state, id, x, y, width, height, content_height, step)
    local scale = state.global_scale or 1
    local offset = ui.scroll(id, x * scale, y * scale, width * scale, height * scale,
        content_height * scale, step * scale)
    return offset / math.max(0.001, scale)
end

local function render_arrow(module, x, y, alpha)
    if not module.expandable then
        return
    end

    local progress = module.expand_progress or 0
    local font_size = 10
    local glyph = ui.codepoint(0xEB4E)
    ui.rotate(180 * progress, x, y, function()
        ui.text_visual_centered("material", glyph, x, y, font_size,
            ui.opacity(TEXT, alpha * (0.8 - 0.3 * progress)))
    end)
end

local function render_values(state, module, x, y, width, visible_height, interactive)
    if visible_height <= 0.5 then
        return
    end

    ui.clip(x, y, width, visible_height, function()
        local value_y = y + VALUE_PADDING * 0.5
        local remaining = visible_height - VALUE_PADDING
        for _, value in ipairs(module.values or {}) do
            if remaining <= 0 then
                break
            end
            local height = value.height or 0
            local clipped_height = math.min(height, remaining)
            ui.custom("value", module.index, value.index, x + VALUE_PADDING, value_y,
                width - VALUE_PADDING * 2, clipped_height, interactive)
            value_y = value_y + height
            remaining = remaining - height
        end
    end)
end

local function render_module(state, module, x, y, width, viewport_y, viewport_height, alpha)
    local header_top = math.max(y, viewport_y)
    local header_bottom = math.min(y + MODULE_HEIGHT, viewport_y + viewport_height)
    local header_visible = math.max(0, header_bottom - header_top)
    local is_hovered = state.interactive and header_visible > 0
        and hovered(state, x, header_top, width, header_visible)
    local hover = animate_exp(module_hover[module.index] or 0, is_hovered and 1 or 0,
        18, state.frame_seconds)
    module_hover[module.index] = hover

    local enabled = module.enabled_progress or 0
    local row_height = module.row_height or MODULE_HEIGHT
    local expanded_height = math.max(0, row_height - MODULE_HEIGHT)

    if hover > 0 then
        ui.rounded_rect(x + 0.5, y + 1, width - 1, MODULE_HEIGHT - 2, 4,
            ui.opacity(MODULE_HOVER, alpha * hover))
    end

    local name_color = ui.mix(TEXT_MUTED, ACCENT, enabled)
    name_color = ui.mix(name_color, TEXT, hover * (1 - enabled) * 0.35)
    ui.text_centered_in_rect(
        ui.trim_text("text", module.name or "", 10, width - 36),
        x, y, width, MODULE_HEIGHT, 10, ui.opacity(name_color, alpha)
    )
    render_arrow(module, x + width - 10, y + MODULE_HEIGHT * 0.5, alpha)

    ui.custom("module_bounds", module.index, x, y, width, row_height, header_visible > 0)
    if header_visible > 0 then
        hitbox(state, x, header_top, width, header_visible, "toggle_module", module.index,
            state.interactive, false)
    end

    if is_hovered and module.description and module.description ~= "" then
        tooltip = module.description
    end

    if expanded_height > 0.5 then
        ui.rounded_rect(x + 4, y + MODULE_HEIGHT, width - 8, expanded_height, 4,
            ui.opacity(SETTINGS_BACKGROUND, alpha * (module.expand_progress or 0)))
        render_values(state, module, x, y + MODULE_HEIGHT, width, expanded_height,
            state.interactive)
    end
end

local function render_panel_contents(state, panel, alpha)
    local body_y = panel.y + HEADER_HEIGHT
    local body_height = panel.height - HEADER_HEIGHT
    local scroll = scroll_area(state, "arraylist:" .. panel.id, panel.x, body_y,
        panel.width, body_height, panel.content_height or 0, 40)

    ui.clip(panel.x, body_y, panel.width, body_height, function()
        local y = body_y - scroll
        for _, module in ipairs(panel.modules or {}) do
            render_module(state, module, panel.x, y, panel.width,
                body_y, body_height, alpha)
            y = y + (module.row_height or MODULE_HEIGHT)
        end
    end)
end

local function render_panel(state, panel, alpha)
    local scale = state.screen_scale or 1
    local center_x = panel.x + panel.width * 0.5
    local center_y = panel.y + panel.height * 0.5
    ui.scale(scale, scale, center_x, center_y, function()
        ui.shadow(panel.x, panel.y, panel.width, panel.height, PANEL_RADIUS, 0, 7, 14,
            ui.opacity(0x8A000000, alpha))
        ui.rounded_rect(panel.x, panel.y, panel.width, panel.height, PANEL_RADIUS,
            ui.opacity(PANEL_BACKGROUND, alpha))
        ui.clip(panel.x + 0.5, panel.y, panel.width - 1, panel.height - 0.5, function()
            ui.text_font(panel.label or "", panel.x + 8, panel.y + 5, 11,
                ui.opacity(TEXT, alpha))
            ui.rect(panel.x, panel.y + HEADER_HEIGHT - 1, panel.width, 1,
                ui.opacity(BORDER, alpha))
            render_panel_contents(state, panel, alpha)
            ui.vertical_gradient(panel.x + 0.5, panel.y + HEADER_HEIGHT - 0.5,
                panel.width - 1, 6, ui.opacity(0x5C000000, alpha), 0x00000000)
        end)
        ui.outline(panel.x, panel.y, panel.width, panel.height, PANEL_RADIUS, 1,
            ui.opacity(BORDER, alpha))
    end)

    hitbox(state, panel.x, panel.y, panel.width, HEADER_HEIGHT, "drag_panel", {
        id = panel.id
    }, state.interactive, true)
end

local function render_tooltip(state, alpha)
    if not tooltip or tooltip == "" then
        return
    end

    local text = ui.trim_text("text", tooltip, 9, 220)
    local width = ui.font_width("text", text, 9) + 12
    local x = clamp((state.mouse_x or 0) + 8, 4, state.width - width - 4)
    local y = clamp((state.mouse_y or 0) + 8, 4, state.height - 22)
    ui.shadow(x, y, width, 18, 4, 0, 4, 9, ui.opacity(0x99000000, alpha))
    ui.rounded_rect(x, y, width, 18, 4, ui.opacity(PANEL_BACKGROUND, alpha))
    ui.outline(x, y, width, 18, 4, 1, ui.opacity(BORDER, alpha))
    ui.text_font(text, x + 6, y + 5, 9, ui.opacity(TEXT_MUTED, alpha))
end

function screen.render(state)
    local alpha = state.screen_visibility or 1
    tooltip = nil
    ui.rect(0, 0, state.screen_width, state.screen_height, ui.opacity(SCREEN_DIM, alpha))
    ui.scale(state.global_scale or 1, state.global_scale or 1, 0, 0, function()
        for _, panel in ipairs(state.panels or {}) do
            render_panel(state, panel, alpha)
        end
        render_tooltip(state, alpha)
    end)
end

function screen.key_pressed(_, key)
    if key.escape then
        ui.action("close")
        return true
    end
    return false
end

return screen
