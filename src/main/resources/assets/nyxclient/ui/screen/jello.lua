local screen = {}

local WHITE = 0xFFF9F9FA
local HEADER = 0xFFEDEDEF
local BORDER = 0xFFD8D9DD
local TEXT = 0xFF26282E
local TEXT_MUTED = 0xFF777B84
local TEXT_SUBTLE = 0xFFA4A7AE
local ACTIVE = 0xFF0FAFE9

local MUSIC_BLACK = 0xF3090A0D
local MUSIC_DARK = 0xF51A1C21
local MUSIC_TEXT = 0xFFF4F5F7
local MUSIC_MUTED = 0xFFA8ACB5
local MUSIC_DIM = 0xFF737985
local MUSIC_ACCENT = 0xFFFF6680

local MODULE_HEADER = 30
local MODULE_ROW = 15
local MUSIC_PLAYER_MIN = 46
local volume_popup = 0

local function clamp(value, minimum, maximum)
    return math.max(minimum, math.min(maximum, value))
end

local function ease_out_cubic(value)
    local inverse = 1 - clamp(value, 0, 1)
    return 1 - inverse * inverse * inverse
end

local function ease_out_back(value)
    local c1 = 1.70158
    local c3 = c1 + 1
    local shifted = clamp(value, 0, 1) - 1
    return 1 + c3 * shifted * shifted * shifted + c1 * shifted * shifted
end

local function animate_exp(current, target, speed, frame_seconds)
    return target + (current - target) * math.exp(-speed * (frame_seconds or 1 / 60))
end

local function border(x, y, width, height, color)
    ui.rect(x, y, width, 1, color)
    ui.rect(x, y + height - 1, width, 1, color)
    ui.rect(x, y, 1, height, color)
    ui.rect(x + width - 1, y, 1, height, color)
end

local function card_motion(state, index)
    local age = math.max(0, (state.open_age or 0) - (index - 1) * 0.020)
    local progress = ease_out_back(clamp(age / 0.18, 0, 1))
    return clamp(age / 0.11, 0, 1), 0.78 + 0.22 * progress
end

local function render_background(state)
    -- The original Jello screen uses one continuous, strongly softened world image.
    ui.panel(0, 0, state.width, state.height, 0, 24, 0x36FFFFFF, 0x42070A10, 0, 0)
end

local function module_grid(state)
    local half = state.width * 0.5
    local width = clamp(state.width * 0.1035, 86, 112)
    local height = clamp(state.height * 0.297, 142, 194)
    local gap_x = clamp(state.width * 0.0095, 8, 12)
    local gap_y = clamp(state.height * 0.0195, 9, 14)
    local grid_width = width * 3 + gap_x * 2
    local grid_height = height * 2 + gap_y
    local left = (half - grid_width) * 0.5
    local top = (state.height - grid_height) * 0.5
    return left, top, width, height, gap_x, gap_y
end

local function render_module_card(state, category, index, x, y, width, height)
    local alpha, scale = card_motion(state, index)
    if alpha <= 0.001 then
        return
    end
    local content_y = y + MODULE_HEADER
    local content_h = math.max(1, height - MODULE_HEADER)
    local content_height = #(category.modules or {}) * MODULE_ROW
    local interactive = state.interactive and not state.modal_blocking

    ui.scale(scale, scale, x + width * 0.5, y + height * 0.5, function()
        ui.rect(x, y, width, height, ui.opacity(WHITE, alpha))
        ui.rect(x, y, width, MODULE_HEADER, ui.opacity(HEADER, alpha))
        border(x, y, width, height, ui.opacity(BORDER, alpha * 0.55))
        ui.text_font(ui.trim_text("text", category.label or "", 12, width - 20),
            x + 10, y + 8, 12, ui.opacity(TEXT_MUTED, alpha))

        local scroll = ui.scroll("jello:category:" .. (category.id or tostring(index)),
            x, content_y, width, content_h, content_height, MODULE_ROW * 1.5)
        ui.clip(x, content_y, width, content_h, function()
            for row_index, module in ipairs(category.modules or {}) do
                local row_y = content_y + (row_index - 1) * MODULE_ROW - scroll
                if row_y + MODULE_ROW > content_y and row_y < content_y + content_h then
                    local enabled = clamp(module.enabled_progress or 0, 0, 1)
                    local hovered = interactive and ui.hovered(x, row_y, width, MODULE_ROW)
                    if enabled > 0.001 then
                        ui.rect(x, row_y, width, MODULE_ROW, ui.opacity(ACTIVE, alpha * enabled))
                    elseif hovered then
                        ui.rect(x, row_y, width, MODULE_ROW, ui.opacity(0x10000000, alpha))
                    end
                    ui.text_font(ui.trim_text("text", module.name or "", 9, width - 18),
                        x + 10 + enabled * 4, row_y + 3, 9,
                        ui.opacity(ui.mix(TEXT, 0xFFFFFFFF, enabled), alpha))
                    ui.custom("module_bounds", module.index, x, row_y, width, MODULE_ROW,
                        content_y, interactive, content_h)
                end
            end
        end)

        local maximum = math.max(0, content_height - content_h)
        if maximum > 0 then
            local bar_h = clamp(content_h * content_h / content_height, 12, content_h)
            local bar_y = content_y + (content_h - bar_h) * (scroll / maximum)
            ui.rect(x + width - 1, bar_y, 1, bar_h, ui.opacity(0x60000000, alpha))
        end
    end)
