#!/usr/bin/env python3
"""Verify native database backup/download/restore against an isolated FCMS fixture."""

import argparse
import hashlib
import http.cookiejar
import json
import os
import re
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from html.parser import HTMLParser
from pathlib import Path


SQL_STRING = r"'(?:[^']|'')*'"
STRING_ARRAY_CAST = re.compile(
    r"(?:E'(?:[^'\\]|\\.|'')*'|" + SQL_STRING + r'|"(?:[^"]|"")*"|--[^\n]*|/\*[\s\S]*?\*/|'
    r"(?P<dollar>\$(?:[A-Za-z_][A-Za-z_0-9]*)?\$)[\s\S]*?(?P=dollar))"
    r"|(?P<cast>\(ARRAY\[(?P<items>" + SQL_STRING + r"::character varying(?:,\s*" + SQL_STRING
    + r"::character varying)*)\]\)::text\[\])"
)


def normalize_string_array_casts(definition):
    """Canonicalize PG18's equivalent array-wide/per-literal varchar-to-text casts only."""
    def replace(match):
        if match.group("cast") is None:
            return match.group(0)
        literals = re.findall(SQL_STRING + r"(?=::character varying)", match.group("items"))
        return "ARRAY[" + ", ".join("(" + value + "::character varying)::text" for value in literals) + "]"

    return STRING_ARRAY_CAST.sub(replace, definition)


class Inputs(HTMLParser):
    def __init__(self):
        super().__init__()
        self.hidden = {}

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == "input" and attrs.get("type") == "hidden" and attrs.get("name"):
            self.hidden[attrs["name"]] = attrs.get("value", "")


def identifier(value):
    return '"' + value.replace('"', '""') + '"'


