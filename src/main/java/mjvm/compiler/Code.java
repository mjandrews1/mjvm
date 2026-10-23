package mjvm.compiler;

import java.io.ByteArrayOutputStream;


/** Split from BytecodeEmitter god file (#58). */
final class Code {
    final ByteArrayOutputStream o = new ByteArrayOutputStream();

    void u1(int v) {
        o.write(v & 0xff);
    }

    void u2(int v) {
        o.write((v >> 8) & 0xff);
        o.write(v & 0xff);
    }

    void op(int b) {
        u1(b);
    }

    void bipush(int n) {
        u1(0x10);
        u1(n);
    }

    void ldc(int idx) {
        u1(0x12);
        u2(idx);
    }

    void ldc2(int idx) {
        u1(0x14);
        u2(idx);
    }

    void iload(int n) {
        u1(0x15);
        u1(n);
    }

    void dload(int n) {
        u1(0x18);
        u1(n);
    }

    void aload(int n) {
        u1(0x19);
        u1(n);
    }

    void aload0() {
        u1(0x2a);
    }

    void istore(int n) {
        u1(0x36);
        u1(n);
    }

    void dstore(int n) {
        u1(0x39);
        u1(n);
    }

    void astore(int n) {
        u1(0x3a);
        u1(n);
    }

    void iinc(int n, int d) {
        u1(0x84);
        u1(n);
        u1(d);
    }

    void aaload() {
        u1(0x32);
    }

    void aastore() {
        u1(0x53);
    }

    void dadd() {
        u1(0x63);
    }

    void dsub() {
        u1(0x67);
    }

    void dmul() {
        u1(0x6b);
    }

    void ddiv() {
        u1(0x6f);
    }

    void isub() {
        u1(0x64);
    }

    void iconst_1_() {
        u1(0x04);
    }

    void iconst_2_() {
        u1(0x05);
    }

    void invokespecial(int i) {
        u1(0xb7);
        u2(i);
    }

    void invokevirtual(int i) {
        u1(0xb6);
        u2(i);
    }

    void invokestatic(int i) {
        u1(0xb8);
        u2(i);
    }

    void invokedynamic(int i) {
        u1(0xba);
        u2(i);
        u2(0);
    }

    void getfield(int i) {
        u1(0xb4);
        u2(i);
    }

    void anewarray(int i) {
        u1(0xbd);
        u2(i);
    }

    void vreturn() {
        u1(0xb1);
    }

    byte[] bytes() {
        return o.toByteArray();
    }
}
