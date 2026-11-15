"""Build the read-only, sanitized PHP reference archive without executing PHP or SQL."""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import io
import json
from pathlib import Path, PurePosixPath
import re
from zipfile import ZipFile, ZipInfo, ZIP_DEFLATED

ROOT = Path(__file__).resolve().parents[1]
OMIT_DIRECTORIES = {
    ".git", ".agents", ".idea", ".vscode", "node_modules", "uploads",
    "profile_images", "sessions", "backups", "logs", "cache", "tmp", "temp",
}
SOURCE_EXTENSIONS = {
    ".php", ".php_", ".js", ".cjs", ".mjs", ".css", ".scss", ".json",
    ".map", ".html", ".htm", ".md", ".txt", ".sql", ".xml", ".xsd",
    ".svg", ".png", ".jpg", ".jpeg", ".gif", ".ico", ".woff", ".woff2",
    ".ttf", ".otf", ".eot", ".afm", ".z",
}
TEXT_EXTENSIONS = {
    ".php", ".php_", ".js", ".cjs", ".mjs", ".css", ".scss", ".json",
    ".map", ".html", ".htm", ".md", ".txt", ".sql", ".xml", ".xsd", ".svg",
}
LITERAL = r"(?:'(?:\\.|[^'\\])*'|\"(?:\\.|[^\"\\])*\")"
ASSIGNMENT = re.compile(
    rf"(?P<target>\$[A-Za-z_]\w*(?:->(?:Password|Username|ClientId|ClientSecret))?)"
    rf"(?P<equals>\s*=\s*)(?P<literal>{LITERAL})(?=\s*;)", re.I,
)
CONNECTION = re.compile(
    rf"(?P<call>\b(?:mysqli_connect|new\s+(?:mysqli|PDO))\s*\(\s*)"
    rf"(?P<host>{LITERAL})(?P<sep1>\s*,\s*)(?P<user>{LITERAL})"
    rf"(?P<sep2>\s*,\s*)(?P<password>{LITERAL})", re.I,
)
MAIL_ADDRESS = re.compile(
    rf"(?P<call>->(?:setFrom|addAddress|addReplyTo)\(\s*)(?P<literal>{LITERAL})", re.I,
)
KNOWN_SECRET = re.compile(
    r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----|"
    r"\b(?:ghp_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{40,}|"
    r"AKIA[0-9A-Z]{16}|AIza[0-9A-Za-z_-]{30,}|xox[baprs]-[A-Za-z0-9-]{20,})\b"
)


def reason_to_omit(relative: PurePosixPath) -> str | None:
    parts = [part.lower() for part in relative.parts]
    if any(part in OMIT_DIRECTORIES for part in parts[:-1]):
        return "runtime-or-local-directory"
    if "assets" in parts and "images" in parts and "users" in parts:
        return "profile-pictures"
    name = relative.name.lower()
    if name.startswith(".env") or name in {"error_log", "php.ini", ".user.ini"}:
        return "local-configuration-or-log"
    if relative.suffix.lower() not in SOURCE_EXTENSIONS and not name.startswith(
        ("license", "notice", "copying", "authors", "readme", "changelog", ".htaccess")
    ):
        return "generated-document-archive-or-non-source"
    return None


def credential_variable(target: str) -> str | None:
    name = target.lower().replace("$", "")
    if "->" in name:
        name = name.split("->")[-1]
    if any(word in name for word in ("error", "message", "label", "placeholder", "regex")):
        return None
    if any(word in name for word in ("password", "passwd", "dbpass", "secret", "api_key", "apikey")) or name in {"pwd", "token", "clientid"}:
        return "FCMS_REFERENCE_SECRET"
    if name in {"username", "dbuser", "db_user", "usernamedb", "user_name"}:
        return "FCMS_REFERENCE_USERNAME"
    if name in {"host", "servername", "dbhost", "db_host"}:
        return "FCMS_REFERENCE_HOST"
    return None


