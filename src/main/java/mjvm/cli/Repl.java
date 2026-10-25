package mjvm.cli;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import jvmumps.MStore;
import mjvm.compiler.MFrontend;
import mjvm.ir.Ir;
import mjvm.runtime.IrMachine;
import mjvm.runtime.MRuntime;

/**
 * mJVM REPL (jvmumps#36) — interactive M shell over native compiler/runtime.
 *
 * <p>Usage: java -cp build:lib/... mjvm.cli.Repl or: mjvm repl
 *
 * <p>Shares command names with jvmumps.Repl (JavaM REPL): /help /load /tags /reset /quit /history
 */
public final class Repl {
  static final String PROMPT = "mJVM> ";
  static final int HISTORY_MAX = 500;

  MRuntime rt;
  MFrontend fe = new MFrontend();
  final List<String> history = new ArrayList<>();
  final Map<String, List<Ir>> programs = new LinkedHashMap<>();
  final Map<String, Map<String, Integer>> tags = new LinkedHashMap<>();

  public static void main(String[] args) throws Exception {
    new Repl().run(args);
  }

  void run(String[] args) throws Exception {
    rt = new MRuntime(new MStore());
    System.out.println("mJVM REPL — interactive M (native compiler/runtime)");
    System.out.println("Type /help for commands. Type /quit to exit.");
    System.out.println();

    // REPL_INIT script
    String init = System.getenv("REPL_INIT");
    if (init != null && !init.isEmpty()) {
      File f = new File(init);
      if (f.isFile()) {
        System.out.println("Running REPL_INIT: " + init);
        for (String line : Files.readAllLines(f.toPath())) {
          evalLine(line);
        }
      }
    }

    // command-line script
    for (String arg : args) {
      File f = new File(arg);
      if (f.isFile()) {
        for (String line : Files.readAllLines(f.toPath())) {
          evalLine(line);
        }
      }
    }

    // interactive loop
    BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
    String line;
    while (true) {
      System.out.print(PROMPT);
      System.out.flush();
      line = stdin.readLine();
      if (line == null) break;
      if (!evalLine(line)) break;
    }
    System.out.println("Goodbye.");
  }

  /**
   * @return false to exit
   */
  boolean evalLine(String line) {
    String trimmed = line.trim();
    if (trimmed.isEmpty()) return true;

    // slash commands
    if (trimmed.startsWith("/")) {
      return slash(trimmed);
    }

    // M code
    addHistory(line);
    try {
      List<Ir> prog;
      // tag definition: starts with ident that's NOT a known command
      boolean isTagLine =
          !trimmed.isEmpty()
              && !trimmed.startsWith(" ")
              && !trimmed.startsWith(".")
              && !trimmed.startsWith(";")
              && Character.isLetter(trimmed.charAt(0))
              && !isKnownCmd(trimmed);
      if (isTagLine || line.indexOf('\n') >= 0) {
        String multi = line.indexOf('\n') >= 0 ? line : line + "\n";
        prog = fe.compileProgram(multi);
        var tagMap = fe.lastTags();
        if (!tagMap.isEmpty()) {
          rt.link(prog, tagMap);
          System.out.println("  [" + tagMap.keySet() + "]");
        } else {
          IrMachine.run(prog, rt, fe.lastTags());
          String out = rt.output();
          if (!out.isEmpty()) System.out.print(out);
        }
      } else {
        prog = fe.compileLine(line);
        IrMachine.run(prog, rt, fe.lastTags());
        String out = rt.output();
        if (!out.isEmpty()) System.out.print(out);
      }
      rt.reset();
    } catch (jvmumps.MError e) {
      System.out.println("%" + e.getMessage());
      rt.reset();
    } catch (Exception e) {
      System.out.println("?Error: " + e.getMessage());
      rt.reset();
    }
    return true;
  }

