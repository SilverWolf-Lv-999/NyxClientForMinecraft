local screen = {}

local WHITE = 0xFFF9F9FA
local HEADER = 0xFFEDEDEF
local BORDER = 0xFFD8D9DD
local TEXT = 0xFF26282E
local TEXT_MUTED = 0xFF777B84
local TEXT_SUBTLE = 0xFFA4A7AE
local ACTIVE = 0xFF18A9F2
local ACTIVE_SOFT = 0x2818A9F2

local MUSIC_BLACK = 0xF3090A0D
local MUSIC_DARK = 0xF51A1C21
local MUSIC_CARD = 0xFF24272E
local MUSIC_HOVER = 0xFF30343D
local MUSIC_TEXT = 0xFFF4F5F7
local MUSIC_MUTED = 0xFFA8ACB5
local MUSIC_DIM = 0xFF737985
local MUSIC_ACCENT = 0xFFFF6680

local MODULE_HEADER = 30
local MODULE_ROW = 24
local MUSIC_PLAYER_MIN = 82
local volume_popup = 0

local function clamp(value, minimum, maximum)
    return math.max(minimum, math.min(maximum, value))
end

local function lerp(from, to, progress)
    return from + (to - from) * progress
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
    -- Radius zero gives a square full-screen Gaussian softening layer over the game.
    ui.panel(0, 0, state.width, state.height, 0, 14, 0x72FFFFFF, 0x36080A0E, 0, 0)
    ui.rect(0, 0, state.width * 0.5, state.height, 0x16FFFFFF)
    ui.rect(state.width * 0.5, 0, state.width * 0.5, state.height, 0x22000000)

end

local function module_grid(state)
    local half = state.width * 0.5
    local left = 18
    local right = half - 18
    local top = math.max(62, state.height * 0.115)
    local bottom = state.height - 18
    local gap_x = clamp(state.width * 0.010, 8, 14)
    local gap_y = clamp(state.height * 0.026, 10, 18)
    local width = math.max(30, (right - left - gap_x * 2) / 3)
    local height = math.max(64, (bottom - top - gap_y) / 2)
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
        border(x, y, width, height, ui.opacity(BORDER, alpha))
        ui.text_font(ui.trim_text("text", category.label or "", 13, width - 42),
            x + 11, y + 8, 13, ui.opacity(TEXT_MUTED, alpha))
        ui.text_font(tostring(category.count or 0), x + width - 23, y + 10, 9,
            ui.opacity(TEXT_SUBTLE, alpha))

        local scroll = ui.scroll("jello:category:" .. (category.id or tostring(index)),
            x, content_y, width, content_h, content_height, MODULE_ROW * 1.5)
        ui.clip(x, content_y, width, content_h, function()
            for row_index, module in ipairs(category.modules or {}) do
                local row_y = content_y + (row_index - 1) * MODULE_ROW - scroll
                if row_y + MODULE_ROW > content_y and row_y < content_y + content_h then
                    local enabled = clamp(module.enabled_progress or 0, 0, 1)
                    local hovered = interactive and ui.hovered(x, row_y, width, MODULE_ROW)
                    if hovered then
                        ui.rect(x, row_y, width, MODULE_ROW, ui.opacity(0x0D000000, alpha))
                    end
                    if enabled > 0.001 then
                        ui.rect(x, row_y, width, MODULE_ROW, ui.opacity(ACTIVE_SOFT, alpha * enabled))
                        ui.rect(x, row_y, 3, MODULE_ROW, ui.opacity(ACTIVE, alpha * enabled))
                    end
                    ui.text_font(ui.trim_text("text", module.name or "", 10, width - 20),
                        x + 10 + enabled * 6, row_y + 7, 10,
                        ui.opacity(ui.mix(TEXT, ACTIVE, enabled * 0.72), alpha))
                    ui.custom("module_bounds", module.index, x, row_y, width, MODULE_ROW,
                        content_y, interactive, content_h)
                end
            end
        end)

        local maximum = math.max(0, content_height - content_h)
        if maximum > 0 then
            local bar_h = clamp(content_h * content_h / content_height, 18, content_h)
            local bar_y = content_y + (content_h - bar_h) * (scroll / maximum)
            ui.rect(x + width - 2, bar_y, 2, bar_h, ui.opacity(0x55000000, alpha))
        end
    end)
end

