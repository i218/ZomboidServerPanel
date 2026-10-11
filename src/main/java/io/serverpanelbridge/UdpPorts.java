package io.serverpanelbridge;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
/** Conservative wildcard bind probe; never holds ports across the replacement launch. */
final class UdpPorts {
    private UdpPorts() {}
    static int[] gamePorts() throws Exception {
        Class<?> game=Class.forName("zombie.network.GameServer");
        int first=game.getField("defaultPort").getInt(null), second=game.getField("udpPort").getInt(null);
        if(first<1 || first>65535 || second<1 || second>65535) throw new IOException("Effective game UDP ports unavailable");
        return first==second?new int[]{first}:new int[]{first,second};
    }
    static boolean available(int[] ports) {
        for(int port:ports) try(DatagramSocket socket=new DatagramSocket(null)) {
            socket.setReuseAddress(false);socket.bind(new InetSocketAddress(port));
        } catch(IOException error) {return false;}
        return true;
    }
    static String owners(int[] ports) {
        if(!System.getProperty("os.name", "").startsWith("Windows")) return "owner PID unavailable on this platform";
        String numbers=Arrays.toString(ports).replace("[", "").replace("]", "");
        // Only validated numeric ports enter this fixed script. Never request command lines or credentials.
        String script="$ErrorActionPreference='Stop'; Get-NetUDPEndpoint -LocalPort "+numbers+" -ErrorAction SilentlyContinue | Select-Object -First 32 | ForEach-Object { [Console]::WriteLine(('port={0}, ownerPid={1}' -f $_.LocalPort,$_.OwningProcess)) }";
        Process query=null;
        try {
            query=new ProcessBuilder("powershell.exe","-NoProfile","-NonInteractive","-Command",script).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            query.getOutputStream().close();
            if(!query.waitFor(3,TimeUnit.SECONDS)) {query.destroyForcibly();return "owner PID query timed out";}
            byte[] output=query.getInputStream().readNBytes(4096);
            String text=new String(output,StandardCharsets.UTF_8).trim().replace('\r',' ').replace('\n',' ');
            return text.isEmpty()?"owner PID unavailable":text;
        } catch(Exception error) {return "owner PID query unavailable";}
        finally {if(query!=null && query.isAlive()) query.destroyForcibly();}
    }
}
