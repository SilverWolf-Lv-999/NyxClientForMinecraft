local screen = {}

local SCREEN_DIM = 0xB005060A
local PANEL = 0xFF0C0D11
local SIDEBAR = 0xCC090A0E
local CARD = 0xFF14161D
local CARD_HOVER = 0xFF1A1E2A
local CONTROL = 0xFF0C0D11
local ACCENT = 0xFF57C7FF
local ACCENT_DARK = 0xFF3D81F7
local ACCENT_ALT = 0xFFFF6373
local TEXT = 0xFFFFFFFF
local TEXT_MUTED = 0xFFA0A5B5
local TEXT_DIM = 0xFF697183
local BORDER = 0x22FFFFFF
local BORDER_SOFT = 0x10FFFFFF
local TRACK = 0x66000000

local TITLE_FONT_SIZE = 12
local BODY_FONT_SIZE = 8.5
local META_FONT_SIZE = 7.5

local hover_progress = {}
local icon_hover_progress = {}
local detail_progress = 0
local detail_lyric_position = nil

local function clamp(value, minimum, maximum)
    return math.max(minimum, math.min(maximum, value))
end

local function lerp(from, to, progress)
    return from + (to - from) * clamp(progress, 0, 1)
end

local function phase(start_value, end_value, value)
    if end_value <= start_value then
        return value >= end_value and 1 or 0
    end
    return clamp((value - start_value) / (end_value - start_value), 0, 1)
end

local function ease_in_out_cubic(value)
    local safe = clamp(value, 0, 1)
    if safe < 0.5 then
        return 4 * safe * safe * safe
    end
    return 1 - ((-2 * safe + 2) ^ 3) * 0.5
end

local function opacity(color, alpha)
    return ui.opacity(color, clamp(alpha, 0, 1))
end

local function animate_hover(id, hovered, frame_seconds)
    local target = hovered and 1 or 0
    local current = hover_progress[id] or 0
    current = target + (current - target) * math.exp(-16 * (frame_seconds or 1 / 60))
    hover_progress[id] = current
    return current
end

local function animate_icon_hover(id, hovered)
    local current = icon_hover_progress[id] or 0
    current = current + ((hovered and 1 or 0) - current) * 0.35
    icon_hover_progress[id] = current
    return current
end

local function bounds(state)
    local width = math.max(1, math.min(640, state.width - 20))
    local height = math.max(1, math.min(420, state.height - 20))
    local x = (state.width - width) * 0.5
    local y = (state.height - height) * 0.5
    local sidebar = clamp(width * 0.2, 88, 128)
    local player_height = clamp(height * 0.22, 78, 92)
    return {
        x = x,
        y = y,
        width = width,
        height = height,
        sidebar = sidebar,
        player_height = player_height,
        content_x = x + sidebar + 18,
        content_width = math.max(1, width - sidebar - 36),
        player_x = x + sidebar,
        player_y = y + height - player_height,
        player_width = width - sidebar
    }
end

local function icon(name, x, y, width, height, color)
    ui.custom("icon", name, x, y, width, height, color)
end

local function body(text, x, y, color)
    ui.text_font(text or "", x, y, BODY_FONT_SIZE, color)
end

local function meta(text, x, y, color)
    ui.text_font(text or "", x, y, META_FONT_SIZE, color)
end

local function title(text, x, y, color)
    ui.display_text(text or "", x, y, TITLE_FONT_SIZE, color)
end

local function body_centered_in_rect(text, x, y, width, height, color)
    ui.text_centered_in_rect(text or "", x, y, width, height, BODY_FONT_SIZE, color)
end

local function small_button(state, id, label, x, y, width, height, action, payload, active, color)
    local enabled = active ~= false
    local hovered = enabled and ui.hovered(x, y, width, height)
    local hover = animate_hover("small:" .. id, hovered, state.frame_seconds)
    ui.rounded_rect(x, y, width, height, 5, ui.mix(CONTROL, CARD_HOVER, hover))
    ui.outline(x, y, width, height, 5, 1, ui.mix(BORDER_SOFT, ACCENT, hover))
    body_centered_in_rect(
        ui.trim_text("text", label, BODY_FONT_SIZE, width - 12),
        x, y, width, height,
        enabled and (color or TEXT_MUTED) or TEXT_DIM
    )
    ui.hitbox(x, y, width, height, action, payload, enabled)
end

