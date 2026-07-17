local screen = {}

local PANEL_WIDTH = 820
local PANEL_HEIGHT = 540
local MIN_MARGIN = 16
local SIDEBAR_WIDTH = 220
local SIDEBAR_WIDTH_COMPACT = 172
local HEADER_HEIGHT = 64
local LOGO_AREA_HEIGHT = 64
local USER_AREA_HEIGHT = 72
local NAV_ITEM_HEIGHT = 34
local CONTENT_PADDING = 16
local CONTENT_TOP_GAP = 24
local COLUMN_GAP = 20
local COLUMN_GAP_COMPACT = 12
local GROUP_HEADER_HEIGHT = 18
local GROUP_PADDING = 4
local MODULE_ROW_HEIGHT = 50
local EXPANDED_TOP_PADDING = 8
local EXPANDED_BOTTOM_PADDING = 8
local BOTTOM_PADDING = 28

local SCREEN_DIM = 0xB005060A
local PANEL_BACKGROUND = 0xFF0C0D11
local SIDEBAR_OVERLAY = 0x33000000
local CARD_BACKGROUND = 0xFF14161D
local CONTROL_BACKGROUND = 0xFF0C0D11
local CONTROL_HOVER = 0xFF181B24
local TEXT = 0xFFFFFFFF
local TEXT_MUTED = 0xFFA0A5B5
local TEXT_DIM = 0xFF4B5263
local TEXT_SUBTLE = 0xFF6C717E
local BORDER = 0x1AFFFFFF
local BORDER_SOFT = 0x0AFFFFFF
local DIVIDER = 0x08FFFFFF
local HOVER = 0x0AFFFFFF
local ACCENT = 0xB33D81F7
local TOGGLE_OFF = 0xFF20222B

local module_hover = {}

local function clamp(value, minimum, maximum)
    return math.max(minimum, math.min(maximum, value))
end

local function lerp(from, to, progress)
    return from + (to - from) * progress
end

local function animate_exp(current, target, speed, frame_seconds)
    return target + (current - target) * math.exp(-speed * (frame_seconds or 1 / 60))
end

local function coordinate_scale(state)
    return math.max(0.001, state.coordinate_scale or 1)
end

local function hovered(state, x, y, width, height)
    local mouse_x = state.mouse_x or 0
    local mouse_y = state.mouse_y or 0
    return mouse_x >= x and mouse_x < x + width and mouse_y >= y and mouse_y < y + height
end

local function hitbox(state, x, y, width, height, action, payload, active, capture)
    local scale = coordinate_scale(state)
    ui.hitbox(x / scale, y / scale, width / scale, height / scale,
        action, payload, active, capture == true)
end

local function scroll_area(state, id, x, y, width, height, content_height, step)
    local scale = coordinate_scale(state)
    return ui.scroll(id, x / scale, y / scale, width / scale, height / scale,
        content_height / scale, step / scale) * scale
end

local function category_by_id(state, id)
    for _, category in ipairs(state.categories or {}) do
        if category.id == id then
            return category
        end
    end
    return nil
end

local function panel_bounds(state)
    local width = math.max(1, math.min(PANEL_WIDTH, state.width - MIN_MARGIN * 2))
    local height = math.max(1, math.min(PANEL_HEIGHT, state.height - MIN_MARGIN * 2))
    local base_x = (state.width - width) * 0.5
    local base_y = (state.height - height) * 0.5
    local sidebar
    if width < 520 then
        sidebar = 138
    elseif width < 760 then
        sidebar = SIDEBAR_WIDTH_COMPACT
    else
        sidebar = SIDEBAR_WIDTH
    end
    return {
        x = base_x + (state.panel_offset_x or 0),
        y = base_y + (state.panel_offset_y or 0),
        base_x = base_x,
        base_y = base_y,
        width = width,
        height = height,
        sidebar = sidebar,
        main_x = base_x + (state.panel_offset_x or 0) + sidebar,
        main_width = width - sidebar
    }
