package jvmumps;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import jvmumps.LineInterp;

/**
 * JavaM REPL (jvmumps#35) — interactive M shell (direct mode).
 *
 * Usage: java -cp build:lib/... jvmumps.Repl
 *
 * Commands:
 *   <M code>        evaluate and print WRITE output
 *   /help           show help
 *   /load FILE      load a routine file (sets tags for DO/QUIT)
 *   /tags           show loaded tags
 *   /reset          clear state
 *   /quit           exit
 *
 * Env:
 *   REPL_INIT       path to a script file run at startup (one M line per line)
 */
@SuppressWarnings("deprecation")
public final class Repl {
    static final String PROMPT = "JavaM> ";
    static final int HISTORY_MAX = 500;

    final LineInterp interp = new LineInterp();
    final List<String> history = new ArrayList<>();
    final Map<String, String> routines = new LinkedHashMap<>();

    public static void main(String[] args) throws Exception {
        new Repl().run(args);
    }

    void run(String[] args) throws Exception {
        System.out.println("JavaM REPL — interactive M (direct mode)");
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

    /** @return false to exit */
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
            String out = interp.run(line);
            if (!out.isEmpty()) System.out.print(out);
        } catch (MError e) {
            System.out.println("%" + e.getMessage());
        } catch (Exception e) {
            System.out.println("?Error: " + e.getMessage());
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
                interp.run("K ^%SYS  K X,Y,Z,A,B,C,D,I,J,K,L,M,N,O,P,Q,R,S,T,U,V,W");
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
            // run the routine once to register tags (ZLINK semantics)
            interp.run(code);
            routines.put(path, code);
            System.out.println("Loaded: " + path);
        } catch (Exception e) {
            System.out.println("?Cannot load " + path + ": " + e.getMessage());
        }
    }

    void showTags() {
        if (routines.isEmpty()) {
            System.out.println("(no routines loaded)");
            return;
        }
        for (Map.Entry<String, String> e : routines.entrySet()) {
            System.out.println("  " + e.getKey());
            // show tag names (lines starting at column 0)
            for (String line : e.getValue().split("\n", -1)) {
                if (!line.isEmpty() && !line.startsWith(" ") && !line.startsWith(".") && !line.startsWith(";")) {
                    int i = 0;
                    while (i < line.length() && (Character.isLetterOrDigit(line.charAt(i)) || line.charAt(i) == '%')) i++;
                    if (i > 0) System.out.println("    " + line.substring(0, i));
                }
            }
        }
    }

    void addHistory(String line) {
        history.add(line);
        if (history.size() > HISTORY_MAX) history.remove(0);
    }

    void printHelp() {
        System.out.println("JavaM REPL commands:");
        System.out.println("  <M code>       evaluate M expression/statement");
        System.out.println("  /load FILE     load a routine file");
        System.out.println("  /tags          show loaded routine tags");
        System.out.println("  /reset         clear variable state");
        System.out.println("  /history       show command history");
        System.out.println("  /help          show this help");
        System.out.println("  /quit          exit");
        System.out.println();
        System.out.println("Env: REPL_INIT=path to startup script");
    }

    private Repl() {}
}