end

local function music_bounds(state)
    local half = state.width * 0.5
    local region_x = half
    local region_w = state.width - half
    local width = math.min(math.max(180, region_w - 20), region_w * 0.84, state.height * 0.74)
    local height = width * 3 / 4
    return {
        x = region_x + (region_w - width) * 0.25,
        y = (state.height - height) * 0.5 - clamp(height * 0.018, 3, 7),
        width = width,
        height = height
    }
end

local function sidebar_item(state, x, y, width, height, label, action, payload, selected, alpha)
    local interactive = state.interactive and not state.modal_blocking
    local hovered = interactive and ui.hovered(x, y, width, height)
    local color = selected and MUSIC_TEXT or (hovered and MUSIC_MUTED or MUSIC_DIM)
    ui.text_centered(ui.trim_text("text", label, 7.5, width - 18),
        x + width * 0.5, y + 5, 7.5, ui.opacity(color, alpha))
    ui.hitbox(x, y, width, height, action, payload, interactive)
end

local function render_music_sidebar(state, bounds, player_height, alpha)
    local music = state.music or {}
    local width = bounds.width / 3
    local height = bounds.height - player_height
    ui.rect(bounds.x, bounds.y, width, height, ui.opacity(MUSIC_BLACK, alpha))
    ui.rect(bounds.x + width - 1, bounds.y, 1, height, ui.opacity(0x18FFFFFF, alpha))

    local title_x = bounds.x + 24
    local title_size = 21
    ui.display_text("Nyx", title_x, bounds.y + 13, title_size, ui.opacity(MUSIC_TEXT, alpha))
    ui.text_font("music", title_x + ui.font_width("display", "Nyx", title_size) + 3,
        bounds.y + 27, 7, ui.opacity(MUSIC_MUTED, alpha))

    local y = bounds.y + 51
    sidebar_item(state, bounds.x, y, width, 19, "推荐音乐",
        "music_page", "recommended", music.page == "recommended", alpha)
    y = y + 20
    sidebar_item(state, bounds.x, y, width, 19, "喜欢的音乐",
        "music_page", "favorites", music.page == "favorites", alpha)
    y = y + 25

    if music.logged_in then
        ui.rect(bounds.x + 18, y, width - 36, 1, ui.opacity(0x24FFFFFF, alpha))
        y = y + 7
        local list_h = math.max(1, bounds.y + height - y - 19)
        local item_h = 18
        local content_h = #(music.playlists or {}) * item_h
        local scroll = ui.scroll("jello:music:playlists", bounds.x, y, width, list_h, content_h, 25)
        ui.clip(bounds.x, y, width, list_h, function()
            for _, playlist in ipairs(music.playlists or {}) do
                local item_y = y + (playlist.index - 1) * item_h - scroll
                if item_y + item_h > y and item_y < y + list_h then
                    sidebar_item(state, bounds.x, item_y, width, item_h,
                        playlist.name or "", "music_playlist", playlist.index,
                        playlist.selected == true, alpha)
                end
            end
        end)
    else
        ui.rect(bounds.x + 18, y, width - 36, 1, ui.opacity(0x24FFFFFF, alpha))
        ui.text_centered("登录网易云后显示歌单", bounds.x + width * 0.5, y + 10, 6.5,
            ui.opacity(MUSIC_DIM, alpha))
    end

    local equalizer_y = bounds.y + height - 10
    ui.rect(bounds.x + 12, equalizer_y - 4, 2, 4, ui.opacity(0x28FFFFFF, alpha))
    ui.rect(bounds.x + 16, equalizer_y - 8, 2, 8, ui.opacity(0x28FFFFFF, alpha))
    ui.rect(bounds.x + 20, equalizer_y - 6, 2, 6, ui.opacity(0x28FFFFFF, alpha))
