package com.steamdeck.launcher.gpu;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.steamdeck.launcher.core.FileUtils;
import com.steamdeck.launcher.core.TarZst;

import org.json.JSONObject;

import java.io.File;

/**
 * The Vulkan driver the in-app compositor runs on. It has to be Turnip: the system Adreno driver
 * does not implement VK_EXT_image_drm_format_modifier, so importing the dma-bufs gamescope hands
 * over fails and the session renders nothing. Two builds ship in the apk, one per Adreno
 * generation, and the GPU decides which is unpacked.
 *
 * <p>The guest's own Turnip is a different copy entirely — a glibc build inside the rootfs, found
 * through its ICD manifest. This one is the bionic build the app process loads through adrenotools.
 */
public final class TurnipDriver {
    private static final String TAG = "TurnipDriver";
    /** Adreno 7xx (8 Gen 2/3, e.g. the Pocket FIT's A750). */
    private static final String DRIVER_A7XX = "turnip25.1.0";
    /** Adreno 8xx (8 Elite, e.g. a Fold 8's A830). */
    private static final String DRIVER_A8XX = "turnip-sdk36";
    /** Overrides the pick, for a device we cannot reach: a7xx, a8xx or system. */
    private static final String OVERRIDE_FILE = "Download/steamdeck-driver";

    private final Context context;
    private final File contentDir;

    public TurnipDriver(Context context) {
        this.context = context;
        this.contentDir = new File(context.getFilesDir(), "graphics_driver");
    }

    /** Absolute directory holding the driver, with a trailing slash — adrenotools wants both. */
    public String driverPath(String driverId) {
        return new File(contentDir, driverId).getAbsolutePath() + "/";
    }

    public String libraryName(String driverId) {
        File meta = new File(new File(contentDir, driverId), "meta.json");
        String content = FileUtils.readString(meta);
        if (content == null) return null;
        try {
            return new JSONObject(content).optString("libraryName", null);
        } catch (Exception e) {
            Log.w(TAG, "meta.json for " + driverId, e);
            return null;
        }
    }

    /**
     * Unpacks the driver this device needs and returns its id, or null to fall back to the system
     * Vulkan loader (which means a black session on Adreno, but is better than refusing to start
     * on a GPU neither build covers).
     */
    public String install() {
        String id = choose();
        if (id == null) return null;
        File dir = new File(contentDir, id);
        if (!new File(dir, "meta.json").isFile()) {
            FileUtils.delete(dir);
            if (!TarZst.extractAsset(context, "graphics_driver/adrenotools-" + id + ".tzst", dir)) {
                Log.e(TAG, "could not unpack " + id);
                FileUtils.delete(dir);
                return null;
            }
        }
        String library = libraryName(id);
        if (library == null || !new File(dir, library).isFile()) {
            Log.e(TAG, id + " unpacked without " + library);
            return null;
        }
        Log.i(TAG, "graphics driver " + id + " (" + library + ")");
        return id;
    }

    private String choose() {
        File override = new File(Environment.getExternalStorageDirectory(), OVERRIDE_FILE);
        String forced = override.isFile() ? FileUtils.readString(override) : null;
        if (forced != null) {
            forced = forced.trim().toLowerCase(java.util.Locale.US);
            Log.i(TAG, "driver forced by " + override + ": " + forced);
            if (forced.startsWith("system")) return null;
            if (forced.startsWith("a8")) return DRIVER_A8XX;
            if (forced.startsWith("a7")) return DRIVER_A7XX;
        }
        String model = gpuModel();
        Log.i(TAG, "gpu model: " + (model == null ? "unknown" : model));
        if (model != null) {
            // "Adreno750", "adreno_830" — the generation is the first digit of the three.
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d)\\d\\d").matcher(model);
            if (m.find()) {
                char generation = m.group(1).charAt(0);
                if (generation >= '8') return DRIVER_A8XX;
                if (generation == '7') return DRIVER_A7XX;
            }
        }
        // Nothing to go on: Android 16 shipped with the 8 Elite, so treat a new device as 8xx.
        return android.os.Build.VERSION.SDK_INT >= 36 ? DRIVER_A8XX : DRIVER_A7XX;
    }

    /** KGSL names the GPU here, and this file is world-readable where /dev/kgsl-3d0 is not. */
    private static String gpuModel() {
        for (String path : new String[]{
                "/sys/class/kgsl/kgsl-3d0/gpu_model",
                "/sys/class/kgsl/kgsl-3d0/gpu_chipid"}) {
            String value = FileUtils.readString(new File(path));
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return null;
    }
}