def sanitize_php(text: str, removed_secrets: set[str]) -> tuple[str, int]:
    changes = 0

    def assignment(match: re.Match) -> str:
        nonlocal changes
        variable = credential_variable(match["target"])
        value = match["literal"][1:-1]
        if variable is None or not value:
            return match[0]
        if variable.endswith("SECRET") and len(value) >= 8:
            removed_secrets.add(value)
        changes += 1
        prefix = text[text.rfind("\n", 0, match.start()) + 1:match.start()]
        # PHP class property defaults must remain constant expressions.
        property_default = re.fullmatch(
            r"\s*(?:(?:public|protected|private|var|static|readonly)\s+)+(?:\??[\w\\]+\s+)?", prefix
        ) is not None
        replacement = "''" if property_default else f"(getenv('{variable}') ?: '')"
        return match["target"] + match["equals"] + replacement

    def connection(match: re.Match) -> str:
        nonlocal changes
        value = match["password"][1:-1]
        if len(value) >= 8:
            removed_secrets.add(value)
        changes += 1
        # Keep the DSN/host and database structure; remove literal authentication values.
        return match["call"] + match["host"] + match["sep1"] + "(getenv('FCMS_REFERENCE_USERNAME') ?: '')" + match["sep2"] + "(getenv('FCMS_REFERENCE_SECRET') ?: '')"

    def address(match: re.Match) -> str:
        nonlocal changes
        changes += 1
        return match["call"] + "'reference@example.invalid'"

    text = ASSIGNMENT.sub(assignment, text)
    text = CONNECTION.sub(connection, text)
    text = MAIL_ADDRESS.sub(address, text)
    return text, changes


