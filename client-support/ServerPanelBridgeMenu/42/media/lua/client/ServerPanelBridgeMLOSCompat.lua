-- Main-menu administrator support. Connecting players do not need this file.
if isServer() or isClient() then return end
local function own(id) return id == "ServerPanelBridge" or id == "\\ServerPanelBridge" end
local function install()
    local active = getActivatedMods()
    if not active:contains("ModLoadOrderSorter_b42") and not active:contains("\\ModLoadOrderSorter_b42") then return end
    local ok, utils = pcall(require, "OptionScreens/ModSelector/Refr_utils")
    if not ok or type(utils) ~= "table" or type(utils.getModsIDs) ~= "function" or type(utils.getWorkshopId) ~= "function" then
        print("[ServerPanelBridge] MLOS compatibility unavailable: unsupported API")
        return
    end
    if utils._serverPanelBridgeLocalCompat then return end
    local original = utils.getModsIDs
    utils.getModsIDs = function(self, items, ...)
        local filtered, localEntries, count = {}, {}, 0
        for i, row in ipairs(items) do
            local data = row.item or {}
            local id = data.modID or data.modId or ""
            local info = data.modInfo
            if own(id) and not info then
                local found, value = pcall(getModInfoByID, "ServerPanelBridge")
                if found then info = value end
            end
            local identified, workshopId, actualId = false, nil, nil
            if own(id) and info then
                local valid, value = pcall(function() return info:getId() end)
                if valid then actualId = value end
                identified, workshopId = pcall(self.getWorkshopId, self, info)
            end
            if own(id) and own(actualId) and identified and (workshopId == nil or workshopId == "" or tostring(workshopId) == "0") then
                localEntries[i] = true
                count = count + 1
            else
                table.insert(filtered, row)
            end
        end
        if count == 0 then return original(self, items, ...) end
        local ids, workshopIDs = original(self, filtered, ...)
        local retained, ordered = {}, {}
        for _, id in ipairs(ids) do retained[id] = (retained[id] or 0) + 1 end
        for i, row in ipairs(items) do
            local id = row.item.modID or row.item.modId or ""
            if localEntries[i] then table.insert(ordered, id)
            elseif (retained[id] or 0) > 0 then
                table.insert(ordered, id)
                retained[id] = retained[id] - 1
            end
        end
        return ordered, workshopIDs
    end
    utils._serverPanelBridgeLocalCompat = true
    print("[ServerPanelBridge] MLOS compatibility enabled: local mod retained without Workshop ID")
end
Events.OnMainMenuEnter.Add(install)