local function icon_button(state, id, name, x, y, size, action, accent)
    local hovered = ui.hovered(x, y, size, size)
    local hover = animate_icon_hover(id, hovered)
    ui.rounded_rect(x, y, size, size, math.min(7, size * 0.5), ui.mix(CONTROL, CARD_HOVER, hover))
    ui.outline(x, y, size, size, math.min(7, size * 0.5), 1, hovered and ACCENT or BORDER_SOFT)
    local icon_size = 10
    icon(name, x + (size - icon_size) * 0.5, y + (size - icon_size) * 0.5,
        icon_size, icon_size, accent and ACCENT or TEXT_MUTED)
    ui.hitbox(x, y, size, size, action)
end

local function nav_button(state, panel, name, label, page, y)
    local x = panel.x + 10
    local width = panel.sidebar - 20
    local active = state.page == page
    local hovered = ui.hovered(x, y, width, 26)
    local hover = animate_hover("nav:" .. page, hovered, state.frame_seconds)
    ui.rounded_rect(x, y, width, 26, 5, active and 0x1E57C7FF or ui.mix(0x00000000, 0x12FFFFFF, hover))
    icon(name, x + 11, y + 8, 10, 10, active and ACCENT or TEXT_MUTED)
    body(label, x + 28, y + 8, active and ACCENT or TEXT_MUTED)
    ui.hitbox(x, y, width, 26, "page", page)
end

local function login_mode_button(state, label, mode, x, y, width)
    local active = state.login_mode == mode
    local hovered = ui.hovered(x, y, width, 22)
    local hover = animate_hover("login_mode:" .. mode, hovered, state.frame_seconds)
    ui.rounded_rect(x, y, width, 22, 5, active and 0x1E57C7FF or ui.mix(CONTROL, CARD_HOVER, hover))
    ui.outline(x, y, width, 22, 5, 1, active and ACCENT or ui.mix(BORDER_SOFT, ACCENT, hover))
    body_centered_in_rect(
        ui.trim_text("text", label, BODY_FONT_SIZE, width - 12),
        x, y, width, 22,
        active and ACCENT or TEXT_MUTED
    )
    ui.hitbox(x, y, width, 22, "login_mode", mode, not state.login_busy)
end

local function render_sidebar(state, panel)
    ui.rect(panel.x, panel.y, panel.sidebar, panel.height, SIDEBAR)
    ui.rect(panel.x + panel.sidebar - 1, panel.y + 12, 1, panel.height - 24, BORDER)
    ui.rounded_horizontal_gradient(panel.x + 16, panel.y + 20, 24, 24, 7, ACCENT, ACCENT_DARK)
    icon("music", panel.x + 22, panel.y + 26, 12, 12, TEXT)
    title("Nyx Music", panel.x + 48, panel.y + 19, TEXT)
    meta("Netease", panel.x + 48, panel.y + 35, TEXT_DIM)

    nav_button(state, panel, "home", "Home", "home", panel.y + 72)
    nav_button(state, panel, "search", "Search", "search", panel.y + 106)
    nav_button(state, panel, "list", "My Lists", "my", panel.y + 140)

    local back_y = panel.y + panel.height - 38
    local back_hovered = ui.hovered(panel.x + 10, back_y, panel.sidebar - 20, 26)
    local back_hover = animate_hover("nav:back", back_hovered, state.frame_seconds)
    ui.rounded_rect(panel.x + 10, back_y, panel.sidebar - 20, 26, 5,
        ui.mix(0x00000000, 0x12FFFFFF, back_hover))
    body("<", panel.x + 21, back_y + 8, ui.mix(TEXT_DIM, ACCENT, back_hover))
    body("Back", panel.x + 38, back_y + 8, ui.mix(TEXT_MUTED, TEXT, back_hover))
    ui.hitbox(panel.x + 10, back_y, panel.sidebar - 20, 26, "back")
end

local function render_search(state, panel)
    local width = math.min(174, math.max(110, panel.content_width * 0.42))
    local x = panel.content_x + panel.content_width - width
    local y = panel.y + 20
    ui.input("search", x, y, width - 38, 24, state.search_text or "", "Search song...", 128,
        "search", true, false, "", BODY_FONT_SIZE)
    small_button(state, "search", ">", x + width - 34, y, 34, 24,
        "search", nil, not state.loading, ACCENT)
end

