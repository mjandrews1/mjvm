/* cdb2pipe — persistent Comdb2 client for JavaM Cdb2Store.
 * Line protocol on stdin/stdout (hex fields, tab-separated):
 *   SET <hexk> <hexsk> <hexval> <isnum> <hexname>
 *   GET <hexk>
 *   ORDER <hexlo> <hexhi> <hexskgt_or_sklt> <dir>
 *   KILL <hexk>
 *   DATA <hexk>
 *   PING
 *   QUIT
 * Responses:
 *   OK | ERR <msg> | VAL <hexval> <isnum> | EMPTY | KEY <hexk> | NUM <n> | PONG
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <ctype.h>
#include <cdb2api.h>
#include <time.h>

static cdb2_hndl_tp *db;
static long next_id = 1;
static int in_txn = 0;

static void die(const char *m) {
    fprintf(stderr, "cdb2pipe: %s\n", m);
    exit(1);
}

static int hexval(int c) {
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    return -1;
}

/* decode hex into malloc'd buffer; returns length or -1 */
static int unhex(const char *s, char **out) {
    size_t n = strlen(s);
    if (n % 2) return -1;
    char *b = malloc(n / 2 + 1);
    if (!b) return -1;
    for (size_t i = 0; i < n; i += 2) {
        int hi = hexval((unsigned char)s[i]), lo = hexval((unsigned char)s[i + 1]);
        if (hi < 0 || lo < 0) { free(b); return -1; }
        b[i / 2] = (char)((hi << 4) | lo);
    }
    b[n / 2] = 0;
    *out = b;
    return (int)(n / 2);
}

static void phex(const char *b, int n) {
    static const char *H = "0123456789abcdef";
    for (int i = 0; i < n; i++) {
        unsigned char c = (unsigned char)b[i];
        putchar(H[c >> 4]);
        putchar(H[c & 15]);
    }
}

static int split_tabs(char *line, char **tok, int max);

static int run(const char *sql) {
    int rc = cdb2_run_statement(db, sql);
    if (rc) {
        printf("ERR run rc=%d %s\n", rc, cdb2_errstr(db));
        return -1;
    }
    return 0;
}

static int bind_and_run(const char *sql, const char **names, const int *types,
                        const void **vals, const int *lens, int n) {
    cdb2_clearbindings(db);
    for (int i = 0; i < n; i++) {
        int rc = cdb2_bind_param(db, names[i], types[i], vals[i], lens[i]);
        if (rc) {
            printf("ERR bind %s rc=%d\n", names[i], rc);
            return -1;
        }
    }
    return run(sql);
}

static void drain(void) {
    while (cdb2_next_record(db) == CDB2_OK) {
    }
}

static int first_row(void) {
    int rc = cdb2_next_record(db);
    return rc == CDB2_OK;
}

static void ensure_schema(void) {
    /* best-effort; ignore "already exists" */
    cdb2_run_statement(db,
        "CREATE TABLE IF NOT EXISTS mnodes("
        "id INTEGER PRIMARY KEY,"
        "name VARCHAR(64),"
        "k VARCHAR(512),"
        "sk VARCHAR(512),"
        "val VARCHAR(512),"
        "isnum INTEGER)");
    while (cdb2_next_record(db) == CDB2_OK) {
    }
    cdb2_run_statement(db, "CREATE UNIQUE INDEX IF NOT EXISTS mnodes_k ON mnodes(k)");
    while (cdb2_next_record(db) == CDB2_OK) {
    }
    cdb2_run_statement(db, "CREATE INDEX IF NOT EXISTS mnodes_nsk ON mnodes(name, sk)");
    while (cdb2_next_record(db) == CDB2_OK) {
    }
    cdb2_run_statement(db, "CREATE INDEX IF NOT EXISTS mnodes_sk ON mnodes(sk)");
    while (cdb2_next_record(db) == CDB2_OK) {
    }
    cdb2_run_statement(db,
        "CREATE TABLE IF NOT EXISTS mlocks("
        "lk VARCHAR(255) PRIMARY KEY,"
        "pid VARCHAR(64),"
        "at INTEGER)");
    while (cdb2_next_record(db) == CDB2_OK) {
    }
}