end

local function sidebar_padding(panel)
    return panel.sidebar < 175 and 14 or 24
end

local function render_logo(state, panel)
    local pad = sidebar_padding(panel)
    local title_x = panel.x + pad
    local title_y = panel.y + 20
    local title = state.client_name or "Nyx"
    ui.text_font(title, title_x, title_y, 22, TEXT)
    local version_x = title_x + ui.font_width("text", title, 22) + 3
    ui.text_font(state.version or "", version_x, title_y - 3, 8, TEXT_DIM)
    ui.rect(panel.x + 16, panel.y + LOGO_AREA_HEIGHT - 1, panel.sidebar - 32, 1, BORDER_SOFT)
end

local function category_y(state, panel, target_id)
    local y = panel.y + LOGO_AREA_HEIGHT + 45
    for _, category in ipairs(state.categories or {}) do
        if category.id == target_id then
            return y
        end
        y = y + NAV_ITEM_HEIGHT + 4
    end
    return nil
end

local function render_category_selection(state, panel)
    local selected_y = category_y(state, panel, state.selected_category)
    if not selected_y then
        return
    end

    local y = selected_y
    if state.previous_category and state.previous_category ~= "" then
        local previous_y = category_y(state, panel, state.previous_category)
        if previous_y then
            y = lerp(previous_y, selected_y, state.category_progress or 1)
        end
    end
    ui.horizontal_gradient(panel.x, y, panel.sidebar, NAV_ITEM_HEIGHT, 0x143D81F7, 0x003D81F7)
    ui.rect(panel.x, y, 2, NAV_ITEM_HEIGHT, ACCENT)
end

local function render_categories(state, panel)
    local pad = sidebar_padding(panel)
    local y = panel.y + LOGO_AREA_HEIGHT + 28
    ui.text_font("MODULES", panel.x + pad, y, 9, TEXT_DIM)
    y = y + 17
    render_category_selection(state, panel)

    for _, category in ipairs(state.categories or {}) do
        local is_hovered = state.interactive and hovered(state, panel.x, y, panel.sidebar, NAV_ITEM_HEIGHT)
        local active = category.active_progress or 0
        if is_hovered and active < 0.1 then
            ui.rect(panel.x, y, panel.sidebar, NAV_ITEM_HEIGHT, 0x03FFFFFF)
        end

        local base_color = is_hovered and TEXT or TEXT_SUBTLE
        local color = active <= 0 and base_color or ui.mix(base_color, ACCENT, active)
        local icon_x = panel.x + pad
        local icon_y = y + (NAV_ITEM_HEIGHT - 18) * 0.5
        ui.rounded_rect(icon_x, icon_y, 18, 18, 4, 0xFF11131A)
        if active > 0 then
            ui.rounded_rect(icon_x, icon_y, 18, 18, 4, ui.opacity(0x183D81F7, active))
        end
        ui.text_centered_in_rect(category.initial or "", icon_x, icon_y, 18, 18, 11, color)
        ui.text_font(category.label or "", icon_x + 30, y + 10, 12, color)
        hitbox(state, panel.x, y, panel.sidebar, NAV_ITEM_HEIGHT,
            "category", category.id, state.interactive)
        y = y + NAV_ITEM_HEIGHT + 4
    end
end

local function render_user(state, panel)
    local pad = sidebar_padding(panel)
    local y = panel.y + panel.height - USER_AREA_HEIGHT
    ui.rect(panel.x + 16, y, panel.sidebar - 32, 1, BORDER_SOFT)

    local avatar_size = 32
    local avatar_x = panel.x + pad
    local avatar_y = y + 20
    ui.custom("avatar", avatar_x, avatar_y, avatar_size, state.screen_visibility or 1)
    if panel.sidebar >= 165 then
        local text_x = avatar_x + avatar_size + 12
        local max_width = math.max(20, panel.x + panel.sidebar - pad - text_x)
        ui.text_font(ui.trim_text("text", state.user_name or "User", 11, max_width),
            text_x, avatar_y + 3, 11, TEXT)
        ui.text_font("Windows", text_x, avatar_y + 20, 9, TEXT_DIM)
    end
