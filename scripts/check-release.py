#!/usr/bin/env python3
"""Fail closed when the multi-registry release metadata is inconsistent."""

from __future__ import annotations

import json
import os
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []


def require(condition: bool, message: str) -> None:
    if not condition:
        errors.append(message)


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def match(path: str, pattern: str) -> str:
    found = re.search(pattern, text(path), re.MULTILINE)
    if not found:
        errors.append(f"{path}: missing version field")
        return "<missing>"
    return found.group(1)


package_dirs = [
    "packages/sdk",
    "packages/sdk-python",
    "packages/sdk-go",
    "packages/sdk-rust",
    "packages/sdk-java",
    "packages/sdk-kotlin",
    "packages/sdk-csharp",
    "packages/sdk-ruby",
    "packages/sdk-php",
]
canonical_license = (ROOT / "LICENSE").read_bytes()
for package_dir in package_dirs:
    license_path = ROOT / package_dir / "LICENSE"
    require(license_path.is_file(), f"{package_dir}: LICENSE is missing")
    if license_path.is_file():
        require(
            license_path.read_bytes() == canonical_license,
            f"{package_dir}: LICENSE differs from the repository license",
        )

npm = json.loads(text("packages/sdk/package.json"))
npm_lock = json.loads(text("packages/sdk/package-lock.json"))
python_manifest = text("packages/sdk-python/pyproject.toml")
rust_manifest = text("packages/sdk-rust/Cargo.toml")
composer_root = json.loads(text("composer.json"))
composer_package = json.loads(text("packages/sdk-php/composer.json"))

versions = {
    "npm": npm["version"],
    "npm-lock": npm_lock["version"],
    "npm-lock-root": npm_lock["packages"][""]["version"],
    "python": match("packages/sdk-python/pyproject.toml", r'^version\s*=\s*"([^"]+)"'),
    "rust": match("packages/sdk-rust/Cargo.toml", r'^version\s*=\s*"([^"]+)"'),
    "ruby": match("packages/sdk-ruby/lib/melaya/version.rb", r'VERSION\s*=\s*"([^"]+)"'),
    "nuget": match("packages/sdk-csharp/Melaya/Melaya.csproj", r"<Version>([^<]+)</Version>"),
    "java": match("packages/sdk-java/build.gradle", r"^version\s*=\s*'([^']+)'"),
    "kotlin": match("packages/sdk-kotlin/build.gradle", r"^version\s*=\s*'([^']+)'"),
}
unique_versions = set(versions.values())
require(
    len(unique_versions) == 1,
    "SDK versions differ: " + ", ".join(f"{name}={version}" for name, version in versions.items()),
)

# GITHUB_REF_NAME is set on EVERY Actions run, so on a push to main it is
# literally "main". The old `if tag:` guard therefore ran the release-tag
# rules on branch builds and failed them every time, which is why CI was red
# on main while passing locally (the variable is unset off-CI). GITHUB_REF_TYPE
# is the precise signal: "tag" or "branch".
tag = os.environ.get("GITHUB_REF_NAME", "")
if tag and os.environ.get("GITHUB_REF_TYPE", "tag") == "tag":
    require(re.fullmatch(r"v\d+\.\d+\.\d+", tag) is not None, f"release tag is invalid: {tag}")
    if len(unique_versions) == 1:
        require(tag == f"v{next(iter(unique_versions))}", f"{tag} does not match SDK version")

require(npm.get("license") == "Apache-2.0", "npm license must be Apache-2.0")
require("LICENSE" in npm.get("files", []), "npm package must include LICENSE")
require('license = "Apache-2.0"' in python_manifest, "Python license must be Apache-2.0")
require('license-files = ["LICENSE"]' in python_manifest, "Python artifact must include LICENSE")
require('license = "Apache-2.0"' in rust_manifest, "Rust license must be Apache-2.0")
require(composer_root.get("license") == "Apache-2.0", "root Composer license must be Apache-2.0")
require(composer_package.get("license") == "Apache-2.0", "PHP package license must be Apache-2.0")
require("version" not in composer_package, "VCS Composer package must derive its version from the tag")
for extension in ("ext-curl", "ext-json", "ext-openssl"):
    require(extension in composer_root.get("require", {}), f"root Composer manifest is missing {extension}")
require((ROOT / "packages/sdk-go/go.sum").is_file(), "Go SDK go.sum is missing")
require((ROOT / "packages/sdk-go/e2e/go.sum").is_file(), "Go E2E go.sum is missing")
require("PackageLicenseExpression>Apache-2.0<" in text("packages/sdk-csharp/Melaya/Melaya.csproj"), "NuGet license must be Apache-2.0")
require('spec.license       = "Apache-2.0"' in text("packages/sdk-ruby/melaya.gemspec"), "Ruby license must be Apache-2.0")