static int upsert_row(const char *k, int kl, const char *sk, int sl,
                      const char *val, int vl, int64_t isnum,
                      const char *name, int nl) {
    int64_t id = (int64_t)next_id++;
    const char *names[] = {"id", "name", "k", "sk", "val", "isnum"};
    int types[] = {CDB2_INTEGER, CDB2_CSTRING, CDB2_CSTRING, CDB2_CSTRING, CDB2_CSTRING, CDB2_INTEGER};
    const void *vals[] = {&id, name, k, sk, val, &isnum};
    int lens[] = {(int)sizeof(int64_t), nl, kl, sl, vl, (int)sizeof(int64_t)};
    if (bind_and_run(
            "INSERT OR REPLACE INTO mnodes(id,name,k,sk,val,isnum) "
            "VALUES(@id,@name,@k,@sk,@val,@isnum)",
            names, types, vals, lens, 6) < 0) {
        return -1;
    }
    drain();
    return 0;
}


/* LOCKA <hexpid> <hexlk> — try insert; OK if held by this pid or inserted.
 * LOCKR <hexpid> <hexlk> — delete if pid matches.
 */
static void cmd_locka(char **tok, int ntok) {
    if (ntok < 3) { printf("ERR bad LOCKA\n"); return; }
    char *pid, *lk;
    int pl = unhex(tok[1], &pid), ll = unhex(tok[2], &lk);
    if (pl < 0 || ll < 0) { printf("ERR hex\n"); return; }
    int64_t now = (int64_t)time(NULL);
    const char *nn[] = {"lk", "pid", "at"};
    int tt[] = {CDB2_CSTRING, CDB2_CSTRING, CDB2_INTEGER};
    const void *vv[] = {lk, pid, &now};
    int ls[] = {ll, pl, (int)sizeof(int64_t)};
    cdb2_clearbindings(db);
    for (int i = 0; i < 3; i++) {
        cdb2_bind_param(db, nn[i], tt[i], vv[i], ls[i]);
    }
    if (cdb2_run_statement(db,
            "INSERT INTO mlocks(lk,pid,at) VALUES(@lk,@pid,@at)") == 0) {
        drain();
        free(pid); free(lk);
        printf("OK\n");
        return;
    }
    drain();
    /* exists — check owner */
    const char *n2[] = {"lk", "pid"};
    int t2[] = {CDB2_CSTRING, CDB2_CSTRING};
    const void *v2[] = {lk, pid};
    int l2[] = {ll, pl};
    cdb2_clearbindings(db);
    for (int i = 0; i < 2; i++) cdb2_bind_param(db, n2[i], t2[i], v2[i], l2[i]);
    if (cdb2_run_statement(db, "SELECT pid FROM mlocks WHERE lk=@lk AND pid=@pid") == 0) {
        if (first_row()) {
            drain();
            free(pid); free(lk);
            printf("OK\n");
            return;
        }
        drain();
    }
    free(pid); free(lk);
    printf("FAIL\n");
}

static void cmd_lockr(char **tok, int ntok) {
    if (ntok < 3) { printf("ERR bad LOCKR\n"); return; }
    char *pid, *lk;
    int pl = unhex(tok[1], &pid), ll = unhex(tok[2], &lk);
    if (pl < 0 || ll < 0) { printf("ERR hex\n"); return; }
    const char *n2[] = {"lk", "pid"};
    int t2[] = {CDB2_CSTRING, CDB2_CSTRING};
    const void *v2[] = {lk, pid};
    int l2[] = {ll, pl};
    cdb2_clearbindings(db);
    for (int i = 0; i < 2; i++) cdb2_bind_param(db, n2[i], t2[i], v2[i], l2[i]);
    if (cdb2_run_statement(db, "DELETE FROM mlocks WHERE lk=@lk AND pid=@pid") == 0) {
        drain();
    }
    free(pid); free(lk);
    printf("OK\n");
}

static void cmd_set(char **tok, int ntok) {
    if (ntok < 6) { printf("ERR bad SET\n"); return; }
    char *k, *sk, *val, *name;
    int kl = unhex(tok[1], &k), sl = unhex(tok[2], &sk),
        vl = unhex(tok[3], &val), nl = unhex(tok[5], &name);
    int64_t isnum = atoi(tok[4]);
    if (kl < 0 || sl < 0 || vl < 0 || nl < 0) { printf("ERR hex\n"); return; }
    int rc = upsert_row(k, kl, sk, sl, val, vl, isnum, name, nl);
    free(k); free(sk); free(val); free(name);
    printf(rc ? "ERR upsert\n" : "OK\n");
}

