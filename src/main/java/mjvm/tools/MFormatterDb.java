package mjvm.tools;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * M code database writer — searchable SQLite database of M source. Stores symbols, tags, globals
 * for cross-reference queries.
 */
public final class MFormatterDb {

  public void writeDatabase(String source, String dbPath) throws Exception {
    Class.forName("org.sqlite.JDBC");
    try (var conn = java.sql.DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
      try (var st = conn.createStatement()) {
        st.execute("DROP TABLE IF EXISTS source");
        st.execute("DROP TABLE IF EXISTS tags");
        st.execute("DROP TABLE IF EXISTS symbols");
        st.execute("DROP TABLE IF EXISTS xref");
        st.execute("DROP TABLE IF EXISTS formatted");
        st.execute(
            """
            CREATE TABLE source (
                id INTEGER PRIMARY KEY,
                line_num INTEGER,
                text TEXT,
                depth INTEGER
            )\
            """);
        st.execute(
            """
            CREATE TABLE formatted (
                id INTEGER PRIMARY KEY,
                line_num INTEGER,
                text TEXT
            )\
            """);
        st.execute(
            """
            CREATE TABLE tags (
                id INTEGER PRIMARY KEY,
                name TEXT,
                params TEXT,
                comment TEXT,
                start_line INTEGER,
                end_line INTEGER
            )\
            """);
        st.execute(
            """
            CREATE TABLE symbols (
                id INTEGER PRIMARY KEY,
                name TEXT,
                kind TEXT,
                scope TEXT,
                def_line INTEGER,
                attributes TEXT
            )\
            """);
        st.execute(
            """
            CREATE TABLE xref (
                id INTEGER PRIMARY KEY,
                symbol_id INTEGER,
                line_num INTEGER,
                FOREIGN KEY(symbol_id) REFERENCES symbols(id)
            )\
            """);
      }

      // Store source lines
      String[] lines = source.split("\n", -1);
      try (var ps =
          conn.prepareStatement("INSERT INTO source(line_num,text,depth) VALUES(?,?,?)")) {
        for (int i = 0; i < lines.length; i++) {
          String line = lines[i];
          int depth = 0;
          while (depth < line.length() && line.charAt(depth) == '.') {
            if (depth + 1 < line.length() && Character.isDigit(line.charAt(depth + 1))) break;
            depth++;
          }
          ps.setInt(1, i + 1);
          ps.setString(2, line);
          ps.setInt(3, depth);
          ps.executeUpdate();
        }
      }

      // Store formatted code
      MFormatter fmt = new MFormatter(2, false);
      try (var ps = conn.prepareStatement("INSERT INTO formatted(line_num,text) VALUES(?,?)")) {
        for (int i = 0; i < lines.length; i++) {
          ps.setInt(1, i + 1);
          ps.setString(2, fmt.format(lines[i]));
          ps.executeUpdate();
        }
      }

      // Store tags
      List<MFormatterOrganizer.Tag> tags = new ArrayList<>();
      MFormatterOrganizer.Tag current = null;
      Map<String, MFormatterXref.Symbol> symbols = new LinkedHashMap<>();

      for (int i = 0; i < lines.length; i++) {
        String line = lines[i];
        int labelEnd = 0;
        while (labelEnd < line.length()
            && (Character.isLetterOrDigit(line.charAt(labelEnd))
                || line.charAt(labelEnd) == '%'
                || line.charAt(labelEnd) == '^')) {
          labelEnd++;
        }
        if (labelEnd == 0) continue;
        if (labelEnd < line.length()) {
          char next = line.charAt(labelEnd);
          if (next != ' ' && next != '\t' && next != '(') continue;
        }
        String label = line.substring(0, labelEnd);
        String params = "";
        String tagLabel = label;
        int paren = -1;
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
        String comment = "";
        int semi = rest.indexOf(';');
        if (semi >= 0) comment = rest.substring(semi + 1).strip();

        current = new MFormatterOrganizer.Tag();
        current.name = tagLabel;
        current.params = params;
        current.comment = comment;
        current.startLine = i;
        tags.add(current);
      }
      for (int i = 0; i < tags.size(); i++) {
        tags.get(i).endLine =
            (i + 1 < tags.size()) ? tags.get(i + 1).startLine - 1 : lines.length - 1;
      }

      try (var ps =
          conn.prepareStatement(
              "INSERT INTO tags(name,params,comment,start_line,end_line) VALUES(?,?,?,?,?)")) {
        for (MFormatterOrganizer.Tag tag : tags) {
          ps.setString(1, tag.name);
          ps.setString(2, tag.params);
          ps.setString(3, tag.comment);
          ps.setInt(4, tag.startLine + 1);
          ps.setInt(5, tag.endLine + 1);
          ps.executeUpdate();
        }
      }

      // Store symbols and xref
      // Reuse the xref logic
      for (MFormatterOrganizer.Tag tag : tags) {
        for (int i = tag.startLine; i <= tag.endLine; i++) {
          String line = lines[i];
          String code = line;
          int semi = line.indexOf(';');
          if (semi >= 0) code = line.substring(0, semi);
          MFormatterOrganizer.extractMetadata(code, tag);
        }
      }

      // Build symbols map (same as xref method)
      for (MFormatterOrganizer.Tag tag : tags) {
        MFormatterXref.Symbol tagSym = new MFormatterXref.Symbol();
        tagSym.name = tag.name;
        tagSym.kind = "TAG";
        tagSym.scope = "(routine)";
        tagSym.defLine = tag.startLine + 1;
        tagSym.attributes = tag.params.isEmpty() ? "" : "params=" + tag.params;
        symbols.put("TAG:" + tag.name, tagSym);

        if (!tag.params.isEmpty()) {
          String paramStr = tag.params.substring(1, tag.params.length() - 1);
          for (String p : paramStr.split(",")) {
            p = p.trim();
            if (!p.isEmpty()) {
              MFormatterXref.Symbol ps = new MFormatterXref.Symbol();
              ps.name = p;
              ps.kind = "PARAM";
              ps.scope = tag.name;
              ps.defLine = tag.startLine + 1;
              ps.attributes = "of " + tag.name;
              symbols.put("PARAM:" + tag.name + ":" + p, ps);
            }
          }
        }
      }

      // Collect references from extractMetadata results
      for (MFormatterOrganizer.Tag tag : tags) {
        for (String call : tag.calls) {
          MFormatterXref.Symbol sym = symbols.get("TAG:" + call);
          if (sym != null) sym.refs.add(tag.startLine + 1);
        }
        for (String ext : tag.extrinsic) {
          MFormatterXref.Symbol sym =
              symbols.computeIfAbsent(
                  "EXT:" + ext,
                  k -> {
                    MFormatterXref.Symbol s = new MFormatterXref.Symbol();
                    s.name = "$$" + ext;
                    s.kind = "EXTRINSIC";
                    s.scope = "(routine)";
                    s.defLine = -1;
                    return s;
                  });
          sym.refs.add(tag.startLine + 1);
        }
        for (String g : tag.globalsRead) {
          MFormatterXref.Symbol sym =
              symbols.computeIfAbsent(
                  "GLOBAL:" + g,
                  k -> {
                    MFormatterXref.Symbol s = new MFormatterXref.Symbol();
                    s.name = g;
                    s.kind = "GLOBAL";
                    s.scope = "(global)";
                    s.defLine = -1;
                    return s;
                  });
          sym.refs.add(tag.startLine + 1);
        }
        for (String g : tag.globalsWritten) {
          MFormatterXref.Symbol sym =
              symbols.computeIfAbsent(
                  "GLOBAL:" + g,
                  k -> {
                    MFormatterXref.Symbol s = new MFormatterXref.Symbol();
                    s.name = g;
                    s.kind = "GLOBAL";
                    s.scope = "(global)";
                    s.defLine = -1;
                    return s;
                  });
          sym.refs.add(tag.startLine + 1);
        }
      }

      // Insert symbols and xref
      try (var psSym =
              conn.prepareStatement(
                  "INSERT INTO symbols(name,kind,scope,def_line,attributes) VALUES(?,?,?,?,?)");
          var psXref = conn.prepareStatement("INSERT INTO xref(symbol_id,line_num) VALUES(?,?)")) {
        for (MFormatterXref.Symbol sym : symbols.values()) {
          psSym.setString(1, sym.name);
          psSym.setString(2, sym.kind);
          psSym.setString(3, sym.scope);
          psSym.setInt(4, sym.defLine);
          psSym.setString(5, sym.attributes);
          psSym.executeUpdate();
          // Get the generated id
          try (var rs = psSym.getGeneratedKeys()) {
            if (rs.next()) {
              int symId = rs.getInt(1);
              for (int lineNum : sym.refs) {
                psXref.setInt(1, symId);
                psXref.setInt(2, lineNum);
                psXref.executeUpdate();
              }
            }
          }
        }
      }

      // Create indexes for searching
      try (var st = conn.createStatement()) {
        st.execute("CREATE INDEX idx_tags_name ON tags(name)");
        st.execute("CREATE INDEX idx_symbols_name ON symbols(name)");
        st.execute("CREATE INDEX idx_symbols_kind ON symbols(kind)");
        st.execute("CREATE INDEX idx_xref_symbol ON xref(symbol_id)");
        st.execute("CREATE INDEX idx_xref_line ON xref(line_num)");
        st.execute("CREATE INDEX idx_source_line ON source(line_num)");
        st.execute("CREATE INDEX idx_formatted_line ON formatted(line_num)");
      }
    }
  }

  // --- CLI ---
}
