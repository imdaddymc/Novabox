"""Builds release/logos.tsv.gz: normalized channel name -> logo URL, from the iptv-org database.

Run: python3 tools/build_logos.py channels.json logos.json release/logos.tsv.gz
(channels.json and logos.json from https://iptv-org.github.io/api/). NovaBox downloads the result once a
month and matches the portal's channel names against it (see data/Logos.kt, which normalizes the same way).
"""
import gzip, json, re, sys, unicodedata

def norm(name):
    s = unicodedata.normalize("NFKD", name).encode("ascii", "ignore").decode().upper()
    s = re.sub(r"^\s*[A-Z]{2,4}\s*[|:\-]\s*", "", s)            # "PK| ", "UK: ", "IN - "
    s = re.sub(r"[\[(].*?[\])]", " ", s)                          # "(1080p)", "[Geo-blocked]"
    s = re.sub(r"\b(F?HD|UHD|SD|4K|8K|HEVC|H265|1080P?|720P?|HQ|LQ|RAW|BACKUP|VIP|LIVE)\b", " ", s)
    s = s.replace("&", "AND").replace("+", "PLUS")
    return re.sub(r"[^A-Z0-9]", "", s)

chans = json.load(open(sys.argv[1]))
logos = json.load(open(sys.argv[2]))
best = {}
for l in logos:
    if not l.get("url") or l.get("in_use") is False:
        continue
    score = (l.get("feed") is None, l.get("format") in ("PNG", "SVG", "WebP"), min(l.get("width") or 0, 600))
    cur = best.get(l["channel"])
    if cur is None or score > cur[0]:
        best[l["channel"]] = (score, l["url"])
rows = {}
for c in chans:
    if c.get("is_nsfw") or c["id"] not in best or c.get("closed"):
        continue
    url = best[c["id"]][1]
    country = c.get("country") or ""
    for n in [c["name"]] + list(c.get("alt_names") or []):
        k = norm(n)
        if len(k) < 2:
            continue
        rows.setdefault(k, {}).setdefault(country, url)
with gzip.open(sys.argv[3], "wt", encoding="utf-8") as f:
    for k in sorted(rows):
        for country, url in rows[k].items():
            f.write(f"{k}\t{country}\t{url}\n")
print(len(rows), "names")
