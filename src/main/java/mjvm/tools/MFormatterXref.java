package mjvm.tools;

import java.util.*;

/**
 * M code cross-reference — symbol usage analysis.
 * Tracks reads, writes, kills for locals and globals.
 */
public final class MFormatterXref {

    public static class Symbol {
        String name;
        String kind;        // "local", "global", "param", "tag", "extrinsic"
        String scope;       // tag name where defined, or "(routine)"
        int defLine;        // line number of definition (1-based)
        List<Integer> refs = new ArrayList<>(); // line numbers of references
        String attributes = "";  // additional attributes (params, type, etc.)
    }

    /**
     * Generate attribute & cross-reference listing.
     *
     * For each symbol (variable, tag, global, extrinsic):
     *   - Attributes: kind, scope, definition line
     *   - Cross-reference: every line where it appears
     *
     * Output format follows IBM PL/I Checkout Compiler conventions:
     *   SYMBOL          KIND      SCOPE    DEF   REFS
     *   %AC             PARAM     DEP      19    20,22,23
     *   ^BANK           GLOBAL    (global)  22   23,24,25,...
     */
    public String xref(String source) {
        String[] lines = source.split("\n", -1);
        List<MFormatterOrganizer.Tag> tags = new ArrayList<>();
        MFormatterOrganizer.Tag current = null;
        Map<String, Symbol> symbols = new LinkedHashMap<>();

        // Pass 1: find tags and their line ranges
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int labelEnd = 0;
            while (labelEnd < line.length() &&
                   (Character.isLetterOrDigit(line.charAt(labelEnd)) ||
                    line.charAt(labelEnd) == '%' || line.charAt(labelEnd) == '^')) {
                labelEnd++;
            }
            if (labelEnd == 0) continue;
            if (labelEnd < line.length()) {
                char next = line.charAt(labelEnd);
                if (next != ' ' && next != '\t' && next != '(') continue;
            }
            // Extract full label including parenthesized params: DEP(%AC,%A)
            String label = line.substring(0, labelEnd);
            String params = "";
            String tagLabel = label;
            int paren = -1;
            // Check if next char after labelEnd is '(' — parameterized tag
            if (labelEnd < line.length() && line.charAt(labelEnd) == '(') {
                paren = labelEnd;
                int close = line.indexOf(')', labelEnd);
                if (close > 0) {
                    params = line.substring(labelEnd, close + 1);
                    tagLabel = line.substring(0, labelEnd);
                }
            }
            String rest = line.substring(labelEnd).strip();
            if (paren > 0) rest = line.substring(line.indexOf(')', labelEnd) + 1).strip();

            current = new MFormatterOrganizer.Tag();
            current.name = tagLabel;
            current.params = params;
            current.startLine = i;
            tags.add(current);

            // Register tag symbol
            Symbol tagSym = new Symbol();
            tagSym.name = tagLabel;
            tagSym.kind = "TAG";
            tagSym.scope = "(routine)";
            tagSym.defLine = i + 1;
            tagSym.attributes = params.isEmpty() ? "" : "params=" + params;
            symbols.put("TAG:" + tagLabel, tagSym);

            // Register parameter symbols
            if (paren > 0 && !params.isEmpty()) {
                String paramStr = params.substring(1, params.length() - 1); // strip parens
                for (String p : paramStr.split(",")) {
                    p = p.trim();
                    if (!p.isEmpty()) {
                        Symbol ps = new Symbol();
                        ps.name = p;
                        ps.kind = "PARAM";
                        ps.scope = tagLabel;
                        ps.defLine = i + 1;
                        ps.attributes = "of " + tagLabel;
                        symbols.put("PARAM:" + tagLabel + ":" + p, ps);
                    }
                }
            }
        }

        for (int i = 0; i < tags.size(); i++) {
            tags.get(i).endLine = (i + 1 < tags.size()) ? tags.get(i + 1).startLine - 1 : lines.length - 1;
        }

