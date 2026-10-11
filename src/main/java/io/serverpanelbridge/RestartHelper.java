package io.serverpanelbridge;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** One relaunch after verified parent exit; observes the replacement without retrying or killing. */
public final class RestartHelper {
    private RestartHelper() {}
    static ProcessHandle parent(long pid,Instant started) {
        ProcessHandle value=ProcessHandle.of(pid).orElse(null);
        return value!=null && value.info().startInstant().filter(started::equals).isPresent()?value:null;
    }
    private static void event(String message) {System.err.println(Instant.now()+" "+message);}
    public static void main(String[] args) {
        String stage="reading launch specification";
        try {
            DataInputStream input=new DataInputStream(System.in);
            long pid=input.readLong();Instant started=Instant.parse(input.readUTF());int timeout=input.readInt();
            Path directory=Paths.get(input.readUTF()), log=Paths.get(input.readUTF());
            int portWait=input.readInt(), portCount=input.readInt();
            if(portWait<1 || portWait>120 || portCount<1 || portCount>2) throw new IOException("Invalid UDP specification");
            int[] ports=new int[portCount];for(int i=0;i<portCount;i++) {ports[i]=input.readInt();if(ports[i]<1 || ports[i]>65535) throw new IOException("Invalid UDP port");}
            List<String> command=new ArrayList<>();command.add(input.readUTF());int count=input.readInt();
            if(count<1 || count>1024 || timeout<1 || timeout>600) throw new IOException("Invalid restart specification");
            for(int i=0;i<count;i++) command.add(input.readUTF());
            stage="checking parent and output file";
            ProcessHandle old=parent(pid,started);
            if(old==null || !old.isAlive()) throw new IOException("Parent identity unavailable before arming");
            if(!Files.isDirectory(directory) || !Files.isRegularFile(Paths.get(command.get(0)))) throw new IOException("Launch paths unavailable");
            // Check output permissions before the original server can be shut down.
            try(OutputStream probe=Files.newOutputStream(log,StandardOpenOption.CREATE,StandardOpenOption.APPEND)) {probe.flush();}
            event("Restart helper ready; parentPid="+pid);
            System.out.write(1);System.out.flush();
            stage="waiting for arming";
            ExecutorService reader=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"Restart-ArmReader");t.setDaemon(true);return t;});
            int armed;
            try {armed=reader.submit(()->input.read()).get(60,TimeUnit.SECONDS);}
            catch(TimeoutException expired){event("Restart cancelled: not armed within 60 seconds");return;}
            finally {reader.shutdownNow();}
            if(armed!=1) {event("Restart cancelled: unarmed input closed or cancellation received");return;}
            event("Restart armed; waiting for parent exit");
            stage="waiting for parent exit";
            try {old.onExit().get(timeout,TimeUnit.SECONDS);}
            catch(TimeoutException expired){event("Restart cancelled: old server did not exit within timeout");return;}
            if(ProcessHandle.of(pid).filter(ProcessHandle::isAlive).isPresent()) {
                event("Restart cancelled: parent PID still occupied");return;
            }
            stage="waiting for UDP ports";
            long portDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(portWait);
            boolean reported=false;
            while(!UdpPorts.available(ports)) {
                if(!reported) {event("Waiting for UDP ports "+Arrays.toString(ports)+"; "+UdpPorts.owners(ports));reported=true;}
                if(System.nanoTime()>=portDeadline) {event("Restart cancelled: UDP ports unavailable; "+UdpPorts.owners(ports));return;}
                Thread.sleep(250);
            }
            event("UDP ports available: "+Arrays.toString(ports));
            stage="launching replacement";
            Process replacement=new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile())).start();
            event("Replacement server launched; pid="+replacement.pid()+"; startup success is not yet confirmed");
            replacement.getOutputStream().close();
            stage="observing replacement";
            // Loading hundreds of mods may take minutes: liveness is never reported as game readiness.
            if(!replacement.waitFor(10,TimeUnit.SECONDS)) event("Replacement process still running after 10 seconds; this does not confirm server readiness");
            int exit=replacement.waitFor();
            if(!UdpPorts.available(ports)) event("UDP ports unavailable after replacement exit; "+UdpPorts.owners(ports));
            event("Replacement server exited: pid="+replacement.pid()+"; exitCode="+exit+" (0x"+Integer.toHexString(exit)+"); inspect restart-console.log and game/native crash logs");
        } catch(Exception error) {
            // Exception messages and launch arguments may contain credentials: only stage and type are logged.
            event("Restart helper failed during "+stage+": "+error.getClass().getSimpleName());
        }
    }
}
