package com.steamdeck.launcher.audio;

import android.content.Context;
import android.os.Process;
import android.util.Log;

import com.steamdeck.launcher.core.FileUtils;
import com.steamdeck.launcher.core.EnvironmentComponent;
import com.steamdeck.launcher.core.ProcessHelper;
import com.steamdeck.launcher.core.TarZst;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;

/**
 * PulseAudio 13.0 running in the app process, with Android's AAudio as its sink, so the guest's
 * libpulse clients (Steam and everything it launches) have a server to talk to. The socket lives
 * in the app's files directory and is bound into the session at its own path, so PULSE_SERVER
 * needs no translating.
 *
 * <p>Ported down from Bannerlator's component: no sink suspend/resume and no route-change
 * recreate — those need the pasink native client, and a session here is a foreground activity
 * that does not background the way a game container does.
 */
public class PulseAudioComponent extends EnvironmentComponent {
    private static final String TAG = "PulseAudio";
    /** Where the guest reaches the daemon; the session exports PULSE_SERVER=unix:<this>. */
    public static final String SOCKET_NAME = "PS0";

    private final File workingDir;
    private int pid = -1;

    public PulseAudioComponent(Context context) {
        this.workingDir = new File(context.getFilesDir(), "pulseaudio");
    }

    public File socket() {
        return new File(workingDir, SOCKET_NAME);
    }

    @Override
    public void start() {
        stop();
        if (!workingDir.isDirectory()) {
            //noinspection ResultOfMethodCallIgnored
            workingDir.mkdirs();
            FileUtils.chmod(workingDir, 0771);
        }
        // The loadable modules (module-aaudio-sink and the native protocol) ride in the apk; the
        // daemon and its libraries come from the native library directory, the one place an app
        // may execute a file from.
        if (!new File(workingDir, "modules/arm64/module-aaudio-sink.so").isFile()) {
            TarZst.extractAsset(context, "pulseaudio.tzst", workingDir);
        }
        copyFromLibraryDir();

        //noinspection ResultOfMethodCallIgnored
        socket().delete();
        FileUtils.writeString(new File(workingDir, "default.pa"), String.join("\n",
                "load-module module-native-protocol-unix auth-anonymous=1 auth-cookie-enabled=0 socket=\""
                        + socket().getAbsolutePath() + "\"",
                // volume=1.0 is not optional: with no volume argument module-aaudio-sink defaults
                // the sink to 0% and the session plays silence.
                "load-module module-aaudio-sink performance_mode=1 adaptive=1 volume=1.0",
                "set-default-sink AAudioSink"));

        File modules = new File(workingDir, "modules/arm64");
        ArrayList<String> env = new ArrayList<>();
        env.add("LD_LIBRARY_PATH=/system/lib64:" + modules + ":" + workingDir.getAbsolutePath());
        env.add("HOME=" + workingDir);
        env.add("TMPDIR=" + workingDir);

        String command = workingDir.getAbsolutePath() + "/libpulseaudio.so"
                + " --system=false --disable-shm=true --fail=false"
                + " -n --file=default.pa --daemonize=false --use-pid-file=false --exit-idle-time=-1";
        pid = ProcessHelper.exec(command, env.toArray(new String[0]), workingDir, null,
                line -> Log.i(TAG, line));
    }

    @Override
    public void stop() {
        if (pid != -1) {
            Process.killProcess(pid);
            pid = -1;
        }
    }

    private void copyFromLibraryDir() {
        String[] libs = {"libltdl.so", "libpulseaudio.so", "libpulse.so",
                "libpulsecommon-13.0.so", "libpulsecore-13.0.so", "libsndfile.so", "libffi.so"};
        ClassLoader loader = PulseAudioComponent.class.getClassLoader();
        for (String lib : libs) {
            URL resource = loader != null ? loader.getResource("lib/arm64-v8a/" + lib) : null;
            if (resource == null) {
                Log.w(TAG, lib + " missing from the apk");
                continue;
            }
            File destination = new File(workingDir, lib);
            try (InputStream in = resource.openStream()) {
                Files.copy(in, destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
                FileUtils.chmod(destination, 0771);
            } catch (Exception e) {
                Log.w(TAG, "copy " + lib, e);
            }
        }
    }
}
