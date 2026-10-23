"""Reuse reviewed preload snapshots; changed snapshots must prove their main-build origin."""
import argparse
import gzip
import hashlib
import io
import json
import os
import posixpath
import re
from pathlib import Path
import subprocess
import tarfile
import urllib.error
import urllib.request
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[2]
REPO = 'Droid-Deck/DroidDeck'
LOCK = 'tools/components/preloads.lock.json'
COMMON = ['app/src/main/cpp/fakeinput_steam.cpp', 'app/src/main/cpp/adrenotools/src/hook/kgsl.h',
          'app/src/main/cpp/venc/venc_proto.h', 'tools/linuxfs/preload',
          'tools/quest/kgsl_compat.c', 'tools/quest/kgsl_compat.h']
INPUTS = {
    'arm64': COMMON + ['tools/proot/fastpath', 'tools/linuxfs/ssbs', 'tools/linuxfs/clipboard'],
    'x86': COMMON + ['tools/linuxfs/fex', 'tools/linuxfs/build-x86-preloads.sh', 'tools/linuxfs/debian-image.env'],
}
OUTPUTS = {
    'arm64': ['libfakeinput.so', 'libblsession.so', 'libblfastpath.so', 'libblaudit.so', 'libssbs.so',
              'usr/local/bin/droiddeck-clipboard'],
    'x86': [f'{arch}/{name}.so' for arch in ('x86_64', 'i386')
            for name in ('libfakeinput', 'libblsession', 'libfaultreport')]
           + ['x86_64/libwebkitinput.so', 'x86_64/libvulkan-thunk.so'],
}
FILES = set(sum(OUTPUTS.values(), []))


def sha(data):
    return hashlib.sha256(data).hexdigest()


def run(*args, root=ROOT):
    return subprocess.check_output(args, cwd=root)


def api(path):
    return json.loads(run('gh', 'api', 'repos/' + REPO + '/' + path))


def source_files(group, root=ROOT, commit=None):
    if commit:
        paths = run('git', 'ls-tree', '-r', '--name-only', commit, '--', *INPUTS[group], root=root).decode().splitlines()
        read = lambda path: run('git', 'show', f'{commit}:{path}', root=root)
    else:
        paths = run('git', 'ls-files', '--cached', '--others', '--exclude-standard', '--', *INPUTS[group], root=root).decode().splitlines()
        read = lambda path: (root / path).read_bytes()
    paths = [p for p in paths if not p.endswith('.md') and '__pycache__' not in p and not p.endswith('.pyc')]
    if not paths:
        raise ValueError('No preload inputs found')
    result = {}
    pending = list(paths)
    while pending:
        path = pending.pop()
        if path in result:
            continue
        if path.startswith('../') or path.startswith('/'):
            raise ValueError('Preload include escapes repository')
        content = read(path).replace(b'\r\n', b'\n')
        result[path] = sha(content)
        for include in re.findall(rb'^\s*#\s*include\s*"([^"\n]+)"', content, re.M):
            pending.append(posixpath.normpath(posixpath.join(posixpath.dirname(path), include.decode())))
    workflow = (run('git', 'show', f'{commit}:.github/workflows/build.yml', root=root).decode() if commit
                else (root / '.github/workflows/build.yml').read_text())
    runner = re.search(r'(?ms)^  build:\n.*?^    runs-on: ([^\n]+)', workflow)
    if runner is None:
        raise ValueError('APK build runner policy is missing')
    result['@runner'] = sha(runner[1].strip().encode())
    return dict(sorted(result.items()))


def recipe(root=ROOT, commit=None):
    def read(path):
        return (run('git', 'show', f'{commit}:{path}', root=root).decode() if commit
                else (root / path).read_text())
    script = 'tools/components/build-arm64-preloads.sh'
    if commit and run('git', 'ls-tree', commit, script, root=root) == b'':
        workflow = read('.github/workflows/build.yml')
        text = workflow[workflow.index('          aarch64-linux-gnu-g++ -shared'):]
        text = text[:text.index('          VK_INCLUDE=')]
    else:
        text = read(script)
        preamble = '#!/usr/bin/env bash\nset -euo pipefail\nD=${1:?output directory}\nmkdir -p "$D"\n'
        if not text.startswith(preamble):
            return sha(text.encode())
        text = text[len(preamble):]
    return sha(' '.join(text.split()).encode())


