/* Exercise the production backdrop with mock Android buffers/transactions. */
#include "../../app/src/main/cpp/waylandcomp/src/sc_layer.c"
#include "../../app/src/main/cpp/waylandcomp/src/quest_device.h"
#include <assert.h>
#include <stdarg.h>
#include <fcntl.h>
#include <sys/eventfd.h>

struct AHardwareBuffer { unsigned char pixels[20 * 16 * 4]; };
static struct AHardwareBuffer buffer;
static int fail_alloc, fail_lock, fail_unlock, fail_tx;
static int allocs, locks, unlocks, buffers, applies, creates, releases, retirements;
static int visible, zorder, opacity;
static ARect destination;
static ANativeWindow *window = (ANativeWindow *)1;
ANativeWindow *vkp_window(void) { return window; }
void droiddeck_log(const char *kind, const char *fmt, ...) { (void)kind; (void)fmt; }
int AHardwareBuffer_allocate(const AHardwareBuffer_Desc *d, AHardwareBuffer **b) {
    assert(d->width == 16 && d->height == 16);
    assert(d->usage & AHARDWAREBUFFER_USAGE_CPU_WRITE_RARELY);
    allocs++;
    if (fail_alloc) return -1;
    memset(&buffer, 0xa5, sizeof(buffer)); *b = &buffer; return 0;
}
void AHardwareBuffer_describe(const AHardwareBuffer *b, AHardwareBuffer_Desc *d) {
    assert(b == &buffer); *d = (AHardwareBuffer_Desc){.width=16,.height=16,.stride=20};
}
int AHardwareBuffer_lock(AHardwareBuffer *b, uint64_t usage, int32_t fd, const ARect *r, void **p) {
    (void)usage; (void)fd; (void)r; assert(b == &buffer); locks++;
    if (fail_lock) return -1;
    *p = buffer.pixels; return 0;
}
int AHardwareBuffer_unlock(AHardwareBuffer *b, int32_t *f) {
    assert(b == &buffer && f == NULL); unlocks++; return fail_unlock ? -1 : 0;
}
void AHardwareBuffer_release(AHardwareBuffer *b) { assert(b == &buffer); releases++; }
static ASurfaceControl *create(ANativeWindow *w, const char *n) {
    assert(w == window && strcmp(n, "droiddeck_wayland_black") == 0); creates++;
    return (ASurfaceControl *)(uintptr_t)(100 + creates);
}
static ASurfaceTransaction *tx_create(void) { return fail_tx ? NULL : (ASurfaceTransaction *)2; }
static void tx_delete(ASurfaceTransaction *t) { assert(t == (ASurfaceTransaction *)2); }
static void tx_apply(ASurfaceTransaction *t) { (void)t; applies++; }
static void set_buffer(ASurfaceTransaction *t, ASurfaceControl *s, AHardwareBuffer *b, int fd) {
    (void)t; (void)s; assert(b == &buffer && fd == -1);
    /* No garbage, including padded row bytes, reaches the display. */
    for (unsigned i=0; i<sizeof(buffer.pixels); i++) assert(buffer.pixels[i] == 0);
    assert(unlocks > 0); buffers++;
}
static void visibility(ASurfaceTransaction *t, ASurfaceControl *s, int8_t v) { (void)t; (void)s; visible=v; }
static void z(ASurfaceTransaction *t, ASurfaceControl *s, int32_t v) { (void)t; (void)s; zorder=v; }
static void transparency(ASurfaceTransaction *t, ASurfaceControl *s, int8_t v) { (void)t; (void)s; opacity=v; }
static void geometry(ASurfaceTransaction *t, ASurfaceControl *s, const ARect *src, const ARect *dst, int32_t rotation) {
    (void)t; (void)s; assert(src->right==16 && src->bottom==16 && rotation==0); destination=*dst;
}
static void reparent(ASurfaceTransaction *t, ASurfaceControl *s, ASurfaceControl *parent) {
    (void)t; (void)s; assert(parent==NULL); retirements++;
}
static void release(ASurfaceControl *s) { assert(s); }
static int pending_present_fd = -1;
static int64_t fence_time;
static sc_complete_fn completion;
static void *completion_context;
void ahb_swapchain_layer_released(void *token, int fd) { (void)token; (void)fd; assert(0); }
int __wrap_ioctl(int fd, unsigned long request, ...) {
    (void)fd;
    if (request != SYNC_IOC_FILE_INFO) return -1;
    va_list args; va_start(args, request);
    struct sync_file_info *info = va_arg(args, struct sync_file_info *);
    va_end(args);
    info->status = fence_time > 0 ? 1 : fence_time < 0 ? -1 : 0;
    info->num_fences = 1;
    if (info->sync_fence_info) {
        struct sync_fence_info *fence = (void *)(uintptr_t)info->sync_fence_info;
        *fence = (struct sync_fence_info){.status = info->status, .timestamp_ns = fence_time};
    }
    return 0;
}
static int present_fence(ASurfaceTransactionStats *stats) {
    assert(stats); int fd = pending_present_fd; pending_present_fd = -1; return fd;
}
static int64_t latch_time(ASurfaceTransactionStats *stats) { assert(stats); return fence_time; }
static void on_complete_set(ASurfaceTransaction *tx, void *context, sc_complete_fn fn) {
    assert(tx); completion = fn; completion_context = context;
}
static void complete_frame(void) {
    completion(completion_context, (ASurfaceTransactionStats *)1);
    completion_context = NULL;
}
static void test_presentation_stats(void) {
    float stats[3];
    layers_init();
    api.presentFence = present_fence;
    api.setOnComplete = on_complete_set;
    reset_present_stats(1);
    sc_layer_set_source_frame(1);
    assert(add_complete_on((ASurfaceTransaction *)2, &g_layers[SC_LAYER_GAME], NULL, -1, NULL, 0, 1) == 0);
    assert(((struct complete_ctx *)completion_context)->present);
    int fd = pending_present_fd = eventfd(0, EFD_CLOEXEC);
    assert(fd >= 0);
    fence_time = 0;
    complete_frame();
    assert(g_present_fd_count == 1 && fcntl(fd, F_GETFD) >= 0);
    fence_time = now_ns();
    sc_layer_presentation_stats(stats);
    assert(g_present_fd_count == 0 && g_present_history.count == 1 && stats[2] == 1);
    assert(fcntl(fd, F_GETFD) == -1 && errno == EBADF);

    assert(add_complete_on((ASurfaceTransaction *)2, &g_layers[SC_LAYER_GAME], NULL, -1, NULL, 0, 1) == 0);
    assert(!((struct complete_ctx *)completion_context)->present);
    complete_frame();
    sc_layer_set_source_frame(2);
    assert(add_complete_on((ASurfaceTransaction *)2, &g_layers[SC_LAYER_GAME], NULL, -1, NULL, 0, 1) == 0);
    pending_present_fd = eventfd(0, EFD_CLOEXEC);
    complete_frame();
    assert(g_present_history.count == 1);

    sc_layer_set_source_frame(3);
    assert(add_complete_on((ASurfaceTransaction *)2, &g_layers[SC_LAYER_GAME], NULL, -1, NULL, 0, 1) == 0);
    reset_present_stats(0);
    complete_frame();
    sc_layer_presentation_stats(stats);
    assert(g_present_history.count == 0 && stats[2] == 0);

    reset_present_stats(1);
    assert(add_complete_on((ASurfaceTransaction *)2, &g_layers[SC_LAYER_OVERLAY], NULL, -1, NULL, 0, 1) == 0);
    assert(!((struct complete_ctx *)completion_context)->present);
    complete_frame();
    assert(add_complete_on((ASurfaceTransaction *)2, &g_layers[SC_LAYER_GAME], NULL, -1, NULL, 0, 1) == 0);
    complete_frame();
    sc_layer_presentation_stats(stats);
    assert(stats[2] == 0);
    sc_layer_set_source_frame(4);
    assert(add_complete_on((ASurfaceTransaction *)2, &g_layers[SC_LAYER_GAME], NULL, -1, NULL, 0, 1) == 0);
    pending_present_fd = eventfd(0, EFD_CLOEXEC);
    fence_time = now_ns();
    complete_frame();
    sc_layer_presentation_stats(stats);
    assert(stats[2] == 1 && g_present_history.count == 1);
    reset_present_stats(1);
    api.latchTime = latch_time;
    fence_time = now_ns();
    assert(add_complete_on((ASurfaceTransaction *)2, &g_layers[SC_LAYER_GAME], NULL, -1, NULL, 0, 1) == 0);
    complete_frame();
    sc_layer_presentation_stats(stats);
    assert(stats[2] == 2 && g_present_history.count == 1);
    sc_layer_set_source_frame(5);
    assert(add_complete_on((ASurfaceTransaction *)2, &g_layers[SC_LAYER_GAME], NULL, -1, NULL, 0, 1) == 0);
    complete_frame();
    assert(g_present_history.count == 1);
    api.latchTime = NULL;
    reset_present_stats(0);
}
static void reset(void) {
    base_hide(1); g_blank=NULL;
    fail_alloc=fail_lock=fail_unlock=fail_tx=0;
    allocs=locks=unlocks=buffers=applies=creates=releases=retirements=0;
    window=(ANativeWindow *)1;
}
int main(void) {
    assert(quest_device_matches("Quest 2", "", ""));
    assert(quest_device_matches("quest 3", "", ""));
    assert(quest_device_matches("Quest 3S", "", ""));
    assert(quest_device_matches("unknown", "hollywood", "Oculus"));
    assert(quest_device_matches("unknown", "eureka", "Meta"));
    assert(quest_device_matches("unknown", "panther", "Oculus"));
    assert(!quest_device_matches("Pixel", "panther", "Google"));
    assert(!quest_device_matches("Quest Pro", "seacliff", "Meta"));
    assert(!quest_device_matches("", "", ""));
    api.state=1; api.createFromWindow=create; api.txCreate=tx_create; api.txDelete=tx_delete;
    api.txApply=tx_apply; api.setBuffer=set_buffer; api.setVisibility=visibility;
    api.setZOrder=z; api.setBufferTransparency=transparency; api.setGeometry=geometry;
    api.reparent=reparent; api.release=release;
    reset();
    assert(sc_layer_base_black(1920,1080)==0);
    assert(visible==ASC_VISIBILITY_SHOW && zorder==0 && opacity==ASC_TRANSPARENCY_OPAQUE);
    assert(destination.right==1920 && destination.bottom==1080);
    assert(allocs==1 && locks==1 && buffers==1 && creates==1);
    assert(sc_layer_base_black(1920,1080)==0 && applies==1); /* retained, not redrawn */
    assert(sc_layer_base_black(1440,1080)==0 && destination.right==1440 && allocs==1);
    sc_layer_hide_base(); assert(!visible && !g_base_shown);
    assert(sc_layer_base_black(1440,1080)==0 && visible && allocs==1);
    base_hide(1); assert(!g_base_sc && !g_base_shown && retirements==1);
    window=(ANativeWindow *)3;
    assert(sc_layer_base_black(1280,720)==0 && creates==2 && allocs==1 && locks==1);
    reset(); fail_alloc=1;
    assert(sc_layer_base_black(1920,1080)==-1 && buffers==0 && locks==0);
    reset(); fail_lock=1;
    assert(sc_layer_base_black(1920,1080)==-1 && buffers==0 && releases==1 && g_blank==NULL);
    reset(); fail_unlock=1;
    assert(sc_layer_base_black(1920,1080)==-1 && buffers==0 && releases==1 && g_blank==NULL);
    reset(); fail_tx=1;
    assert(sc_layer_base_black(1920,1080)==-1 && buffers==0 && !g_base_shown);
    fail_tx=0; assert(sc_layer_base_black(1920,1080)==0 && creates==1 && locks==1);
    reset(); window=NULL;
    assert(sc_layer_base_black(1920,1080)==-1 && allocs==0);
    window=(ANativeWindow *)1;
    assert(sc_layer_base_black(0,1080)==-1 && allocs==0);
    test_presentation_stats();
    puts("PASS: Quest backdrop initialization, padding, reuse, resize, retire and failure recovery");
}
