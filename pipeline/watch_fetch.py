"""The watcher's only way onto the network: one identified, polite client (see CLAUDE.md, the watcher).

Every request names FinePrint and the owner's contact. Before any path on a host, its robots.txt is read;
a disallow, a 401, 403 or 451, or a redirect to a not-found page is a refusal and parks the URL. A refusal is never retried with another
client, and there is no browser and no JavaScript. Requests go one at a time, at least GAP seconds apart
per host, with the validators of the last copy (ETag, Last-Modified). 429 and 5xx are retried with
exponential backoff, honouring Retry-After, TRIES times in all. Redirects are followed one hop at a time,
each hop checked against the denylist and robots.txt first. Timeout TIMEOUT s; bodies over BODY_CAP (or a
source's own max_bytes in sources.json) are dropped.
"""
from __future__ import annotations

import email.utils
import time
import urllib.robotparser
from dataclasses import dataclass, field
from urllib.parse import urljoin, urlparse

import requests

from build import NOT_FOUND  # the same not-found test as build.py's URL check

VERSION = "0.1"
TIMEOUT = 30
BODY_CAP = 5 * 1024 * 1024
GAP = 5.0          # seconds between two requests to one host
TRIES = 3
MAX_WAIT = 120.0   # the longest backoff or Retry-After one run waits out; longer ones fail until the next run
MAX_HOPS = 5
REFUSED = (401, 403, 451)
RETRIED = (429, 500, 502, 503, 504)
REDIRECTS = (301, 302, 303, 307, 308)
NOT_FOUND_REASON = "redirects the watcher to a not-found page"


def user_agent(contact: str) -> str:
    return f"FinePrint-watcher/{VERSION} (+https://github.com/LonglifeIO/FinePrint; {contact})"


def denied(url: str, denylist: list[str]) -> str | None:
    """The denylist entry ruling this URL out: a host (its subdomains too), or a host and a path prefix."""
    parts = urlparse(url)
    host = (parts.hostname or "").lower()
    for rule in denylist:
        rule_host, _, rule_path = rule.lower().partition("/")
        if (host == rule_host or host.endswith("." + rule_host)) and parts.path.lower().lstrip("/").startswith(rule_path):
            return rule
    return None


@dataclass
class Fetched:
    """What one fetch came to. outcome: ok, unchanged (304), refused (denylist), parked (robots.txt, a
    401/403/451 or a redirect to a not-found page), failed (anything else), redirect (internal: one hop)."""
    outcome: str
    url: str
    final_url: str = ""
    status: int | None = None
    body: bytes = b""
    headers: dict = field(default_factory=dict)
    reason: str = ""
    evidence: bytes = b""  # what came back instead of the page (robots.txt, an error body), for dedupe