        // Pass 2: find all symbol references
        for (MFormatterOrganizer.Tag tag : tags) {
            for (int i = tag.startLine; i <= tag.endLine; i++) {
                String line = lines[i];
                int lineNum = i + 1; // 1-based
                // Strip comment for symbol analysis
                String code = line;
                int semi = line.indexOf(';');
                if (semi >= 0) code = line.substring(0, semi);

                // Find globals: ^NAME
                int pos = 0;
                while (pos < code.length()) {
                    if (code.charAt(pos) == '^') {
                        int j = pos + 1;
                        int start = j;
                        while (j < code.length() && (Character.isLetterOrDigit(code.charAt(j)) ||
                               code.charAt(j) == '%')) j++;
                        if (j > start) {
                            String gname = "^" + code.substring(start, j);
                            Symbol sym = symbols.computeIfAbsent("GLOBAL:" + gname, k -> {
                                Symbol s = new Symbol();
                                s.name = gname;
                                s.kind = "GLOBAL";
                                s.scope = "(global)";
                                s.defLine = -1;
                                return s;
                            });
                            if (!sym.refs.contains(lineNum)) sym.refs.add(lineNum);
                        }
                        pos = j;
                        continue;
                    }
                    // Find locals: %NAME
                    if (code.charAt(pos) == '%' && pos > 0 &&
                        (Character.isLetterOrDigit(code.charAt(pos-1)) || code.charAt(pos-1) == '%' ||
                         code.charAt(pos-1) == '^')) {
                        // % preceded by identifier char — part of subscript, skip
                        pos++;
                        continue;
                    }
                    if (code.charAt(pos) == '%') {
                        int j = pos + 1;
                        int start = j;
                        while (j < code.length() && (Character.isLetterOrDigit(code.charAt(j)) ||
                               code.charAt(j) == '%')) j++;
                        if (j > start) {
                            String lname = "%" + code.substring(start, j);
                            // Check if it's a parameter of this tag
                            String key = "PARAM:" + tag.name + ":" + lname;
                            Symbol sym = symbols.get(key);
                            if (sym == null) {
                                sym = symbols.computeIfAbsent("LOCAL:" + tag.name + ":" + lname, k -> {
                                    Symbol s = new Symbol();
                                    s.name = lname;
                                    s.kind = "LOCAL";
                                    s.scope = tag.name;
                                    s.defLine = lineNum;
                                    return s;
                                });
                            }
                            if (!sym.refs.contains(lineNum)) sym.refs.add(lineNum);
                        }
                        pos = j;
                        continue;
                    }
                    // Find extrinsic: $$NAME
                    if (code.charAt(pos) == '$' && pos+1 < code.length() && code.charAt(pos+1) == '$') {
                        int j = pos + 2;
                        int start = j;
                        while (j < code.length() && (Character.isLetterOrDigit(code.charAt(j)) ||
                               code.charAt(j) == '%' || code.charAt(j) == '^')) j++;
                        if (j > start) {
                            String ename = code.substring(start, j);
                            Symbol sym = symbols.computeIfAbsent("EXT:" + ename, k -> {
                                Symbol s = new Symbol();
                                s.name = "$$" + ename;
                                s.kind = "EXTRINSIC";
                                s.scope = "(routine)";
                                s.defLine = -1;
                                return s;
                            });
                            if (!sym.refs.contains(lineNum)) sym.refs.add(lineNum);
                        }
                        pos = j;
                        continue;
                    }
                    pos++;
                }

                // Find DO/GOTO targets (tag references)
                pos = 0;
                while (pos < code.length()) {
                    char c = code.charAt(pos);
                    if ((c == 'D' || c == 'd' || c == 'G' || c == 'g') &&
                        (pos == 0 || code.charAt(pos-1) == ' ' || code.charAt(pos-1) == '\t') &&
                        (pos+1 >= code.length() || code.charAt(pos+1) == ' ' || code.charAt(pos+1) == '\t')) {
                        int j = pos + 1;
                        while (j < code.length() && (code.charAt(j) == ' ' || code.charAt(j) == '\t')) j++;
                        int start = j;
                        while (j < code.length() && (Character.isLetterOrDigit(code.charAt(j)) ||
                               code.charAt(j) == '%' || code.charAt(j) == '^')) j++;
                        if (j > start) {
                            String target = code.substring(start, j);
                            String key = "TAG:" + target;
                            Symbol sym = symbols.get(key);
                            if (sym != null && !sym.refs.contains(lineNum)) {
                                sym.refs.add(lineNum);
                            }
                        }
                        pos = j;
                        continue;
                    }
                    pos++;
                }
            }
        }

        // Generate output
        StringBuilder sb = new StringBuilder();
        String routineName = tags.isEmpty() ? "(routine)" : tags.get(0).name;
        sb.append("=== ").append(routineName).append(" — Attribute & Cross-Reference ===\n\n");

        // Column header
        sb.append(String.format("%-20s %-10s %-12s %5s  %s%n",
            "SYMBOL", "KIND", "SCOPE", "DEF", "CROSS-REFERENCE (line numbers)"));
        sb.append("-".repeat(80)).append('\n');

        // Sort: TAGs first, then PARAMs, LOCALs, GLOBALs, EXTRINSICs
        List<Symbol> sorted = new ArrayList<>(symbols.values());
        sorted.sort((a, b) -> {
            int ka = kindOrder(a.kind), kb = kindOrder(b.kind);
            if (ka != kb) return ka - kb;
            return a.name.compareTo(b.name);
        });

        for (Symbol sym : sorted) {
            String defStr = sym.defLine > 0 ? String.valueOf(sym.defLine) : "—";
            String refsStr = sym.refs.isEmpty() ? "(none)" :
                sym.refs.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
            String attrs = sym.attributes.isEmpty() ? "" : "  [" + sym.attributes + "]";
            sb.append(String.format("%-20s %-10s %-12s %5s  %s%s%n",
                sym.name, sym.kind, sym.scope, defStr, refsStr, attrs));
        }

        // Summary
        sb.append("\n");
        long nTags = symbols.values().stream().filter(s -> s.kind.equals("TAG")).count();
        long nParams = symbols.values().stream().filter(s -> s.kind.equals("PARAM")).count();
        long nLocals = symbols.values().stream().filter(s -> s.kind.equals("LOCAL")).count();
        long nGlobals = symbols.values().stream().filter(s -> s.kind.equals("GLOBAL")).count();
        long nExtr = symbols.values().stream().filter(s -> s.kind.equals("EXTRINSIC")).count();
        sb.append(String.format("Summary: %d tags, %d params, %d locals, %d globals, %d extrinsics%n",
            nTags, nParams, nLocals, nGlobals, nExtr));

        return sb.toString();
    }

    static int kindOrder(String kind) {
        switch (kind) {
            case "TAG": return 0;
            case "PARAM": return 1;
            case "LOCAL": return 2;
            case "GLOBAL": return 3;
            case "EXTRINSIC": return 4;
            default: return 5;
        }
    }

    // --- Database output ---

    /**
     * Write formatted code, tags, symbols, and cross-references to a SQLite database.
     * Tables: source, tags, symbols, xref.
     */
}