/* SETB <n> then n lines: <hexk> <hexsk> <hexval> <isnum> <hexname>
 * One multi-row INSERT OR REPLACE in a single transaction. */
static void cmd_setb(char **tok, int ntok) {
    if (ntok < 2) { printf("ERR bad SETB\n"); return; }
    int n = atoi(tok[1]);
    if (n < 1 || n > 100000) { printf("ERR bad SETB n\n"); return; }

    /* gather rows */
    char **ks = calloc(n, sizeof(char *)), **sks = calloc(n, sizeof(char *));
    char **vals = calloc(n, sizeof(char *)), **names = calloc(n, sizeof(char *));
    int *kls = calloc(n, sizeof(int)), *sls = calloc(n, sizeof(int));
    int *vls = calloc(n, sizeof(int)), *nls = calloc(n, sizeof(int));
    int64_t *isnums = calloc(n, sizeof(int64_t));
    int64_t *ids = calloc(n, sizeof(int64_t));
    char line[8192];
    int got = 0;
    for (int i = 0; i < n; i++) {
        if (!fgets(line, sizeof(line), stdin)) { printf("ERR SETB eof\n"); goto out; }
        size_t L = strlen(line);
        while (L && (line[L-1]=='\n' || line[L-1]=='\r')) line[--L]=0;
        char *t[8];
        int nt = split_tabs(line, t, 8);
        if (nt < 5) { printf("ERR SETB row\n"); goto out; }
        kls[i] = unhex(t[0], &ks[i]);
        sls[i] = unhex(t[1], &sks[i]);
        vls[i] = unhex(t[2], &vals[i]);
        isnums[i] = (int64_t)atoi(t[3]);
        nls[i] = unhex(t[4], &names[i]);
        if (kls[i] < 0 || sls[i] < 0 || vls[i] < 0 || nls[i] < 0) {
            printf("ERR SETB hex\n"); goto out;
        }
        ids[i] = (int64_t)next_id++;
        got++;
    }

    /* one INSERT OR REPLACE with n value tuples, positional binds */
    size_t cap = 256 + (size_t)n * 40;
    char *sql = malloc(cap);
    if (!sql) { printf("ERR oom\n"); goto out; }
    strcpy(sql, "INSERT OR REPLACE INTO mnodes(id,name,k,sk,val,isnum) VALUES ");
    for (int i = 0; i < n; i++) {
        if (i) strcat(sql, ",");
        strcat(sql, "(?,?,?,?,?,?)");
        if (strlen(sql) + 32 > cap) {
            cap *= 2;
            sql = realloc(sql, cap);
            if (!sql) { printf("ERR oom\n"); goto out; }
        }
    }

    cdb2_clearbindings(db);
    {
        int idx = 1;
        for (int i = 0; i < n; i++) {
            if (cdb2_bind_index(db, idx++, CDB2_INTEGER, &ids[i], (int)sizeof(int64_t))
             || cdb2_bind_index(db, idx++, CDB2_CSTRING, names[i], nls[i])
             || cdb2_bind_index(db, idx++, CDB2_CSTRING, ks[i], kls[i])
             || cdb2_bind_index(db, idx++, CDB2_CSTRING, sks[i], sls[i])
             || cdb2_bind_index(db, idx++, CDB2_CSTRING, vals[i], vls[i])
             || cdb2_bind_index(db, idx++, CDB2_INTEGER, &isnums[i], (int)sizeof(int64_t))) {
                printf("ERR bind index %d\n", idx);
                free(sql);
                goto out;
            }
        }
    }
    /* Wrap in explicit transaction for large batches — auto-commit fsync
     * dominates for 50+ rows. Small batches (1-5 rows) are faster with
     * auto-commit since BEGIN/COMMIT overhead exceeds fsync savings.
     * Measured on U02: 50 rows 110ms (auto) vs 15ms (txn) = 7x faster. */
    int rc;
    if (n >= 10) {
        cdb2_run_statement(db, "BEGIN TRANSACTION");
        drain();
        rc = cdb2_run_statement(db, sql);
        free(sql);
        if (rc) {
            printf("ERR SETB rc=%d %s\n", rc, cdb2_errstr(db));
            cdb2_run_statement(db, "ROLLBACK");
            drain();
            goto out;
        }
        drain();
        cdb2_run_statement(db, "COMMIT");
        drain();
    } else {
        rc = cdb2_run_statement(db, sql);
        free(sql);
        if (rc) {
            printf("ERR SETB rc=%d %s\n", rc, cdb2_errstr(db));
            goto out;
        }
        drain();
    }
    printf("OK\n");
out:
    for (int i = 0; i < got; i++) {
        free(ks[i]); free(sks[i]); free(vals[i]); free(names[i]);
    }
    free(ks); free(sks); free(vals); free(names);
    free(kls); free(sls); free(vls); free(nls); free(isnums); free(ids);
}