end

local function render_sidebar(state, panel)
    ui.rect(panel.x, panel.y, panel.sidebar, panel.height, SIDEBAR_OVERLAY)
    ui.rect(panel.x + panel.sidebar - 1, panel.y + 12, 1, panel.height - 24, BORDER_SOFT)
    render_logo(state, panel)
    render_categories(state, panel)
    render_user(state, panel)
end

local function render_header(state, panel)
    ui.rect(panel.main_x, panel.y, panel.main_width, HEADER_HEIGHT, PANEL_BACKGROUND)
    ui.rect(panel.main_x + 16, panel.y + HEADER_HEIGHT - 1, panel.main_width - 32, 1, BORDER_SOFT)

    local selected = category_by_id(state, state.selected_category)
    local button_y = panel.y + (HEADER_HEIGHT - 28) * 0.5
    local first_x = panel.main_x + 16
    ui.rounded_rect(first_x, button_y, 150, 28, 4, CONTROL_BACKGROUND)
    ui.outline(first_x, button_y, 150, 28, 4, 1, BORDER_SOFT)
    ui.rect(first_x + 31, button_y, 1, 28, BORDER_SOFT)
    ui.text_centered_in_rect("S", first_x, button_y, 31, 28, 11, TEXT_SUBTLE)
    ui.text_font(ui.trim_text("text", selected and selected.label or "", 11, 96),
        first_x + 42, button_y + 8, 11, TEXT_MUTED)
    ui.text_font("v", first_x + 134, button_y + 8, 11, TEXT_SUBTLE)

    local second_x = first_x + 162
    ui.rounded_rect(second_x, button_y, 96, 28, 4, CONTROL_BACKGROUND)
    ui.outline(second_x, button_y, 96, 28, 4, 1, BORDER_SOFT)
    ui.text_font("Global", second_x + 10, button_y + 8, 11, TEXT_MUTED)
    ui.text_font("v", second_x + 80, button_y + 8, 11, TEXT_SUBTLE)

    ui.text_centered("/", panel.main_x + panel.main_width - 32, panel.y + 23, 16, TEXT_DIM)
    hitbox(state, panel.x, panel.y, panel.width, HEADER_HEIGHT, "drag_panel", {
        x = panel.x,
        y = panel.y,
        base_x = panel.base_x,
        base_y = panel.base_y
    }, state.interactive, true)
end

local function shortest_column(heights)
    local target = 1
    for index = 2, #heights do
        if heights[index] < heights[target] then
            target = index
        end
    end
    return target
end

local function column_layout(panel, category)
    local content_width = panel.main_width - CONTENT_PADDING * 2
    local columns = content_width >= 480 and 2 or 1
    local gap = columns == 1 and COLUMN_GAP_COMPACT or COLUMN_GAP
    local column_width = (content_width - gap * (columns - 1)) / columns
    local lists = {}
    local heights = {}
    for index = 1, columns do
        lists[index] = {}
        heights[index] = 0
    end

    for _, module in ipairs(category.modules or {}) do
        local column = shortest_column(heights)
        table.insert(lists[column], module)
        heights[column] = heights[column] + (module.row_height or MODULE_ROW_HEIGHT)
    end
    return lists, heights, column_width, gap
end

local function content_height(panel, category)
    local _, heights = column_layout(panel, category)
    local maximum = 0
    for _, height in ipairs(heights) do
        maximum = math.max(maximum, height)
    end
    return CONTENT_TOP_GAP + GROUP_HEADER_HEIGHT + GROUP_PADDING * 2 + maximum + BOTTOM_PADDING
end

