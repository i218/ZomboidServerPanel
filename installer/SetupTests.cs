using System;
using System.IO;
using System.Text;
using System.Security.Cryptography;
class SetupTests {
    static int passed;
    static void Check(bool condition,string text) { if(!condition) throw new Exception(text); passed++; Console.WriteLine("PASS "+text); }
    static void Fails(Action action) { try { action(); } catch { return; } throw new Exception("Expected failure"); }
    static void Main() {
        string root=Path.Combine(Path.GetTempPath(),"ServerPanelBridge-tests-"+Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(root);
        string both=Path.Combine(root,"both");
        Directory.CreateDirectory(Path.Combine(both,"Server")); Directory.CreateDirectory(Path.Combine(both,"mods"));
        string ini=Path.Combine(both,"Server","test1.ini");
        string original="# сохранено\r\nMods=\\Other;\\ZombieBuddy\r\nWorkshopItems=3619862853;12345\r\nPublicName=Тест\r\n";
        File.WriteAllText(ini,original,new UTF8Encoding(true));
        string profile=Path.Combine(both,"mods","default.txt");
        File.WriteAllText(profile,"VERSION = 1,\nmods\n{\n    mod = Other,\n}\nmaps\n{\n    map = CustomMap,\n}\n");
        Setup.Install(both,"test1",true,true);
        string jar=Path.Combine(both,"mods","ServerPanelBridge","42","media","java","ServerPanelBridge.jar");
        Check(File.Exists(jar) && new FileInfo(jar).Length>1000,"embedded JAR deployed");
        Check(File.ReadAllText(ini)==original.Replace("Mods=\\Other;\\ZombieBuddy","Mods=\\Other;\\ZombieBuddy;\\ServerPanelBridge"),"preserve INI settings and IDs");
        Check(File.Exists(Path.Combine(both,"mods","ServerPanelBridgeMenu","42","media","lua","client","ServerPanelBridgeMLOSCompat.lua")) && File.ReadAllText(profile).Contains("mod = ServerPanelBridgeMenu"),"menu component installed and enabled automatically");
        Check(!Directory.Exists(Path.Combine(both,"mods","ServerPanelBridge","42","media","lua","client")) && !File.ReadAllText(ini).Contains("ServerPanelBridgeMenu"),"menu Lua excluded from core mod and server INI");
        byte[] bytes=File.ReadAllBytes(ini); Check(bytes[0]==239 && bytes[1]==187 && bytes[2]==191,"preserve UTF-8 BOM");
        Check(File.ReadAllText(profile).Contains("mod = Other") && File.ReadAllText(profile).Contains("mod = ServerPanelBridge") && File.ReadAllText(profile).Contains("map = CustomMap"),"client profile preserves mods and maps");
        string config=Path.Combine(both,"ServerPanelBridge","test1","ServerPanelBridge.properties");
        File.WriteAllText(config,"enabled=false\nhostName=Мой сервер\n");
        string installed=File.ReadAllText(ini), enabled=File.ReadAllText(profile);
        File.SetAttributes(jar,FileAttributes.ReadOnly);
        Setup.Install(both,"test1",true,true);
        Check(File.ReadAllText(config)=="enabled=false\nhostName=Мой сервер\n","runtime config preserved");
        Check(File.ReadAllText(ini)==installed && File.ReadAllText(profile)==enabled,"idempotent repeat install");
        Check((File.GetAttributes(jar)&FileAttributes.ReadOnly)!=0,"read-only file updated");
        Check(Directory.GetFiles(Path.Combine(both,"ServerPanelBridge-installer-backups"),"*",SearchOption.AllDirectories).Length>0,"backups retained");
        string server=Path.Combine(root,"server");
        Directory.CreateDirectory(Path.Combine(server,"Server")); File.WriteAllText(Path.Combine(server,"Server","srv.ini"),"Mods=Other\n");
        Setup.Install(server,"srv",true,false);
        Check(!File.Exists(Path.Combine(server,"mods","default.txt")),"server mode leaves client profile");
        Check(!Directory.Exists(Path.Combine(server,"mods","ServerPanelBridgeMenu")),"server-only mode excludes menu component");
        string client=Path.Combine(root,"client"); Setup.Install(client,"",false,true);
        Check(File.ReadAllText(Path.Combine(client,"mods","default.txt")).Contains("ServerPanelBridge") && !Directory.Exists(Path.Combine(client,"ServerPanelBridge")),"client mode creates no runtime config");
        Check(Setup.EnableServer("Mods=Other\n")=="Mods=Other;ZombieBuddy;ServerPanelBridge\n","plain ID syntax preserved");
        Fails(()=>Setup.EnableServer("Mods=A\nMods=B\n"));
        Fails(()=>Setup.Install(both,"../escape",true,false)); Fails(()=>Setup.Install(both,"missing",true,true));
        Check(File.ReadAllText(ini)==installed,"invalid selections leave INI unchanged");
        string foreign=Path.Combine(root,"foreign"); Directory.CreateDirectory(Path.Combine(foreign,"mods","ServerPanelBridge"));
        File.WriteAllText(Path.Combine(foreign,"mods","ServerPanelBridge","mine.txt"),"keep");
        Fails(()=>Setup.Install(foreign,"",false,true));
        Check(File.ReadAllText(Path.Combine(foreign,"mods","ServerPanelBridge","mine.txt"))=="keep","unknown directory protected");
        string before=File.ReadAllText(profile);
        using(var locked=new FileStream(profile,FileMode.Open,FileAccess.Read,FileShare.Read)) Fails(()=>Setup.Install(both,"test1",true,true));
        Check(File.ReadAllText(ini)==installed && File.ReadAllText(profile)==before,"locked profile rolls back INI");
        File.WriteAllText(ini,"Mods=\\Other;\\ZombieBuddy\r\n");
        File.WriteAllText(profile,"VERSION = 1,\nmods\n{\n    mod = Other,\n}\nmaps\n{\n}\n");
        string oldIni=File.ReadAllText(ini), oldProfile=File.ReadAllText(profile);
        using(var locked=new FileStream(jar,FileMode.Open,FileAccess.Read,FileShare.Read)) Fails(()=>Setup.Install(both,"test1",true,true));
        Check(File.ReadAllText(ini)==oldIni && File.ReadAllText(profile)==oldProfile,"late locked JAR restores changed server and client settings");
        using(var hash=SHA256.Create()) {
            string first=BitConverter.ToString(hash.ComputeHash(File.ReadAllBytes(jar)));
            Setup.Install(both,"test1",true,false);
            Check(first==BitConverter.ToString(hash.ComputeHash(File.ReadAllBytes(jar))),"repeat JAR integrity");
        }
        Console.WriteLine("RESULT: "+passed+" passed, 0 failed. Fixtures: "+root);
    }
}