local function music_bounds(state)
    local half = state.width * 0.5
    local region_x = half
    local region_w = state.width - half
    local max_w = math.max(180, region_w - 36)
    local max_h = math.max(150, state.height - 52)
    local width = math.min(max_w, max_h * 4 / 3)
    local height = math.min(max_h, width * 3 / 4)
    if height > max_h then
        height = max_h
        width = height * 4 / 3
    end
    return {
        x = region_x + (region_w - width) * 0.5,
        y = (state.height - height) * 0.5,
        width = width,
        height = height
    }
end

local function sidebar_item(state, music, x, y, width, label, icon, action, payload, selected, alpha)
    local interactive = state.interactive and not state.modal_blocking
    local hovered = interactive and ui.hovered(x, y, width, 25)
    if selected or hovered then
        ui.rect(x, y, width, 25, ui.opacity(selected and 0x263D81F7 or 0x10FFFFFF, alpha))
    end
    if selected then
        ui.rect(x, y, 2, 25, ui.opacity(0xFF57C7FF, alpha))
    end
    ui.custom("icon", icon, x + 10, y + 7, 10, 10,
        ui.opacity(selected and 0xFF57C7FF or MUSIC_DIM, alpha))
    ui.text_font(ui.trim_text("text", label, 9, width - 36), x + 28, y + 8, 9,
        ui.opacity(selected and MUSIC_TEXT or MUSIC_MUTED, alpha))
    ui.hitbox(x, y, width, 25, action, payload, interactive)
end

local function render_music_sidebar(state, bounds, player_height, alpha)
    local music = state.music or {}
    local width = bounds.width / 3
    local height = bounds.height - player_height
    ui.rect(bounds.x, bounds.y, width, height, ui.opacity(MUSIC_BLACK, alpha))
    ui.rect(bounds.x + width - 1, bounds.y, 1, height, ui.opacity(0x20FFFFFF, alpha))

    ui.text_font("music", bounds.x + 13, bounds.y + 18, 9, ui.opacity(MUSIC_DIM, alpha))
    ui.display_text("Nyx", bounds.x + 45, bounds.y + 11, 21, ui.opacity(MUSIC_TEXT, alpha))
    ui.rect(bounds.x + 12, bounds.y + 43, width - 24, 1, ui.opacity(0x18FFFFFF, alpha))

    local y = bounds.y + 51
    sidebar_item(state, music, bounds.x, y, width, "推荐音乐", "home",
        "music_page", "recommended", music.page == "recommended", alpha)
    y = y + 27
    sidebar_item(state, music, bounds.x, y, width, "喜欢的音乐", "music",
        "music_page", "favorites", music.page == "favorites", alpha)
    y = y + 32

    if music.logged_in then
        ui.rect(bounds.x + 12, y, width - 24, 1, ui.opacity(0x18FFFFFF, alpha))
        ui.text_font("我的歌单", bounds.x + 12, y + 8, 8, ui.opacity(MUSIC_DIM, alpha))
        y = y + 23
        local list_h = math.max(1, bounds.y + height - y - 7)
        local content_h = #(music.playlists or {}) * 23
        local scroll = ui.scroll("jello:music:playlists", bounds.x, y, width, list_h, content_h, 28)
        ui.clip(bounds.x, y, width, list_h, function()
            for _, playlist in ipairs(music.playlists or {}) do
                local item_y = y + (playlist.index - 1) * 23 - scroll
                if item_y + 23 > y and item_y < y + list_h then
                    sidebar_item(state, music, bounds.x, item_y, width,
                        playlist.name or "", "list", "music_playlist", playlist.index,
                        playlist.selected == true, alpha)
                end
            end
        end)
    else
        ui.text_font("登录网易云后显示歌单", bounds.x + 12, y + 6, 8,
            ui.opacity(MUSIC_DIM, alpha))
    end
end

