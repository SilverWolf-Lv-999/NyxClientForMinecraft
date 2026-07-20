local common = {}

local PANEL_RADIUS = 18
local PANEL_BLUR_RADIUS = 18
local PANEL_BORDER_WIDTH = 3
local PANEL_PADDING = 16
local HEADER_HEIGHT = 58
local ROW_HEIGHT = 58
local ROW_GAP = 8
local ICON_SIZE = 42
local ACTION_BUTTON_HEIGHT = 30
local ACTION_BUTTON_GAP = 8
local ACTION_BUTTON_TOP_GAP = 14
local FLIP_EDGE_MIN_SCALE = 0.075

local TEXT = 0xFFFFFFFF
local TEXT_MUTED = 0xFFE2E6EF
local TEXT_SUBTLE = 0xFFA8AFBE
local TEXT_DIM = 0xFF687181
local TEXT_DISABLED = 0xFF687181
local TITLE_SHADOW = 0xAA000000
local PANEL_BLUR = 0xE6FFFFFF
local PANEL_BACKGROUND = 0xB80A0C12
local PANEL_BORDER = 0x66FFFFFF
local ROW_BACKGROUND = 0x9913161E
local ROW_HOVER = 0xBB1A1E29
local ROW_SELECTED = 0x993D81F7
local ROW_DISABLED = 0x77333642
local CONTROL_BACKGROUND = 0xAA0E1118
local CONTROL_HOVER = 0xD7191D28
local CONTROL_BORDER = 0x22FFFFFF
local CONTROL_BORDER_HOVER = 0x663D81F7
local ACCENT = 0xFF3D81F7

local main_button_labels = {"Single Player", "Muti Player", "Alt Manager", "Option", "Exit"}
local button_hover = {}

function common.clamp(value, minimum, maximum)
    return math.max(minimum, math.min(maximum, value))
end

function common.lerp(from, to, progress)
    return from + (to - from) * progress
end

function common.phase(start_value, end_value, value)
    if end_value <= start_value then
        return value >= end_value and 1 or 0
    end
    return common.clamp((value - start_value) / (end_value - start_value), 0, 1)
end

function common.ease_in_cubic(value)
    return value * value * value
end

function common.ease_out_cubic(value)
    local inverse = 1 - value
    return 1 - inverse * inverse * inverse
end

function common.ease_in_out_cubic(value)
    if value < 0.5 then
        return 4 * value * value * value
    end
    local inverse = -2 * value + 2
    return 1 - inverse * inverse * inverse * 0.5
end

function common.ease_out_back(value)
    local c1 = 1.70158
    local c3 = c1 + 1
    local shifted = value - 1
    return 1 + c3 * shifted * shifted * shifted + c1 * shifted * shifted
end

function common.animate_exp(current, target, speed, frame_seconds)
    return target + (current - target) * math.exp(-speed * frame_seconds)
end

function common.center_panel(state, max_width, min_width, max_height, min_height)
    local width = common.clamp(state.width * 0.336, math.min(min_width, math.max(1, state.width - 32)),
        math.max(1, math.min(max_width, state.width - 32)))
    local height = common.clamp(state.height * 0.432, math.min(min_height, math.max(1, state.height - 32)),
        math.max(1, math.min(max_height, state.height - 32)))
    return {
        x = (state.width - width) * 0.5,
        y = (state.height - height) * 0.5,
        width = width,
        height = height
    }
end

function common.target_panel(state)
    local max_width = math.max(1, math.min(620, state.width - 42))
    local min_width = math.min(320, max_width)
    local base_width = common.clamp(state.width * 0.74, min_width, max_width)
    local width = math.max(1, base_width * 0.8)

    local controls_height = ACTION_BUTTON_HEIGHT * 2 + ACTION_BUTTON_GAP
    local available_height = math.max(1, state.height - controls_height - ACTION_BUTTON_TOP_GAP - 76)
    local max_height = math.max(1, math.min(388, available_height))
    local min_height = math.min(184, max_height)
    local base_height = common.clamp(state.height * 0.58, min_height, max_height)
    local height = math.min(available_height, base_height * 1.1)
    local y = math.max(44, (state.height - height - controls_height - ACTION_BUTTON_TOP_GAP - 18) * 0.5)

    return {
        x = (state.width - width) * 0.5,
        y = y,
        width = width,
        height = height
    }