def schema_only(text: str) -> str:
    # The dump's CREATE/ALTER statements are single statements ending on a semicolon line.
    statements = re.findall(r"(?ims)^((?:CREATE TABLE|ALTER TABLE)\b.*?;)[ \t]*(?:\r?\n|$)", text)
    expected = len(re.findall(r"(?im)^CREATE TABLE\b", text))
    if expected == 0 or sum(bool(re.match("CREATE TABLE", s, re.I)) for s in statements) != expected:
        raise ValueError("Could not retain all dump table definitions")
    result = "-- Schema-only reference; all dumped rows and credentials omitted.\n\n" + "\n\n".join(statements) + "\n"
    result = re.sub(r"\bAUTO_INCREMENT\s*=\s*\d+", "AUTO_INCREMENT=1", result, flags=re.I)
    if re.search(r"(?im)^\s*(?:INSERT|REPLACE|COPY)\b", result):
        raise ValueError("Dump data appeared in the schema-only reference")
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--date", default="2026-11-15")
    args = parser.parse_args()
    source = args.source.resolve(strict=True)
    destination = ROOT / "reference"
    destination.mkdir(exist_ok=True)
    output = destination / "fcms-php-reference.zip"
    date = tuple(map(int, args.date.split("-"))) + (0, 0, 0)
    entries: dict[str, bytes] = {}
    modified: dict[str, dict] = {}
    snapshots: list[tuple[Path, int, int]] = []
    excluded: Counter = Counter()
    secrets: set[str] = set()
    for file in sorted(source.rglob("*")):
        if not file.is_file() or file.is_symlink():
            continue
        relative = PurePosixPath(file.relative_to(source).as_posix())
        reason = reason_to_omit(relative)
        if reason:
            excluded[reason] += 1
            continue
        before = file.stat()
        content = file.read_bytes()
        snapshots.append((file, before.st_mtime_ns, before.st_size))
        archived = relative.as_posix()
        if file.suffix.lower() in {".php", ".php_"}:
            text = content.decode("utf-8", errors="surrogateescape")
            text, count = sanitize_php(text, secrets)
            if count:
                content = text.encode("utf-8", errors="surrogateescape")
                modified[archived] = {"reason": "credential-or-mail-identity-redaction", "changes": count}
        elif file.suffix.lower() == ".sql" and ("phpmyadmin sql dump" in content[:2048].decode("utf-8", errors="ignore").lower() or file.name.lower() == "fcms_db.sql"):
            content = schema_only(content.decode("utf-8-sig")).encode("utf-8")
            archived = str(relative.with_name(relative.stem + ".schema.sql"))
            modified[archived] = {"reason": "schema-only-dump", "original": relative.as_posix()}
        entries[archived] = content
    for name, content in list(entries.items()):
        if PurePosixPath(name).suffix.lower() in TEXT_EXTENSIONS:
            text = content.decode("utf-8", errors="surrogateescape")
            # A legacy test assigns the literal "password" to a password variable.
            # Never replace ordinary field names or placeholder words across source code.
            global_secrets = {
                value for value in secrets
                if value.lower() not in {"password", "changeme", "changeme123", "your_password", "your_client_secret"}
                and (len(value) >= 12 or not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", value))
            }
            for secret in sorted(global_secrets, key=len, reverse=True):
                if secret in text:
                    text = text.replace(secret, "[REDACTED_REFERENCE_SECRET]")
                    modified.setdefault(name, {"reason": "credential-value-redaction"})
            if KNOWN_SECRET.search(text):
                raise ValueError(f"Unreviewed secret pattern in {name}")
            entries[name] = text.encode("utf-8", errors="surrogateescape")
    for file, mtime, size in snapshots:
        after = file.stat()
        if after.st_mtime_ns != mtime or after.st_size != size:
            raise ValueError(f"Source changed during snapshot: {file.relative_to(source)}")
    manifest = {
        "snapshotDate": args.date, "purpose": "read-only FCMS PHP migration reference",
        "source": source.as_posix(), "sourceFiles": len(entries),
        "excludedByReason": dict(sorted(excluded.items())), "sanitizedFiles": modified,
        "files": [{"path": name, "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()} for name, data in sorted(entries.items())],
    }
    entries["REFERENCE-MANIFEST.json"] = (json.dumps(manifest, indent=2) + "\n").encode()
    entries["REFERENCE-README.txt"] = (
        "FCMS PHP read-only reference snapshot, " + args.date + "\n"
        "See reference/README.md in the Spring repository before extracting.\n"
        "Credentials/mail identities are redacted. Runtime/user files and dumped database rows are omitted.\n"
        "CODES, CODE2 and images contain historical application copies; root PHP files are canonical.\n"
        "This archive is for source inspection, not deployment or connection to an operational database.\n"
    ).encode()
    buffer = io.BytesIO()
    with ZipFile(buffer, "w", ZIP_DEFLATED, compresslevel=9) as archive:
        for name, content in sorted(entries.items()):
            info = ZipInfo("fcms/" + name, date_time=date)
            info.compress_type = ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, content, compresslevel=9)
    data = buffer.getvalue()
    if len(data) >= 50 * 1024 * 1024:
        raise ValueError("Reference ZIP exceeds the intended 50 MiB repository bound")
    with ZipFile(io.BytesIO(data)) as archive:
        if archive.testzip() is not None:
            raise ValueError("ZIP integrity check failed")
        for entry in archive.infolist():
            parts = PurePosixPath(entry.filename).parts
            if parts[0] != "fcms" or ".." in parts or PurePosixPath(entry.filename).is_absolute():
                raise ValueError("Unsafe archive path")
        for file in manifest["files"]:
            if hashlib.sha256(archive.read("fcms/" + file["path"])).hexdigest() != file["sha256"]:
                raise ValueError("Archive manifest mismatch")
    output.write_bytes(data)
    summary = {key: manifest[key] for key in ("snapshotDate", "sourceFiles", "excludedByReason", "sanitizedFiles")}
    summary.update(archive=output.name, archiveBytes=len(data), sha256=hashlib.sha256(data).hexdigest())
    (destination / "fcms-php-reference.manifest.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    (destination / "fcms-php-reference.zip.sha256").write_text(summary["sha256"] + "  " + output.name + "\n", encoding="ascii")
    print(json.dumps({key: summary[key] for key in ("sourceFiles", "archiveBytes", "sha256")}))


if __name__ == "__main__":
    main()
