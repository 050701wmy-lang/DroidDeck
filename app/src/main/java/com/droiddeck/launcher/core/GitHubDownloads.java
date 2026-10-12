package com.droiddeck.launcher.core;

import android.content.Context;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.function.Consumer;

/** Only public GitHub release files and raw resources go through the chosen accelerator. */
public final class GitHubDownloads {
    public static final String DEFAULT = "https://ghfast.top/";
    private static volatile String prefix = "";
    private GitHubDownloads() {}

    public static void init(Context context) {
        prefix = context.getSharedPreferences("github-downloads", Context.MODE_PRIVATE)
                .getString("prefix", DEFAULT);
    }

    public static String current() { return prefix; }

    public static String normalize(String value) {
        String text = value.trim();
        if (text.isEmpty()) return "";
        URI uri = URI.create(text);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                || uri.getRawFragment() != null) throw new IllegalArgumentException("HTTPS prefix required");
        return text.endsWith("/") ? text : text + "/";
    }

    public static void save(Context context, String value) {
        String normalized = normalize(value);
        context.getSharedPreferences("github-downloads", Context.MODE_PRIVATE).edit()
                .putString("prefix", normalized).apply();
        prefix = normalized;
    }

    public static String accelerated(String original) {
        String selected = prefix;
        if (selected.isEmpty()) return original;
        try {
            URI uri = URI.create(original);
            String host = uri.getHost();
            String path = uri.getPath();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) return original;
            if ("raw.githubusercontent.com".equalsIgnoreCase(host)
                    || ("github.com".equalsIgnoreCase(host) && path != null
                        && path.matches("/[^/]+/[^/]+/releases/download/.+"))) {
                return selected + original;
            }
        } catch (IllegalArgumentException ignored) {}
        return original;
    }

    /** Configure headers before connecting, including Range; a failed accelerator falls back. */
    public static HttpURLConnection open(String original, Consumer<HttpURLConnection> configure) throws IOException {
        String url = accelerated(original);
        HttpURLConnection connection = null;
        if (!url.equals(original)) {
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                configure.accept(connection);
                int code = connection.getResponseCode();
                String type = connection.getContentType();
                if (code / 100 == 2 && (type == null || !type.toLowerCase(java.util.Locale.ROOT).contains("text/html"))) return connection;
            } catch (IOException ignored) {
                // Preserve the original download's error reporting when both routes fail.
            }
            if (connection != null) connection.disconnect();
        }
        connection = (HttpURLConnection) new URL(original).openConnection();
        configure.accept(connection);
        return connection;
    }
}