static void cmd_get(char **tok, int ntok) {
    if (ntok < 2) { printf("ERR bad GET\n"); return; }
    char *k;
    int kl = unhex(tok[1], &k);
    if (kl < 0) { printf("ERR hex\n"); return; }
    const char *names[] = {"k"};
    int types[] = {CDB2_CSTRING};
    const void *vals[] = {k};
    int lens[] = {kl};
    if (bind_and_run("SELECT val, isnum FROM mnodes WHERE k=@k", names, types, vals, lens, 1) < 0) {
        free(k);
        return;
    }
    if (!first_row()) {
        drain();
        free(k);
        printf("EMPTY\n");
        return;
    }
    const char *val = cdb2_column_value(db, 0);
    int vlen = val ? (int)strlen(val) : 0;
    int isnum = 0;
    const void *np = cdb2_column_value(db, 1);
    if (np) isnum = *(int *)np;
    printf("VAL ");
    phex(val ? val : "", val ? vlen : 0);
    printf(" %d\n", isnum);
    drain();
    free(k);
}

static void cmd_order(char **tok, int ntok) {
    if (ntok < 5) { printf("ERR bad ORDER\n"); return; }
    /* ORDER <hexname> <hexsk> <hexskhi> <dir> — (name,sk) index range */
    char *name, *sk, *skhi;
    int nl = unhex(tok[1], &name), sl = unhex(tok[2], &sk), hl = unhex(tok[3], &skhi);
    int dir = atoi(tok[4]);
    if (nl < 0 || sl < 0 || hl < 0) { printf("ERR hex\n"); return; }
    const char *sql = dir > 0
        ? "SELECT k FROM mnodes WHERE name=@name AND sk>@sk AND sk<@skhi ORDER BY sk LIMIT 1"
        : "SELECT k FROM mnodes WHERE name=@name AND sk<@sk AND sk>@skhi ORDER BY sk DESC LIMIT 1";
    const char *names[] = {"name", "sk", "skhi"};
    int types[] = {CDB2_CSTRING, CDB2_CSTRING, CDB2_CSTRING};
    const void *vals[] = {name, sk, skhi};
    int lens[] = {nl, sl, hl};
    if (bind_and_run(sql, names, types, vals, lens, 3) < 0) {
        free(name); free(sk); free(skhi);
        return;
    }
    if (!first_row()) {
        drain();
        free(name); free(sk); free(skhi);
        printf("EMPTY\n");
        return;
    }
    const char *k = cdb2_column_value(db, 0);
    int kl = k ? (int)strlen(k) : 0;
    printf("KEY ");
    phex(k ? k : "", k ? kl : 0);
    printf("\n");
    drain();
    free(name); free(sk); free(skhi);
}


/* GETB <n> then n lines: <hexk>
 * Returns n response lines: VAL <hexval> <isnum> | EMPTY
 * Uses single WHERE k IN (...) query for all keys. */