local function render_toggle(x, y, width, height, enabled_progress, hover_progress)
    local fill = ui.mix(TOGGLE_OFF, ACCENT, enabled_progress)
    if hover_progress > 0 then
        fill = ui.mix(fill, CONTROL_HOVER, hover_progress * (1 - enabled_progress) * 0.45)
    end
    ui.rounded_rect(x, y, width, height, height * 0.5, fill)
    local padding = math.max(2, height * 0.125)
    local radius = math.max(1, (height - padding * 2) * 0.5)
    local knob_x = lerp(x + padding + radius, x + width - padding - radius, enabled_progress)
    ui.circle(knob_x, y + height * 0.5, radius + hover_progress * 0.5, TEXT)
end

local function render_module(state, module, x, y, width, active_view, viewport_y, viewport_height)
    local is_hovered = state.interactive and active_view and hovered(state, x, y, width, MODULE_ROW_HEIGHT)
    local hover = animate_exp(module_hover[module.index] or 0, is_hovered and 1 or 0, 18, state.frame_seconds)
    module_hover[module.index] = hover
    local enabled = module.enabled_progress or 0
    local expanded = module.expand_progress or 0
    local row_height = module.row_height or MODULE_ROW_HEIGHT

    if hover > 0 then
        ui.rounded_rect(x, y + 2, width, MODULE_ROW_HEIGHT - 4, 6, ui.opacity(HOVER, hover))
    end
    if enabled > 0 then
        ui.rect(x + 2, y + 10, 2, MODULE_ROW_HEIGHT - 20, ui.opacity(ACCENT, enabled))
    end

    local toggle_width = 32
    local toggle_height = 16
    local arrow_area = module.expandable and 18 or 0
    local arrow_x = x + width - arrow_area - 8
    local toggle_x = module.expandable and arrow_x - toggle_width - 12 or x + width - toggle_width - 8
    local text_x = x + 12
    local text_width = math.max(20, toggle_x - text_x - 12)
    local name_color = ui.mix(TEXT_MUTED, TEXT, math.max(enabled, hover * 0.65))
    ui.text_font(ui.trim_text("text", module.name or "", 12, text_width),
        text_x, y + 9, 12, name_color)
    ui.text_font(ui.trim_text("text", module.description or "", 10, text_width),
        text_x, y + 29, 10, ui.mix(TEXT_DIM, TEXT_SUBTLE, hover * 0.45))
    render_toggle(toggle_x, y + 17, toggle_width, toggle_height, enabled, hover)

    if module.expandable then
        local center_x = arrow_x + arrow_area * 0.5
        local center_y = y + MODULE_ROW_HEIGHT * 0.5
        ui.rotate(lerp(0, 90, expanded), center_x, center_y, function()
            ui.text_centered(">", center_x, center_y - 7, 14,
                ui.mix(TEXT_SUBTLE, ACCENT, math.max(expanded, hover * 0.35)))
        end)
    end

    ui.custom("module_bounds", module.index, x, y, width, row_height, active_view)
    local hitbox_y = math.max(y, viewport_y)
    local hitbox_bottom = math.min(y + MODULE_ROW_HEIGHT, viewport_y + viewport_height)
    if hitbox_bottom > hitbox_y then
        hitbox(state, x, hitbox_y, width, hitbox_bottom - hitbox_y,
            "toggle_module", module.index, state.interactive and active_view)
    end

    local expanded_height = row_height - MODULE_ROW_HEIGHT
    if expanded_height <= 0.5 then
        return
    end

    ui.rect(x + 12, y + MODULE_ROW_HEIGHT, width - 24, 1, ui.opacity(DIVIDER, expanded))
    ui.clip(x, y + MODULE_ROW_HEIGHT, width, expanded_height, function()
        local value_x = x + 12
        local value_y = y + MODULE_ROW_HEIGHT + EXPANDED_TOP_PADDING
        local value_width = width - 24
        local remaining = expanded_height - EXPANDED_TOP_PADDING - EXPANDED_BOTTOM_PADDING
        for _, value in ipairs(module.values or {}) do
            if remaining <= 0 then
                break
            end
            local visible_height = math.min(value.height or 0, remaining)
            ui.custom("value", module.index, value.index, value_x, value_y, value_width,
                visible_height, active_view and state.interactive)
            value_y = value_y + (value.height or 0)
            remaining = remaining - (value.height or 0)
        end
    end)
