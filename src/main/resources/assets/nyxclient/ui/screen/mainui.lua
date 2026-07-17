local common = include("nyxclient:ui/screen/common.lua")
local screen = {}

function screen.render(state)
    ui.shared_background()
    ui.shared_user_card(0, 1)

    local panel = common.center_panel(state, 301, 175, 312, 180)
    common.panel(panel)
    common.draw_title("Nyx Client", state.width * 0.5, math.max(8, panel.y - 44), 26, 1)

    local labels = {"Single Player", "Muti Player", "Alt Manager", "Option", "Exit"}
    local actions = {"singleplayer", "multiplayer", "alt_manager", "options", "exit"}
    local button_height = common.clamp(panel.height * 0.11, 28, 34)
    local gap = common.clamp(panel.height * 0.032, 7, 10)
    local inset = common.clamp(panel.width * 0.13, 18, 32)
    local button_width = panel.width - inset * 2
    local total_height = #labels * button_height + (#labels - 1) * gap
    local x = panel.x + inset
    local y = panel.y + (panel.height - total_height) * 0.5

    for index, label in ipairs(labels) do
        local active = actions[index] ~= "multiplayer" or state.multiplayer_allowed
        common.animated_button("main:" .. actions[index], label, x, y, button_width, button_height,
            actions[index], nil, active, true, 1, 1, 12, state.frame_seconds)
        y = y + button_height + gap
    end

    ui.background_selector()
end

return screen
