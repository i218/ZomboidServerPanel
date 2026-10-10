package io.serverpanelbridge;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** Separate JVM, one launch after verified parent exit. Never stops or kills a process. */
public final class RestartHelper {
    private RestartHelper() {}
    static ProcessHandle parent(long pid,Instant started) {
        ProcessHandle value=ProcessHandle.of(pid).orElse(null);
        return value!=null && value.info().startInstant().filter(started::equals).isPresent()?value:null;
    }
    public static void main(String[] args) {
        try {
            DataInputStream input=new DataInputStream(System.in);
            long pid=input.readLong();Instant started=Instant.parse(input.readUTF());int timeout=input.readInt();
            Path directory=Paths.get(input.readUTF()), log=Paths.get(input.readUTF());
            List<String> command=new ArrayList<>();command.add(input.readUTF());int count=input.readInt();
            if(count<1 || count>1024 || timeout<1 || timeout>600) throw new IOException("Invalid restart specification");
            for(int i=0;i<count;i++) command.add(input.readUTF());
            ProcessHandle old=parent(pid,started);
            if(old==null || !old.isAlive()) throw new IOException("Parent identity unavailable before arming");
            if(!Files.isDirectory(directory) || !Files.isRegularFile(Paths.get(command.get(0)))) throw new IOException("Launch paths unavailable");
            System.out.write(1);System.out.flush();
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(60);
            while(input.available()==0) {
                if(!old.isAlive() || System.nanoTime()>=deadline) return;
                Thread.sleep(50);
            }
            if(input.read()!=1) return;
            try {old.onExit().get(timeout,TimeUnit.SECONDS);}
            catch(TimeoutException expired){System.err.println("Restart cancelled: old server did not exit within timeout");return;}
            // A reused PID must also finish before launching; no overlapping server processes.
            if(ProcessHandle.of(pid).filter(ProcessHandle::isAlive).isPresent()) {
                System.err.println("Restart cancelled: parent PID still occupied");return;
            }
            new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile())).start().getOutputStream().close();
            System.err.println("Replacement server launched; startup success is not yet confirmed");
        } catch(Exception error) {
            // Do not print argument values or exception messages that may contain secrets.
            System.err.println("Restart helper failed: "+error.getClass().getSimpleName());
        }
    }
}