end

local function render_category_content(state, panel, category, offset, active_view)
    local lists, heights, column_width, gap = column_layout(panel, category)
    local viewport_x = panel.main_x
    local viewport_y = panel.y + HEADER_HEIGHT
    local viewport_width = panel.main_width
    local viewport_height = panel.height - HEADER_HEIGHT
    local total_height = content_height(panel, category)
    local scroll = scroll_area(state, "clickgui:" .. category.id, viewport_x, viewport_y,
        viewport_width, viewport_height, total_height, 34)
    local top = viewport_y + CONTENT_TOP_GAP - scroll + offset
    local content_x = panel.main_x + CONTENT_PADDING

    for column = 1, #lists do
        local modules = lists[column]
        if #modules > 0 then
            local x = content_x + (column - 1) * (column_width + gap)
            local title = #lists == 1 and category.label or (column == 1 and "Right" or "Left")
            ui.text_font(title, x, top, 9, TEXT_DIM)
            local card_y = top + GROUP_HEADER_HEIGHT
            local card_height = GROUP_PADDING * 2 + heights[column]
            ui.rounded_rect(x, card_y, column_width, card_height, 8, CARD_BACKGROUND)
            ui.outline(x, card_y, column_width, card_height, 8, 1, BORDER_SOFT)

            local row_y = card_y + GROUP_PADDING
            for index, module in ipairs(modules) do
                render_module(state, module, x + GROUP_PADDING, row_y,
                    column_width - GROUP_PADDING * 2, active_view, viewport_y, viewport_height)
                row_y = row_y + (module.row_height or MODULE_ROW_HEIGHT)
                if index < #modules then
                    ui.rect(x + 12, row_y, column_width - 24, 1, DIVIDER)
                end
            end
        end
    end
end

local function render_content(state, panel)
    local viewport_height = panel.height - HEADER_HEIGHT
    ui.clip(panel.main_x, panel.y + HEADER_HEIGHT, panel.main_width, viewport_height, function()
        local selected = category_by_id(state, state.selected_category)
        if not selected then
            ui.text_font("No modules", panel.main_x + CONTENT_PADDING,
                panel.y + HEADER_HEIGHT + CONTENT_TOP_GAP, 12, TEXT_DIM)
            return
        end

        local previous = category_by_id(state, state.previous_category or "")
        if previous then
            local progress = state.category_progress or 1
            local direction = state.category_direction or 1
            render_category_content(state, panel, previous, -direction * viewport_height * progress, false)
            render_category_content(state, panel, selected,
                direction * viewport_height * (1 - progress), true)
        else
            render_category_content(state, panel, selected, 0, true)
        end
    end)
end

local function render_panel(state, panel)
    ui.shadow(panel.x, panel.y, panel.width, panel.height, 12, 0, 18, 30, 0xA0000000)
    ui.rounded_rect(panel.x, panel.y, panel.width, panel.height, 12, PANEL_BACKGROUND)
    ui.outline(panel.x, panel.y, panel.width, panel.height, 12, 1, BORDER)
    ui.clip(panel.x, panel.y, panel.width, panel.height, function()
        render_sidebar(state, panel)
        render_header(state, panel)
        render_content(state, panel)
    end)
end

function screen.render(state)
    local visibility = state.screen_visibility or 1
    ui.rect(0, 0, state.width, state.height, ui.opacity(SCREEN_DIM, visibility))
    local panel = panel_bounds(state)
    ui.scale(state.screen_scale or 1, state.screen_scale or 1,
        panel.x + panel.width * 0.5, panel.y + panel.height * 0.5, function()
            render_panel(state, panel)
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
