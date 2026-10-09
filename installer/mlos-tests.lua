local function check(value, label) assert(value, label); print("PASS " .. label) end
runHook()
local first = utils.getModsIDs
runHook()
check(first == utils.getModsIDs, "hook is idempotent")
local items = { row("A",info("A","11")), row("ServerPanelBridge",info("ServerPanelBridge",nil)), row("B",info("B","22")) }
local ids, workshops = utils:getModsIDs(items)
check(table.concat(ids,",") == "A,ServerPanelBridge,B" and table.concat(workshops,",") == "11,22", "local ID retained in order without fake workshop ID")
check(warnings == 0, "installed local bridge does not trigger missing warning")
local ids2, ws2 = utils:getModsIDs({row("\\ServerPanelBridge", info("ServerPanelBridge",""))})
check(ids2[1] == "\\ServerPanelBridge" and #ws2 == 0, "backslash ID and empty Workshop ID supported")
lookup = info("ServerPanelBridge",nil)
local fallback = utils:getModsIDs({row("ServerPanelBridge",nil)})
check(fallback[1] == "ServerPanelBridge", "installed metadata fallback")
lookup = nil
local before = warnings
local missing = utils:getModsIDs({row("ServerPanelBridge",nil),row("OtherMissing",nil)})
check(#missing == 0 and warnings == before + 2, "missing mods still handled by original MLOS")
local published, publishedIds = utils:getModsIDs({row("ServerPanelBridge",info("ServerPanelBridge","123"))})
check(published[1] == "ServerPanelBridge" and publishedIds[1] == "123", "published item follows original path")
local foreign = utils:getModsIDs({row("ServerPanelBridge",info("Other",nil))})
check(#foreign == 0, "foreign metadata not treated as installed bridge")
local prior = originalCalls
utils:getModsIDs({row("A",info("A","11"))})
check(originalCalls == prior + 1, "unrelated items delegated")
print("RESULT: 9 Lua assertions passed")
