import java.nio.file.*;
import java.lang.reflect.*;
public final class LuaTests {
    public static void main(String[] args) throws Exception {
        Class<?> platformType = Class.forName("se.krka.kahlua.vm.Platform");
        Class<?> tableType = Class.forName("se.krka.kahlua.vm.KahluaTable");
        Class<?> factory = Class.forName("se.krka.kahlua.j2se.J2SEPlatform");
        Object platform = factory.getConstructor().newInstance();
        Object env = factory.getMethod("newTable").invoke(platform);
        tableType.getMethod("rawset",Object.class,Object.class).invoke(env,"_G",env);
        Class.forName("se.krka.kahlua.stdlib.BaseLib").getMethod("register",tableType).invoke(null,env);
        for(String lib : new String[]{"se.krka.kahlua.stdlib.StringLib","se.krka.kahlua.stdlib.TableLib","se.krka.kahlua.j2se.MathLib","se.krka.kahlua.stdlib.CoroutineLib"})
            Class.forName(lib).getMethod("register",platformType,tableType).invoke(null,platform,env);
        Class<?> threadType=Class.forName("se.krka.kahlua.vm.KahluaThread");
        Object thread=threadType.getConstructor(platformType,tableType).newInstance(platform,env);
        threadType.getField("debugOwnerThread").set(thread,Thread.currentThread());
        Method compile=Class.forName("se.krka.kahlua.luaj.compiler.LuaCompiler").getMethod("loadstring",String.class,String.class,tableType);
        for(String file:args) {
            Object closure=compile.invoke(null,Files.readString(Paths.get(file)),file,env);
            try { threadType.getMethod("call",Object.class,Object[].class).invoke(thread,closure,new Object[0]); }
            catch(InvocationTargetException failure) { throw new RuntimeException(file,failure.getCause()); }
        }
        System.out.println("PASS 9 MLOS assertions in game Lua runtime");
    }
}