end

function common.current_panel(from, to, transition_progress)
    local progress = common.ease_in_out_cubic(common.phase(0.24, 0.74, transition_progress))
    return {
        x = common.lerp(from.x, to.x, progress),
        y = common.lerp(from.y, to.y, progress),
        width = common.lerp(from.width, to.width, progress),
        height = common.lerp(from.height, to.height, progress)
    }
end

function common.panel(bounds)
    ui.panel(bounds.x, bounds.y, bounds.width, bounds.height, PANEL_RADIUS, PANEL_BLUR_RADIUS,
        PANEL_BLUR, PANEL_BACKGROUND, PANEL_BORDER_WIDTH, PANEL_BORDER)
end

function common.flat_panel(bounds)
    ui.rounded_rect(bounds.x, bounds.y, bounds.width, bounds.height, PANEL_RADIUS, PANEL_BACKGROUND)
    ui.outline(bounds.x, bounds.y, bounds.width, bounds.height, PANEL_RADIUS, PANEL_BORDER_WIDTH, PANEL_BORDER)
end

function common.draw_title(text, center_x, y, size, alpha)
    if alpha <= 0.001 then
        return
    end
    ui.display_centered_text(text, center_x + 1, y + 1, size, ui.opacity(TITLE_SHADOW, alpha))
    ui.display_centered_text(text, center_x, y, size, ui.opacity(TEXT, alpha))
end

function common.render_titles(state, main_panel, current_panel, target_title)
    local transition = state.transition_progress or 0
    local main_alpha = 1 - common.ease_out_cubic(common.phase(0.12, 0.42, transition))
    common.draw_title("Nyx Client", main_panel.x + main_panel.width * 0.5,
        math.max(8, main_panel.y - 44), 26, main_alpha)

    local target_alpha = common.ease_out_cubic(common.phase(0.54, 0.88, transition))
    common.draw_title(target_title, current_panel.x + current_panel.width * 0.5,
        math.max(8, current_panel.y - 38), 24, target_alpha)
end

function common.render_ghost_buttons(panel, alpha)
    if alpha <= 0.001 then
        return
    end

    local inset = common.clamp(panel.width * 0.13, 18, 32)
    local width = math.max(1, panel.width - inset * 2)
    local height = math.max(1, common.clamp(panel.height * 0.11, 28, 34))
    local gap = common.clamp(panel.height * 0.032, 7, 10)
    local total_height = #main_button_labels * height + (#main_button_labels - 1) * gap
    local x = panel.x + (panel.width - width) * 0.5
    local y = panel.y + (panel.height - total_height) * 0.5
    local font_height = ui.font_height("text", 12)

    for _, label in ipairs(main_button_labels) do
        ui.shadow(x, y, width, height, 8, 0, 5, 12, ui.opacity(0x55000000, alpha * 0.55))
        ui.rounded_rect(x, y, width, height, 8, ui.opacity(CONTROL_BACKGROUND, alpha))
        ui.outline(x, y, width, height, 8, 1, ui.opacity(CONTROL_BORDER, alpha))
        ui.text_centered(ui.trim_text("text", label, 12, width - 18), x + width * 0.5,
            y + (height - font_height) * 0.5, 12, ui.opacity(TEXT_MUTED, alpha))
        y = y + height + gap
    end
end

function common.render_user_card_transition(state)
    local transition = state.transition_progress or 0
    local hidden_offset = -ui.shared_user_card_width() - 28
    local offset
    local alpha

    if state.exiting then
        local progress = common.phase(0, 0.42, 1 - transition)
        offset = common.lerp(hidden_offset, 0, common.ease_out_back(progress))
        alpha = common.clamp(progress * 1.4, 0, 1)
    else
        local progress = common.phase(0, 0.42, transition)
        if progress <= 0.22 then
            offset = common.lerp(0, 12, common.ease_out_cubic(progress / 0.22))
        else
            offset = common.lerp(12, hidden_offset,
                common.ease_in_cubic((progress - 0.22) / 0.78))
        end
        alpha = 1 - common.ease_out_cubic(common.phase(0.52, 1, progress))
    end

    if alpha > 0.001 then
        ui.shared_user_card(offset, alpha)
    end
