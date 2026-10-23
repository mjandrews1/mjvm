# JavaM / mJVM dependencies (pinned)

Pinned **vendored** jars under `lib/`. No floating versions in release paths.

| Artifact | Version | SHA-256 | CVE check (OSV) |
|----------|---------|---------|-----------------|
| `slf4j-api.jar` | **2.0.13** | `e7c2a48e8515ba1f49fa637d57b4e2f590b3f5bd97407ac699c3aa5efb1204a9` | **0** (2026-10-11) |
| `slf4j-nop.jar` | **2.0.13** | `8962a107b4a8bdf80b6c17e470cd3614ad3329643833ff5cf0c60c7dce9deaac` | **0** (2026-10-11) |
| `sqlite-jdbc.jar` | **3.45.1.0** | `f5f5404fa5a60f9e0b15e7bea2ea2d137e255f01babd0bfcb9dafcd2e3bf9cd2` | **0** (2026-10-11) |

## Policy
- **Pin** exact versions; update only with a PR that refreshes this table + hashes
- **CVE:** block High/Critical unless dated exception below
- **CI:** optional `osv-scanner` / `trivy fs lib/` (JavaM#65 / heuristics #46)

## Exceptions
*(none)*

## Verify
```bash
shasum -a 256 lib/*.jar   # or sha256sum
```

Related: `m-semantics#18` licensing/attribution · JavaM#65.
