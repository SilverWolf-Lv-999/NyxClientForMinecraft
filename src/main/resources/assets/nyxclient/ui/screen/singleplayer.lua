local common = include("nyxclient:ui/screen/common.lua")
local screen = {}

local config = {
    title = "Single Player",
    heading = "Worlds",
    scroll_id = "worlds",
    icon = "world_icon",
    select_action = "select",
    double_action = "join_index",
    first_row_count = 4
}

function config.items(state)
    return state.worlds or {}
end

function config.subtitle(_, items)
    return #items == 1 and "1 saved world" or tostring(#items) .. " saved worlds"
end

function config.status(state, count)
    if state.loading then
        return "Loading worlds..."
    end
    if state.load_error and state.load_error ~= "" then
        return state.load_error
    end
    if count == 0 then
        return "No worlds found"
    end
    return nil
end

function config.active(item)
    return item.active == true
end

function config.name(item)
    return item.name or ""
end

function config.detail(item)
    return item.detail or ""
end

function config.info(item)
    return item.info or ""
end

function config.name_color(item)
    return item.active and 0xFFFFFFFF or 0xFF687181
end

function config.info_color(item)
    return item.selected and 0xFFD7E6FF or 0xFFA8AFBE
end

function config.actions(state)
    return {
        {state.select_label or "Select", "join", state.can_join},
        {"Create", "create", true},
        {"Edit", "edit", state.can_edit},
        {"Delete", "delete", state.can_delete},
        {"Backup", "backup", state.can_backup},
        {"Recreate", "recreate", state.can_recreate},
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
    return false
end

return screen