forbidden_admin_markers = (
    "/api/v1/admin",
    "/api/v1/internal",
    "/api/v1/auth/admin-contact",
    "adminContact",
    "admin_contact",
    "AdminContact",
)
forbidden_tls_bypass_markers = (
    "MELAYA_INSECURE_TLS",
    "InsecureSkipVerify",
    "danger_accept_invalid_certs",
    "ServerCertificateCustomValidationCallback",
    "VERIFY_NONE",
    "CERT_NONE",
    "NoVerifier",
    "trustAllSslContext",
)
event_query_credential_markers = (
    'searchParams.set("apiKey"',
    '.append_pair("apiKey"',
    'q.Set("apiKey"',
    '"apiKey": self._api_key',
    '"apiKey"    => @_tok',
    "&apiKey=",
)
ignored_parts = {"build", "dist", "target", "node_modules", ".gradle", ".venv", "venv", "__pycache__", ".pytest_cache"}
source_suffixes = {".ts", ".py", ".go", ".rs", ".java", ".kt", ".cs", ".rb", ".php", ".md"}
for directory, dirnames, filenames in os.walk(ROOT / "packages"):
    dirnames[:] = [name for name in dirnames if name not in ignored_parts]
    for filename in filenames:
        path = Path(directory) / filename
        if path.suffix.lower() not in source_suffixes:
            continue
        contents = path.read_text(encoding="utf-8", errors="ignore")
        for marker in forbidden_admin_markers:
            require(marker not in contents, f"{path.relative_to(ROOT)} exposes internal admin surface: {marker}")
        require(
            re.search(r"\badmin\b", contents, re.IGNORECASE) is None,
            f"{path.relative_to(ROOT)} exposes the internal admin concept",
        )
        for marker in forbidden_tls_bypass_markers:
            require(marker not in contents, f"{path.relative_to(ROOT)} contains TLS bypass marker: {marker}")
        if "event" in path.name.lower():
            for marker in event_query_credential_markers:
                require(
                    marker not in contents,
                    f"{path.relative_to(ROOT)} places an event credential in the URL: {marker}",
                )

# The admin/TLS markers must not leak through the non-SDK trees either — the
# benchmark, data, and example directories plus llms.txt all ship in the repo
# and are read by humans and crawlers. Only the precise marker strings are
# checked here (internal API paths, TLS-bypass flags), NOT the fuzzy \badmin\b
# word match, so documented public auth parameters and benign prose (e.g. the
# public-stream ?apiKey= query, "requires admin rights" in a bench script)
# never false-positive. scripts/ is deliberately NOT scanned: the marker list
# itself lives in this script.
leak_scan_suffixes = source_suffixes | {".toml", ".json", ".txt", ".csv", ".sh", ".ps1", ".yml", ".yaml"}
leak_scan_paths = [ROOT / "llms.txt"]
for leak_tree in ("benchmarks", "data", "examples"):
    for directory, dirnames, filenames in os.walk(ROOT / leak_tree):
        dirnames[:] = [name for name in dirnames if name not in ignored_parts]
        for filename in filenames:
            path = Path(directory) / filename
            if path.suffix.lower() in leak_scan_suffixes:
                leak_scan_paths.append(path)
for path in leak_scan_paths:
    contents = path.read_text(encoding="utf-8", errors="ignore")
    for marker in forbidden_admin_markers:
        require(marker not in contents, f"{path.relative_to(ROOT)} exposes internal admin surface: {marker}")
    for marker in forbidden_tls_bypass_markers:
        require(marker not in contents, f"{path.relative_to(ROOT)} contains TLS bypass marker: {marker}")


def require_contains(path: str, marker: str, reason: str) -> None:
    require(marker in text(path), f"{path}: {reason} (missing {marker!r})")


def require_not_contains(path: str, marker: str, reason: str) -> None:
    require(marker not in text(path), f"{path}: {reason} (found {marker!r})")


