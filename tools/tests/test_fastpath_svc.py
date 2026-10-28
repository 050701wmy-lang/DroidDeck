from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


HEADER = Path(__file__).resolve().parents[1] / "proot/fastpath/svc-patch.h"


class FastPathSvcEncodingTest(unittest.TestCase):
    def test_instruction_decoding_and_branch_limits(self):
        if shutil.which("cc") is None:
            self.skipTest("cc is not available")
        source = r'''
#include <assert.h>
#include <stdint.h>
#include <stddef.h>
#define FP_SVC_ENCODINGS_ONLY
#include "HEADER"
int main(void) {
  assert(svc_number(0xd2800708) == 56);
  assert(svc_number(0x528009e8) == 79);
  assert(svc_number(0xd2800709) == -1);
  assert(svc_number(0xd2a00708) == -1);
  assert(svc_number(0xf2800708) == -1);
  assert(svc_number(0xaa0003e8) == -1);
  const uint32_t create[] = {0xd2800708, 0x29047fe0, 0x92800c60, 0xd4000001};
  const uint32_t create_at[] = {0xd2800708, 0x29047fe3, 0xb9404be3, 0xd4000001};
  const uint32_t xstat[] = {0xd28009e8, 0xaa0203e1, 0xaa0303e2, 0x93407c83, 0xd4000001};
  const uint32_t overwritten[] = {0xd2800708, 0xaa0003e8, 0xd4000001};
  const uint32_t loaded[] = {0xd2800708, 0xf94003e8, 0xd4000001};
  const uint32_t branch[] = {0xd2800708, 0x14000001, 0xd4000001};
  const uint32_t call[] = {0xd2800708, 0x94000001, 0xd4000001};
  const uint32_t shift[] = {0xd2800708, 0xd2a00708, 0xd4000001};
  const uint32_t robust[] = {0xd2800c68, 0xf9000260, 0xa9000261, 0xf9400260, 0x9100e260, 0xd4000001};
  const uint32_t writeback[] = {0xd2800708, 0xa9bf07e0, 0xd4000001};
  const uint32_t added[] = {0xd2800708, 0x91000408, 0xd4000001};
  assert(svc_site_number(create, 3) == 56);
  assert(svc_site_number(create_at, 3) == 56);
  assert(svc_site_number(xstat, 4) == 79);
  assert(svc_site_number(overwritten, 2) == -1);
  assert(svc_site_number(loaded, 2) == -1);
  assert(svc_site_number(branch, 2) == -1);
  assert(svc_site_number(call, 2) == -1);
  assert(svc_site_number(shift, 2) == -1);
  assert(svc_site_number(robust, 5) == 99);
  assert(svc_site_number(writeback, 2) == -1);
  assert(svc_site_number(added, 2) == -1);
  uint32_t out;
  uintptr_t pc = 0x10000000;
  assert(svc_branch(pc, pc + 4, &out) && out == 0x14000001);
  assert(svc_branch(pc, pc - 4, &out) && out == 0x17ffffff);
  assert(svc_branch(pc, pc + 0x7fffffc, &out) && out == 0x15ffffff);
  assert(svc_branch(pc, pc - 0x8000000, &out) && out == 0x16000000);
  assert(!svc_branch(pc, pc + 0x8000000, &out));
  assert(!svc_branch(pc, pc - 0x8000004, &out));
  assert(!svc_branch(pc, pc + 2, &out));
  return 0;
}
'''
        with tempfile.TemporaryDirectory() as directory:
            work = Path(directory)
            program = work / "check.c"
            program.write_text(source.replace("HEADER", HEADER.as_posix()))
            output = work / "check"
            subprocess.run(["cc", "-O2", "-Wall", "-Wextra", "-Werror", str(program), "-o", str(output)], check=True)
            subprocess.run([str(output)], check=True, timeout=10)

    def test_cache_revalidates_image_offsets_and_untrusted_files(self):
        if shutil.which("cc") is None:
            self.skipTest("cc is not available")
        source = r'''
#define _GNU_SOURCE
#include <assert.h>
#include <stdint.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <unistd.h>
#include <link.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#define FP_SVC_ENCODINGS_ONLY
#include "HEADER"
static int svc_android_api = 33;
static struct { uint32_t *site; } svc_plan[256];
static size_t svc_planned;
static int svc_supported(int nr) { return nr == 56 || nr == 79; }
#define sc(n,a,b,c,d,e) syscall(n,a,b,c,d,e)
static long fp_open(int d, const char *p, int f, mode_t m) { return openat(d,p,f,m); }
static long fp_rename(int d, const char *p, int e, const char *q, int f) { (void)f; return renameat(d,p,e,q); }
static long fp_unlink(int d, const char *p, int f) { return unlinkat(d,p,f); }
#include "CACHE"
static void store(const char *path, struct svc_cached_sites *table) {
  int fd = open(path, O_WRONLY | O_TRUNC);
  assert(fd >= 0 && write(fd, table, sizeof *table) == sizeof *table && close(fd) == 0);
}
int main(void) {
  uint32_t image[32] = {0xd2800708, 0xd4000001, 0xd28009e8, 0xd4000001};
  ElfW(Phdr) ph[] = {{.p_type=PT_LOAD, .p_flags=PF_R|PF_X, .p_memsz=sizeof image},
                    {.p_type=PT_NOTE, .p_vaddr=64, .p_memsz=24}};
  struct dl_phdr_info info = {.dlpi_addr=(uintptr_t)image, .dlpi_phdr=ph, .dlpi_phnum=2};
  ElfW(Nhdr) note = {.n_type=NT_GNU_BUILD_ID, .n_namesz=4, .n_descsz=8};
  memcpy((char *)image+64, &note, sizeof note);
  memcpy((char *)image+76, "GNU", 4);
  uint64_t id = getpid();
  memcpy((char *)image+80, &id, 8);
  char path[256], other[256];
  assert(svc_cache_name(&info, path, sizeof path));
  svc_android_api = 35;
  assert(svc_cache_name(&info, other, sizeof other) && strcmp(path, other));
  svc_android_api = 33;
  setenv("PROOT_FP_SVC_CACHE", "0", 1);
  assert(!svc_cache_name(&info, other, sizeof other));
  unsetenv("PROOT_FP_SVC_CACHE");
  ph[1].p_memsz = sizeof image;
  assert(!svc_cache_name(&info, other, sizeof other));
  ph[1].p_memsz = 24;
  svc_plan[0].site = image+1; svc_plan[1].site = image+3; svc_planned=2;
  svc_cache_write(&info, path, 0);
  svc_planned=0;
  assert(svc_cache_read(&info, path) && svc_planned==2 && svc_plan[1].site==image+3);
  struct svc_cached_sites valid, table;
  int fd = open(path, O_RDONLY);
  assert(fd >= 0 && read(fd, &valid, sizeof valid)==sizeof valid && close(fd)==0);
#define REJECT(change) do { table=valid; change; store(path,&table); svc_planned=0; \
  assert(!svc_cache_read(&info,path) && svc_planned==0); } while(0)
  REJECT(table.magic++);
  REJECT(table.api=35);
  REJECT(table.count=0);
  REJECT(table.count=257);
  REJECT(table.offset[0]=3);
  REJECT(table.offset[0]=sizeof image);
  REJECT(table.offset[0]=UINT64_MAX-3);
  REJECT(table.offset[1]=table.offset[0]);
  REJECT(table.offset[0]=12; table.offset[1]=4);
  store(path,&valid);
  image[1]=0x14000001;
  assert(!svc_cache_read(&info,path));
  image[1]=0xd4000001; image[0]=0xd2800008;
  assert(!svc_cache_read(&info,path));
  image[0]=0xd2800708;
  assert(chmod(path,0644)==0 && !svc_cache_read(&info,path));
  assert(chmod(path,0600)==0 && truncate(path,8)==0 && !svc_cache_read(&info,path));
  assert(unlink(path)==0 && symlink("/dev/zero",path)==0 && !svc_cache_read(&info,path));
  assert(unlink(path)==0 && mkdir(path,0700)==0 && !svc_cache_read(&info,path));
  assert(rmdir(path)==0);
  svc_planned=0;
  svc_cache_write(&info,path,0);
  assert(access(path,F_OK)<0);
  (void)svc_cache_hits; (void)svc_cache_misses;
  return 0;
}
'''
        with tempfile.TemporaryDirectory() as directory:
            work = Path(directory)
            program = work / "cache.c"
            program.write_text(source.replace('"HEADER"', '"' + HEADER.as_posix() + '"')
                              .replace('"CACHE"', '"' + HEADER.with_name("svc-cache.h").as_posix() + '"'))
            output = work / "cache"
            subprocess.run(["cc", "-O2", "-Wall", "-Wextra", "-Werror", str(program), "-o", str(output)], check=True)
            subprocess.run([str(output)], check=True, timeout=10)