end

function common.animated_button(id, label, x, y, width, height, action, payload, active, interactive,
                                alpha, scale, font_size, frame_seconds)
    local enabled = active ~= false
    local hovered = enabled and interactive and ui.hovered(x, y, width, height)
    local hover = common.animate_exp(button_hover[id] or 0, hovered and 1 or 0, 16, frame_seconds or 1 / 60)
    button_hover[id] = hover

    local opacity = (alpha or 1) * (enabled and 1 or 0.48)
    local fill = ui.mix(CONTROL_BACKGROUND, CONTROL_HOVER, hover)
    local border = ui.mix(CONTROL_BORDER, CONTROL_BORDER_HOVER, hover)
    local text_color = enabled and ui.mix(TEXT_MUTED, TEXT, hover) or TEXT_DISABLED
    local font = font_size or 11
    local draw_scale = scale or 1

    ui.scale(draw_scale, draw_scale, x + width * 0.5, y + height * 0.5, function()
        ui.shadow(x, y, width, height, 8, 0, 5, 12,
            ui.opacity(0x55000000, opacity * (0.55 + hover * 0.45)))
        ui.rounded_rect(x, y, width, height, 8, ui.opacity(fill, opacity))
        ui.outline(x, y, width, height, 8, 1, ui.opacity(border, opacity))
        ui.text_centered_in_rect(ui.trim_text("text", label, font, width - 16),
            x, y, width, height, font, ui.opacity(text_color, opacity))
    end)
    ui.hitbox(x, y, width, height, action, payload, enabled and interactive)
end

function common.action_motion(state)
    local transition = state.transition_progress or 0
    if state.exiting then
        local exit = 1 - transition
        if exit < 0.18 then
            return 1, 1 + 0.08 * common.ease_out_cubic(exit / 0.18)
        end
        local shrink = common.ease_in_cubic(common.phase(0.18, 0.50, exit))
        return 1 - shrink, 1.08 * (1 - shrink)
    end

    local progress = common.ease_out_back(common.phase(0.72, 1, transition))
    return progress, 0.86 + 0.14 * progress
end