def artifact_bytes(origin, cache):
    build = api(f"actions/runs/{origin['run']}")
    if (build['event'] != 'push' or build['head_branch'] != 'main' or build['conclusion'] != 'success'
            or build['head_sha'] != origin['commit'] or build['path'] != '.github/workflows/build.yml'
            or build['repository']['full_name'] != REPO):
        raise ValueError('Snapshot is not from a successful main APK build')
    artifact = api(f"actions/artifacts/{origin['artifact']}")
    if (artifact['name'] != 'droiddeck-apk' or artifact['workflow_run']['id'] != origin['run']
            or artifact['digest'] != 'sha256:' + origin['artifact_sha256']):
        raise ValueError('Original APK artifact identity or digest mismatch')
    cache.mkdir(parents=True, exist_ok=True)
    path = cache / (origin['artifact_sha256'] + '.zip')
    if not path.exists():
        path.write_bytes(run('gh', 'api', f"repos/{REPO}/actions/artifacts/{origin['artifact']}/zip"))
    content = path.read_bytes()
    if sha(content) != origin['artifact_sha256']:
        raise ValueError('Original APK archive checksum mismatch')
    with ZipFile(io.BytesIO(content)) as archive:
        names = [name for name in archive.namelist() if name.endswith('.apk')]
        if len(names) != 1:
            raise ValueError('Expected one original APK')
        with ZipFile(io.BytesIO(archive.read(names[0]))) as apk:
            return {name: apk.read('assets/linuxfs/' + name) for name in FILES}


def validate_lock(data):
    if data.get('version') != 1 or set(data['groups']) != set(INPUTS):
        raise ValueError('Unsupported preload snapshot')
    if set(data['outputs']) != FILES:
        raise ValueError('Unexpected preload output set')
    for digest in [data['archive_sha256'], *data['outputs'].values()]:
        if len(digest) != 64 or any(c not in '0123456789abcdef' for c in digest):
            raise ValueError('Invalid preload checksum')


def prove_origin(data, cache, root=ROOT):
    content = artifact_bytes(data['origin'], cache)
    if {name: sha(value) for name, value in content.items()} != data['outputs']:
        raise ValueError('Snapshot binaries differ from the original main APK')
    commit = data['origin']['commit']
    run('git', 'fetch', '--no-tags', 'origin', commit, root=root)
    for group in INPUTS:
        if source_files(group, root, commit) != data['groups'][group]:
            raise ValueError('Snapshot inputs differ from the producing source')
    if recipe(root, commit) != data['arm64_recipe']:
        raise ValueError('Snapshot recipe differs from the producing source')


def unpack(content, data):
    if sha(content) != data['archive_sha256']:
        raise ValueError('Preload archive checksum mismatch')
    result = {}
    total = 0
    with tarfile.open(fileobj=io.BytesIO(content), mode='r:gz') as archive:
        for entry in archive:
            if entry.name not in FILES or entry.name in result or not entry.isfile() or entry.size > 32 * 1024 * 1024:
                raise ValueError('Unsafe or unexpected preload archive entry')
            total += entry.size
            if total > 64 * 1024 * 1024:
                raise ValueError('Preload archive exceeds expanded size limit')
            value = archive.extractfile(entry).read()
            if sha(value) != data['outputs'][entry.name]:
                raise ValueError('Preload binary checksum mismatch')
            result[entry.name] = value
    if set(result) != FILES:
        raise ValueError('Incomplete preload archive')
    return result