static void cmd_getb(char **tok, int ntok) {
    if (ntok < 2) { printf("ERR bad GETB\n"); return; }
    int n = atoi(tok[1]);
    if (n < 1 || n > 1000) { printf("ERR bad GETB n\n"); return; }

    /* Gather keys */
    char line[8192];
    char **ks = calloc(n, sizeof(char *));
    int *kls = calloc(n, sizeof(int));
    int got = 0;
    for (int i = 0; i < n; i++) {
        if (!fgets(line, sizeof(line), stdin)) { printf("ERR GETB eof\n"); goto out; }
        size_t L = strlen(line);
        while (L && (line[L-1]=='\n' || line[L-1]=='\r')) line[--L]=0;
        char *t[2];
        int nt = split_tabs(line, t, 2);
        if (nt < 1) { ks[i] = NULL; kls[i] = -1; continue; }
        kls[i] = unhex(t[0], &ks[i]);
        if (kls[i] < 0) { ks[i] = NULL; }
        got++;
    }

    /* Build single query: SELECT k, val, isnum FROM mnodes WHERE k IN (?,?,...) */
    {
        size_t cap = 128 + (size_t)n * 4;
        char *sql = malloc(cap);
        if (!sql) { printf("ERR oom\n"); goto out; }
        strcpy(sql, "SELECT k, val, isnum FROM mnodes WHERE k IN (");
        for (int i = 0; i < n; i++) {
            if (i) strcat(sql, ",");
            strcat(sql, "?");
        }
        strcat(sql, ")");

        /* Bind all keys positionally */
        cdb2_clearbindings(db);
        int bound = 0;
        for (int i = 0; i < n; i++) {
            if (ks[i] == NULL) continue;
            if (cdb2_bind_index(db, i + 1, CDB2_CSTRING, ks[i], kls[i])) {
                printf("ERR bind %d\n", i);
                free(sql);
                goto out;
            }
            bound++;
        }

        if (cdb2_run_statement(db, sql) != 0) {
            printf("ERR GETB rc=%s\n", cdb2_errstr(db));
            free(sql);
            goto out;
        }
        free(sql);

        /* Store results in a map keyed by k */
        /* For simplicity, collect all rows then match */
        /* Actually, just emit in order — but SQL IN doesn't guarantee order */
        /* So collect into arrays */
        char **vals = calloc(n, sizeof(char *));
        int *vlens = calloc(n, sizeof(int));
        int *isnums = calloc(n, sizeof(int));
        char **found_keys = calloc(n, sizeof(char *));
        int *fkls = calloc(n, sizeof(int));
        int nrows = 0;

        while (cdb2_next_record(db) == CDB2_OK && nrows < n) {
            const char *k = cdb2_column_value(db, 0);
            const char *val = cdb2_column_value(db, 1);
            int kl = k ? (int)strlen(k) : 0;
            int vlen = val ? (int)strlen(val) : 0;
            int isnum = 0;
            const void *np = cdb2_column_value(db, 2);
            if (np) isnum = *(int *)np;
            found_keys[nrows] = strdup(k ? k : "");
            fkls[nrows] = kl;
            vals[nrows] = strdup(val ? val : "");
            vlens[nrows] = vlen;
            isnums[nrows] = isnum;
            nrows++;
        }

        /* Emit results in request order */
        for (int i = 0; i < n; i++) {
            if (ks[i] == NULL) { printf("EMPTY\n"); continue; }
            int found = 0;
            for (int j = 0; j < nrows; j++) {
                if (fkls[j] == kls[i] && memcmp(found_keys[j], ks[i], kls[i]) == 0) {
                    printf("VAL ");
                    phex(vals[j], vlens[j]);
                    printf(" %d\n", isnums[j]);
                    found = 1;
                    break;
                }
            }
            if (!found) printf("EMPTY\n");
        }

        for (int j = 0; j < nrows; j++) { free(found_keys[j]); free(vals[j]); }
        free(vals); free(vlens); free(isnums); free(found_keys); free(fkls);
    }

out:
    for (int i = 0; i < n; i++) free(ks[i]);
    free(ks); free(kls);
}

static void cmd_data(char **tok, int ntok) {
    if (ntok < 2) { printf("ERR bad DATA\n"); return; }
    char *k;
    int kl = unhex(tok[1], &k);
    if (kl < 0) { printf("ERR hex\n"); return; }
    const char *names[] = {"k"};
    int types[] = {CDB2_CSTRING};
    const void *vals[] = {k};
    int lens[] = {kl};
    int hasVal = 0, hasDesc = 0;
    if (bind_and_run("SELECT 1 FROM mnodes WHERE k=@k LIMIT 1", names, types, vals, lens, 1) == 0) {
        if (first_row()) hasVal = 1;
        drain();
    }
    /* descendant: k+SEP prefix — match SqlStore (k like k||chr(1)||'%' ) */
    char like[1024];
    int n = snprintf(like, sizeof(like), "%c", 1);
    /* build like pattern in hex-free bind: use k||char(1)||'%' via SQL */
    if (bind_and_run(
            "SELECT 1 FROM mnodes WHERE k>=@k||CHAR(1) AND k<@k||CHAR(2) LIMIT 1",
            names, types, vals, lens, 1) == 0) {
        if (first_row()) hasDesc = 1;
        drain();
    }
    (void)n;
    (void)like;
    free(k);
    if (hasVal && hasDesc) printf("NUM 11\n");
    else if (hasVal) printf("NUM 1\n");
    else if (hasDesc) printf("NUM 10\n");
    else printf("NUM 0\n");
}