# These assertions make the release job fail if any SDK drifts from the public
# phone-control wire contract. They intentionally check the serialized shapes,
# not merely similarly named public method parameters.
phone_contracts = {
    "packages/sdk/src/phone.ts": (
        "return response.devices;",
        "return response.result?.apps ?? [];",
        "apps: packageNames.map((packageName) => ({ package: packageName }))",
    ),
    "packages/sdk-python/src/melaya/phone.py": (
        'return response.get("devices", [])',
        'response.get("result", {}).get("apps", [])',
        'json={"apps": [{',
    ),
    "packages/sdk-go/melaya/phone.go": (
        "Devices []PhoneDevice",
        "Apps []PhoneApp",
        '"apps": apps,',
    ),
    "packages/sdk-rust/src/phone.rs": (
        '"apps": package_names',
        'get("devices")',
        'get("result")',
    ),
    "packages/sdk-java/src/main/java/org/melaya/PhoneAPI.java": (
        '.path("devices")',
        '.path("result").path("apps")',
        'Map.of("apps", apps)',
    ),
    "packages/sdk-kotlin/src/main/kotlin/org/melaya/PhoneAPI.kt": (
        'optJSONArray("devices")',
        'optJSONObject("result")',
        'mapOf("apps" to packageNames.map',
    ),
    "packages/sdk-csharp/Melaya/PhoneApi.cs": (
        "PhoneDevicesResult",
        "PhoneAppsResult",
        "new { apps =",
    ),
    "packages/sdk-ruby/lib/melaya/phone.rb": (
        '.fetch("devices", [])',
        '.dig("result", "apps")',
        '"apps" => apps',
    ),
    "packages/sdk-php/src/PhoneAPI.php": (
        "$response['devices']",
        "$response['result']['apps']",
        "['apps' => $apps]",
    ),
}
for contract_path, markers in phone_contracts.items():
    for contract_marker in markers:
        require_contains(contract_path, contract_marker, "phone contract regression")

legacy_phone_payloads = {
    "packages/sdk/src/phone.ts": ('"packageNames":', '"pairingCode"'),
    "packages/sdk-python/src/melaya/phone.py": ('json={"packageNames"', '"pairingCode"'),
    "packages/sdk-go/melaya/phone.go": ('"packageNames":', '"pairingCode"'),
    "packages/sdk-rust/src/phone.rs": ('"packageNames":', '"pairingCode"'),
    "packages/sdk-java/src/main/java/org/melaya/PhoneAPI.java": ('Map.of("packageNames"', '"pairingCode"'),
    "packages/sdk-kotlin/src/main/kotlin/org/melaya/PhoneAPI.kt": ('mapOf("packageNames"', '"pairingCode"'),
    "packages/sdk-csharp/Melaya/PhoneApi.cs": ('new { packageNames', '"pairingCode"'),
    "packages/sdk-ruby/lib/melaya/phone.rb": ('"packageNames" =>', '"pairingCode"'),
    "packages/sdk-php/src/PhoneAPI.php": ("['packageNames' =>", "'pairingCode'"),
}
for contract_path, markers in legacy_phone_payloads.items():
    for contract_marker in markers:
        require_not_contains(contract_path, contract_marker, "legacy phone wire field")

pipeline_run_contracts = {
    "packages/sdk/src/pipelines.ts": (
        'executionTarget?: "local-runner" | "cloud-spawn"',
        "studio_url?: string",
        "env_overrides?: Record<string, string>",
    ),
    "packages/sdk-python/src/melaya/pipelines.py": (
        'body["executionTarget"] = execution_target',
        'body["studio_url"] = studio_url',
        'body["env_overrides"] = env_overrides',
    ),
    "packages/sdk-go/melaya/platform_types.go": (
        'json:"executionTarget,omitempty"',
        'json:"studio_url,omitempty"',
        'json:"env_overrides,omitempty"',
    ),
    "packages/sdk-rust/src/pipelines.rs": (
        '#[serde(rename = "studio_url", skip_serializing_if = "Option::is_none")]',
        '#[serde(rename = "env_overrides", skip_serializing_if = "Option::is_none")]',
        "local-runner or cloud-spawn",
    ),
    "packages/sdk-java/src/main/java/org/melaya/PipelinesAPI.java": (
        "{@code executionTarget}",
        "{@code studio_url}",
        "{@code env_overrides}",
    ),
    "packages/sdk-kotlin/src/main/kotlin/org/melaya/PipelinesAPI.kt": (
        'put("executionTarget", executionTarget)',
        'put("studio_url",      studioUrl)',
        'put("env_overrides", envOverrides)',
    ),
    "packages/sdk-csharp/Melaya/PlatformTypes.cs": (
        '[JsonPropertyName("executionTarget")]',
        '[JsonPropertyName("studio_url")]',
        '[JsonPropertyName("env_overrides")]',
    ),
    "packages/sdk-ruby/lib/melaya/pipelines.rb": (
        '"executionTarget" => execution_target',
        '"studio_url"      => studio_url',
        '"env_overrides"   => env_overrides',
    ),
    "packages/sdk-php/src/PipelinesAPI.php": (
        "executionTarget",
        "studio_url",
        "env_overrides",
    ),
}
for contract_path, markers in pipeline_run_contracts.items():
    for contract_marker in markers:
        require_contains(contract_path, contract_marker, "pipeline run contract regression")

