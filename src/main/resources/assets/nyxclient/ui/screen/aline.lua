local screen = {}

local HEADER_HEIGHT = 18
local MODULE_HEIGHT = 18
local VALUE_HEIGHT = 17
local COLOR_PICKER_HEIGHT = 47
local TEXT_OFFSET_Y = 3
local SETTING_INDENT = 5
local SETTING_RIGHT_INSET = 2

local BACKDROP_BLUR = 11
local BACKDROP_BLUR_COLOR = 0x1CFFFFFF
local BACKDROP_TINT = 0x10080618
local PANEL_BLUR = 12
local PANEL_BLUR_COLOR = 0x22FFFFFF
local PANEL_BACKGROUND = 0x62100932
local PANEL_HEADER = 0xD238149C
local PANEL_HEADER_FOCUSED = 0xE14B20BF
local PANEL_HEADER_BOTTOM = 0xC2290A76
local PANEL_BORDER = 0x936D4CC4
local MODULE_BACKGROUND = 0x2F0C0820
local MODULE_HOVER = 0x4B482078
local MODULE_ENABLED = 0xC9471CC3
local SETTING_BACKGROUND = 0x280B071B
local SETTING_HOVER = 0x4A50217F
local SETTING_SELECTED = 0xB7451AB5
local TEXT = 0xFFFFFFFF
local TEXT_MUTED = 0xFFD9D0F3
local TEXT_DIM = 0xFFC7BCE9
local TEXT_DISABLED = 0xFFA59CBF
local ACCENT = 0xFFF6F0FF

local module_hover = {}
local setting_hover = {}
local tooltip = nil

local function clamp(value, minimum, maximum)
    return math.max(minimum, math.min(maximum, value))
end

local function animate_exp(current, target, speed, frame_seconds)
    return target + (current - target) * math.exp(-speed * (frame_seconds or 1 / 60))
end

local function actual(state, value)
    return value * (state.global_scale or 1)
end

local function hovered(state, x, y, width, height)
    local mouse_x = state.mouse_x or 0
    local mouse_y = state.mouse_y or 0
    return mouse_x >= x and mouse_x < x + width and mouse_y >= y and mouse_y < y + height
end

local function hitbox(state, x, y, width, height, action, payload, active, capture)
    ui.hitbox(actual(state, x), actual(state, y), actual(state, width), actual(state, height),
        action, payload, active, capture == true)
end

local function clipped_hitbox(state, x, y, width, height, viewport_y, viewport_height,
                              action, payload, active, capture)
    local top = math.max(y, viewport_y)
    local bottom = math.min(y + height, viewport_y + viewport_height)
    if bottom > top then
        hitbox(state, x, top, width, bottom - top, action, payload, active, capture)
    end
end

local function scroll_area(state, id, x, y, width, height, content_height, step)
    local scale = state.global_scale or 1
    local offset = ui.scroll(id, x * scale, y * scale, width * scale, height * scale,
        content_height * scale, step * scale)
    return offset / math.max(0.001, scale)
end

local function setting_payload(module, value)
    return {
        module = module.index,
        value = value.index
    }
end

local function render_arrow(open, x, y, alpha)
    ui.text_visual_centered("material", ui.codepoint(open and 0xE5CF or 0xE5CC), x, y, 8,
        ui.opacity(TEXT, alpha * 0.84))
end

