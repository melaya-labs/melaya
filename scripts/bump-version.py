#!/usr/bin/env python3
"""Bump every Melaya SDK manifest to one version, in lockstep.

    python scripts/bump-version.py 0.1.3

Touches all 9 language manifests + Cargo.lock so a release tag publishes cleanly.
Go has no manifest (it versions from the git tag). Run from anywhere; paths are
resolved relative to this file. Prints every file it changes; exits non-zero if a
manifest's version field could not be found (so a silent miss can't slip through).
"""
import io, os, re, sys

if len(sys.argv) != 2 or not re.fullmatch(r"\d+\.\d+\.\d+", sys.argv[1]):
    sys.exit("usage: python scripts/bump-version.py X.Y.Z")
NEW = sys.argv[1]
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# (relative path, regex with two capture groups around the version, label)
EDITS = [
    ("packages/sdk/package.json",                 r'("version":\s*")[^"]+(")',                              "npm"),
    ("packages/sdk-python/pyproject.toml",         r'(?m)(^version\s*=\s*")[^"]+(")',                        "pypi"),
    ("packages/sdk-rust/Cargo.toml",               r'(?m)(^version\s*=\s*")[^"]+(")',                        "crates"),
    ("packages/sdk-rust/Cargo.lock",               r'(name = "melaya"\nversion = ")[^"]+(")',               "crates-lock"),
    ("packages/sdk-ruby/lib/melaya/version.rb",    r'(VERSION\s*=\s*")[^"]+(")',                             "rubygems"),
    ("packages/sdk-csharp/Melaya/Melaya.csproj",   r'(<Version>)[^<]+(</Version>)',                          "nuget"),
    ("packages/sdk-java/build.gradle",             r"(?m)(^version\s*=\s*')[^']+(')",                        "maven-java version"),
    ("packages/sdk-java/build.gradle",             r"(coordinates\('org\.melaya',\s*'melaya-sdk',\s*')[^']+(')",        "maven-java coords"),
    ("packages/sdk-kotlin/build.gradle",           r"(?m)(^version\s*=\s*')[^']+(')",                        "maven-kotlin version"),
    ("packages/sdk-kotlin/build.gradle",           r"(coordinates\('org\.melaya',\s*'melaya-sdk-kotlin',\s*')[^']+(')", "maven-kotlin coords"),
]

failed = []
for rel, pat, label in EDITS:
    p = os.path.join(ROOT, rel)
    s = io.open(p, encoding="utf-8").read()
    s2, n = re.subn(pat, lambda m: m.group(1) + NEW + m.group(2), s, count=1)
    if n == 0:
        failed.append((rel, label)); print(f"  MISS  {label:22} {rel}")
        continue
    io.open(p, "w", encoding="utf-8", newline="\n").write(s2)
    print(f"  ok    {label:22} {rel}")

if failed:
    sys.exit(f"\nFAILED to bump {len(failed)} field(s) — fix the manifest/regex before releasing.")
print(f"\nAll manifests set to {NEW}. Next:  git commit -am 'release {NEW}' && git tag v{NEW} && git push --follow-tags")
