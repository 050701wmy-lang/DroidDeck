#include <assert.h>
#include <stdint.h>
#include <stddef.h>
#include <stdio.h>
typedef int ASurfaceTransaction;
struct vkp_image { int unused; };
struct slot { struct vkp_image *img; };
struct layer { int shown, first_logged; uint64_t overlay_serial; struct slot slots[1]; void *sc; };
enum { SC_LAYER_OVERLAY, AHB_RGBA8, ASC_TRANSPARENCY_TRANSLUCENT, ASC_TRANSPARENCY_OPAQUE };
static struct layer overlay;
static struct vkp_image image;
static int blits, presents, transactions, x, fail_tx;
static struct layer *layer_of(int id) { (void)id; return &overlay; }
static int sc_layer_overlay_affordable(void) { return 1; }
static int ensure_sc(struct layer *l) { (void)l; return 0; }
static ASurfaceTransaction *create(void) { return fail_tx ? NULL : (ASurfaceTransaction *)1; }
static void apply(ASurfaceTransaction *tx) { assert(tx); transactions++; }
static void delete_tx(ASurfaceTransaction *tx) { assert(tx); }
static void transparency(ASurfaceTransaction *tx, void *sc, int t) { (void)tx; (void)sc; (void)t; }
static struct { ASurfaceTransaction *(*txCreate)(void); void (*txApply)(ASurfaceTransaction *);
    void (*txDelete)(ASurfaceTransaction *); void (*setBufferTransparency)(ASurfaceTransaction *, void *, int);
} api = {create, apply, delete_tx, transparency};
static void apply_geometry(ASurfaceTransaction *tx, struct layer *l, const int geo[8]) {
    (void)tx; (void)l; x = geo[4];
}
static int vkp_image_width(struct vkp_image *i) { (void)i; return 32; }
static int vkp_image_height(struct vkp_image *i) { (void)i; return 32; }
static int take_free_slot(struct layer *l, int w, int h, int fmt, int *fd) {
    (void)l; (void)w; (void)h; (void)fmt; *fd = -1; return 0;
}
static void log_drop(struct layer *l) { (void)l; }
static int vkp_blit_image(struct vkp_image *src, struct vkp_image *dst, int fd) {
    (void)src; (void)dst; (void)fd; blits++; return 0;
}
static int present_slot(struct layer *l, int idx, const int geo[8], void *color, int alpha) {
    (void)idx; (void)color; (void)alpha; presents++; l->shown = 1; x = geo[4]; return 0;
}
static void log_layer_count(void) {}
#include "overlay_under_test.inc"

int main(void) {
    int geo[8] = {0};
    assert(sc_layer_present_overlay(&image, geo, 1, 1) == 0 && blits == 1 && presents == 1);
    geo[4] = 20;
    assert(sc_layer_present_overlay(&image, geo, 1, 1) == 0 && blits == 1 && x == 20 && transactions == 1);
    assert(sc_layer_present_overlay(&image, geo, 1, 2) == 0 && blits == 2);
    overlay.shown = 0;
    assert(sc_layer_present_overlay(&image, geo, 1, 2) == 0 && blits == 3);
    assert(sc_layer_present_overlay(&image, geo, 1, 0) == 0 && blits == 4);
    assert(sc_layer_present_overlay(&image, geo, 1, 0) == 0 && blits == 5);
    assert(sc_layer_present_overlay(&image, geo, 1, 3) == 0 && blits == 6);
    fail_tx = 1;
    assert(sc_layer_present_overlay(&image, geo, 1, 3) == -1 && blits == 6);
    puts("PASS: unchanged overlay moves without a GPU copy, new or hidden content is copied");
    return 0;
}