local function render_number_value(state, module, value, x, y, width, viewport_y, viewport_height, alpha)
    local hover_id = "n:" .. module.index .. ":" .. value.index
    local is_hovered = state.interactive and hovered(state, x, y, width, VALUE_HEIGHT)
    local hover = animate_exp(setting_hover[hover_id] or 0, is_hovered and 1 or 0, 20, state.frame_seconds)
    setting_hover[hover_id] = hover
    local fill = ui.mix(SETTING_BACKGROUND, SETTING_HOVER, hover)
    local payload = setting_payload(module, value)

    ui.rect(x + 1, y, width - 2, VALUE_HEIGHT - 1, ui.opacity(fill, alpha))
    ui.text_font(ui.trim_text("text", value.name or "", 8.0, width - 42), x + 4, y + TEXT_OFFSET_Y, 8.0,
        ui.opacity(TEXT_MUTED, alpha))
    ui.text_font(ui.trim_text("text", value.display or "", 8.0, 31), x + width - 4
        - ui.font_width("text", ui.trim_text("text", value.display or "", 8.0, 31), 8.0), y + TEXT_OFFSET_Y, 8.0,
        ui.opacity(TEXT, alpha))
    ui.rect(x + 2, y + VALUE_HEIGHT - 2, width - 4, 1, ui.opacity(0x665E3F9E, alpha))
    ui.rect(x + 2, y + VALUE_HEIGHT - 2, (width - 4) * (value.progress or 0), 1,
        ui.opacity(SETTING_SELECTED, alpha))
    clipped_hitbox(state, x, y, width, VALUE_HEIGHT, viewport_y, viewport_height,
        "number_drag", {
            module = module.index,
            value = value.index,
            x = x + 2,
            width = width - 4
        }, state.interactive, true)
end

local function render_bool_value(state, module, value, x, y, width, viewport_y, viewport_height, alpha)
    local hover_id = "b:" .. module.index .. ":" .. value.index
    local is_hovered = state.interactive and hovered(state, x, y, width, VALUE_HEIGHT)
    local hover = animate_exp(setting_hover[hover_id] or 0, is_hovered and 1 or 0, 20, state.frame_seconds)
    setting_hover[hover_id] = hover
    local fill = value.enabled and SETTING_SELECTED or ui.mix(SETTING_BACKGROUND, SETTING_HOVER, hover)

    ui.rect(x + 1, y, width - 2, VALUE_HEIGHT - 1, ui.opacity(fill, alpha))
    ui.text_font(ui.trim_text("text", value.name or "", 8.2, width - 12), x + 4, y + TEXT_OFFSET_Y, 8.2,
        ui.opacity(value.enabled and TEXT or TEXT_MUTED, alpha))
    clipped_hitbox(state, x, y, width, VALUE_HEIGHT, viewport_y, viewport_height,
        "setting_click", setting_payload(module, value), state.interactive, false)
end

local function render_enum_value(state, module, value, x, y, width, viewport_y, viewport_height, alpha)
    local hover_id = "e:" .. module.index .. ":" .. value.index
    local is_hovered = state.interactive and hovered(state, x, y, width, VALUE_HEIGHT)
    local hover = animate_exp(setting_hover[hover_id] or 0, is_hovered and 1 or 0, 20, state.frame_seconds)
    setting_hover[hover_id] = hover
    local fill = ui.mix(SETTING_BACKGROUND, SETTING_HOVER, hover)

    ui.rect(x + 1, y, width - 2, VALUE_HEIGHT - 1, ui.opacity(fill, alpha))
    ui.text_font(ui.trim_text("text", (value.name or "") .. ": " .. (value.display or ""), 8.0, width - 14),
        x + 4, y + TEXT_OFFSET_Y, 8.0, ui.opacity(TEXT_MUTED, alpha))
    render_arrow(value.open, x + width - 7, y + VALUE_HEIGHT * 0.5, alpha)
    clipped_hitbox(state, x, y, width, VALUE_HEIGHT, viewport_y, viewport_height,
        "setting_click", setting_payload(module, value), state.interactive, false)

    if not value.open then
        return
    end

    local option_y = y + VALUE_HEIGHT
    for _, option in ipairs(value.options or {}) do
        local selected = option.selected == true
        ui.rect(x + 1, option_y, width - 2, VALUE_HEIGHT - 1,
            ui.opacity(selected and SETTING_SELECTED or SETTING_BACKGROUND, alpha))
        ui.text_centered(ui.trim_text("text", option.label or "", 8.0, width - 10), x + width * 0.5,
            option_y + TEXT_OFFSET_Y, 8.0, ui.opacity(selected and TEXT or TEXT_DISABLED, alpha))
        clipped_hitbox(state, x, option_y, width, VALUE_HEIGHT, viewport_y, viewport_height,
            "enum_select", {
                module = module.index,
                value = value.index,
                mode = option.value
            }, state.interactive, false)
        option_y = option_y + VALUE_HEIGHT
    end
