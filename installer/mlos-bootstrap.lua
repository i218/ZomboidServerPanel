function isServer() return false end
function isClient() return false end
local callback
Events = { OnMainMenuEnter = { Add = function(fn) callback = fn end } }
function runHook() callback() end
function getActivatedMods() return {contains=function(_, id) return id == "ModLoadOrderSorter_b42" end} end
lookup = nil
function getModInfoByID(id) return lookup end
utils = {}
warnings = 0
originalCalls = 0
function utils:getWorkshopId(info) return info:getWorkshopID() end
function utils:getModsIDs(items)
    originalCalls = originalCalls + 1
    local ids, workshops = {}, {}
    for _, row in ipairs(items) do
        local info = row.item.modInfo
        local id = info and self:getWorkshopId(info)
        if id and id ~= "" and id ~= "0" then
            table.insert(ids, row.item.modID or row.item.modId)
            table.insert(workshops, id)
        else warnings = warnings + 1 end
    end
    return ids, workshops
end
function require(path) return utils end
function info(id, workshop) return {getId=function() return id end, getWorkshopID=function() return workshop end} end
function row(id, metadata) return {item={modID=id,modInfo=metadata}} end
