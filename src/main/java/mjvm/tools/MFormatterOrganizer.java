package mjvm.tools;

import java.util.*;

/**
 * M code organizer — extracts tags, calls, globals, metadata. Produces a structural view of M
 * source.
 */
public final class MFormatterOrganizer {

  /** Extract tags (labels at column 0) and their metadata. */
  public static class Tag {
    String name;
    String params;
    int startLine;
    int endLine;
    List<String> calls = new ArrayList<>();
    List<String> extrinsic = new ArrayList<>();
    Set<String> globalsRead = new LinkedHashSet<>();
    Set<String> globalsWritten = new LinkedHashSet<>();
    String comment;
  }

  /** Build organizational view: tags, calls, globals, with full-format code. */
  public String organize(String source) {
    String[] lines = source.split("\n", -1);
    List<Tag> tags = new ArrayList<>();
    Tag current = null;

    // Pass 1: find tags
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
      String rest = line.substring(labelEnd).strip();
      String params = "";
      int paren = label.indexOf('(');
      String tagLabel = label;
      if (paren > 0) {
        tagLabel = label.substring(0, paren);
        params = label.substring(paren);
      }
      String comment = "";
      int semi = rest.indexOf(';');
      if (semi >= 0) comment = rest.substring(semi + 1).strip();

      current = new Tag();
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

    // Pass 2: extract metadata
    for (Tag tag : tags) {
      for (int i = tag.startLine; i <= tag.endLine; i++) {
        String line = lines[i];
        int semi = line.indexOf(';');
        if (semi >= 0) line = line.substring(0, semi);
        extractMetadata(line, tag);
      }
    }

    // Generate output
    StringBuilder sb = new StringBuilder();
    String routineName = tags.isEmpty() ? "(routine)" : tags.get(0).name;
    sb.append("=== ").append(routineName).append(" — Organization ===\n\n");

    sb.append("Tags:\n");
    for (Tag tag : tags) {
      sb.append("  ").append(tag.name);
      if (!tag.params.isEmpty()) sb.append(tag.params);
      if (!tag.comment.isEmpty()) sb.append("  // ").append(tag.comment);
      sb.append('\n');
      if (!tag.calls.isEmpty())
        sb.append("    calls: ").append(String.join(", ", tag.calls)).append('\n');
      if (!tag.extrinsic.isEmpty())
        sb.append("    extrinsic: ").append(String.join(", ", tag.extrinsic)).append('\n');
      if (!tag.globalsRead.isEmpty())
        sb.append("    reads: ").append(String.join(", ", tag.globalsRead)).append('\n');
      if (!tag.globalsWritten.isEmpty())
        sb.append("    writes: ").append(String.join(", ", tag.globalsWritten)).append('\n');
    }

    sb.append("\nCall graph:\n");
    boolean anyCalls = false;
    for (Tag tag : tags) {
      for (String target : tag.calls) {
        sb.append("  ").append(tag.name).append(" \u2192 ").append(target).append('\n');
        anyCalls = true;
      }
      for (String target : tag.extrinsic) {
        sb.append("  ").append(tag.name).append(" \u2192 $$").append(target).append('\n');
        anyCalls = true;
      }
    }
    if (!anyCalls) sb.append("  (no inter-tag calls)\n");

    sb.append("\nGlobal access:\n");
    Map<String, List<String>> globalReaders = new LinkedHashMap<>();
    Map<String, List<String>> globalWriters = new LinkedHashMap<>();
    for (Tag tag : tags) {
      for (String g : tag.globalsRead)
        globalReaders.computeIfAbsent(g, k -> new ArrayList<>()).add(tag.name);
      for (String g : tag.globalsWritten)
        globalWriters.computeIfAbsent(g, k -> new ArrayList<>()).add(tag.name);
    }
    for (String g : globalReaders.keySet()) {
      sb.append("  ")
          .append(g)
          .append(" (read by ")
          .append(String.join(", ", globalReaders.get(g)))
          .append(")");
      if (globalWriters.containsKey(g))
        sb.append(" (written by ").append(String.join(", ", globalWriters.get(g))).append(")");
      sb.append('\n');
    }
    for (String g : globalWriters.keySet()) {
      if (!globalReaders.containsKey(g))
        sb.append("  ")
            .append(g)
            .append(" (written by ")
            .append(String.join(", ", globalWriters.get(g)))
            .append(")\n");
    }

    sb.append("\nCode:\n");
    MFormatter fmt = new MFormatter(2, false);
    for (Tag tag : tags) {
      sb.append("\n").append(tag.name).append(tag.params.isEmpty() ? "" : tag.params).append(":\n");
      for (int i = tag.startLine; i <= tag.endLine; i++)
        sb.append("  ").append(fmt.format(lines[i]).strip()).append('\n');
    }

    return sb.toString();
  }

  public static void extractMetadata(String line, Tag tag) {
    int i = 0;
    while (i < line.length()) {
      char c = line.charAt(i);
      // DO target: D TAG
      if ((c == 'D' || c == 'd')
          && (i == 0 || line.charAt(i - 1) == ' ' || line.charAt(i - 1) == '\t')
          && (i + 1 >= line.length() || line.charAt(i + 1) == ' ' || line.charAt(i + 1) == '\t')) {
        int j = i + 1;
        while (j < line.length() && (line.charAt(j) == ' ' || line.charAt(j) == '\t')) j++;
        int start = j;
        while (j < line.length()
            && (Character.isLetterOrDigit(line.charAt(j))
                || line.charAt(j) == '%'
                || line.charAt(j) == '^')) j++;
        if (j > start) {
          String target = line.substring(start, j);
          if (!tag.calls.contains(target)) tag.calls.add(target);
        }
        i = j;
        continue;
      }
      // Extrinsic: $$TAG
      if (c == '$' && i + 1 < line.length() && line.charAt(i + 1) == '$') {
        int j = i + 2;
        int start = j;
        while (j < line.length()
            && (Character.isLetterOrDigit(line.charAt(j))
                || line.charAt(j) == '%'
                || line.charAt(j) == '^')) j++;
        if (j > start) {
          String target = line.substring(start, j);
          if (!tag.extrinsic.contains(target)) tag.extrinsic.add(target);
        }
        i = j;
        continue;
      }
      // Global: ^NAME
      if (c == '^') {
        int j = i + 1;
        int start = j;
        while (j < line.length()
            && (Character.isLetterOrDigit(line.charAt(j)) || line.charAt(j) == '%')) j++;
        if (j > start) {
          String gname = "^" + line.substring(start, j);
          boolean isWrite = isGlobalWrite(line, i);
          if (isWrite) {
            if (!tag.globalsWritten.contains(gname)) tag.globalsWritten.add(gname);
          } else {
            if (!tag.globalsRead.contains(gname)) tag.globalsRead.add(gname);
          }
        }
        i = j;
        continue;
      }
      i++;
    }
  }

  public static boolean isGlobalWrite(String line, int pos) {
    String before = line.substring(0, pos).toUpperCase(java.util.Locale.ROOT);
    return before.contains("SET ")
        || before.endsWith(" S ")
        || before.equals("S")
        || before.contains("KILL ")
        || before.endsWith(" K ")
        || before.equals("K");
  }

  // --- Attribute & Cross-Reference Generator (IBM PL/I Checkout style) ---

}