class Fetcher:
    def __init__(self, contact: str, denylist: list[str], http=requests, sleep=time.sleep, clock=time.monotonic):
        if not contact or not contact.strip():
            raise ValueError("no contact: the watcher identifies itself in every request, so it refuses to run without one")
        self.agent = user_agent(contact.strip())
        self.denylist, self.http, self.sleep, self.clock = denylist, http, sleep, clock
        self.last: dict[str, float] = {}        # host -> when its last request finished
        self.robots: dict[str, object] = {}     # scheme://host -> RobotFileParser, or the Fetched failure
        self.robots_text: dict[str, bytes] = {}
        self.robots_refused: dict[str, int] = {}  # scheme://host -> the status its robots.txt request got, if refused
        self.requests = 0

    def fetch(self, url: str, meta: dict | None = None, max_bytes: int = BODY_CAP) -> Fetched:
        """GET url, following redirects hop by hop. meta is the last copy's (etag, last_modified, final_url)."""
        meta = meta or {}
        current = url
        for _ in range(MAX_HOPS + 1):
            rule = denied(current, self.denylist)
            if rule:
                return Fetched("refused", url, current, reason=f"{rule} is on the denylist", evidence=rule.encode())
            blocked = self.blocked(current)
            if blocked:
                blocked.url, blocked.final_url = url, current
                return blocked
            headers = {}
            if current == meta.get("final_url", url):  # the validators belong to the copy we hold
                if meta.get("etag"):
                    headers["If-None-Match"] = meta["etag"]
                if meta.get("last_modified"):
                    headers["If-Modified-Since"] = meta["last_modified"]
            got = self.attempts(current, headers, max_bytes=max_bytes)
            if got.outcome != "redirect":
                got.url, got.final_url = url, got.final_url or current
                return got
            if NOT_FOUND.search(urlparse(got.final_url).path):  # a refusal, not a move: parked like a 403, never followed
                return Fetched("parked", url, got.final_url, got.status, reason=NOT_FOUND_REASON)
            current = got.final_url
        return Fetched("failed", url, current, reason=f"more than {MAX_HOPS} redirects")

    def blocked(self, url: str) -> Fetched | None:
        """None if robots.txt lets us fetch url; otherwise why not (parked, or failed if it can't be read)."""
        parts = urlparse(url)
        origin = f"{parts.scheme}://{parts.netloc}"
        if origin not in self.robots:
            self.robots[origin] = self.read_robots(origin)
        rules = self.robots[origin]
        if isinstance(rules, Fetched):
            return Fetched(rules.outcome, url, url, rules.status, reason=rules.reason, evidence=rules.evidence)
        if not rules.can_fetch(self.agent, url):
            refused = self.robots_refused.get(origin)
            reason = (f"robots.txt at {origin} answered HTTP {refused}, which counts as disallowing everything" if refused
                      else f"robots.txt at {origin} disallows this path")
            return Fetched("parked", url, url, reason=reason, evidence=self.robots_text[origin])
        return None

    def read_robots(self, origin: str):
        """RFC 9309, cautiously: 200 is read; 401/403 forbid everything (as urllib.robotparser has it); any other
        4xx allows everything; 5xx or no answer means robots.txt can't be checked, so nothing is fetched."""
        got = self.attempts(origin + "/robots.txt", {}, follow=True)
        rules = urllib.robotparser.RobotFileParser(origin + "/robots.txt")
        self.robots_text[origin] = got.body or got.evidence
        if got.outcome == "ok":
            rules.parse(got.body.decode("utf-8", errors="replace").splitlines())
        elif got.status in REFUSED:
            rules.disallow_all = True
            self.robots_refused[origin] = got.status
        elif got.status and 400 <= got.status < 500:
            rules.allow_all = True
        else:
            return Fetched("failed", origin, status=got.status, reason=f"robots.txt can't be read ({got.reason or got.status})")
        return rules

    def attempts(self, url: str, headers: dict, follow: bool = False, max_bytes: int = BODY_CAP) -> Fetched:
        error, status = "", None
        for attempt in range(TRIES):
            wait = GAP * 2 ** (attempt + 1)
            try:
                status, got_headers, body = self.request(url, headers, follow, max_bytes)
            except requests.RequestException as e:
                error, status = e.__class__.__name__, None
            else:
                if status in REDIRECTS and got_headers.get("location") and not follow:
                    return Fetched("redirect", url, urljoin(url, got_headers["location"]), status)
                if status == 304:
                    return Fetched("unchanged", url, url, 304, headers=got_headers)
                if status == 200:
                    if body is None:
                        return Fetched("failed", url, url, 200, reason=f"body over {max_bytes / (1024 * 1024):g} MB")
                    return Fetched("ok", url, url, 200, body, got_headers)
                if status in REFUSED:
                    return Fetched("parked", url, url, status, reason=f"HTTP {status}", evidence=body or b"")
                if status not in RETRIED:
                    return Fetched("failed", url, url, status, reason=f"HTTP {status}", evidence=body or b"")
                error = f"HTTP {status}"
                wait = retry_after(got_headers.get("retry-after")) or wait
            if attempt + 1 < TRIES:
                if wait > MAX_WAIT:
                    return Fetched("failed", url, url, status, reason=f"{error}; asked to wait {wait:.0f} s, left for the next run")
                self.sleep(wait)
        return Fetched("failed", url, url, status, reason=f"{error} after {TRIES} tries")

    def request(self, url: str, headers: dict, follow: bool, max_bytes: int = BODY_CAP) -> tuple[int, dict, bytes | None]:
        """One GET, after this host's turn comes round. The body is None if it runs past max_bytes."""
        host = urlparse(url).hostname or ""
        gap = GAP - (self.clock() - self.last.get(host, float("-inf")))
        if gap > 0:
            self.sleep(gap)
        self.requests += 1
        try:
            resp = self.http.get(url, headers={"User-Agent": self.agent, **headers}, timeout=TIMEOUT,
                                 allow_redirects=follow, stream=True)
            try:
                return resp.status_code, {k.lower(): v for k, v in resp.headers.items()}, read_capped(resp, max_bytes)
            finally:
                resp.close()
        finally:
            self.last[host] = self.clock()


def read_capped(resp, max_bytes: int = BODY_CAP) -> bytes | None:
    chunks, size = [], 0
    for chunk in resp.iter_content(65536):
        size += len(chunk)
        if size > max_bytes:
            return None
        chunks.append(chunk)
    return b"".join(chunks)


def retry_after(value: str | None) -> float | None:
    """Retry-After as seconds: a number, or an HTTP date."""
    if not value:
        return None
    if value.strip().isdigit():
        return float(value.strip())
    try:
        when = email.utils.parsedate_to_datetime(value)
    except (TypeError, ValueError):
        return None
    return max(0.0, when.timestamp() - time.time())
