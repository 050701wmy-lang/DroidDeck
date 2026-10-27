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
  assert(svc_site_number(create, 3) == 56);
  assert(svc_site_number(create_at, 3) == 56);
  assert(svc_site_number(xstat, 4) == 79);
  assert(svc_site_number(overwritten, 2) == -1);
  assert(svc_site_number(loaded, 2) == -1);
  assert(svc_site_number(branch, 2) == -1);
  assert(svc_site_number(call, 2) == -1);
  assert(svc_site_number(shift, 2) == -1);
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