end

local function render_music_content(state, bounds, player_height, alpha)
    local music = state.music or {}
    local sidebar = bounds.width / 3
    local x = bounds.x + sidebar
    local y = bounds.y
    local width = bounds.width - sidebar
    local height = bounds.height - player_height
    ui.rect(x, y, width, height, ui.opacity(MUSIC_DARK, alpha))

    ui.text_centered(ui.trim_text("text", music.page_title or "音乐", 12, width - 34),
        x + width * 0.5, y + 14, 12, ui.opacity(MUSIC_TEXT, alpha))
    if music.loading then
        ui.text_centered(ui.trim_text("text", music.status or "正在加载...", 6.5, width - 28),
            x + width * 0.5, y + 31, 6.5, ui.opacity(MUSIC_DIM, alpha))
    end

    local list_x = x
    local list_y = y + 44
    local list_w = width
    local list_h = math.max(1, height - 47)
    local gap_x = 10
    local gap_y = 7
    local side_padding = 18
    local caption_h = 20
    local cell_w = (list_w - side_padding * 2 - gap_x * 2) / 3
    local cover_by_height = math.max(30, (list_h - gap_y) * 0.5 - caption_h)
    local cover_size = math.min(cell_w, cover_by_height)
    local grid_w = cover_size * 3 + gap_x * 2
    local grid_x = list_x + (list_w - grid_w) * 0.5
    local row_h = cover_size + caption_h + gap_y
    local rows = math.ceil(#(music.songs or {}) / 3)
    local content_h = math.max(0, rows * row_h - gap_y)
    local scroll = ui.scroll("jello:music:songs", list_x, list_y, list_w, list_h, content_h, row_h)

    ui.clip(list_x, list_y, list_w, list_h, function()
        if #(music.songs or {}) == 0 then
            ui.text_centered(ui.trim_text("text", music.status or "暂无音乐", 8, list_w - 20),
                list_x + list_w * 0.5, list_y + list_h * 0.5 - 5, 8,
                ui.opacity(MUSIC_DIM, alpha))
            return
        end
        for order, song in ipairs(music.songs or {}) do
            local column = (order - 1) % 3
            local row = math.floor((order - 1) / 3)
            local item_x = grid_x + column * (cover_size + gap_x)
            local item_y = list_y + row * row_h - scroll
            if item_y + cover_size + caption_h > list_y and item_y < list_y + list_h then
                local active = song.current == true
                local hovered = state.interactive and not state.modal_blocking
                    and ui.hovered(item_x, item_y, cover_size, cover_size + caption_h)
                ui.custom("cover", song.cover or "", item_x, item_y, cover_size, cover_size,
                    alpha * (hovered and 1 or 0.94))
                if active then
                    border(item_x, item_y, cover_size, cover_size, ui.opacity(MUSIC_ACCENT, alpha))
                elseif hovered then
                    border(item_x, item_y, cover_size, cover_size, ui.opacity(0x80FFFFFF, alpha))
                end
                ui.text_centered(ui.trim_text("text", song.name or "", 6.5, cover_size + 8),
                    item_x + cover_size * 0.5, item_y + cover_size + 5, 6.5,
                    ui.opacity(active and MUSIC_TEXT or MUSIC_MUTED, alpha))
                ui.text_centered(ui.trim_text("text", song.artist or "", 5.5, cover_size + 8),
                    item_x + cover_size * 0.5, item_y + cover_size + 14, 5.5,
                    ui.opacity(MUSIC_DIM, alpha))
                ui.hitbox(item_x, item_y, cover_size, cover_size + caption_h, "music_song", song.index,
                    state.interactive and not state.modal_blocking)
            end
        end
    end)
end

local function music_control(state, icon, x, y, size, action, scale, emphasized, alpha, enabled)
    local available = enabled ~= false
    local interactive = state.interactive and not state.modal_blocking and available
    local hovered = interactive and ui.hovered(x, y, size, size)
    ui.scale(scale or 1, scale or 1, x + size * 0.5, y + size * 0.5, function()
        local icon_size = emphasized and size * 0.58 or size * 0.46
        local icon_color = emphasized and 0xFFFFFFFF or MUSIC_MUTED
        ui.custom("icon", icon, x + (size - icon_size) * 0.5, y + (size - icon_size) * 0.5,
            icon_size, icon_size, ui.opacity(icon_color,
                alpha * (available and (hovered and 1 or 0.86) or 0.30)))
    end)
    ui.hitbox(x, y, size, size, action, nil, interactive)
end

local function render_music_player(state, bounds, player_height, alpha)
    local music = state.music or {}
    local x = bounds.x
    local y = bounds.y + bounds.height - player_height
    local width = bounds.width
    local left_width = width / 3
    ui.custom("blurred_cover", music.cover or "", x, y, width, player_height, alpha)
    ui.rect(x, y, width, player_height, ui.opacity(0x9E23131F, alpha))
    ui.rect(x, y, width, 1, ui.opacity(0x22FFFFFF, alpha))

    local cover_size = math.min(left_width * 0.46, player_height * 1.18)
    local cover_x = x + (left_width - cover_size) * 0.5
    local cover_target_y = y + player_height * 0.52 - cover_size
    local cover_y = cover_target_y + (1 - (music.cover_progress or 0)) * (cover_size + 12)
    local cover_alpha = alpha * clamp((music.cover_progress or 0) * 1.4, 0, 1)
    if music.has_song then
        ui.custom("cover", music.cover or "", cover_x, cover_y, cover_size, cover_size, cover_alpha)
        border(cover_x, cover_y, cover_size, cover_size, ui.opacity(0x38FFFFFF, cover_alpha))
        ui.hitbox(cover_x, cover_y, cover_size, cover_size,
            "music_detail", nil, state.interactive and not state.modal_blocking
                and (music.cover_progress or 0) > 0.88)
    else
        ui.custom("cover", "", cover_x, cover_target_y, cover_size, cover_size, alpha)
    end
    ui.custom("song_title", music.song_name or "暂无音乐", x + 7,
        y + player_height * 0.57, left_width - 14, 7, ui.opacity(MUSIC_TEXT, alpha))

    local controls_x = x + left_width
    local controls_w = width - left_width
    local button = clamp(player_height * 0.43, 19, 23)
    local gap = clamp(controls_w * 0.11, 25, 34)
    local group_w = button * 3 + gap * 2
    local group_x = controls_x + (controls_w - group_w) * 0.5
    local group_y = y + 5
    music_control(state, "previous", group_x, group_y, button, "music_previous",
        music.previous_scale or 1, false, alpha, music.has_song)
    music_control(state, music.playing and "pause" or "play", group_x + button + gap, group_y,
        button, "music_toggle", music.toggle_scale or 1, true, alpha, music.has_song)
    music_control(state, "next", group_x + button * 2 + gap * 2, group_y, button,
        "music_next", music.next_scale or 1, false, alpha, music.has_song)

    local volume_button_x = x + width - 25
    local volume_button_y = y + 5
    local slider_x = volume_button_x + 9
    local slider_y = y - 53
    local slider_h = 47
    local volume_hovered = ui.hovered(volume_button_x, volume_button_y, 20, 20)
        or ui.hovered(slider_x - 6, slider_y - 4, 16,
            volume_button_y + 20 - (slider_y - 4))
    volume_popup = animate_exp(volume_popup, volume_hovered and 1 or 0, 18, state.frame_seconds)
    if volume_popup > 0.01 then
        local rise = (1 - ease_out_cubic(volume_popup)) * 10
        ui.rect(slider_x - 5, slider_y - 4 + rise, 14, slider_h + 8,
            ui.opacity(0xB20A0B0F, alpha * volume_popup))
        ui.rect(slider_x, slider_y + rise, 3, slider_h,
            ui.opacity(0xFF3A3D46, alpha * volume_popup))
        local fill_h = slider_h * clamp(music.volume or 0, 0, 1)
        ui.rect(slider_x, slider_y + slider_h - fill_h + rise, 3, fill_h,
            ui.opacity(MUSIC_ACCENT, alpha * volume_popup))
        ui.rect(slider_x - 2, slider_y + slider_h - fill_h - 1 + rise, 7, 3,
            ui.opacity(0xFFFFFFFF, alpha * volume_popup))
        ui.hitbox(slider_x - 6, slider_y - 4 + rise, 16, slider_h + 8,
            "music_volume", {y = slider_y + rise, height = slider_h},
            state.interactive and not state.modal_blocking, true)
    end
    ui.custom("icon", "volume", volume_button_x + 5, volume_button_y + 5, 10, 10,
        ui.opacity(MUSIC_MUTED, alpha))

    local bar_x = controls_x + 51
    local bar_w = math.max(30, controls_w - 102)
    local bar_y = y + player_height - 3
    ui.rect(bar_x, bar_y, bar_w, 2, ui.opacity(0xFF423E47, alpha))
    ui.rect(bar_x, bar_y, bar_w * clamp(music.progress or 0, 0, 1), 2,
        ui.opacity(0xFFFFFFFF, alpha))
    ui.text_font(music.position_label or "00:00", controls_x + 8, bar_y - 10, 6,
        ui.opacity(MUSIC_MUTED, alpha))
    ui.text_font(music.duration_label or "00:00", x + width - 35, bar_y - 10, 6,
        ui.opacity(MUSIC_MUTED, alpha))
    ui.hitbox(bar_x, bar_y - 5, bar_w, 9, "music_progress",
        {x = bar_x, width = bar_w}, state.interactive and not state.modal_blocking
            and music.has_song, true)
end

local function render_music_detail(state, bounds, player_height, alpha)
    local progress = clamp(state.detail_progress or 0, 0, 1)
    if progress <= 0.005 then
        return
    end
    local music = state.music or {}
    local upper_h = bounds.height - player_height
    local cover_source_x = bounds.x + bounds.width / 6
    local cover_source_y = bounds.y + bounds.height - player_height * 0.5
    local scale = 0.24 + 0.76 * ease_out_cubic(progress)
    local detail_alpha = alpha * clamp(progress * 1.5, 0, 1)

    ui.hitbox(bounds.x, bounds.y, bounds.width, upper_h, "block", nil, true)
    ui.scale(scale, scale, cover_source_x, cover_source_y, function()
        ui.rect(bounds.x, bounds.y, bounds.width, upper_h, ui.opacity(0xFF121419, detail_alpha))
        local padding = 24
        local cover_size = math.min(upper_h - padding * 2, bounds.width * 0.28)
        local cover_x = bounds.x + padding
        local cover_y = bounds.y + (upper_h - cover_size) * 0.5
        ui.custom("cover", music.cover or "", cover_x, cover_y, cover_size, cover_size, detail_alpha)
        border(cover_x, cover_y, cover_size, cover_size, ui.opacity(0x35FFFFFF, detail_alpha))
        ui.text_font(ui.trim_text("text", music.song_name or "", 12, cover_size),
            cover_x, cover_y + cover_size + 8, 12, ui.opacity(MUSIC_TEXT, detail_alpha))
        ui.text_font(ui.trim_text("text", music.artist or "", 8, cover_size),
            cover_x, cover_y + cover_size + 25, 8, ui.opacity(MUSIC_DIM, detail_alpha))

        local lyric_x = cover_x + cover_size + 30
        local lyric_w = bounds.x + bounds.width - padding - lyric_x
        local line_h = 19
        local lyrics = music.lyrics or {}
        local total_h = #lyrics * line_h
        local lyric_y = bounds.y + (upper_h - total_h) * 0.5
        if #lyrics == 0 then
            ui.text_centered("暂无歌词", lyric_x + lyric_w * 0.5,
                bounds.y + upper_h * 0.5 - 5, 10, ui.opacity(MUSIC_DIM, detail_alpha))
        else
            for index, lyric in ipairs(lyrics) do
                local current = lyric.current == true
                local size = current and 11 or 9
                ui.text_centered(ui.trim_text("text", lyric.text or "", size, lyric_w),
                    lyric_x + lyric_w * 0.5, lyric_y + (index - 1) * line_h,
                    size, ui.opacity(current and 0xFFFFFFFF or MUSIC_DIM,
                        detail_alpha * (current and 1 or 0.72)))
            end
        end
        ui.text_font("X", bounds.x + bounds.width - 22, bounds.y + 10, 10,
            ui.opacity(MUSIC_MUTED, detail_alpha))
    end)
    ui.hitbox(bounds.x + bounds.width - 32, bounds.y, 32, 32,
        "music_detail_close", nil, state.interactive and progress > 0.88 and not state.modal_blocking)
end

local function render_music_card(state)
    local bounds = music_bounds(state)
    local alpha, scale = card_motion(state, 7)
    if alpha <= 0.001 then
        return
    end
    local player_height = clamp(bounds.height * 0.16, MUSIC_PLAYER_MIN, 60)
    ui.scale(scale, scale, bounds.x + bounds.width * 0.5, bounds.y + bounds.height * 0.5, function()
        ui.rect(bounds.x, bounds.y, bounds.width, bounds.height, ui.opacity(MUSIC_DARK, alpha))
        render_music_sidebar(state, bounds, player_height, alpha)
        render_music_content(state, bounds, player_height, alpha)
        render_music_player(state, bounds, player_height, alpha)
        render_music_detail(state, bounds, player_height, alpha)
        border(bounds.x, bounds.y, bounds.width, bounds.height, ui.opacity(0x2DFFFFFF, alpha))
    end)
end

local function render_modal(state)
    local progress = clamp(state.modal_progress or 0, 0, 1)
    local modal = state.modal or {}
    if progress <= 0.004 or not modal.module_index then
        return
    end
    local alpha = (state.screen_visibility or 1) * clamp(progress * 1.5, 0, 1)
    local width = clamp(state.width * 0.48, 310, 520)
    local height = clamp(state.height * 0.70, 230, 430)
    width = math.min(width, state.width - 34)
    height = math.min(height, state.height - 34)
    local x = (state.width - width) * 0.5
    local y = (state.height - height) * 0.5
    local header_h = 61

    ui.rect(0, 0, state.width, state.height, ui.opacity(0x99000000, alpha))
    ui.hitbox(0, 0, state.width, state.height, "block", nil, true)
    ui.scale(state.modal_scale or 1, state.modal_scale or 1,
        x + width * 0.5, y + height * 0.5, function()
            ui.rect(x, y, width, height, ui.opacity(WHITE, alpha))
            ui.rect(x, y, width, header_h, ui.opacity(HEADER, alpha))
            border(x, y, width, height, ui.opacity(BORDER, alpha))
            ui.text_centered(ui.trim_text("display", modal.name or "Module", 16, width - 70),
                x + width * 0.5, y + 12, 16, ui.opacity(TEXT, alpha))
            ui.text_centered(ui.trim_text("text", modal.description or "", 8, width - 60),
                x + width * 0.5, y + 35, 8, ui.opacity(TEXT_MUTED, alpha))
            ui.text_font("X", x + width - 25, y + 18, 10, ui.opacity(TEXT_MUTED, alpha))

            local content_y = y + header_h
            local content_h = height - header_h
            local content_w = width
            local scroll = ui.scroll("jello:modal", x, content_y, content_w, content_h,
                modal.content_height or 0, 34)
            ui.clip(x, content_y, content_w, content_h, function()
                if #(modal.values or {}) == 0 then
                    ui.text_centered("该模块没有配置项", x + width * 0.5,
                        content_y + content_h * 0.5 - 5, 10, ui.opacity(TEXT_SUBTLE, alpha))
                    return
                end
                local value_y = content_y - scroll
                for _, value in ipairs(modal.values or {}) do
                    if value_y + value.height > content_y and value_y < content_y + content_h then
                        ui.custom("setting", modal.module_index, value.index, x, value_y,
                            content_w, value.height, content_y, content_h,
                            state.modal_target_open and progress > 0.88)
                        ui.rect(x + 14, value_y + value.height - 1, content_w - 28, 1,
                            ui.opacity(0xFFE4E5E8, alpha))
                    end
                    value_y = value_y + value.height + 1
                end
            end)
        end)
    ui.hitbox(x + width - 38, y, 38, header_h,
        "modal_close", nil, state.modal_target_open and progress > 0.88)
end

function screen.init(state)
    volume_popup = 0
end

function screen.render(state)
    local visibility = state.screen_visibility or 1
    if visibility <= 0.001 then
        return
    end
    render_background(state)
    ui.scale(state.screen_scale or 1, state.screen_scale or 1,
        state.width * 0.5, state.height * 0.5, function()
            local left, top, card_w, card_h, gap_x, gap_y = module_grid(state)
            for index, category in ipairs(state.categories or {}) do
                local column = (index - 1) % 3
                local row = math.floor((index - 1) / 3)
                render_module_card(state, category, index,
                    left + column * (card_w + gap_x),
                    top + row * (card_h + gap_y),
                    card_w, card_h)
            end
            render_music_card(state)
            render_modal(state)
        end)
end

function screen.key_pressed(state, key)
    return false
end

return screen
