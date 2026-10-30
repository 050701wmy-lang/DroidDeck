#include <stdint.h>
#include <assert.h>
#include <stdio.h>
static struct { unsigned ticks; } g_perf;
static struct { uint64_t ticks, ticks_late_2ms; int64_t tick_late_ns, tick_late_max_ns; } g_pace;
static int64_t clock_ns, g_previous_frame_ns, g_last_vsync_ns, g_tick_vsync_ns;
static int64_t g_refresh_ns = 8333333;
static int g_output_refresh_mhz;
static int64_t g_scene_due_ns;
static int g_fps_limit, g_release_unpaced;
static int g_dirty = 1, renders, g_tick_held;
static int64_t g_hold_missed_ns, g_dirty_since_ns;
#define HOLD_RETRY_NS 1000000000LL
static int commit_renders_on(void) { return 0; }
static int64_t now_ns(void) { return clock_ns; }
static int vkp_apply_window_request(void) { return 0; }
static int vkp_effects_sync(void) { return 0; }
static int vkp_cardboard_pose_changed(void) { return 0; }
static int scene_waits_for_game(int64_t now) { (void)now; return 0; }
static void render_scene(void) { renders++; }
#include "on_vsync_under_test.inc"

int main(void) {
    for (int i = 1; i <= 120; i++) {
        int64_t frame = 1000000000LL + (int64_t)i * 8333333;
        clock_ns = frame + (i % 3) * 20000000LL;
        on_vsync(frame);
        on_vsync(frame);
    }
    assert(g_refresh_ns == 8333333);
    assert(renders == 120 && g_perf.ticks == 120);
    assert(g_pace.ticks == 120 && g_pace.ticks_late_2ms == 80);
    int64_t last = g_tick_vsync_ns;
    on_vsync(last - 1);
    assert(renders == 120 && g_tick_vsync_ns == last);
    clock_ns += 1000000000LL;
    on_vsync(last + 1000000000LL);
    assert(g_refresh_ns == 8333333);
    g_output_refresh_mhz = 165000;
    on_vsync(last + 1100000000LL);
    assert(g_refresh_ns == 6060606);
    g_output_refresh_mhz = 120000;
    on_vsync(last + 1125000000LL);
    assert(g_refresh_ns == 8333333);
    puts("PASS: compositor follows vsync timestamps despite dispatch stalls and duplicate callbacks");
    return 0;
}
