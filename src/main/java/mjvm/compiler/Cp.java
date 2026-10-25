package mjvm.compiler;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** Split from BytecodeEmitter god file (#58). */
final class Cp {
  final ByteArrayOutputStream bo = new ByteArrayOutputStream();

  void u1(int v) {
    bo.write(v & 0xff);
  }

  void u2(int v) {
    bo.write((v >> 8) & 0xff);
    bo.write(v & 0xff);
  }

  void u4(int v) {
    u2(v >> 16);
    u2(v);
  }

  void utf8Raw(String s) {
    byte[] u = s.getBytes(StandardCharsets.UTF_8);
    u1(1);
    u2(u.length);
    bo.writeBytes(u);
  }

  int utf8(String s) {
    int idx = ++count;
    utf8Raw(s);
    return idx;
  }

  int cls(String internal) {
    int u = utf8(internal);
    int idx = ++count;
    u1(7);
    u2(u);
    return idx;
  }

  int nat(String n, String d) {
    int a = utf8(n), b = utf8(d);
    int idx = ++count;
    u1(12);
    u2(a);
    u2(b);
    return idx;
  }

  int mref(String c, String n, String d) {
    int k = cls(c), t = nat(n, d);
    int idx = ++count;
    u1(10);
    u2(k);
    u2(t);
    return idx;
  }

  int fref(String c, String n, String d) {
    int k = cls(c), t = nat(n, d);
    int idx = ++count;
    u1(9);
    u2(k);
    u2(t);
    return idx;
  }

  int str(String s) {
    int u = utf8(s);
    int idx = ++count;
    u1(8);
    u2(u);
    return idx;
  }

  int integer(int v) {
    int idx = ++count;
    u1(3);
    u4(v);
    return idx;
  }

  int dbl(double v) {
    long bits = Double.doubleToLongBits(v);
    int idx = ++count;
    u1(6);
    for (int i = 7; i >= 0; i--) u1((int) (bits >> (8 * i)));
    count++; // wide
    return idx;
  }

  int mhandle(int kind, int ref) {
    int idx = ++count;
    u1(15);
    u1(kind);
    u2(ref);
    return idx;
  }

  int mtype(String d) {
    int u = utf8(d);
    int idx = ++count;
    u1(16);
    u2(u);
    return idx;
  }

  int indy(int bsm, String n, String d) {
    int t = nat(n, d);
    int idx = ++count;
    u1(18);
    u2(bsm);
    u2(t);
    return idx;
  }

  int count = 0;

  byte[] prefix(int access, int thisC, int superC, int[] ifs) {
    ByteArrayOutputStream o = new ByteArrayOutputStream();
    o.writeBytes(new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE});
    // major 51 (Java 7) — invokedynamic + MethodHandle; linear code
    // needs no StackMapTable frames.
    o.writeBytes(new byte[] {0, 0, 0, 51});
    writeU2(o, count + 1);
    o.writeBytes(bo.toByteArray());
    writeU2(o, access);
    writeU2(o, thisC);
    writeU2(o, superC);
    writeU2(o, ifs.length);
    for (int i : ifs) writeU2(o, i);
    return o.toByteArray();
  }

  static void writeU2(ByteArrayOutputStream o, int v) {
    o.write((v >> 8) & 0xff);
    o.write(v & 0xff);
  }

  static void writeU4(ByteArrayOutputStream o, int v) {
    writeU2(o, v >> 16);
    writeU2(o, v);
  }
}
