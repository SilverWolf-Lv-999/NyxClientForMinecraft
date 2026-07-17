local common = include("nyxclient:ui/screen/common.lua")
local screen = {}

local config = {
    title = "Muti Player",
    heading = "Servers",
    scroll_id = "servers",
    icon = "server_icon",
    select_action = "select",
    double_action = "join_index",
    first_row_count = 4
}

function config.items(state)
    return state.servers or {}
end

function config.subtitle(_, items)
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

function config.actions(state)
    return {
        {"Join", "join", state.can_join},
        {"Direct", "direct", true},
        {"Add", "add", true},
        {"Edit", "edit", state.can_edit},
        {"Delete", "delete", state.can_delete},
        {"Refresh", "refresh", true},
        {"Back", "back", true}
    }
end

function screen.render(state)
    common.render_collection_screen(state, config)
end

function screen.key_pressed(_, key)
    if key.escape then
        ui.action("back")
        return true
    end
    if key.selection then
        ui.action("join")
        return true
    end
    if key.code == 294 then
        ui.action("refresh")
        return true
    end
    return false
end

return screen
