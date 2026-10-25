package mjvm.tools;

import java.io.*;
import java.nio.file.*;

/**
 * M code formatter — expands terse M source into "full" format.
 *
 * <p>Expands command abbreviations (S→SET, W→WRITE, F→FOR, …), expands special variable
 * abbreviations ($G→$GET, $O→$ORDER, …), breaks one-liners at command boundaries, and indents
 * dotted blocks.
 *
 * <p>Usage: java mjvm.tools.MFormatter [--break] [--indent N] [file] --break break one-liners onto
 * separate lines at command boundaries --indent N indent size per dot level (default 2) file input
 * file (default: stdin)
 *
 * <p>Output goes to stdout.
 */
public final class MFormatter {

  private final int indentSize;
  private final boolean breakLines;
  private final MFormatterExpansions exp = new MFormatterExpansions();

  public MFormatter(int indentSize, boolean breakLines) {
    this.indentSize = indentSize;
    this.breakLines = breakLines;
  }

  /** Format M source: expand abbreviations, indent, optionally break lines. */
  public String format(String source) {
    String[] lines = source.split("\n", -1);
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      if (i > 0) out.append('\n');
      formatLine(line, out);
    }
    return out.toString();
  }

  /** Format a single M line: indent, label, expand commands/vars. */
  void formatLine(String line, StringBuilder out) {
    // Count leading dots for indent
    int depth = 0;
    int pos = 0;
    while (pos < line.length() && line.charAt(pos) == '.') {
      // Dotted block indent (but not decimal points)
      if (pos + 1 < line.length() && Character.isDigit(line.charAt(pos + 1))) break;
      depth++;
      pos++;
    }
    // Apply indentation
    for (int j = 0; j < depth * indentSize; j++) out.append(' ');

    // Split label from body
    String rest = line.substring(pos);
    int labelEnd = 0;
    while (labelEnd < rest.length()
        && (Character.isLetterOrDigit(rest.charAt(labelEnd))
            || rest.charAt(labelEnd) == '%'
            || rest.charAt(labelEnd) == '^')) {
      labelEnd++;
    }
    // Label must be followed by space, tab, or EOL
    if (labelEnd > 0 && labelEnd < rest.length()) {
      char next = rest.charAt(labelEnd);
      if (next != ' ' && next != '\t' && next != '=') {
        labelEnd = 0; // not a label
      }
    }
    if (labelEnd > 0) {
      out.append(rest, 0, labelEnd);
      rest = rest.substring(labelEnd);
      // Normalize spacing after label
      int sp = 0;
      while (sp < rest.length() && (rest.charAt(sp) == ' ' || rest.charAt(sp) == '\t')) sp++;
      if (sp > 0) {
        out.append(' ');
        rest = rest.substring(sp);
      }
    }

    // Expand commands and special variables in body
    String body = exp.expandCommands(rest);
    body = exp.expandSpecialVars(body);

    // Break lines at command boundaries if requested
    if (breakLines) {
      body = exp.breakAtCommands(body);
    }

    out.append(body);
  }

  /** Organize: structural view of M source (tags, calls, globals). */
  public String organize(String source) {
    return new MFormatterOrganizer().organize(source);
  }

  /** Cross-reference: symbol usage analysis. */
  public String xref(String source) {
    return new MFormatterXref().xref(source);
  }

  /** Write searchable SQLite database. */
  public void writeDatabase(String source, String dbPath) throws Exception {
    new MFormatterDb().writeDatabase(source, dbPath);
  }

  /** CLI entry point. */
  public static void main(String[] args) throws Exception {
    boolean doBreak = false;
    int indent = 2;
    String file = null;
    boolean doOrganize = false;
    boolean doXref = false;
    String dbPath = null;

    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--break" -> doBreak = true;
        case "--indent" -> indent = Integer.parseInt(args[++i]);
        case "--organize" -> doOrganize = true;
        case "--xref" -> doXref = true;
        case "--db" -> dbPath = args[++i];
        default -> file = args[i];
      }
    }

    String source;
    if (file != null) {
      source =
          new String(
              Files.readAllBytes(Path.of(file)), java.nio.charset.StandardCharsets.ISO_8859_1);
    } else {
      source = new String(System.in.readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1);
    }

    MFormatter fmt = new MFormatter(indent, doBreak);
    String result;
    if (doOrganize) {
      result = fmt.organize(source);
    } else if (doXref) {
      result = fmt.xref(source);
    } else if (dbPath != null) {
      fmt.writeDatabase(source, dbPath);
      return;
    } else {
      result = fmt.format(source);
    }
    System.out.println(result);
  }
}
