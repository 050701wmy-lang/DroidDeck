package com.the412banner.steamdeck.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** An ordered NAME=VALUE set, in the shape {@code ProcessBuilder} and proot want it. */
public class EnvVars {
    private final Map<String, String> vars = new LinkedHashMap<>();

    public EnvVars put(String name, String value) {
        vars.put(name, value);
        return this;
    }

    public void putAll(EnvVars other) {
        if (other != null) vars.putAll(other.vars);
    }

    public String get(String name) { return vars.get(name); }

    public boolean has(String name) { return vars.containsKey(name); }

    public boolean isEmpty() { return vars.isEmpty(); }

    public String[] toStringArray() {
        String[] out = new String[vars.size()];
        int i = 0;
        for (Map.Entry<String, String> e : vars.entrySet()) out[i++] = e.getKey() + "=" + e.getValue();
        return out;
    }
}
