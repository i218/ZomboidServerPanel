package io.serverpanelbridge;
import java.util.*;
/** Small strict parser for bounded panel responses; not a script evaluator. */
final class Json {
    private final String text; private int at;
    private Json(String text) { this.text = text; }
    static Object parse(String text) {
        Json p = new Json(text); Object value = p.value(0); p.space();
        if (p.at != text.length()) throw new IllegalArgumentException("Trailing JSON");
        return value;
    }
    private void space() { while (at < text.length() && " \t\r\n".indexOf(text.charAt(at)) >= 0) at++; }
    private char take() { if (at >= text.length()) throw new IllegalArgumentException("Truncated JSON"); return text.charAt(at++); }
    private Object value(int depth) {
        if (depth > 24) throw new IllegalArgumentException("JSON too deep");
        space(); char c = take();
        if (c == '"') return string();
        if (c == '[') {
            List<Object> list = new ArrayList<>(); space();
            if (at < text.length() && text.charAt(at) == ']') { at++; return list; }
            while (true) { list.add(value(depth+1)); space(); c=take(); if(c==']') return list; if(c!=',') throw new IllegalArgumentException("Invalid array"); }
        }
        if (c == '{') {
            Map<String,Object> map = new LinkedHashMap<>(); space();
            if (at < text.length() && text.charAt(at) == '}') { at++; return map; }
            while (true) {
                space(); if(take()!='"') throw new IllegalArgumentException("Invalid key");
                String key=string(); space(); if(take()!=':') throw new IllegalArgumentException("Invalid object");
                if(map.containsKey(key)) throw new IllegalArgumentException("Duplicate key");
                map.put(key,value(depth+1)); space(); c=take(); if(c=='}') return map;
                if(c!=',') throw new IllegalArgumentException("Invalid object");
            }
        }
        at--; int start=at;
        while(at<text.length() && ",]} \t\r\n".indexOf(text.charAt(at)) < 0) at++;
        String token=text.substring(start,at);
        if(token.equals("true")) return Boolean.TRUE;
        if(token.equals("false")) return Boolean.FALSE;
        if(token.equals("null")) return null;
        if(token.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) return new java.math.BigDecimal(token);
        throw new IllegalArgumentException("Invalid JSON token");
    }
    private String string() {
        StringBuilder out=new StringBuilder();
        while(true) {
            char c=take(); if(c=='"') return out.toString();
            if(c<32) throw new IllegalArgumentException("Control character");
            if(c=='\\') {
                c=take();
                switch(c) {
                    case '"': case '\\': case '/': out.append(c); break;
                    case 'b': out.append('\b'); break; case 'f': out.append('\f'); break;
                    case 'n': out.append('\n'); break; case 'r': out.append('\r'); break; case 't': out.append('\t'); break;
                    case 'u':
                        if(at+4>text.length()) throw new IllegalArgumentException("Invalid escape");
                        String hex=text.substring(at,at+4);
                        if(!hex.matches("[0-9a-fA-F]{4}")) throw new IllegalArgumentException("Invalid escape");
                        out.append((char)Integer.parseInt(hex,16)); at+=4; break;
                    default: throw new IllegalArgumentException("Invalid escape");
                }
            } else out.append(c);
        }
    }
}
