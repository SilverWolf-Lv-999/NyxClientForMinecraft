local common = include("nyxclient:ui/screen/common.lua")
local screen = {}

local config = {
    title = "Muti Player",
    icon = "server_icon",
    first_row_count = 4
}

function config.items(state)
    return state.version_page and (state.versions or {}) or (state.servers or {})
end

function config.heading(state)
    return state.version_page and "Versions" or "Servers"
end

function config.scroll_id(state)
    return state.version_page and "versions" or "servers"
end

function config.select_action(state)
    return state.version_page and "select_version" or "select"
end

function config.double_action(state)
    return state.version_page and "apply_version" or "join_index"
end

function config.subtitle(state, items)
    if state.version_page then
        return "Target " .. (state.target_protocol or "Unavailable") ..
            " - Server " .. (state.selected_server_protocol or "Global")
    end

    local saved = 0
    local lan = 0
    for _, item in ipairs(items) do
        if item.kind == "online" then
            saved = saved + 1
        elseif item.kind == "lan" then
            lan = lan + 1
        end
    end
    return tostring(saved) .. (saved == 1 and " saved server" or " saved servers") ..
        " - " .. tostring(lan) .. " LAN"
end

function config.status(state, count)
    if state.version_page then
        if count == 0 then
            return "No protocol versions available"
        end
        return nil
    end

    if not state.loaded then
        return "Loading servers..."
    end
    if count == 0 then
        return "No servers found"
    end
    return nil
end

function config.active(item)
    return item.selectable == true
end

function config.name(item)
    return item.title or ""
end

function config.detail(item)
    return item.detail or ""
end

function config.info(item)
    return item.status or ""
end

function config.name_color(item)
    return item.selectable and 0xFFFFFFFF or 0xFF687181
end

function config.info_color(item)
    return item.selected and 0xFFD7E6FF or 0xFFA8AFBE
end

function config.accent_border(item)
    return item.native == true
end

function config.actions(state)
    if state.version_page then
        return {
            {"Use", "apply_version", state.can_apply_version},
            {"Native", "native_version", true},
            {"Servers", "toggle_versions", true},
            {"Back", "back", true}
        }
    end

    return {
        {"Join", "join", state.can_join},
        {"Direct", "direct", true},
        {"Add", "add", true},
        {"Versions", "toggle_versions", true},
        {"Edit", "edit", state.can_edit},
        {"Delete", "delete", state.can_delete},
        {"Refresh", "refresh", true},
        {"Back", "back", true}
    }
end

function screen.render(state)
    common.render_collection_screen(state, config)
end

function screen.key_pressed(state, key)
    if key.escape then
        ui.action("back")
        return true
    end
    if key.selection then
        if state and state.version_page then
            ui.action("apply_version")
        else
            ui.action("join")
        end
        return true
    end
    if key.code == 294 then
        ui.action("refresh")
        return true
    end
    return false
end

return screen