require_contains(
    "packages/sdk-rust/src/pipelines.rs",
    '#[serde(rename = "run_id")]',
    "Rust run acceptance must deserialize run_id",
)
require_contains(
    "packages/sdk-rust/src/pipelines.rs",
    "pub cost: Option<Value>",
    "Rust run cost must preserve the structured server object",
)
require_contains(
    "packages/sdk-csharp/Melaya/PlatformTypes.cs",
    "public PipelineRunCost? Cost",
    "C# run cost must preserve the structured server object",
)

for agent_doc in ("README.md", "docs/agent-builder.md"):
    for stale_marker in (
        "pipelines.runs(",
        "pipelines.run.get(",
        '"api_key":',
        '"apiKey":',
        '"target": "cloud"',
        '"target": "runner"',
    ):
        require_not_contains(agent_doc, stale_marker, "stale or unsafe Agent Builder example")

for public_doc in (ROOT / "docs").glob("*.md"):
    contents = public_doc.read_text(encoding="utf-8", errors="ignore")
    require(
        re.search(r"\badmin\b", contents, re.IGNORECASE) is None,
        f"{public_doc.relative_to(ROOT)} exposes the internal admin concept",
    )
    for marker in forbidden_admin_markers + (
        "list_apps_admin",
        "set_allowed_apps",
        "return_to_melaya",
        "long-poll",
        "Redis key",
    ):
        require(
            marker not in contents,
            f"{public_doc.relative_to(ROOT)} exposes an internal-only implementation detail: {marker}",
        )

require(
    re.search(r"\badmin\b", text("README.md"), re.IGNORECASE) is None,
    "README.md exposes the internal admin concept",
)
# Public positioning gate. These track the CURRENT product line; update them
# deliberately when the line changes, never by deleting the check.
# Until 2026-09-08 this required "Mobile Device Control" and "Melaya Trading".
# The public line is now six products and trading is no longer publicly
# positioned, so the old strings gated the README against the truth.
require_contains("README.md", "Melaya Agents", "public positioning must lead with Melaya Agents")
require_contains("README.md", "Device Control", "public positioning must feature Device Control")
require_contains("README.md", "Browser Control", "public positioning must feature Browser Control")
require_contains("README.md", "MCP Server", "public positioning must feature the MCP Server")

require_contains(
    ".github/workflows/release.yml",
    "npm install -g npm@11.5.1",
    "npm Trusted Publishing helper must be pinned",
)
require_not_contains(
    ".github/workflows/release.yml",
    "npm@latest",
    "release tooling must not float to latest",
)

for python_workflow in (
    ".github/workflows/verify.yml",
    ".github/workflows/release.yml",
):
    require_contains(python_workflow, "build==1.5.0", "Python build frontend must be pinned")
    require_contains(python_workflow, "twine==7.0.0", "Twine must be pinned")

archive_attributes = text(".gitattributes")
for excluded_tree in (
    "/.github",
    "/assets",
    "/benchmarks",
    "/data",
    "/docs",
    "/examples",
    "/scripts",
    "/packages/sdk",
    "/packages/sdk-python",
    "/packages/sdk-go",
    "/packages/sdk-rust",
    "/packages/sdk-java",
    "/packages/sdk-kotlin",
    "/packages/sdk-csharp",
    "/packages/sdk-ruby",
):
    require(
        re.search(rf"^{re.escape(excluded_tree)}\s+export-ignore\s*$", archive_attributes, re.MULTILINE)
        is not None,
        f"Packagist archive does not exclude {excluded_tree}",
    )
require(
    re.search(r"^/packages/sdk-php\s+export-ignore\s*$", archive_attributes, re.MULTILINE) is None,
    "Packagist archive excludes the PHP SDK",
)

for workflow in (ROOT / ".github/workflows").glob("*.yml"):
    contents = workflow.read_text(encoding="utf-8")
    for action, revision in re.findall(
        r"^\s*-\s+uses:\s+([^@\s]+)@([^\s#]+)",
        contents,
        re.MULTILINE,
    ):
        require(
            re.fullmatch(r"[0-9a-f]{40}", revision) is not None,
            f"{workflow.relative_to(ROOT)} action is not SHA-pinned: {action}@{revision}",
        )

if errors:
    print("Release readiness FAILED:", file=sys.stderr)
    for error in errors:
        print(f"  - {error}", file=sys.stderr)
    raise SystemExit(1)

print(f"Release metadata OK: {next(iter(unique_versions))}, 9 SDKs, Apache-2.0")







