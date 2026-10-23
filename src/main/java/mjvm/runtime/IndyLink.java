package mjvm.runtime;

/**
 * mJVM M3 — functional interface used as an {@code invokedynamic}
 * call-site shape.  Generated code binds a lambda to this type; the
 * lambda bootstrap is LambdaMetafactory (a real INVOKEDYNAMIC).
 */
@FunctionalInterface
public interface IndyLink {
    void invoke(MRuntime rt, String name, int argc);
}