static void cmd_kill(char **tok, int ntok) {
    if (ntok < 2) { printf("ERR bad KILL\n"); return; }
    char *k;
    int kl = unhex(tok[1], &k);
    if (kl < 0) { printf("ERR hex\n"); return; }
    const char *names[] = {"k"};
    int types[] = {CDB2_CSTRING};
    const void *vals[] = {k};
    int lens[] = {kl};
    /* node + descendants via k >= k and k < k+SEP+high — same as SqlStore prefix */
    if (bind_and_run("DELETE FROM mnodes WHERE k=@k OR (k>=@k||CHAR(1) AND k<@k||CHAR(2))",
                     names, types, vals, lens, 1) < 0) {
        free(k);
        return;
    }
    drain();
    free(k);
    printf("OK\n");
}

static int split_tabs(char *line, char **tok, int max) {
    int n = 0;
    char *p = line;
    while (n < max) {
        tok[n++] = p;
        char *t = strchr(p, '\t');
        if (!t) break;
        *t = 0;
        p = t + 1;
    }
    return n;
}

int main(int argc, char **argv) {
    const char *dbname = argc > 1 ? argv[1] : "testdb";
    const char *type = argc > 2 ? argv[2] : "local";
    int rc = cdb2_open(&db, dbname, type, 0);
    if (rc) die("cdb2_open failed");
    ensure_schema();
    {
        cdb2_run_statement(db, "SELECT COALESCE(MAX(id),0)+1 FROM mnodes");
        if (cdb2_next_record(db) == CDB2_OK) {
            const void *p = cdb2_column_value(db, 0);
            if (p) { long long v = *(const long long *)p; next_id = v > 0 ? (long)v : 1; }
        }
        drain();
    }
    printf("PONG\n");
    fflush(stdout);

    char line[8192];
    while (fgets(line, sizeof(line), stdin)) {
        size_t L = strlen(line);
        while (L && (line[L - 1] == '\n' || line[L - 1] == '\r')) line[--L] = 0;
        if (!L) continue;
        char *tok[16];
        int ntok = split_tabs(line, tok, 16);
        if (ntok < 1) continue;
        if (!strcmp(tok[0], "QUIT")) break;
        if (!strcmp(tok[0], "PING")) { printf("PONG\n"); }
        else if (!strcmp(tok[0], "LOCKA")) cmd_locka(tok, ntok);
        else if (!strcmp(tok[0], "LOCKR")) cmd_lockr(tok, ntok);
        else if (!strcmp(tok[0], "SET")) cmd_set(tok, ntok);
        else if (!strcmp(tok[0], "SETB")) cmd_setb(tok, ntok);
        else if (!strcmp(tok[0], "BEGIN")) {
            if (run("BEGIN TRANSACTION") < 0) { drain(); } else { drain(); in_txn = 1; } printf("OK\n");
        }
        else if (!strcmp(tok[0], "COMMIT")) {
            if (run("COMMIT") < 0) { drain(); } else { drain(); in_txn = 0; } printf("OK\n");
        }
        else if (!strcmp(tok[0], "ROLLBACK")) {
            if (run("ROLLBACK") < 0) { drain(); } else { drain(); in_txn = 0; } printf("OK\n");
        }
        else if (!strcmp(tok[0], "GET")) cmd_get(tok, ntok);
        else if (!strcmp(tok[0], "GETB")) cmd_getb(tok, ntok);
        else if (!strcmp(tok[0], "ORDER")) cmd_order(tok, ntok);
        else if (!strcmp(tok[0], "DATA")) cmd_data(tok, ntok);
        else if (!strcmp(tok[0], "KILL")) cmd_kill(tok, ntok);
        else printf("ERR unknown\n");
        fflush(stdout);
    }
    cdb2_close(db);
    return 0;
}
