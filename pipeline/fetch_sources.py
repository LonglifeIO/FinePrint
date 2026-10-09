#!/usr/bin/env python3
"""Save a verbatim copy of each source page a reviewed record cites, for quote checks.

    python3 pipeline/fetch_sources.py LIST            # LIST lines: <name> <url>; '#' lines are comments
    python3 pipeline/fetch_sources.py LIST --force    # fetch again even if <name> is already saved
    python3 pipeline/fetch_sources.py --retext        # rebuild every <name>.txt from the saved pages
    python3 pipeline/fetch_sources.py --hand-saved NAME URL FILE   # register a copy saved by hand

Each page is saved as it was served (<name>.html, .pdf, .docx, .json or .raw) next to its visible text
(<name>.txt) in pipeline/raw/sources/, and pipeline/raw/sources/index.json records which URL each
name holds, so check_quotes.py can find the copy behind any source. For a page that blocks scripts,
list the address its record names as verify_url (an archived copy or the same article's API).
A page only a person can capture (its text needs JavaScript, say) is marked "manual": true on its
source: this script never fetches it, even with --force. Save it from a browser (the page as shown,
or a PDF or text of it) and register the file with --hand-saved, which keeps it as a fetched page is
kept, with "manual": true in its index entry.
Everything under pipeline/raw/ is gitignored: these are other people's copyrighted pages.
Exodus pages are never fetched (see CLAUDE.md, Exodus etiquette).
"""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import html
import json
import re
import shutil
import subprocess
import sys
import time
import zipfile
from pathlib import Path
from urllib.parse import urlparse
from zoneinfo import ZoneInfo

import requests

import check_quotes

REPO = Path(__file__).resolve().parent.parent
OUT = REPO / "pipeline" / "raw" / "sources"  # gitignored
INDEX = OUT / "index.json"
HALIFAX = ZoneInfo("America/Halifax")
USER_AGENT = "FinePrint-pipeline (+https://github.com/LonglifeIO/FinePrint)"
NOT_FETCHED_HOSTS = ("exodus-privacy.eu.org",)
NAME = re.compile(r"^[a-z0-9][a-z0-9.-]*$")
HAND_SAVED = {".html": ("text/html", ".html"), ".htm": ("text/html", ".html"), ".pdf": ("application/pdf", ".pdf"),
              ".txt": ("text/plain", ".raw")}  # a browser's file -> (content type, the suffix it's kept under)


def decode(data: bytes, content_type: str = "") -> str:
    """UTF-8 when it is (archived pages often omit the charset), else the charset the server or the
    page declares (windows-1251 for Russian official texts), else Windows-1252."""
    try:
        return data.decode("utf-8")
    except UnicodeDecodeError:
        pass
    declared = re.search(r"charset=[\"']?([A-Za-z0-9_-]+)", content_type) or re.search(rb"charset=[\"']?([A-Za-z0-9_-]+)", data[:4096])
    if declared:
        charset = declared.group(1)
        try:
            return data.decode(charset if isinstance(charset, str) else charset.decode("ascii"))
        except (LookupError, UnicodeDecodeError):
            pass
    return data.decode("cp1252", errors="replace")


def text_of_html(page: str) -> str:
    """The page's visible text, one block per line."""
    # Comments go in the same pass as these blocks, whichever opens first, as in a browser: a comment that
    # names <noscript> (placer.ai's does) opens no block, and a script's "<!--" opens no comment. A comment
    # ends at --> or --!>; <!--> and <!---> are empty ones.
    page = re.sub(r"(?is)<!--(?:-?>|.*?--!?>)|<(script|style|noscript|template)\b.*?</\1>", " ", page)
    page = re.sub(r"(?i)<br\s*/?>|</(p|div|li|h[1-6]|tr|section|article|blockquote|dd|dt)>", "\n", page)
    page = re.sub(r"(?s)<[^>]+>", " ", page)
    page = html.unescape(page).replace(" ", " ")
    page = re.sub(r"[ \t\r\f\v]+", " ", page)
    return "\n".join(line.strip() for line in page.splitlines() if line.strip())


def text_of_pdf(path: Path) -> str:
    """macOS PDFKit (the Mac mini), else poppler's pdftotext."""
    if shutil.which("osascript"):
        script = ("ObjC.import('PDFKit'); var d = $.PDFDocument.alloc.initWithURL($.NSURL.fileURLWithPath(%s)); d.string.js"
                  % json.dumps(str(path.resolve())))
        return subprocess.run(["osascript", "-l", "JavaScript", "-e", script],
                              capture_output=True, text=True, check=True).stdout
    return subprocess.run(["pdftotext", "-layout", str(path), "-"], capture_output=True, text=True, check=True).stdout


def text_of_docx(path: Path) -> str:
    """A Word file's paragraphs, one per line (China's national law database serves laws as .docx)."""
    with zipfile.ZipFile(path) as z:
        body = z.read("word/document.xml").decode("utf-8")
    paragraphs = (html.unescape(re.sub(r"<[^>]+>", "", p)).strip() for p in re.findall(r"(?s)<w:p[ >].*?</w:p>", body))
    return "\n".join(p for p in paragraphs if p)


def text_of(raw: Path, content_type: str = "") -> str:
    if raw.suffix == ".pdf":
        return text_of_pdf(raw)
    if raw.suffix == ".docx":
        return text_of_docx(raw)
    text = decode(raw.read_bytes(), content_type)
    if raw.suffix == ".json":
        body = json.loads(text)
        article = body.get("article") if isinstance(body, dict) else None  # a help centre's article API
        return text_of_html(article["body"]) if isinstance(article, dict) and "body" in article else text
    return text_of_html(text) if raw.suffix == ".html" else text


