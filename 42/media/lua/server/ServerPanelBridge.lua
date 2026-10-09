if not isServer() then return end
local missing = false
local failed = false
Events.OnTick.Add(function()
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
    end
end)