local function render_login(state, panel)
    local x = panel.content_x
    local y = panel.y + 62
    local width = panel.content_width
    local available_height = math.max(80, panel.player_y - y - 12)
    local height = math.min(192, available_height)
    ui.rounded_rect(x, y, width, height, 7, CARD)
    ui.outline(x, y, width, height, 7, 1, BORDER_SOFT)
    title("Netease Login", x + 14, y + 13, TEXT)
    meta("Login to load your playlists", x + 14, y + 31, TEXT_DIM)

    login_mode_button(state, "Code", "captcha", x + 14, y + 49, 66)
    login_mode_button(state, "QR", "qr", x + 84, y + 49, 52)
    login_mode_button(state, "Password", "password", x + 140, y + 49, 82)

    if state.login_mode == "qr" then
        local qr_x = x + 14
        local qr_y = y + 80
        local qr_size = math.min(112, math.max(76, height - 88))
        ui.rounded_rect(qr_x, qr_y, qr_size, qr_size, 6, CONTROL)
        ui.outline(qr_x, qr_y, qr_size, qr_size, 6, 1, BORDER_SOFT)
        if state.qr_image and state.qr_image ~= "" then
            ui.custom("cover", state.qr_image, qr_x + 8, qr_y + 8, qr_size - 16, 3)
        else
            icon("music", qr_x + qr_size * 0.5 - 10, qr_y + qr_size * 0.36, 20, 20, TEXT_DIM)
            ui.text_centered((state.qr_polling or state.login_busy) and "Loading..." or "No QR",
                qr_x + qr_size * 0.5, qr_y + qr_size * 0.65, META_FONT_SIZE, TEXT_DIM)
        end

        local text_x = qr_x + qr_size + 18
        body("Scan with Netease Music app", text_x, qr_y + 4, TEXT)
        meta(
            ui.trim_text("text", state.status or "", META_FONT_SIZE, math.max(1, width - (text_x - x) - 14)),
            text_x, qr_y + 21,
            (state.qr_polling or state.login_busy) and ACCENT or TEXT_DIM
        )
        small_button(state, "qr_start",
            state.qr_image and state.qr_image ~= "" and "Refresh" or "Create QR",
            text_x, qr_y + 48, 82, 22, "qr_start", nil, not state.login_busy, TEXT)
        small_button(state, "qr_cancel", "Cancel", text_x + 90, qr_y + 48, 62, 22,
            "qr_cancel", nil, true, TEXT_MUTED)
        return
    end

    ui.input("phone", x + 14, y + 84, math.min(232, width - 28), 24, state.phone_text or "", "Phone", 32,
        state.login_mode == "password" and "login_password" or "login_captcha",
        not state.login_busy, false, "", BODY_FONT_SIZE)
    if state.login_mode == "captcha" then
        ui.input("captcha", x + 14, y + 116, math.min(138, width - 122), 24, state.captcha_text or "", "Captcha", 16,
            "login_captcha", not state.login_busy, false, "", BODY_FONT_SIZE)
        small_button(state, "send_captcha", "Send", x + 158, y + 116, 88, 24,
            "send_captcha", nil, not state.login_busy, TEXT_MUTED)
        small_button(state, "login_captcha", "Login", x + 14, y + 152, 76, 22,
            "login_captcha", nil, not state.login_busy, TEXT)
    else
        ui.input("password", x + 14, y + 116, math.min(232, width - 28), 24, state.password_text or "", "Password", 128,
            "login_password", not state.login_busy, true, "", BODY_FONT_SIZE)
        small_button(state, "login_password", "Login", x + 14, y + 152, 76, 22,
            "login_password", nil, not state.login_busy, TEXT)
    end
end

local function render_account_bar(state, panel)
    local x = panel.content_x
    local y = panel.y + 54
    local width = panel.content_width
    ui.rounded_rect(x, y, width, 28, 6, CARD)
    ui.outline(x, y, width, 28, 6, 1, BORDER_SOFT)
    body(ui.trim_text("text", state.session_name or "Netease Account", BODY_FONT_SIZE, math.max(1, width - 152)),
        x + 10, y + 6, TEXT)
    meta(tostring(state.playlist_count or 0) .. " playlists", x + 10, y + 18, TEXT_DIM)
    small_button(state, "refresh_playlists", "Refresh", x + width - 132, y + 5, 60, 18,
        "refresh_playlists", nil, not state.loading, TEXT_MUTED)
    small_button(state, "logout", "Logout", x + width - 66, y + 5, 56, 18,
        "logout", nil, true, ACCENT_ALT)
