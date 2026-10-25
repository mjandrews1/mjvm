package jvmumps;

import java.io.File;
import java.nio.file.Files;

/** J8 devices + W8 specials smoke. */
public final class J8Smoke {
  public static void main(String[] args) throws Exception {
    File f = File.createTempFile("j8s", ".txt");
    Files.writeString(f.toPath(), "alpha\nbeta\n");
    LineInterp m = new LineInterp();
    String out = m.run("O 2:" + f.getAbsolutePath() + " U 2 R A R B C 2 W A,\"|\",B,!");
    if (!"alpha|beta\n".equals(out)) {
      throw new IllegalStateException("read " + out);
    }
    out = m.run("W $PRINCIPAL,\" \",$DEVICE,\" \",$SYSTEM,!");
    if (!"0  JvMumps\n".equals(out)) {
      throw new IllegalStateException("w8 " + out);
    }
    out = m.run("W $IO,\" \",$ESTACK,\" \",$STORAGE,!");
    if (!out.startsWith("0 0")) {
      throw new IllegalStateException("w8b " + out);
    }
    if (!f.delete()) f.deleteOnExit();
    System.out.println("J8 DEVICES + W8 SPECIALS OK");
  }
}
