#!/usr/bin/env python3
"""Verify the portable agent reference package without running application code."""

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path, PurePosixPath
from urllib.parse import unquote, urlsplit


SKILLS = (
    "springboot-1k-rps-optimization",
    "microfinance-ui-governance",
    "microfinance-ui-style",
    "microfinance-table-standard",
)
PACKAGE = Path("reference/microfinance-ui")
SOURCE = PACKAGE / "source"
BINARY_SUFFIXES = {".ttf", ".woff2"}
SHELL_INCLUDES = {"header.jspf", "sidebar.jspf", "footer.jspf"}


def verify(root):
    errors = []
    checks = {"files": 0, "skills": 0, "localLinks": 0, "jspIncludes": 0, "cssAssets": 0}
    manifest = json.loads((root / PACKAGE / "manifest.json").read_text(encoding="utf-8"))
    entries = manifest["files"]
    allowed = [SOURCE.as_posix() + "/", (PACKAGE / "labels").as_posix() + "/"]
    allowed += [".agents/skills/" + name + "/" for name in SKILLS]
    seen = set()
    for entry in entries:
        name = entry["path"]
        relative = PurePosixPath(name)
        if relative.is_absolute() or ".." in relative.parts or "\\" in name or not name.startswith(tuple(allowed)):
            errors.append("Unsafe manifest path: " + name)
            continue
        if name in seen:
            errors.append("Duplicate manifest path: " + name)
        seen.add(name)
        target = root / name
        if target.is_symlink() or not target.is_file() or not target.resolve().is_relative_to(root):
            errors.append("Missing/unsafe reference file: " + name)
            continue
        data = target.read_bytes()
        if len(data) != entry["bytes"] or hashlib.sha256(data).hexdigest() != entry["sha256"]:
            errors.append("Reference integrity mismatch: " + name)
        if not re.fullmatch(r"[0-9a-f]{64}", entry.get("sourceSha256", entry["sha256"])):
            errors.append("Invalid source provenance hash: " + name)
        if target.suffix.lower() not in BINARY_SUFFIXES:
            try:
                content = data.decode("utf-8")
            except UnicodeDecodeError:
                errors.append("Non-UTF-8 reference text: " + name)
                continue
            if "\r" in content:
                errors.append("Non-portable text line endings: " + name)
            if re.search(r"[A-Za-z]:[\\/](?:Users|xampp)|/Users/[^/]+/|/home/[^/]+/", content):
                errors.append("Workstation path in portable package: " + name)
            if re.search(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY", content):
                errors.append("Private key marker: " + name)
            # Match literal credential values, not field names, environment lookups or live DOM input.
            if re.search(r'''(?i)\b(?:password|api[_-]?key|client[_-]?secret|access[_-]?token)\s*[:=]\s*["'][^"'\s]{8,}["']''', content):
                errors.append("Review credential-like literal: " + name)
        checks["files"] += 1

    for prefix in allowed:
        for target in (root / prefix).rglob("*"):
            if target.is_file() and target.relative_to(root).as_posix() not in seen:
                errors.append("Unmanifested reference file: " + target.relative_to(root).as_posix())

    for name in SKILLS:
        skill = root / ".agents/skills" / name / "SKILL.md"
        content = skill.read_text(encoding="utf-8")
        front = re.match(r"\A---\n(.*?)\n---\n", content, re.S)
        if not front or not re.search(r"^name: " + re.escape(name) + r"$", front[1], re.M):
            errors.append("Invalid skill name/frontmatter: " + name)
        if not front or not re.search(r"^description: .+", front[1], re.M):
            errors.append("Missing skill description: " + name)
        # The baseline lists supporting references in backticks rather than Markdown links.
        for dependency in re.findall(r"`((?:references|assets|scripts)/[^`\s]+)`", content):
            if not (skill.parent / dependency).is_file():
                errors.append("Missing skill dependency: " + name + "/" + dependency)
        checks["skills"] += 1

    documents = [root / "AGENTS.md", root / "README.md", root / "reference/README.md"]
    documents += list((root / PACKAGE).rglob("*.md"))
    documents += list((root / ".agents/skills").rglob("*.md"))
    for document in documents:
        content = document.read_text(encoding="utf-8-sig")
        for link in re.findall(r"(?<!!)\[[^\]\n]+\]\(([^)\n]+)\)", content):
            target = link.strip().strip("<>")
            parsed = urlsplit(target)
            if parsed.scheme or parsed.netloc or not parsed.path:
                continue
            destination = (document.parent / unquote(parsed.path)).resolve()
            if not destination.is_relative_to(root) or not destination.exists():
                errors.append("Broken/non-portable link in " + document.relative_to(root).as_posix() + ": " + target)
            checks["localLinks"] += 1

    for document in (root / SOURCE).rglob("*"):
        if document.suffix not in {".jsp", ".jspf", ".tag"}:
            continue
        content = document.read_text(encoding="utf-8")
        for dependency in re.findall(r'<%@\s*include\s+file="([^"]+)"', content):
            if PurePosixPath(dependency).name in SHELL_INCLUDES:
                continue  # Explicitly mapped to the FCMS shell in the component guide.
            destination = (document.parent / dependency).resolve()
            if not destination.is_relative_to(root / SOURCE) or not destination.is_file():
                errors.append("Unresolved source JSP include: " + document.relative_to(root).as_posix() + " -> " + dependency)
            checks["jspIncludes"] += 1

    for stylesheet in (root / SOURCE).rglob("*.css"):
        content = stylesheet.read_text(encoding="utf-8")
        for dependency in re.findall(r"url\(\s*['\"]?([^)'\"\s]+)", content):
            if dependency.startswith(("data:", "http:", "https:", "#")):
                continue
            destination = (stylesheet.parent / dependency.split("?")[0]).resolve()
            if not destination.is_relative_to(root / SOURCE) or not destination.is_file():
                errors.append("Unresolved source CSS asset: " + stylesheet.relative_to(root).as_posix())
            checks["cssAssets"] += 1

    notices = (
        "src/main/resources/static/js/vendor/cloudscape-icons.LICENSE",
        "src/main/resources/static/js/vendor/cloudscape-icons.NOTICE",
        "src/main/resources/static/fonts/open-sans/CLOUDSCAPE-GLOBAL-STYLES-LICENSE.txt",
        "src/main/resources/static/fonts/open-sans/CLOUDSCAPE-GLOBAL-STYLES-NOTICE.txt",
        "src/main/resources/report-fonts/LICENSE.txt",
        "src/main/resources/report-fonts/NOTICE.md",
    )
    for notice in notices:
        if not (root / SOURCE / notice).is_file():
            errors.append("Missing third-party license/notice: " + notice)

    return checks, errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1], help="Repository checkout to verify")
    args = parser.parse_args()
    try:
        checks, errors = verify(args.root.resolve())
    except (OSError, ValueError, KeyError, TypeError) as error:
        print("Reference verification failed: " + str(error), file=sys.stderr)
        return 1
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print(json.dumps({"result": "passed", **checks}, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