function common.render_action_buttons(state, panel, actions, first_row_count)
    local alpha, scale = common.action_motion(state)
    if alpha <= 0.001 or scale <= 0.001 or #actions == 0 then
        return
    end

    local first_count = math.min(first_row_count, #actions)
    local second_count = #actions - first_count
    local top = panel.y + panel.height + ACTION_BUTTON_TOP_GAP
    local first_width = (panel.width - ACTION_BUTTON_GAP * (first_count - 1)) / first_count

    for index = 1, first_count do
        local item = actions[index]
        local x = panel.x + (index - 1) * (first_width + ACTION_BUTTON_GAP)
        common.animated_button("action:" .. item[2], item[1], x, top, first_width, ACTION_BUTTON_HEIGHT,
            item[2], item[5], item[3], state.interactive, alpha, scale, 11, state.frame_seconds)
    end

    if second_count <= 0 then
        return
    end

    local second_width = (panel.width - ACTION_BUTTON_GAP * (second_count - 1)) / second_count
    local total_width = second_width * second_count + ACTION_BUTTON_GAP * (second_count - 1)
    local second_x = panel.x + (panel.width - total_width) * 0.5
    local second_y = top + ACTION_BUTTON_HEIGHT + ACTION_BUTTON_GAP
    for index = 1, second_count do
        local item = actions[index + first_count]
        local x = second_x + (index - 1) * (second_width + ACTION_BUTTON_GAP)
        common.animated_button("action:" .. item[2], item[1], x, second_y, second_width, ACTION_BUTTON_HEIGHT,
            item[2], item[5], item[3], state.interactive, alpha, scale, 11, state.frame_seconds)
    end
end

local function render_status(message, x, y, width, height, alpha)
    local font_height = ui.font_height("text", 12)
    ui.text_centered(ui.trim_text("text", message, 12, width - 24), x + width * 0.5,
        y + (height - font_height) * 0.5, 12, ui.opacity(TEXT_DIM, alpha))
end

local function render_scrollbar(list_x, list_y, list_width, list_height, count, scroll, alpha)
    local content_height = math.max(0, count * (ROW_HEIGHT + ROW_GAP) - ROW_GAP)
    local maximum = math.max(0, content_height - list_height)
    if maximum <= 0.001 or count == 0 then
        return
    end

    local bar_height = common.clamp(list_height * (list_height / math.max(list_height, content_height)), 24, list_height)
    local bar_y = list_y + (list_height - bar_height) * (scroll / maximum)
    ui.rounded_rect(list_x + list_width - 4, bar_y, 3, bar_height, 1.5, ui.opacity(0x66FFFFFF, alpha))
end

function common.render_collection_area(state, panel, face_alpha, config)
    if face_alpha <= 0.001 then
        return
    end

    local list_progress = common.phase(0.56, 0.96, state.transition_progress or 0)
    if list_progress <= 0.001 then
        return
    end

    local content_progress = common.ease_out_cubic(state.content_progress or 1)
    local alpha = common.ease_out_cubic(list_progress) * content_progress * face_alpha
    local items = config.items(state)
    local count = #items
    local header_x = panel.x + PANEL_PADDING
    local heading = type(config.heading) == "function" and config.heading(state) or config.heading
    ui.display_text(heading, header_x, panel.y + 18, 17, ui.opacity(TEXT, alpha))
    ui.text_font(ui.trim_text("text", config.subtitle(state, items), 10, panel.width - PANEL_PADDING * 2),
        header_x, panel.y + 39, 10, ui.opacity(TEXT_DIM, alpha))

    local list_x = panel.x + PANEL_PADDING
    local list_y = panel.y + HEADER_HEIGHT
    local list_width = panel.width - PANEL_PADDING * 2
    local list_height = math.max(0, panel.height - HEADER_HEIGHT - PANEL_PADDING)
    local content_height = math.max(0, count * (ROW_HEIGHT + ROW_GAP) - ROW_GAP)
    local scroll_id = type(config.scroll_id) == "function" and config.scroll_id(state) or config.scroll_id
    local scroll = ui.scroll(scroll_id, list_x, list_y, list_width, list_height, content_height, 38)

    ui.clip(list_x, list_y, list_width, list_height, function()
        local status = config.status(state, count)
        if status then
            render_status(status, list_x, list_y, list_width, list_height, alpha)
            return
        end

        for index, item in ipairs(items) do
            local row_y = list_y + (index - 1) * (ROW_HEIGHT + ROW_GAP) - scroll
            if row_y + ROW_HEIGHT >= list_y and row_y <= list_y + list_height then
                local row_progress = common.ease_out_cubic(common.clamp(
                    math.min(list_progress, state.content_progress or 1) * 1.18 - (index - 1) * 0.045, 0, 1))
                if row_progress > 0.001 then
                    local active = config.active(item)
                    local selected = item.selected == true
                    local hovered = state.interactive and active and ui.hovered(list_x, row_y, list_width, ROW_HEIGHT)
                    local visible_width = list_width * row_progress
                    local row_x = list_x + (list_width - visible_width) * 0.5
                    local row_alpha = alpha * row_progress
                    local base_fill = active and ROW_BACKGROUND or ROW_DISABLED
                    local fill = selected and ROW_SELECTED or ui.mix(base_fill, ROW_HOVER, hovered and 1 or 0)
                    local accent_border = selected or (config.accent_border and config.accent_border(item))
                    local border = accent_border and ACCENT or CONTROL_BORDER

                    ui.rounded_rect(row_x, row_y, visible_width, ROW_HEIGHT, 8, ui.opacity(fill, row_alpha))
                    ui.outline(row_x, row_y, visible_width, ROW_HEIGHT, 8, 1, ui.opacity(border, row_alpha))

                    if visible_width >= 78 then
                        local icon_x = row_x + 9
                        local icon_y = row_y + (ROW_HEIGHT - ICON_SIZE) * 0.5
                        ui.custom(config.icon, item.index, icon_x, icon_y, ICON_SIZE, row_alpha)

                        local text_x = icon_x + ICON_SIZE + 12
                        local text_width = math.max(1, row_x + visible_width - 12 - text_x)
                        ui.text_font(ui.trim_text("text", config.name(item), 12, text_width),
                            text_x, row_y + 10, 12, ui.opacity(config.name_color(item), row_alpha))
                        ui.text_font(ui.trim_text("text", config.detail(item), 9, text_width),
                            text_x, row_y + 29, 9, ui.opacity(TEXT_DIM, row_alpha))
                        ui.text_font(ui.trim_text("text", config.info(item), 9, text_width),
                            text_x, row_y + 43, 9, ui.opacity(config.info_color(item), row_alpha))
                    end

                    local select_action = type(config.select_action) == "function" and config.select_action(state) or config.select_action
                    local double_action = type(config.double_action) == "function" and config.double_action(state) or config.double_action
                    ui.hitbox(list_x, row_y, list_width, ROW_HEIGHT, select_action, item.index,
                        state.interactive and active, false, double_action)
                end
            end
        end
    end)

    render_scrollbar(list_x, list_y, list_width, list_height, count, scroll, alpha)
end

function common.render_flipping_panel(state, panel, config)
    local flip_progress = common.ease_in_out_cubic(common.phase(0.24, 0.74, state.transition_progress or 0))
    if flip_progress <= 0.001 then
        common.panel(panel)
        common.render_ghost_buttons(panel, 1)
        return
    end
    if flip_progress >= 0.999 then
        common.panel(panel)
        common.render_collection_area(state, panel, 1, config)
        return
    end

    local degrees = flip_progress * 180
    local projection_scale = ui.vertical_flip_scale(degrees)
    if projection_scale <= FLIP_EDGE_MIN_SCALE then
        local edge_width = math.max(2, PANEL_BORDER_WIDTH)
        local x = panel.x + panel.width * 0.5 - edge_width * 0.5
        ui.shadow(x, panel.y, edge_width, panel.height, edge_width * 0.5, 0, 5, 16, 0x77000000)
        ui.rounded_rect(x, panel.y, edge_width, panel.height, edge_width * 0.5, PANEL_BACKGROUND)
        ui.rounded_rect(x + edge_width * 0.5 - 0.5, panel.y + PANEL_RADIUS, 1,
            math.max(0, panel.height - PANEL_RADIUS * 2), 0.5, PANEL_BORDER)
        return
    end

    local back_face = ui.vertical_flip_back_face(degrees)
    local face_alpha = common.ease_out_cubic(common.clamp(
        (projection_scale - FLIP_EDGE_MIN_SCALE) / (1 - FLIP_EDGE_MIN_SCALE), 0, 1))
    ui.vertical_perspective_flip(degrees, panel.x + panel.width * 0.5, panel.y + panel.height * 0.5,
        panel.width, FLIP_EDGE_MIN_SCALE, function()
            common.flat_panel(panel)
            local shade_alpha = math.sin(math.rad(degrees)) * 0.34
            if shade_alpha > 0.001 then
                ui.rounded_horizontal_gradient(panel.x, panel.y, panel.width, panel.height, PANEL_RADIUS,
                    ui.opacity(0x99000000, shade_alpha), ui.opacity(0x22000000, shade_alpha))
            end
            if back_face then
                ui.horizontal_reflection(panel.x + panel.width * 0.5, function()
                    common.render_collection_area(state, panel, face_alpha, config)
                end)
            else
                common.render_ghost_buttons(panel, face_alpha)
            end
        end)
end

function common.render_collection_screen(state, config)
    ui.shared_background()
    ui.rect(0, 0, state.width, state.height,
        ui.opacity(0x66000000, 0.32 * (state.transition_progress or 0)))
    common.render_user_card_transition(state)

    local main_panel = common.center_panel(state, 301, 175, 312, 180)
    local target_panel = common.target_panel(state)
    local current_panel = common.current_panel(main_panel, target_panel, state.transition_progress or 0)
    common.render_flipping_panel(state, current_panel, config)
    common.render_titles(state, main_panel, current_panel, config.title)
    common.render_action_buttons(state, target_panel, config.actions(state), config.first_row_count)
    ui.background_selector()
end

function common.key_back(key)
    if key.escape then
        ui.action("back")
        return true
    end
    return false
end

return common
