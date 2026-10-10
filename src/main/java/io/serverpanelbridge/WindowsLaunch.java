package io.serverpanelbridge;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Windows CRT quoting rules; fixed WMI query when ProcessHandle omits arguments. */
final class WindowsLaunch {
    private WindowsLaunch() {}
    static String[] capture(long pid,Path executable) throws Exception {
        Path powershell=Paths.get(System.getenv("SystemRoot"),"System32","WindowsPowerShell","v1.0","powershell.exe");
        if(!Files.isRegularFile(powershell)) throw new IOException("Windows process query unavailable");
        String script="$ErrorActionPreference='Stop';[Console]::OutputEncoding=New-Object Text.UTF8Encoding($false);"
            + "$p=Get-CimInstance Win32_Process -Filter 'ProcessId = "+pid+"';[Console]::Write($p.CommandLine)";
        Process query=new ProcessBuilder(powershell.toString(),"-NoProfile","-NonInteractive","-WindowStyle","Hidden","-Command",script)
            .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        ExecutorService reader=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"ServerPanelBridge-LaunchCapture");t.setDaemon(true);return t;});
        try {
            Future<byte[]> bytes=reader.submit(()->query.getInputStream().readNBytes(131073));
            byte[] captured=bytes.get(5,TimeUnit.SECONDS);
            if(captured.length>131072) throw new IOException("Windows command line too large");
            String raw=new String(captured,StandardCharsets.UTF_8);
            if(!query.waitFor(1,TimeUnit.SECONDS) || query.exitValue()!=0 || raw.length()>32767) throw new IOException("Windows process query failed");
            List<String> parts=parse(raw);
            if(parts.size()<2 || !Files.isSameFile(Paths.get(parts.get(0)),executable)) throw new IOException("Captured executable mismatch");
            return parts.subList(1,parts.size()).toArray(new String[0]);
        } finally {
            // Only the private read-only query child may be terminated on timeout.
            if(query.isAlive()) query.destroy();
            reader.shutdownNow();
        }
    }
    static List<String> parse(String raw) {
        if(raw.isBlank() || raw.indexOf('\0')>=0 || raw.indexOf('\n')>=0 || raw.indexOf('\r')>=0)
            throw new IllegalArgumentException("Invalid Windows command line");
        List<String> parts=new ArrayList<>();int pos=0;
        while(pos<raw.length()) {
            while(pos<raw.length() && (raw.charAt(pos)==' ' || raw.charAt(pos)=='\t')) pos++;
            if(pos==raw.length()) break;
            StringBuilder value=new StringBuilder();boolean quoted=false;boolean first=parts.isEmpty();
            while(pos<raw.length()) {
                char c=raw.charAt(pos);
                if(!quoted && (c==' ' || c=='\t')) break;
                if(first) {
                    if(c=='"') quoted=!quoted; else value.append(c);
                    pos++;continue;
                }
                int slashes=0;
                while(pos<raw.length() && raw.charAt(pos)=='\\') {slashes++;pos++;}
                if(pos<raw.length() && raw.charAt(pos)=='"') {
                    value.append("\\".repeat(slashes/2));
                    if(slashes%2==1) {value.append('"');pos++;}
                    else if(quoted && pos+1<raw.length() && raw.charAt(pos+1)=='"') {value.append('"');pos+=2;}
                    else {quoted=!quoted;pos++;}
                } else {
                    value.append("\\".repeat(slashes));
                    if(pos<raw.length()) {
                        c=raw.charAt(pos);
                        if(!quoted && (c==' ' || c=='\t')) break;
                        value.append(c);pos++;
                    }
                }
            }
            if(quoted) throw new IllegalArgumentException("Unclosed Windows quote");
            parts.add(value.toString());
        }
        return parts;
    }
}