def literal(value):
    return "'" + value.replace("'", "''") + "'"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://127.0.0.1:8093")
    parser.add_argument("--source-db", required=True)
    parser.add_argument("--schema", required=True)
    parser.add_argument("--pg-bin", type=Path, help="PostgreSQL 18 bin directory; otherwise use PATH")
    parser.add_argument("--output", type=Path, default=Path(".verification/backup-restore"))
    parser.add_argument("--private-backup-dir", type=Path)
    args = parser.parse_args()
    parsed = urllib.parse.urlsplit(args.base_url)
    if parsed.scheme != "http" or parsed.hostname not in {"127.0.0.1", "localhost"} or parsed.username or parsed.password:
        parser.error("Use the loopback fixture URL")
    if not re.fullmatch(r"fcms_codex_verify_[a-z0-9_]+", args.source_db):
        parser.error("Only an explicitly named verification database may be backed up by this harness")
    if not re.fullmatch(r"[a-z][a-z0-9_]*", args.schema):
        parser.error("Invalid fixture schema")
    email, password = os.environ.get("FCMS_UI_EMAIL"), os.environ.get("FCMS_UI_PASSWORD")
    if not email or not password:
        parser.error("Set FCMS_UI_EMAIL and FCMS_UI_PASSWORD outside source control")
    env = dict(os.environ, PGCLIENTENCODING="UTF8")
    if "PGPASSWORD" not in env and "FCMS_DB_PASSWORD" in env:
        env["PGPASSWORD"] = env["FCMS_DB_PASSWORD"]
    if "PGUSER" not in env and "FCMS_DB_USERNAME" in env:
        env["PGUSER"] = env["FCMS_DB_USERNAME"]
    env.setdefault("PGHOST", "127.0.0.1")
    env.setdefault("PGPORT", "5432")

    def executable(name):
        return str(args.pg_bin / (name + (".exe" if os.name == "nt" else ""))) if args.pg_bin else name

    def command(name, *values, timeout=120, **kwargs):
        return subprocess.run([executable(name), *values], env=env, check=True, timeout=timeout, **kwargs)

    def sql(query, database=args.source_db):
        return command("psql", "-X", "-q", "-A", "-t", "-w", "-v", "ON_ERROR_STOP=1", "-d", database,
                       input=query, capture_output=True, encoding="utf-8").stdout.strip()

    server = sql("show server_version;")
    versions = {name: command(name, "--version", capture_output=True, text=True).stdout.strip()
                for name in ("pg_dump", "pg_restore")}
    assert server.startswith("18."), "Expected the PostgreSQL 18 fixture server"
    assert all(re.search(r"\b18\.", version) for version in versions.values()), "Use PostgreSQL 18 tools"
    args.output.mkdir(parents=True, exist_ok=True)
    base = args.base_url.rstrip("/")
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

    def read(path):
        with opener.open(base + path, timeout=90) as response:
            assert response.status == 200
            assert path == "/login" or urllib.parse.urlsplit(response.geturl()).path != "/login"
            return response.read().decode("utf-8")

    login = Inputs()
    readiness = time.monotonic() + 120
    while True:
        try:
            login.feed(read("/login"))
            break
        except (urllib.error.URLError, TimeoutError):
            if time.monotonic() >= readiness:
                raise
            time.sleep(1)
    with opener.open(base + "/login", urllib.parse.urlencode(dict(login.hidden, email=email, password=password)).encode(), timeout=90) as response:
        assert urllib.parse.urlsplit(response.geturl()).path == "/dashboard", "Login failed"
        response.read()
    page = Inputs()
    page.feed(read("/backups"))
    assert "_csrf" in page.hidden
    table = identifier(args.schema) + ".system_backups"
    assert sql(f"select count(*) from {table} where status in ('QUEUED','RUNNING');") == "0", "Wait for the existing backup job before verification"
    assert int(sql(f"select count(*) from {table} where status='READY';")) < 20, "Backup retention limit reached"
    before_ids = set(sql(f"select id from {table};").splitlines())
    before_count = sql(f"select count(*) from {table};")
    try:
        opener.open(base + "/backups", b"", timeout=30)
        raise AssertionError("A backup request without CSRF was accepted")
    except urllib.error.HTTPError as error:
        assert error.code == 403
    assert sql(f"select count(*) from {table};") == before_count

    # Submit once; an uncertain response must be inspected rather than replayed.
    with opener.open(base + "/backups", urllib.parse.urlencode({"_csrf": page.hidden["_csrf"]}).encode(), timeout=90) as response:
        assert response.status == 200
        response.read()
    new_ids = set(sql(f"select id from {table};").splitlines()) - before_ids
    assert len(new_ids) == 1, "Expected one admitted native backup request"
    backup = str(uuid.UUID(new_ids.pop()))
    print("Native backup request admitted; waiting for the worker.", flush=True)
    states = []
    deadline = time.monotonic() + 240
    while time.monotonic() < deadline:
        state = sql(f"select status from {table} where id={literal(backup)}::uuid;")
        if not states or states[-1] != state:
            states.append(state)
        assert state != "FAILED", "Native backup worker reported FAILED"
        if state == "READY":
            break
        time.sleep(1)
    else:
        raise AssertionError("Native worker did not complete within four minutes")

    path = f"/backups/{backup}/download"
    anonymous = urllib.request.build_opener()
    with anonymous.open(base + path, timeout=30) as response:
        assert urllib.parse.urlsplit(response.geturl()).path == "/login", "Anonymous download exposed the archive"
        assert not response.read().startswith(b"PGDMP")
    with opener.open(base + path, timeout=120) as response:
        assert response.status == 200
        assert "no-store" in response.headers.get("Cache-Control", "")
        assert "attachment" in response.headers.get("Content-Disposition", "")
        assert response.headers.get_content_type() == "application/octet-stream"
        data = response.read()
    assert data.startswith(b"PGDMP")
    assert len(data) == int(sql(f"select size_bytes from {table} where id={literal(backup)}::uuid;"))
    digest = hashlib.sha256(data).hexdigest()
    archive = args.output / "native-backup.dump"
    archive.write_bytes(data)
    print("Backup READY; authorized download and anonymous denial verified.", flush=True)
    if args.private_backup_dir:
        assert hashlib.sha256((args.private_backup_dir / (backup + ".dump")).read_bytes()).hexdigest() == digest
    command("pg_restore", "--list", str(archive), stdout=subprocess.DEVNULL)
    restore = "fcms_codex_verify_backup_restore_" + uuid.uuid4().hex[:12]
    assert restore != args.source_db
    command("createdb", "--maintenance-db=postgres", restore)
    # No --clean, --create or original-database connection: restore only into the new database.
    with (args.output / "restore.log").open("w", encoding="utf-8") as restore_log:
        command("pg_restore", "--exit-on-error", "--no-owner", "--no-privileges", "--single-transaction", "--dbname", restore,
                str(archive), timeout=600, stdout=subprocess.DEVNULL, stderr=restore_log)
    print("Archive restored into a new isolated database; comparing data and schema.", flush=True)

    namespace = "n.nspname not in ('pg_catalog','information_schema') and n.nspname !~ '^pg_'"

    def snapshot(database):
        tables = json.loads(sql("select coalesce(json_agg(json_build_array(n.nspname,c.relname) order by n.nspname,c.relname),'[]'::json) "
                                "from pg_class c join pg_namespace n on n.oid=c.relnamespace where c.relkind in ('r','p') and " + namespace + ";", database))
        queries = ["begin isolation level repeatable read read only;set local timezone='UTC';set local search_path=pg_catalog;"]
        for schema, name in tables:
            predicate = f" where id<>{literal(backup)}::uuid" if (schema, name) == (args.schema, "system_backups") else ""
            queries.append(f"with hashes as (select encode(sha256(convert_to(to_jsonb(t)::text,'UTF8')),'hex') h from {identifier(schema)}.{identifier(name)} t{predicate}) "
                           f"select json_build_array({literal(schema)},{literal(name)},count(*),encode(sha256(convert_to(coalesce(string_agg(h,'' order by h),''),'UTF8')),'hex')) from hashes;")
        queries.append("commit;")
        rows = [json.loads(line) for line in sql("\n".join(queries), database).splitlines()]
        sequences = json.loads(sql("select coalesce(json_agg(json_build_array(schemaname,sequencename,last_value,start_value,increment_by,min_value,max_value,cache_size,cycle) order by schemaname,sequencename),'[]'::json) "
                                   "from pg_sequences where schemaname not in ('pg_catalog','information_schema') and schemaname !~ '^pg_';", database))
        definitions = sql("set search_path=pg_catalog;select coalesce(jsonb_agg(d order by d::text),'[]'::jsonb)::text from ("
                          "select jsonb_build_array('constraint',n.nspname,c.relname,k.conname,pg_get_constraintdef(k.oid)) d from pg_constraint k join pg_class c on c.oid=k.conrelid join pg_namespace n on n.oid=c.relnamespace where " + namespace +
                          " union all select jsonb_build_array('column',n.nspname,c.relname,a.attnum,a.attname,format_type(a.atttypid,a.atttypmod),a.attnotnull,a.attidentity,a.attgenerated,pg_get_expr(v.adbin,v.adrelid)) from pg_attribute a join pg_class c on c.oid=a.attrelid join pg_namespace n on n.oid=c.relnamespace left join pg_attrdef v on v.adrelid=a.attrelid and v.adnum=a.attnum where a.attnum>0 and not a.attisdropped and c.relkind in ('r','p') and " + namespace +
                          " union all select jsonb_build_array('view',n.nspname,c.relname,pg_get_viewdef(c.oid)) from pg_class c join pg_namespace n on n.oid=c.relnamespace where c.relkind in ('v','m') and " + namespace +
                          " union all select jsonb_build_array('index',n.nspname,c.relname,pg_get_indexdef(i.indexrelid)) from pg_index i join pg_class c on c.oid=i.indrelid join pg_namespace n on n.oid=c.relnamespace where " + namespace +
                          " union all select jsonb_build_array('trigger',n.nspname,c.relname,t.tgname,t.tgenabled,pg_get_triggerdef(t.oid)) from pg_trigger t join pg_class c on c.oid=t.tgrelid join pg_namespace n on n.oid=c.relnamespace where not t.tgisinternal and " + namespace +
                          " union all select jsonb_build_array('function',n.nspname,p.proname,pg_get_functiondef(p.oid)) from pg_proc p join pg_namespace n on n.oid=p.pronamespace where p.prokind in ('f','p') and " + namespace + ") objects;", database)
        objects = json.loads(definitions)
        for definition in objects:
            if definition[0] in {"constraint", "index", "view"}:
                definition[-1] = normalize_string_array_casts(definition[-1])
        objects.sort(key=lambda value: json.dumps(value, ensure_ascii=False))
        canonical = json.dumps(objects, ensure_ascii=False, separators=(",", ":"))
        return dict(tables=rows, sequences=sequences, ddlSha256=hashlib.sha256(canonical.encode()).hexdigest())

    original, restored = snapshot(args.source_db), snapshot(restore)
    (args.output / "source-snapshot.json").write_text(json.dumps(original, indent=2), encoding="utf-8")
    (args.output / "restore-snapshot.json").write_text(json.dumps(restored, indent=2), encoding="utf-8")
    assert original == restored, "Restored data, sequences or schema definitions differ; inspect snapshot JSON files"
    projection = "json_build_array(id,requested_by,created_at,started_at)::text"
    condition = f" from {table} where id={literal(backup)}::uuid;"
    assert sql("set timezone='UTC';select " + projection + condition) == sql("set timezone='UTC';select " + projection + condition, restore)
    assert sql("select status" + condition, restore) == "RUNNING", "The dump must retain its in-progress control record"
    assert sql(f"select max(version::int) from {identifier(args.schema)}.flyway_schema_history where success and version ~ '^[0-9]+$';", restore) == "27"
    result = dict(result="passed", sourceDatabase=args.source_db, schema=args.schema, restoreDatabase=restore, nativeBackup=backup,
                  observedStates=states, archiveBytes=len(data), archiveSha256=digest, server=server, tools=versions,
                  matchedTables=len(original["tables"]), matchedSequences=len(original["sequences"]), matchedSchemaDefinitions=True,
                  schemaCanonicalization="Only literal unbounded varchar-array to text-array cast placement; labels, operators, order, types and object names retained",
                  csrfEnforced=True, anonymousDownloadDenied=True,
                  queueDelaySeconds=float(sql(f"select extract(epoch from started_at-created_at) from {table} where id={literal(backup)}::uuid;")),
                  snapshotBoundary="The backup's own control row is RUNNING inside its dump and READY after completion; identity/actor/start timestamps match. Other rows and tables match exactly.")
    (args.output / "results.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