local function render_recommended_playlists(state, music, x, y, width, alpha)
    if music.page ~= "recommended" or #(music.recommended_playlists or {}) == 0 then
        return 0
    end
    local shown = math.min(2, #(music.recommended_playlists or {}))
    local gap = 7
    local item_w = (width - gap * (shown - 1)) / shown
    for index = 1, shown do
        local playlist = music.recommended_playlists[index]
        local item_x = x + (index - 1) * (item_w + gap)
        local hovered = state.interactive and not state.modal_blocking and ui.hovered(item_x, y, item_w, 40)
        ui.rect(item_x, y, item_w, 40, ui.opacity(hovered and MUSIC_HOVER or MUSIC_CARD, alpha))
        ui.custom("cover", playlist.cover or "", item_x + 5, y + 5, 30, 30, alpha)
        ui.text_font(ui.trim_text("text", playlist.name or "", 8, item_w - 44),
            item_x + 40, y + 9, 8, ui.opacity(MUSIC_TEXT, alpha))
        ui.text_font("推荐歌单", item_x + 40, y + 24, 7, ui.opacity(MUSIC_DIM, alpha))
        ui.hitbox(item_x, y, item_w, 40, "music_recommended_playlist", playlist.index,
            state.interactive and not state.modal_blocking)
    end
    return 47
end

local function render_music_content(state, bounds, player_height, alpha)
    local music = state.music or {}
    local sidebar = bounds.width / 3
    local x = bounds.x + sidebar
    local y = bounds.y
    local width = bounds.width - sidebar
    local height = bounds.height - player_height
    ui.rect(x, y, width, height, ui.opacity(MUSIC_DARK, alpha))

    ui.text_font(ui.trim_text("text", music.page_title or "音乐", 13, width - 42),
        x + 13, y + 13, 13, ui.opacity(MUSIC_TEXT, alpha))
    ui.text_font(ui.trim_text("text", music.status or "", 8, width - 58),
        x + 13, y + 31, 8, ui.opacity(MUSIC_DIM, alpha))
    ui.custom("icon", "repeat", x + width - 25, y + 13, 11, 11,
        ui.opacity(MUSIC_DIM, alpha))
    ui.hitbox(x + width - 31, y + 7, 25, 25, "music_refresh", nil,
        state.interactive and not state.modal_blocking)

    local list_x = x + 12
    local list_y = y + 47
    local list_w = width - 24
    local list_h = math.max(1, height - 54)
    local playlist_h = render_recommended_playlists(state, music, list_x, list_y, list_w, alpha)
    list_y = list_y + playlist_h
    list_h = math.max(1, list_h - playlist_h)
    local row_h = 36
    local content_h = #(music.songs or {}) * row_h
    local scroll = ui.scroll("jello:music:songs", list_x, list_y, list_w, list_h, content_h, 44)

    ui.clip(list_x, list_y, list_w, list_h, function()
        if #(music.songs or {}) == 0 then
            ui.text_centered(ui.trim_text("text", music.status or "暂无音乐", 9, list_w - 20),
                list_x + list_w * 0.5, list_y + list_h * 0.5 - 5, 9,
                ui.opacity(MUSIC_DIM, alpha))
            return
        end
        for _, song in ipairs(music.songs or {}) do
            local row_y = list_y + (song.index - 1) * row_h - scroll
            if row_y + row_h > list_y and row_y < list_y + list_h then
                local active = song.current == true
                local hovered = state.interactive and not state.modal_blocking and ui.hovered(list_x, row_y, list_w, row_h)
                if active or hovered then
                    ui.rect(list_x, row_y, list_w, row_h,
                        ui.opacity(active and 0x263D81F7 or 0x0FFFFFFF, alpha))
                end
                ui.custom("cover", song.cover or "", list_x + 4, row_y + 4, 28, 28, alpha)
                ui.text_font(ui.trim_text("text", song.name or "", 9, list_w - 82),
                    list_x + 39, row_y + 7, 9, ui.opacity(active and 0xFF65CFFF or MUSIC_TEXT, alpha))
                ui.text_font(ui.trim_text("text", song.artist or "", 7, list_w - 82),
                    list_x + 39, row_y + 21, 7, ui.opacity(MUSIC_DIM, alpha))
                ui.text_font(song.duration or "00:00", list_x + list_w - 38, row_y + 14, 7,
                    ui.opacity(MUSIC_DIM, alpha))
                ui.hitbox(list_x, row_y, list_w, row_h, "music_song", song.index,
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
        ui.rect(x, y, size, size, ui.opacity(hovered and 0x28FFFFFF or 0x12000000, alpha))
        border(x, y, size, size, ui.opacity(hovered and 0x45FFFFFF or 0x16FFFFFF, alpha))
        local icon_size = emphasized and size * 0.42 or size * 0.36
        local icon_color = emphasized and 0xFFFFFFFF or MUSIC_MUTED
        ui.custom("icon", icon, x + (size - icon_size) * 0.5, y + (size - icon_size) * 0.5,
            icon_size, icon_size, ui.opacity(icon_color, alpha * (available and 1 or 0.38)))
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
    ui.rect(x, y, width, player_height, ui.opacity(0xB90C0D12, alpha))
    ui.rect(x, y, width, 1, ui.opacity(0x38FFFFFF, alpha))

    local cover_size = math.min(left_width * 0.5, player_height * 0.54)
    local cover_x = x + (left_width - cover_size) * 0.5
    local cover_target_y = y + player_height * 0.5 - cover_size
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
        y + player_height * 0.5 + 5, left_width - 14, 8, ui.opacity(MUSIC_TEXT, alpha))

    local controls_x = x + left_width
    local controls_w = width - left_width
    local button = clamp(player_height * 0.31, 24, 31)
    local gap = clamp(controls_w * 0.055, 9, 18)
    local group_w = button * 3 + gap * 2
    local group_x = controls_x + (controls_w - group_w) * 0.5 - 5
    local group_y = y + 13
    music_control(state, "previous", group_x, group_y, button, "music_previous",
        music.previous_scale or 1, false, alpha, music.has_song)
    music_control(state, music.playing and "pause" or "play", group_x + button + gap, group_y - 2,
        button + 4, "music_toggle", music.toggle_scale or 1, true, alpha, music.has_song)
    music_control(state, "next", group_x + button * 2 + gap * 2 + 4, group_y, button,
        "music_next", music.next_scale or 1, false, alpha, music.has_song)

    local volume_button_x = x + width - 28
    local volume_button_y = y + 15
    local slider_x = volume_button_x + 8
    local slider_y = y - 58
    local slider_h = 50
    local volume_hovered = ui.hovered(volume_button_x, volume_button_y, 22, 22)
        or ui.hovered(slider_x - 7, slider_y - 5, 18,
            volume_button_y + 22 - (slider_y - 5))
    volume_popup = animate_exp(volume_popup, volume_hovered and 1 or 0, 18, state.frame_seconds)
    if volume_popup > 0.01 then
        local rise = (1 - ease_out_cubic(volume_popup)) * 12
        ui.rect(slider_x - 6, slider_y - 5 + rise, 16, slider_h + 10,
            ui.opacity(0xE6191B21, alpha * volume_popup))
        border(slider_x - 6, slider_y - 5 + rise, 16, slider_h + 10,
            ui.opacity(0x34FFFFFF, alpha * volume_popup))
        ui.rect(slider_x, slider_y + rise, 4, slider_h,
            ui.opacity(0xFF3A3D46, alpha * volume_popup))
        local fill_h = slider_h * clamp(music.volume or 0, 0, 1)
        ui.rect(slider_x, slider_y + slider_h - fill_h + rise, 4, fill_h,
            ui.opacity(MUSIC_ACCENT, alpha * volume_popup))
        ui.rect(slider_x - 2, slider_y + slider_h - fill_h - 1 + rise, 8, 3,
            ui.opacity(0xFFFFFFFF, alpha * volume_popup))
        ui.hitbox(slider_x - 7, slider_y - 5 + rise, 18, slider_h + 10,
            "music_volume", {y = slider_y + rise, height = slider_h},
            state.interactive and not state.modal_blocking, true)
    end
    ui.custom("icon", "volume", volume_button_x + 5, volume_button_y + 5, 12, 12,
        ui.opacity(MUSIC_MUTED, alpha))

    local bar_x = controls_x + 40
    local bar_w = math.max(30, width - left_width - 80)
    local bar_y = y + player_height - 10
    ui.rect(bar_x, bar_y, bar_w, 3, ui.opacity(0xFF3C3F47, alpha))
    ui.rect(bar_x, bar_y, bar_w * clamp(music.progress or 0, 0, 1), 3,
        ui.opacity(0xFFFFFFFF, alpha))
    ui.text_font(music.position_label or "00:00", controls_x + 5, bar_y - 3, 7,
        ui.opacity(MUSIC_MUTED, alpha))
    ui.text_font(music.duration_label or "00:00", x + width - 36, bar_y - 3, 7,
        ui.opacity(MUSIC_MUTED, alpha))
    ui.hitbox(bar_x, bar_y - 5, bar_w, 13, "music_progress",
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
    local player_height = clamp(bounds.height * 0.29, MUSIC_PLAYER_MIN, 106)
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