def restore(root=ROOT):
    data = json.loads((root / LOCK).read_text())
    validate_lock(data)
    matching = {group: source_files(group, root) == data['groups'][group] for group in INPUTS}
    matching['arm64'] &= recipe(root) == data['arm64_recipe']
    if os.environ.get('FORCE_PRELOAD_REBUILD') == 'true':
        matching = dict.fromkeys(INPUTS, False)
    if any(matching.values()):
        base = os.environ.get('COMPONENT_TRUSTED_BASE')
        approved = None
        if base:
            result = subprocess.run(['git', 'show', f'{base}:{LOCK}'], cwd=root, capture_output=True)
            if result.returncode == 0:
                approved = json.loads(result.stdout)
        if approved != data:
            prove_origin(data, root / 'app/build/component-downloads', root)
        url = (f"https://github.com/Droid-Deck/DroidDeck-Components/releases/download/"
               f"preloads-{data['archive_sha256']}/preloads.tar.gz")
        try:
            with urllib.request.urlopen(url, timeout=60) as response:
                content = response.read(64 * 1024 * 1024)
        except urllib.error.HTTPError as error:
            if error.code != 404:
                raise
            print('Preload snapshot is unavailable; rebuilding from source.')
            matching = dict.fromkeys(INPUTS, False)
        else:
            binaries = unpack(content, data)
            for group, matches in matching.items():
                if matches:
                    for name in OUTPUTS[group]:
                        path = root / 'app/src/main/assets/linuxfs' / name
                        if not path.resolve().is_relative_to(root.resolve()) or path.is_symlink():
                            raise ValueError('Preload output escapes repository or is a symlink')
                        path.parent.mkdir(parents=True, exist_ok=True)
                        path.write_bytes(binaries[name])
                        path.chmod(0o755 if name.endswith('droiddeck-clipboard') else 0o644)
    for group, matches in matching.items():
        print(f"{group}: {'verified snapshot reused' if matches else 'inputs changed or snapshot unavailable; rebuild required'}")
        if os.environ.get('GITHUB_OUTPUT'):
            with open(os.environ['GITHUB_OUTPUT'], 'a') as output:
                output.write(f'{group}={str(matches).lower()}\n')
    return matching


def snapshot(run_id, destination, root=ROOT):
    build = api(f'actions/runs/{run_id}')
    candidates = api(f'actions/runs/{run_id}/artifacts?per_page=100')['artifacts']
    artifact = next(a for a in candidates if a['name'] == 'droiddeck-apk')
    origin = dict(run=run_id, commit=build['head_sha'], artifact=artifact['id'],
                  artifact_sha256=artifact['digest'].removeprefix('sha256:'),
                  url=build['html_url'])
    content = artifact_bytes(origin, root / 'app/build/component-downloads')
    run('git', 'fetch', '--no-tags', 'origin', origin['commit'], root=root)
    data = dict(version=1, origin=origin, groups={g: source_files(g, root, origin['commit']) for g in INPUTS},
                arm64_recipe=recipe(root, origin['commit']), outputs={name: sha(value) for name, value in content.items()},
                toolchain=dict(arm64='Ubuntu 24.04 GCC cross compiler; exact build commands pinned by arm64_recipe',
                               x86='Debian bullseye compiler image and recipe pinned by source inputs'))
    stream = io.BytesIO()
    with gzip.GzipFile(fileobj=stream, mode='wb', mtime=0) as compressed:
        with tarfile.open(fileobj=compressed, mode='w') as archive:
            for name, value in sorted(content.items()):
                entry = tarfile.TarInfo(name)
                entry.size, entry.mode = len(value), 0o644
                archive.addfile(entry, io.BytesIO(value))
    destination.mkdir(parents=True, exist_ok=True)
    (destination / 'preloads.tar.gz').write_bytes(stream.getvalue())
    data['archive_sha256'] = sha(stream.getvalue())
    (root / LOCK).write_text(json.dumps(data, indent=2, sort_keys=True) + '\n')
    print('Snapshot SHA-256: ' + data['archive_sha256'])


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('operation', choices=['restore', 'snapshot'])
    parser.add_argument('--run', type=int)
    parser.add_argument('--out', type=Path, default=ROOT / 'app/build/component-snapshot')
    args = parser.parse_args()
    if args.operation == 'restore':
        restore()
    else:
        snapshot(args.run, args.out)