  boolean slash(String cmd) {
    String[] parts = cmd.split("\\s+", 2);
    String c = parts[0].toLowerCase();
    String arg = parts.length > 1 ? parts[1].trim() : "";

    switch (c) {
      case "/help":
      case "/h":
      case "/?":
        printHelp();
        return true;
      case "/quit":
      case "/q":
      case "/exit":
        return false;
      case "/load":
        if (arg.isEmpty()) {
          System.out.println("?Usage: /load FILE.m");
        } else {
          loadRoutine(arg);
        }
        return true;
      case "/tags":
        showTags();
        return true;
      case "/reset":
        rt = new MRuntime(new MStore());
        programs.clear();
        tags.clear();
        System.out.println("State cleared.");
        return true;
      case "/history":
      case "/hist":
        for (int i = 0; i < history.size(); i++) {
          System.out.printf("  %3d  %s%n", i + 1, history.get(i));
        }
        return true;
      default:
        System.out.println("?Unknown command: " + c + " (try /help)");
        return true;
    }
  }

  void loadRoutine(String path) {
    try {
      String code = Files.readString(Path.of(path));
      var prog = fe.compileProgram(code);
      var tagMap = fe.lastTags();
      // link the routine so $$tag and DO work
      rt.link(prog, tagMap);
      programs.put(path, prog);
      tags.put(path, tagMap);
      System.out.println("Loaded: " + path + " (" + tagMap.size() + " tags)");
    } catch (Exception e) {
      System.out.println("?Cannot load " + path + ": " + e.getMessage());
    }
  }

  void showTags() {
    if (tags.isEmpty()) {
      System.out.println("(no routines loaded)");
      return;
    }
    for (Map.Entry<String, Map<String, Integer>> e : tags.entrySet()) {
      System.out.println("  " + e.getKey());
      for (String tag : e.getValue().keySet()) {
        System.out.println("    " + tag);
      }
    }
  }

  void addHistory(String line) {
    history.add(line);
    if (history.size() > HISTORY_MAX) history.remove(0);
  }

  static boolean isKnownCmd(String line) {
    if (line.isEmpty()) return false;
    int i = 0;
    while (i < line.length() && Character.isLetter(line.charAt(i))) i++;
    String w = line.substring(0, i).toUpperCase();
    return w.equals("S")
        || w.equals("SET")
        || w.equals("W")
        || w.equals("WRITE")
        || w.equals("Q")
        || w.equals("QUIT")
        || w.equals("H")
        || w.equals("HALT")
        || w.equals("J")
        || w.equals("JOB")
        || w.equals("D")
        || w.equals("DO")
        || w.equals("L")
        || w.equals("LOCK")
        || w.equals("O")
        || w.equals("OPEN")
        || w.equals("C")
        || w.equals("CLOSE")
        || w.equals("U")
        || w.equals("USE")
        || w.equals("R")
        || w.equals("READ")
        || w.equals("G")
        || w.equals("GOTO")
        || w.equals("TSTART")
        || w.equals("TCOMMIT")
        || w.equals("TROLLBACK")
        || w.equals("X")
        || w.equals("XECUTE")
        || w.equals("I")
        || w.equals("IF")
        || w.equals("E")
        || w.equals("ELSE")
        || w.equals("F")
        || w.equals("FOR")
        || w.equals("N")
        || w.equals("NEW")
        || w.equals("K")
        || w.equals("KILL")
        || w.equals("M")
        || w.equals("MERGE")
        || w.equals("HANG")
        || w.equals("VIEW");
  }

  void printHelp() {
    System.out.println("mJVM REPL commands:");
    System.out.println("  <M code>       evaluate M expression/statement");
    System.out.println("  /load FILE     load a routine file (link for DO/$$)");
    System.out.println("  /tags          show loaded routine tags");
    System.out.println("  /reset         clear variable state and loaded routines");
    System.out.println("  /history       show command history");
    System.out.println("  /help          show this help");
    System.out.println("  /quit          exit");
    System.out.println();
    System.out.println("Env: REPL_INIT=path to startup script");
  }

  private Repl() {}
}
