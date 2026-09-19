package com.steamdeck.launcher.core;

/** One-argument callback, kept so the ported runtime classes read as they did in Bannerlator. */
public interface Callback<T> {
    void call(T value);
}