end

local function render_color_picker(state, module, value, x, y, width, viewport_y, viewport_height, alpha)
    local picker_y = y + VALUE_HEIGHT + 2
    local picker_h = COLOR_PICKER_HEIGHT - 4
    local hue_w = 4
    local alpha_w = value.allow_alpha and 4 or 0
    local gap = 3
    local saturation_x = x + 3
    local saturation_w = width - 6 - hue_w - alpha_w - gap * (value.allow_alpha and 2 or 1)
    local hue_x = saturation_x + saturation_w + gap
    local alpha_x = hue_x + hue_w + gap
    local hue_color = value.hue_color or 0xFFFF0000
    local payload = setting_payload(module, value)

    ui.horizontal_gradient(saturation_x, picker_y, saturation_w, picker_h, 0xFFFFFFFF, hue_color)
    ui.vertical_gradient(saturation_x, picker_y, saturation_w, picker_h, 0x00000000, 0xFF000000)
    ui.outline(saturation_x, picker_y, saturation_w, picker_h, 0, 1, ui.opacity(0xAAFFFFFF, alpha * 0.42))
    ui.circle(saturation_x + saturation_w * (value.saturation or 0), picker_y
        + picker_h * (1 - (value.brightness or 0)), 2.0, ui.opacity(TEXT, alpha))

    local hue_steps = value.hue_steps or {}
    local step_height = picker_h / math.max(1, #hue_steps)
    for index, color in ipairs(hue_steps) do
        ui.rect(hue_x, picker_y + (index - 1) * step_height, hue_w, step_height + 0.4, ui.opacity(color, alpha))
    end
    ui.outline(hue_x, picker_y, hue_w, picker_h, 0, 1, ui.opacity(0xAAFFFFFF, alpha * 0.42))
    ui.rect(hue_x - 1, picker_y + picker_h * (value.hue or 0) - 1, hue_w + 2, 2, ui.opacity(TEXT, alpha))

    if value.allow_alpha then
        ui.vertical_gradient(alpha_x, picker_y, alpha_w, picker_h, value.alpha_opaque or hue_color,
            value.alpha_transparent or 0x00000000)
        ui.outline(alpha_x, picker_y, alpha_w, picker_h, 0, 1, ui.opacity(0xAAFFFFFF, alpha * 0.42))
        ui.rect(alpha_x - 1, picker_y + picker_h * (value.alpha or 0) - 1, alpha_w + 2, 2,
            ui.opacity(TEXT, alpha))
    end

    clipped_hitbox(state, saturation_x, picker_y, saturation_w, picker_h, viewport_y, viewport_height,
        "color_edit", {
            module = payload.module,
            value = payload.value,
            part = "sv",
            x = saturation_x,
            y = picker_y,
            width = saturation_w,
            height = picker_h
        }, state.interactive, true)
    clipped_hitbox(state, hue_x, picker_y, hue_w, picker_h, viewport_y, viewport_height,
        "color_edit", {
            module = payload.module,
            value = payload.value,
            part = "hue",
            x = hue_x,
            y = picker_y,
            width = hue_w,
            height = picker_h
        }, state.interactive, true)
    if value.allow_alpha then
        clipped_hitbox(state, alpha_x, picker_y, alpha_w, picker_h, viewport_y, viewport_height,
            "color_edit", {
                module = payload.module,
                value = payload.value,
                part = "alpha",
                x = alpha_x,
                y = picker_y,
                width = alpha_w,
                height = picker_h
            }, state.interactive, true)
    end
end

local function render_color_value(state, module, value, x, y, width, viewport_y, viewport_height, alpha)
    local hover_id = "c:" .. module.index .. ":" .. value.index
    local is_hovered = state.interactive and hovered(state, x, y, width, VALUE_HEIGHT)
    local hover = animate_exp(setting_hover[hover_id] or 0, is_hovered and 1 or 0, 20, state.frame_seconds)
    setting_hover[hover_id] = hover
    local fill = ui.mix(SETTING_BACKGROUND, SETTING_HOVER, hover)

    ui.rect(x + 1, y, width - 2, VALUE_HEIGHT - 1, ui.opacity(fill, alpha))
    ui.text_font(ui.trim_text("text", value.name or "", 8.2, width - 26), x + 4, y + TEXT_OFFSET_Y, 8.2,
        ui.opacity(TEXT_MUTED, alpha))
    ui.rect(x + width - 15, y + 3, 10, 8, ui.opacity(value.color or 0xFFFFFFFF, alpha))
    ui.outline(x + width - 15, y + 3, 10, 8, 0, 1, ui.opacity(TEXT, alpha * 0.62))
    render_arrow(value.open, x + width - 4, y + VALUE_HEIGHT * 0.5, alpha)
    clipped_hitbox(state, x, y, width, VALUE_HEIGHT, viewport_y, viewport_height,
        "setting_click", setting_payload(module, value), state.interactive, false)

    if value.open then
        render_color_picker(state, module, value, x, y, width, viewport_y, viewport_height, alpha)
    end
end

local function render_text_value(state, module, value, x, y, width, viewport_y, viewport_height, alpha)
    local hover_id = "t:" .. module.index .. ":" .. value.index
    local is_hovered = state.interactive and hovered(state, x, y, width, VALUE_HEIGHT)
    local hover = animate_exp(setting_hover[hover_id] or 0, is_hovered and 1 or 0, 20, state.frame_seconds)
    setting_hover[hover_id] = hover
    local fill = ui.mix(SETTING_BACKGROUND, SETTING_HOVER, hover)
    local text = value.display or ""

    if value.type == "keybind" then
        text = value.editing and "Press a key..." or (value.bound or text)
    elseif value.type == "string" and value.editing and math.floor((state.time_seconds or 0) * 2) % 2 == 0 then
        text = text .. "_"
    end
    ui.rect(x + 1, y, width - 2, VALUE_HEIGHT - 1, ui.opacity(fill, alpha))
    ui.text_font(ui.trim_text("text", value.name or "", 7.8, width * 0.44), x + 4, y + TEXT_OFFSET_Y, 7.8,
        ui.opacity(TEXT_DIM, alpha))
    local right = ui.trim_text("text", text, 7.8, width * 0.47)
    ui.text_font(right, x + width - 4 - ui.font_width("text", right, 7.8), y + TEXT_OFFSET_Y, 7.8,
        ui.opacity(value.editing and TEXT or TEXT_MUTED, alpha))
    clipped_hitbox(state, x, y, width, VALUE_HEIGHT, viewport_y, viewport_height,
        "setting_click", setting_payload(module, value), state.interactive, false)
end

local function render_button_value(state, module, value, x, y, width, viewport_y, viewport_height, alpha)
    local hover_id = "a:" .. module.index .. ":" .. value.index
    local is_hovered = state.interactive and hovered(state, x, y, width, VALUE_HEIGHT)
    local hover = animate_exp(setting_hover[hover_id] or 0, is_hovered and 1 or 0, 20, state.frame_seconds)
    setting_hover[hover_id] = hover
    local fill = ui.mix(SETTING_BACKGROUND, SETTING_SELECTED, hover * 0.72)

    ui.rect(x + 1, y, width - 2, VALUE_HEIGHT - 1, ui.opacity(fill, alpha))
    ui.text_centered(ui.trim_text("text", value.name or "", 8.0, width - 10), x + width * 0.5,
        y + TEXT_OFFSET_Y, 8.0,
        ui.opacity(TEXT, alpha))
    clipped_hitbox(state, x, y, width, VALUE_HEIGHT, viewport_y, viewport_height,
        "setting_click", setting_payload(module, value), state.interactive, false)
end

local function render_simple_value(state, module, value, x, y, width, viewport_y, viewport_height, alpha)
    ui.rect(x + 1, y, width - 2, VALUE_HEIGHT - 1, ui.opacity(SETTING_BACKGROUND, alpha))
    ui.text_font(ui.trim_text("text", (value.name or "") .. ": " .. (value.display or ""), 8.0, width - 8),
        x + 4, y + TEXT_OFFSET_Y, 8.0, ui.opacity(TEXT_DISABLED, alpha))
    clipped_hitbox(state, x, y, width, VALUE_HEIGHT, viewport_y, viewport_height,
        "setting_click", setting_payload(module, value), state.interactive, false)
end

local function render_value(state, module, value, x, y, width, visible_height,
                            viewport_y, viewport_height, alpha)
    if visible_height <= 0 then
        return
    end

    local base_top = math.max(y, viewport_y)
    local base_bottom = math.min(y + VALUE_HEIGHT, viewport_y + viewport_height)
    if base_bottom > base_top then
        ui.custom("setting_bounds", module.index, value.index, x, base_top, width, base_bottom - base_top)
    end

    if value.type == "bool" then
        render_bool_value(state, module, value, x, y, width, viewport_y, viewport_height, alpha)
    elseif value.type == "number" then
        render_number_value(state, module, value, x, y, width, viewport_y, viewport_height, alpha)
    elseif value.type == "enum" then
        render_enum_value(state, module, value, x, y, width, viewport_y, viewport_height, alpha)
    elseif value.type == "color" then
        render_color_value(state, module, value, x, y, width, viewport_y, viewport_height, alpha)
    elseif value.type == "button" then
        render_button_value(state, module, value, x, y, width, viewport_y, viewport_height, alpha)
    elseif value.type == "keybind" or value.type == "string" then
        render_text_value(state, module, value, x, y, width, viewport_y, viewport_height, alpha)
    else
        render_simple_value(state, module, value, x, y, width, viewport_y, viewport_height, alpha)
    end
end

local function render_values(state, module, x, y, width, visible_height, viewport_y, viewport_height, alpha)
    if visible_height <= 0.5 then
        return
    end

    ui.clip(x, y, width, visible_height, function()
        local value_y = y
        local remaining = visible_height
        for _, value in ipairs(module.values or {}) do
            if remaining <= 0 then
                break
            end
            local height = value.height or VALUE_HEIGHT
            render_value(state, module, value, x, value_y, width, math.min(height, remaining),
                viewport_y, viewport_height, alpha)
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
    local hover = animate_exp(module_hover[module.index] or 0, is_hovered and 1 or 0, 20, state.frame_seconds)
    module_hover[module.index] = hover
    local enabled = module.enabled_progress or 0
    local row_height = module.row_height or MODULE_HEIGHT
    local expanded_height = math.max(0, row_height - MODULE_HEIGHT)
    local fill = ui.mix(MODULE_BACKGROUND, MODULE_HOVER, hover)

    ui.rect(x + 1, y, width - 2, MODULE_HEIGHT - 1, ui.opacity(fill, alpha))
    if enabled > 0.001 then
        ui.rect(x + 1, y, width - 2, MODULE_HEIGHT - 1, ui.opacity(MODULE_ENABLED, alpha * enabled))
    end
    ui.text_font(ui.trim_text("text", module.name or "", 8.5, width - 18), x + 4, y + TEXT_OFFSET_Y, 8.5,
        ui.opacity(ui.mix(TEXT_DIM, ACCENT, enabled), alpha))
    if module.expandable then
        render_arrow(module.expanded, x + width - 7, y + MODULE_HEIGHT * 0.5, alpha)
    end

    ui.custom("module_bounds", module.index, x, header_top, width, header_visible, header_visible > 0)
    if header_visible > 0 then
        hitbox(state, x, header_top, width, header_visible, "toggle_module", module.index,
            state.interactive, false)
    end
    if is_hovered and module.description and module.description ~= "" then
        tooltip = module.description
    end

    if expanded_height > 0.5 then
        local settings_x = x + SETTING_INDENT
        local settings_width = width - SETTING_INDENT - SETTING_RIGHT_INSET
        ui.rect(x + 2, y + MODULE_HEIGHT, 1, expanded_height,
            ui.opacity(PANEL_BORDER, alpha * (module.expand_progress or 0) * 0.72))
        ui.rect(settings_x, y + MODULE_HEIGHT, settings_width, expanded_height,
            ui.opacity(SETTING_BACKGROUND, alpha * (module.expand_progress or 0)))
        render_values(state, module, settings_x, y + MODULE_HEIGHT, settings_width, expanded_height,
            viewport_y, viewport_height, alpha)
    end
end

local function render_panel_contents(state, panel, alpha)
    local body_y = panel.y + HEADER_HEIGHT
    local body_height = panel.height - HEADER_HEIGHT
    local scroll = scroll_area(state, "aline:" .. panel.id, panel.x, body_y,
        panel.width, body_height, panel.content_height or 0, 30)

    ui.clip(panel.x, body_y, panel.width, body_height, function()
        local y = body_y - scroll
        for _, module in ipairs(panel.modules or {}) do
            render_module(state, module, panel.x, y, panel.width, body_y, body_height, alpha)
            y = y + (module.row_height or MODULE_HEIGHT)
        end
    end)
end

local function render_panel(state, panel, alpha)
    local scale = state.screen_scale or 1
    local center_x = panel.x + panel.width * 0.5
    local center_y = panel.y + panel.height * 0.5
    local header_color = panel.focused and PANEL_HEADER_FOCUSED or PANEL_HEADER

    ui.scale(scale, scale, center_x, center_y, function()
        ui.shadow(panel.x, panel.y, panel.width, panel.height, 0, 0, 2, 7,
            ui.opacity(0x6511052D, alpha))
        ui.panel(panel.x, panel.y, panel.width, panel.height, 0, PANEL_BLUR,
            ui.opacity(PANEL_BLUR_COLOR, alpha), ui.opacity(PANEL_BACKGROUND, alpha), 1,
            ui.opacity(PANEL_BORDER, alpha))
        ui.vertical_gradient(panel.x, panel.y, panel.width, HEADER_HEIGHT,
            ui.opacity(header_color, alpha), ui.opacity(PANEL_HEADER_BOTTOM, alpha))
        ui.clip(panel.x, panel.y, panel.width, panel.height, function()
            ui.text_font(panel.label or "", panel.x + 4, panel.y + TEXT_OFFSET_Y, 8.5, ui.opacity(TEXT, alpha))
            ui.text_visual_centered("material", ui.codepoint(0xE5D5),
                panel.x + panel.width - 7, panel.y + HEADER_HEIGHT * 0.5, 8, ui.opacity(TEXT, alpha * 0.76))
            render_panel_contents(state, panel, alpha)
        end)
    end)

    hitbox(state, panel.x, panel.y, panel.width, HEADER_HEIGHT, "drag_panel", {
        id = panel.id
    }, state.interactive, true)
end

local function render_tooltip(state, alpha)
    if not tooltip or tooltip == "" then
        return
    end

    local text = ui.trim_text("text", tooltip, 8.0, 180)
    local width = ui.font_width("text", text, 8.0) + 10
    local x = clamp((state.mouse_x or 0) + 7, 4, state.width - width - 4)
    local y = clamp((state.mouse_y or 0) + 7, 4, state.height - 17)
    ui.panel(x, y, width, 16, 0, 7, ui.opacity(0x22FFFFFF, alpha),
        ui.opacity(0xBC10092F, alpha), 1, ui.opacity(PANEL_BORDER, alpha))
    ui.text_font(text, x + 5, y + TEXT_OFFSET_Y, 8.0, ui.opacity(TEXT_MUTED, alpha))
end

function screen.render(state)
    local alpha = state.screen_visibility or 1
    tooltip = nil
    ui.panel(0, 0, state.screen_width, state.screen_height, 0, BACKDROP_BLUR,
        ui.opacity(BACKDROP_BLUR_COLOR, alpha), ui.opacity(BACKDROP_TINT, alpha), 0, 0)
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
