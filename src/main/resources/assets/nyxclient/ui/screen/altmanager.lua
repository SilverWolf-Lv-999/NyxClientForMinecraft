local common = include("nyxclient:ui/screen/common.lua")
local screen = {}

local config = {
    title = "Alt Manager",
    heading = "Accounts",
    scroll_id = "accounts",
    icon = "account_icon",
    select_action = "select",
    double_action = "login_index",
    first_row_count = 3
}

function config.items(state)
    return state.accounts or {}
end

function config.subtitle(state, items)
    local value = #items == 1 and "1 saved account" or tostring(#items) .. " saved accounts"
    if state.current_name and state.current_name ~= "" then
        value = value .. " - Current: " .. state.current_name
    end
    return value
end

function config.status(state, count)
    if not state.loaded then
        return "Loading accounts..."
    end
    if count == 0 then
        return "No accounts found"
    end
    return nil
end

function config.active(_)
    return true
end

function config.accent_border(item)
    return item.current == true
end

function config.name(item)
    return item.name or ""
end

function config.detail(item)
    return item.type_line or ""
end

function config.info(item)
    return item.info_line or ""
end

function config.name_color(_)
    return 0xFFFFFFFF
end

function config.info_color(item)
    return item.current and 0xFFD7E6FF or 0xFFA8AFBE
end

function config.actions(state)
    return {
        {state.login_label or "Login", "login", state.can_login},
        {"Add", "add", true},
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
        ui.action("login")
        return true
    end
    if key.code == 294 then
        ui.action("refresh")
        return true
    end
    return false
end

return screen
