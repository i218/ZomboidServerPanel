if not isServer() then return end
local missing, failed, confirmed = false, false, false
local function updateBridge()
    if not ServerPanelBridge then
        if not missing then
            print("[ServerPanelBridge] Java bridge unavailable. Check ZombieBuddy agent loading and JAR approval.")
            missing = true
        end
        return
    end
    if failed then return end
    local ok, err = pcall(ServerPanelBridge.onTick)
    if not ok then
        failed = true
        print("[ServerPanelBridge] Tick bridge disabled: " .. tostring(err))
        return
    end
    if not confirmed and ServerPanelBridge.isClientOptionalReady then
        local ready, value = pcall(ServerPanelBridge.isClientOptionalReady)
        if ready and value then
            confirmed = true
            print("[ServerPanelBridge] Client optional mode confirmed: ServerPanelBridge removed from required mod list")
        end
    end
end
-- The first connection must not depend on an unpaused simulation tick.
Events.OnServerStarted.Add(updateBridge)
-- Keeps the server bridge and command dispatch alive with PauseEmpty=true.
Events.OnTickEvenPaused.Add(updateBridge)