def suffix_for(ctype: str, content: bytes) -> str:
    """The file type a page is saved as, which decides how text_of reads it (ctype in lower case)."""
    if "pdf" in ctype or content[:5] == b"%PDF-":
        return ".pdf"
    if "wordprocessingml" in ctype:  # before "xml": the Word type is application/vnd.openxmlformats-…
        return ".docx"
    if "json" in ctype:
        return ".json"
    if "html" in ctype or "xml" in ctype:
        return ".html"
    return ".raw"


def save(name: str, url: str, resp: requests.Response) -> dict:
    ctype = resp.headers.get("content-type", "").lower()
    raw = OUT / f"{name}{suffix_for(ctype, resp.content)}"
    raw.write_bytes(resp.content)
    (OUT / f"{name}.txt").write_text(text_of(raw, ctype), encoding="utf-8")
    return {
        "url": url,
        "final_url": resp.url,
        "content_type": ctype,
        "file": raw.name,
        "sha256": hashlib.sha256(resp.content).hexdigest(),
        "fetched_at": dt.datetime.now(HALIFAX).isoformat(timespec="seconds"),
    }


def hand_saved(name: str, url: str, file: Path) -> dict:
    """A copy a person saved from a browser, kept as a fetched page is; saved_at is when the file was written."""
    if not NAME.match(name) or not urlparse(url).scheme.startswith("http") or file.suffix.lower() not in HAND_SAVED:
        raise ValueError(f"expected NAME URL FILE (.html, .htm, .pdf or .txt), got {name!r} {url!r} {file.name!r}")
    ctype, suffix = HAND_SAVED[file.suffix.lower()]
    content = file.read_bytes()
    raw = OUT / f"{name}{suffix}"
    raw.write_bytes(content)
    (OUT / f"{name}.txt").write_text(text_of(raw, ctype), encoding="utf-8")
    return {
        "url": url,
        "final_url": url,
        "content_type": ctype,
        "file": raw.name,
        "sha256": hashlib.sha256(content).hexdigest(),
        "saved_at": dt.datetime.fromtimestamp(file.stat().st_mtime, HALIFAX).isoformat(timespec="seconds"),
        "manual": True,
    }


def read_list(path: Path) -> list[tuple[str, str]]:
    entries = []
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        name, _, url = line.partition(" ")
        url = url.strip()
        if not NAME.match(name) or not urlparse(url).scheme.startswith("http"):
            raise ValueError(f"{path.name}:{number}: expected '<name> <url>', got {line!r}")
        entries.append((name, url))
    return entries


def load_index() -> dict:
    try:
        return json.loads(INDEX.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return {}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("list", type=Path, nargs="?", help="file of '<name> <url>' lines")
    parser.add_argument("--force", action="store_true", help="fetch again names that are already saved")
    parser.add_argument("--retext", action="store_true", help="rebuild the .txt files from the saved pages, no fetching")
    parser.add_argument("--delay", type=float, default=2.0, help="seconds between requests")
    parser.add_argument("--hand-saved", nargs=3, metavar=("NAME", "URL", "FILE"),
                        help="register a copy of a manual source saved by hand (.html, .htm, .pdf or .txt)")
    args = parser.parse_args()

    index = load_index()
    if args.hand_saved:
        name, url, file = args.hand_saved
        if name in index and index[name]["url"] != url:
            print(f"  ERROR {name} already holds {index[name]['url']}", file=sys.stderr)
            return 1
        OUT.mkdir(parents=True, exist_ok=True)
        index[name] = hand_saved(name, url, Path(file))
        INDEX.write_text(json.dumps(index, indent=1, sort_keys=True), encoding="utf-8")
        print(f"  saved by hand  {name}  {url}")
        if url not in check_quotes.manual_urls(check_quotes.REVIEWED):
            print('  note: no reviewed source marks this url "manual": true yet')
        return 0
    if args.retext:
        for name, entry in sorted(index.items()):
            (OUT / f"{name}.txt").write_text(text_of(OUT / entry["file"], entry.get("content_type", "")), encoding="utf-8")
        print(f"rebuilt {len(index)} text copies in {OUT}")
        return 0
    if args.list is None:
        parser.error("give a LIST file, or --retext")

    OUT.mkdir(parents=True, exist_ok=True)
    failed, manual = 0, check_quotes.manual_urls(check_quotes.REVIEWED)
    for name, url in read_list(args.list):
        host = urlparse(url).hostname or ""
        if host.endswith(NOT_FETCHED_HOSTS):
            print(f"  skipped (Exodus etiquette)  {name}")
            continue
        if url in manual:  # only a person can capture it; a fetch would overwrite their copy with an empty shell
            print(f"  skipped (manual: saved by hand, see --hand-saved)  {name}")
            continue
        if name in index and not args.force:
            if index[name]["url"] != url:
                print(f"  ERROR {name} already holds {index[name]['url']}", file=sys.stderr)
                failed += 1
            continue
        try:
            resp = requests.get(url, timeout=60, headers={"User-Agent": USER_AGENT}, allow_redirects=True)
        except requests.RequestException as e:
            print(f"  error: {e.__class__.__name__}  {name}  {url}")
            failed += 1
            continue
        if resp.status_code == 200:
            index[name] = save(name, url, resp)
            print(f"  200  {len(resp.content):>9,}  {name}")
        else:
            print(f"  {resp.status_code}  {name}  {url}  (blocked? name an archived copy as verify_url)")
            failed += 1
        INDEX.write_text(json.dumps(index, indent=1, sort_keys=True), encoding="utf-8")
        time.sleep(args.delay)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