end

local function playlist_height(count, columns)
    if count <= 0 then
        return 0
    end
    return 30 + math.ceil(count / columns) * 34
end

local function render_list(state, panel)
    local list_y = state.page == "my" and state.logged_in and panel.y + 88 or panel.y + 58
    local list_height = math.max(0, panel.player_y - list_y - 14)
    local playlists = state.playlists or {}
    local songs = state.songs or {}
    local columns = panel.content_width >= 330 and 2 or 1
    local playlist_section_height = playlist_height(#playlists, columns)
    local content_height = playlist_section_height + #songs * 42
    if content_height <= 0 then
        content_height = list_height
    end
    local scroll = ui.scroll("music_content", panel.content_x, list_y, panel.content_width, list_height, content_height, 22)

    ui.clip(panel.content_x, list_y, panel.content_width, list_height, function()
        local y = list_y - scroll
        if #playlists > 0 then
            body(state.page == "my" and "My playlists" or "Recommended playlists",
                panel.content_x, y, TEXT_MUTED)
            y = y + 16
            local gap = 10
            local row_width = (panel.content_width - gap * (columns - 1)) / columns
            for index, playlist in ipairs(playlists) do
                local column = (index - 1) % columns
                local row = math.floor((index - 1) / columns)
                local row_x = panel.content_x + column * (row_width + gap)
                local row_y = y + row * 34
                if row_y + 28 >= list_y and row_y <= list_y + list_height then
                    local hovered = ui.hovered(row_x, row_y, row_width, 28)
                    local hover = animate_hover("playlist:" .. tostring(playlist.index), hovered, state.frame_seconds)
                    ui.rounded_rect(row_x, row_y, row_width, 28, 5, ui.mix(CARD, CARD_HOVER, hover))
                    ui.custom("cover", playlist.cover or "", row_x + 4, row_y + 4, 20, 4)
                    ui.clip(row_x + 31, row_y, math.max(1, row_width - 37), 28, function()
                        body(ui.trim_text("text", playlist.name or "", BODY_FONT_SIZE, math.max(1, row_width - 38)),
                            row_x + 31, row_y + 5, TEXT)
                        meta(playlist.play_count or "", row_x + 31, row_y + 17.5, TEXT_DIM)
                    end)
                    ui.hitbox(row_x, row_y, row_width, 28, "open_playlist", playlist.index, true)
                end
            end
            y = y + math.ceil(#playlists / columns) * 34 + 14
        end

        if #songs == 0 then
            local message = state.loading and "Loading..." or (state.selected_playlist and "No songs" or "Select a playlist")
            body(message, panel.content_x, y + 12, TEXT_DIM)
            return
        end

        for index, song in ipairs(songs) do
            local row_y = y + (index - 1) * 42
            if row_y + 36 >= list_y and row_y <= list_y + list_height then
                local hovered = ui.hovered(panel.content_x, row_y, panel.content_width, 36)
                local hover = animate_hover("song:" .. tostring(song.index), hovered, state.frame_seconds)
                ui.rounded_rect(panel.content_x, row_y, panel.content_width, 36, 6,
                    song.current and 0x1E57C7FF or ui.mix(CARD, CARD_HOVER, hover))
                if song.current then
                    ui.rounded_rect(panel.content_x + 2, row_y + 8, 2, 20, 1, ACCENT)
                end
                ui.custom("cover", song.cover or "", panel.content_x + 6, row_y + 5, 26, 5)
                local duration_width = 52
                ui.clip(panel.content_x + 40, row_y, math.max(1, panel.content_width - 40 - duration_width), 36, function()
                    body(ui.trim_text("text", song.name or "", BODY_FONT_SIZE,
                            math.max(1, panel.content_width - 40 - duration_width)),
                        panel.content_x + 40, row_y + 6, song.current and ACCENT or TEXT)
                    meta(ui.trim_text("text", song.artist or "", META_FONT_SIZE,
                            math.max(1, panel.content_width - 40 - duration_width)),
                        panel.content_x + 40, row_y + 20, TEXT_DIM)
                end)
                meta(song.duration or "", panel.content_x + panel.content_width - 48, row_y + 14, TEXT_MUTED)
                ui.hitbox(panel.content_x, row_y, panel.content_width, 36, "play_song", song.index, true)
            end
        end
    end)
end

local function render_header(state, panel)
    local title_right = panel.content_x + panel.content_width
    if state.page == "search" then
        local search_width = math.min(174, math.max(110, panel.content_width * 0.42))
        title_right = title_right - search_width - 8
    end
    ui.clip(panel.content_x, panel.y + 12, math.max(1, title_right - panel.content_x), 40, function()
        title(state.title or "", panel.content_x, panel.y + 17, TEXT)
        meta(
            ui.trim_text("text", state.status or "", META_FONT_SIZE, math.max(1, title_right - panel.content_x)),
            panel.content_x, panel.y + 34,
            (state.loading or state.login_busy or state.qr_polling) and ACCENT or TEXT_DIM
        )
    end)
    if state.page == "search" then
        render_search(state, panel)
    end
end

local function render_mode_button(state, x, y)
    local hovered = ui.hovered(x, y, 62, 24)
    local hover = animate_hover("player:mode", hovered, state.frame_seconds)
    ui.rounded_rect(x, y, 62, 24, 6, ui.mix(CONTROL, CARD_HOVER, hover))
    ui.outline(x, y, 62, 24, 6, 1, ui.mix(BORDER_SOFT, ACCENT, hover))
    icon(state.mode_icon or "list", x + 8, y + 7, 10, 10, ACCENT)
    body(ui.trim_text("text", state.mode_label or "", BODY_FONT_SIZE, 32),
        x + 24, y + 7.5, TEXT_MUTED)
    ui.hitbox(x, y, 62, 24, "cycle_mode")
end

local function render_player(state, panel)
    local x = panel.player_x
    local y = panel.player_y
    local width = panel.player_width
    ui.rect(x, y, width, 1, BORDER)
    ui.rect(x, y + 1, width, panel.player_height - 1, 0xDD0B0D12)

    local cover_hovered = state.has_current_song and ui.hovered(x + 12, y + 10, 56, 56)
    local cover_hover = animate_hover("player:cover", cover_hovered, state.frame_seconds)
    if cover_hover > 0.001 then
        ui.shadow(x + 16, y + 14, 48, 48, 8, 0, 3, 10, opacity(ACCENT, cover_hover * 0.28))
    end
    ui.custom("cover", state.current_cover or "", x + 16, y + 14, 48, 8)
    ui.outline(x + 16, y + 14, 48, 48, 8, 1, ui.mix(BORDER_SOFT, ACCENT, cover_hover))
    ui.hitbox(x + 12, y + 10, 56, 56, "open_detail", nil, state.has_current_song)
    local controls_x = x + width - 226
    local info_width = math.max(70, controls_x - (x + 74) - 8)
    ui.clip(x + 74, y + 10, info_width, 40, function()
        body(ui.trim_text("text", state.current_song or "No song selected", BODY_FONT_SIZE, info_width),
            x + 74, y + 17, TEXT)
        meta(ui.trim_text("text", state.current_artist or "", META_FONT_SIZE, info_width),
            x + 74, y + 33, TEXT_DIM)
    end)

    icon_button(state, "player:previous", "previous", controls_x, y + 17, 24, "previous", false)
    icon_button(state, "player:toggle", state.playing and "pause" or "play",
        controls_x + 32, y + 12, 34, "toggle", true)
    icon_button(state, "player:next", "next", controls_x + 74, y + 17, 24, "next", false)
    icon_button(state, "player:stop", "stop", controls_x + 106, y + 17, 24, "stop", false)
    render_mode_button(state, controls_x + 138, y + 17)

    local bar_x = x + 74
    local bar_y = y + 61
    local bar_width = math.max(40, width - 172)
    ui.rounded_rect(bar_x, bar_y, bar_width, 4, 2, TRACK)
    ui.rounded_horizontal_gradient(bar_x, bar_y, bar_width * clamp(state.progress or 0, 0, 1), 4, 2, ACCENT, ACCENT_DARK)
    ui.circle(bar_x + bar_width * clamp(state.progress or 0, 0, 1), bar_y + 2, 3, TEXT)
    meta(state.time_label or "00:00 / 00:00", bar_x + bar_width + 8, bar_y - 4, TEXT_DIM)

    local volume_x = x + width - 190
    local volume_y = y + panel.player_height - 19
    local volume_width = 112
    icon("volume", volume_x, volume_y - 4, 11, 11, TEXT_DIM)
    local slider_x = volume_x + 17
    ui.rounded_rect(slider_x, volume_y, volume_width, 4, 2, TRACK)
    ui.rounded_horizontal_gradient(slider_x, volume_y, volume_width * clamp(state.volume or 0, 0, 1), 4, 2, ACCENT_ALT, ACCENT)
    local volume_hovered = ui.hovered(slider_x - 4, volume_y - 7, volume_width + 8, 16)
    ui.circle(slider_x + volume_width * clamp(state.volume or 0, 0, 1), volume_y + 2, volume_hovered and 4 or 3, TEXT)
    meta(state.volume_label or "0%", slider_x + volume_width + 7, volume_y - 4, TEXT_DIM)
    ui.hitbox(slider_x - 4, volume_y - 7, volume_width + 8, 16, "set_volume",
        {x = slider_x, width = volume_width}, true, true)

    if state.lyric and state.lyric ~= "" then
        ui.clip(x + 16, y + panel.player_height - 25, math.max(1, volume_x - x - 24), 18, function()
            body(ui.trim_text("text", state.lyric, BODY_FONT_SIZE, math.max(1, volume_x - x - 24)),
                x + 16, y + panel.player_height - 20, ACCENT)
        end)
    end
end

local function detail_icon_button(state, id, name, x, y, size, action, accent, alpha, rotation)
    local active = state.detail_open and alpha > 0.82
    local hovered = active and ui.hovered(x, y, size, size)
    local hover = animate_icon_hover("detail:" .. id, hovered)
    local radius = math.min(7, size * 0.5)
    ui.rounded_rect(x, y, size, size, radius, opacity(ui.mix(CONTROL, CARD_HOVER, hover), alpha))
    ui.outline(x, y, size, size, radius, 1,
        opacity(ui.mix(BORDER_SOFT, ACCENT, hover), alpha))
    local icon_size = size >= 30 and 11 or 9
    local icon_x = x + (size - icon_size) * 0.5
    local icon_y = y + (size - icon_size) * 0.5
    if rotation ~= nil and rotation ~= 0 then
        ui.rotate(rotation, x + size * 0.5, y + size * 0.5, function()
            icon(name, icon_x, icon_y, icon_size, icon_size,
                opacity(accent and ACCENT or TEXT_MUTED, alpha))
        end)
    else
        icon(name, icon_x, icon_y, icon_size, icon_size,
            opacity(accent and ACCENT or TEXT_MUTED, alpha))
    end
    ui.hitbox(x, y, size, size, action, nil, active)
end

local function update_detail_lyric_position(state)
    local target = state.detail_lyric_index or 0
    if target <= 0 then
        detail_lyric_position = nil
        return
    end
    if detail_lyric_position == nil or math.abs(detail_lyric_position - target) > 5 then
        detail_lyric_position = target
        return
    end
    local frame_seconds = state.frame_seconds or 1 / 60
    detail_lyric_position = target
        + (detail_lyric_position - target) * math.exp(-9 * frame_seconds)
end

local function render_detail_lyrics(state, panel, split_y, alpha)
    local area_x = panel.x + 24
    local area_y = panel.y + 47
    local area_width = math.max(1, panel.width * 0.5 - 38)
    local area_height = math.max(1, split_y - area_y - 18)
    title("Lyrics", panel.x + 54, panel.y + 19, opacity(TEXT, alpha))
    meta("NOW PLAYING", panel.x + 54, panel.y + 35, opacity(TEXT_DIM, alpha))

    local lyrics = state.detail_lyrics or {}
    if #lyrics == 0 or detail_lyric_position == nil then
        ui.text_centered("No lyrics available", area_x + area_width * 0.5,
            area_y + area_height * 0.5, BODY_FONT_SIZE, opacity(TEXT_DIM, alpha))
        return
    end

    local center_y = area_y + area_height * 0.5
    local current_index = state.detail_lyric_index or 0
    ui.clip(area_x, area_y, area_width, area_height, function()
        for _, line in ipairs(lyrics) do
            local distance = line.index - detail_lyric_position
            local row_y = center_y + distance * 25
            if row_y >= area_y - 16 and row_y <= area_y + area_height + 4 then
                local absolute_distance = math.abs(distance)
                local row_alpha = alpha * clamp(1 - absolute_distance * 0.13, 0.18, 1)
                local current = line.index == current_index
                local font_size = current and 11 or BODY_FONT_SIZE
                local color = current and ACCENT or TEXT_MUTED
                local text = (line.text == nil or line.text == "") and "..." or line.text
                ui.text_centered(
                    ui.trim_text("text", text, font_size, math.max(1, area_width - 16)),
                    area_x + area_width * 0.5,
                    row_y - font_size * 0.5,
                    font_size,
                    opacity(color, row_alpha)
                )
            end
        end
    end)
end

local function detail_cover_target(panel, split_y)
    local right_x = panel.x + panel.width * 0.5
    local upper_y = panel.y + 18
    local upper_height = math.max(1, split_y - upper_y - 18)
    local size = math.max(42, math.min(panel.width * 0.38, upper_height - 38))
    return right_x + (panel.width * 0.5 - size) * 0.5,
        upper_y + (upper_height - size) * 0.5,
        size
end

local function render_detail_bottom(state, panel, split_y, alpha)
    local progress = clamp(state.progress or 0, 0, 1)
    local bar_x = panel.x + 18
    local bar_y = split_y - 2
    local bar_width = math.max(1, panel.width - 36)

    ui.rect(panel.x, split_y - 1, panel.width, 1, opacity(BORDER, alpha))
    ui.rounded_rect(bar_x, bar_y, bar_width, 4, 2, opacity(TRACK, alpha))
    ui.rounded_horizontal_gradient(
        bar_x, bar_y, bar_width * progress, 4, 2,
        opacity(ACCENT, alpha), opacity(ACCENT_DARK, alpha)
    )
    ui.circle(bar_x + bar_width * progress, bar_y + 2, 3.5, opacity(TEXT, alpha))
    ui.hitbox(bar_x, bar_y - 6, bar_width, 16, "set_progress",
        {x = bar_x, width = bar_width},
        state.detail_open and alpha > 0.82 and (state.duration or 0) > 0)

    meta(state.position_label or "00:00", panel.x + 18, split_y + 8, opacity(TEXT_DIM, alpha))
    local duration_label = state.duration_label or "00:00"
    local duration_width = ui.font_width("text", duration_label, META_FONT_SIZE)
    meta(duration_label, panel.x + panel.width - 18 - duration_width, split_y + 8, opacity(TEXT_DIM, alpha))

    local compact = panel.width < 520
    local very_compact = panel.width < 360
    local controls_center = panel.x + panel.width * (very_compact and 0.48 or 0.52)
    local controls_y = split_y + 34
    local play_x = controls_center - 17
    detail_icon_button(state, "previous", "previous", play_x - 32, controls_y + 5, 24,
        "previous", false, alpha)
    detail_icon_button(state, "toggle", state.playing and "pause" or "play", play_x, controls_y, 34,
        "toggle", true, alpha)
    detail_icon_button(state, "next", "next", play_x + 42, controls_y + 5, 24,
        "next", false, alpha)
    if not compact then
        detail_icon_button(state, "mode", state.mode_icon or "list", play_x + 76, controls_y + 5, 24,
            "cycle_mode", true, alpha)
    end

    local info_x = panel.x + 20
    local info_width = math.max(54, play_x - 32 - info_x - 12)
    ui.clip(info_x, controls_y - 2, info_width, 44, function()
        body(ui.trim_text("text", state.current_song or "", BODY_FONT_SIZE, info_width),
            info_x, controls_y + 4, opacity(TEXT, alpha))
        meta(ui.trim_text("text", state.current_artist or "", META_FONT_SIZE, info_width),
            info_x, controls_y + 21, opacity(TEXT_DIM, alpha))
    end)

    local volume_x = panel.x + panel.width - (very_compact and 88 or (compact and 112 or 158))
    local volume_y = controls_y + 15
    local volume_width = very_compact and 50 or (compact and 62 or 92)
    icon("volume", volume_x, volume_y - 4, 11, 11, opacity(TEXT_DIM, alpha))
    local slider_x = volume_x + 17
    ui.rounded_rect(slider_x, volume_y, volume_width, 4, 2, opacity(TRACK, alpha))
    ui.rounded_horizontal_gradient(
        slider_x, volume_y, volume_width * clamp(state.volume or 0, 0, 1), 4, 2,
        opacity(ACCENT_ALT, alpha), opacity(ACCENT, alpha)
    )
    local volume_hovered = alpha > 0.82
        and ui.hovered(slider_x - 4, volume_y - 7, volume_width + 8, 16)
    ui.circle(slider_x + volume_width * clamp(state.volume or 0, 0, 1), volume_y + 2,
        volume_hovered and 4 or 3, opacity(TEXT, alpha))
    if not very_compact then
        meta(state.volume_label or "0%", slider_x + volume_width + 6, volume_y - 4,
            opacity(TEXT_DIM, alpha))
    end
    ui.hitbox(slider_x - 4, volume_y - 7, volume_width + 8, 16, "set_volume",
        {x = slider_x, width = volume_width}, state.detail_open and alpha > 0.82, true)
end

local function render_detail(state, panel)
    local target = state.detail_open and 1 or 0
    local frame_seconds = state.frame_seconds or 1 / 60
    local step = frame_seconds / 0.30
    if target > detail_progress then
        detail_progress = math.min(target, detail_progress + step)
    elseif target < detail_progress then
        detail_progress = math.max(target, detail_progress - step)
    end
    if detail_progress <= 0.001 then
        detail_progress = 0
        detail_lyric_position = nil
        return
    end

    update_detail_lyric_position(state)

    local eased = ease_in_out_cubic(detail_progress)
    local content_alpha = phase(0.24, 0.72, eased)
    local source_x = panel.player_x + 16
    local source_y = panel.player_y + 14
    local source_size = 48
    local rect_x = lerp(source_x, panel.x, eased)
    local rect_y = lerp(source_y, panel.y, eased)
    local rect_width = lerp(source_size, panel.width, eased)
    local rect_height = lerp(source_size, panel.height, eased)
    local rect_radius = lerp(8, 10, eased)
    local bottom_height = math.min(106, math.max(76, panel.height * 0.25), panel.height * 0.42)
    local split_y = panel.y + panel.height - bottom_height
    local cover_target_x, cover_target_y, cover_target_size = detail_cover_target(panel, split_y)
    local cover_x = lerp(source_x, cover_target_x, eased)
    local cover_y = lerp(source_y, cover_target_y, eased)
    local cover_size = lerp(source_size, cover_target_size, eased)
    local cover_radius = lerp(8, 10, eased)

    ui.hitbox(panel.x, panel.y, panel.width, panel.height, "detail_block")
    ui.shadow(rect_x, rect_y, rect_width, rect_height, rect_radius, 0, 10, 24,
        opacity(0xD0000000, eased))
    ui.rounded_rect(rect_x, rect_y, rect_width, rect_height, rect_radius, 0xFF0A0C11)
    ui.outline(rect_x, rect_y, rect_width, rect_height, rect_radius, 1, opacity(BORDER, eased))

    ui.clip(rect_x, rect_y, rect_width, rect_height, function()
        if content_alpha > 0.001 then
            render_detail_lyrics(state, panel, split_y, content_alpha)
            render_detail_bottom(state, panel, split_y, content_alpha)
            detail_icon_button(state, "back", "arrow_right", panel.x + 16, panel.y + 15, 26,
                "close_detail", false, content_alpha, 180)
        end

        if eased > 0.12 then
            ui.shadow(cover_x, cover_y, cover_size, cover_size, cover_radius, 0, 8, 22,
                opacity(0xB0000000, eased))
        end
        ui.custom("detail_cover", state.current_cover or "", cover_x, cover_y, cover_size, cover_radius)
    end)
end

function screen.render(state)
    local panel = bounds(state)
    ui.rect(0, 0, state.width, state.height, SCREEN_DIM)
    ui.shadow(panel.x, panel.y, panel.width, panel.height, 12, 0, 18, 30, 0xA0000000)
    ui.rounded_rect(panel.x, panel.y, panel.width, panel.height, 10, PANEL)
    ui.outline(panel.x, panel.y, panel.width, panel.height, 10, 1, BORDER)

    ui.clip(panel.x, panel.y, panel.width, panel.height, function()
        render_sidebar(state, panel)
        render_header(state, panel)
        if state.page == "my" and not state.logged_in then
            render_login(state, panel)
        else
            if state.page == "my" then
                render_account_bar(state, panel)
            end
            render_list(state, panel)
        end
        render_player(state, panel)
    end)
    render_detail(state, panel)
end

function screen.key_pressed(state, key)
    if key.escape then
        if state.detail_open or detail_progress > 0.001 then
            ui.action("close_detail")
        else
            ui.action("back")
        end
        return true
    end
    return false
end

return screen
