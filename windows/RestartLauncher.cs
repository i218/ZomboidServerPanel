using System;
using System.ComponentModel;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;

// Restrict inherited handles to the private stdin/stdout/stderr channels.
// This process may inherit native server sockets itself, but exits as soon as
// the original server (or the helper) exits. The helper never receives them.
internal static class RestartLauncher {
    [StructLayout(LayoutKind.Sequential)] struct StartupInfo {
        public int cb; public IntPtr reserved, desktop, title;
        public int x,y,xSize,ySize,xChars,yChars,fill,flags;
        public short show, reservedBytes; public IntPtr reserved2,input,output,error;
    }
    [StructLayout(LayoutKind.Sequential)] struct StartupInfoEx {public StartupInfo info;public IntPtr attributes;}
    [StructLayout(LayoutKind.Sequential)] struct ProcessInfo {public IntPtr process,thread;public uint pid,tid;}
    [DllImport("kernel32.dll",SetLastError=true)] static extern IntPtr GetStdHandle(int value);
    [DllImport("kernel32.dll",SetLastError=true)] static extern bool SetHandleInformation(IntPtr handle,uint mask,uint flags);
    [DllImport("kernel32.dll",SetLastError=true)] static extern bool InitializeProcThreadAttributeList(IntPtr list,int count,int flags,ref IntPtr size);
    [DllImport("kernel32.dll",SetLastError=true)] static extern bool UpdateProcThreadAttribute(IntPtr list,uint flags,IntPtr attribute,IntPtr value,IntPtr size,IntPtr previous,IntPtr returned);
    [DllImport("kernel32.dll")] static extern void DeleteProcThreadAttributeList(IntPtr list);
    [DllImport("kernel32.dll",CharSet=CharSet.Unicode,SetLastError=true)] static extern bool CreateProcessW(string app,StringBuilder cmd,IntPtr processAttributes,IntPtr threadAttributes,bool inherit,uint flags,IntPtr environment,string directory,ref StartupInfoEx startup,out ProcessInfo process);
    [DllImport("kernel32.dll",SetLastError=true)] static extern IntPtr OpenProcess(uint access,bool inherit,uint pid);
    [DllImport("kernel32.dll",SetLastError=true)] static extern uint WaitForMultipleObjects(uint count,IntPtr[] handles,bool all,uint milliseconds);
    [DllImport("kernel32.dll")] static extern bool CloseHandle(IntPtr handle);
    static void Check(bool value) {if(!value) throw new Win32Exception(Marshal.GetLastWin32Error());}
    static string Quote(string value) {
        var result=new StringBuilder("\"");int slashes=0;
        foreach(char c in value) {
            if(c=='\\') {slashes++;continue;}
            if(c=='\"') {result.Append('\\',slashes*2+1);result.Append(c);}
            else {result.Append('\\',slashes);result.Append(c);}
            slashes=0;
        }
        result.Append('\\',slashes*2);return result.Append('"').ToString();
    }
    static int Main(string[] args) {
        IntPtr parent=IntPtr.Zero,list=IntPtr.Zero,handles=IntPtr.Zero;
        ProcessInfo child=new ProcessInfo();bool initialized=false;
        try {
            uint pid;
            if(args.Length!=3 || !UInt32.TryParse(args[0],out pid) || pid==0 || !Path.IsPathRooted(args[1]) || !File.Exists(args[1]) || !Path.IsPathRooted(args[2]) || !File.Exists(args[2])) throw new ArgumentException();
            parent=OpenProcess(0x00100000,false,pid);Check(parent!=IntPtr.Zero);
            var startup=new StartupInfoEx();startup.info.cb=Marshal.SizeOf(typeof(StartupInfoEx));startup.info.flags=0x100;
            startup.info.input=GetStdHandle(-10);startup.info.output=GetStdHandle(-11);startup.info.error=GetStdHandle(-12);
            IntPtr[] allowed={startup.info.input,startup.info.output,startup.info.error};
            handles=Marshal.AllocHGlobal(IntPtr.Size*allowed.Length);
            for(int i=0;i<allowed.Length;i++) {
                if(allowed[i]==IntPtr.Zero || allowed[i]==new IntPtr(-1)) throw new IOException();
                Check(SetHandleInformation(allowed[i],1,1));Marshal.WriteIntPtr(handles,i*IntPtr.Size,allowed[i]);
            }
            IntPtr size=IntPtr.Zero;InitializeProcThreadAttributeList(IntPtr.Zero,1,0,ref size);
            if(size==IntPtr.Zero) throw new Win32Exception(Marshal.GetLastWin32Error());
            list=Marshal.AllocHGlobal(size);Check(InitializeProcThreadAttributeList(list,1,0,ref size));initialized=true;
            Check(UpdateProcThreadAttribute(list,0,new IntPtr(0x00020002),handles,new IntPtr(IntPtr.Size*allowed.Length),IntPtr.Zero,IntPtr.Zero));startup.attributes=list;
            string cmd=Quote(args[1])+" -cp "+Quote(args[2])+" io.serverpanelbridge.RestartHelper";
            Check(CreateProcessW(args[1],new StringBuilder(cmd),IntPtr.Zero,IntPtr.Zero,true,0x08080000,IntPtr.Zero,Directory.GetCurrentDirectory(),ref startup,out child));
            CloseHandle(child.thread);child.thread=IntPtr.Zero;
            // Keep the private Java pipes alive until arming is delivered; release
            // inherited native sockets promptly when the old server exits.
            uint wait=WaitForMultipleObjects(2,new[]{parent,child.process},false,UInt32.MaxValue);
            if(wait==UInt32.MaxValue) throw new Win32Exception(Marshal.GetLastWin32Error());
            return 0;
        } catch(Exception error) {Console.Error.WriteLine("Windows restart launcher failed: "+error.GetType().Name);return 1;}
        finally {
            if(child.thread!=IntPtr.Zero) CloseHandle(child.thread);
            if(child.process!=IntPtr.Zero) CloseHandle(child.process);
            if(parent!=IntPtr.Zero) CloseHandle(parent);
            if(initialized) DeleteProcThreadAttributeList(list);
            if(list!=IntPtr.Zero) Marshal.FreeHGlobal(list);
            if(handles!=IntPtr.Zero) Marshal.FreeHGlobal(handles);
        }
    }
}
