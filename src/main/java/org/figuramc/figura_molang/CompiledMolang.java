package org.figuramc.figura_molang;


import java.util.Arrays;

public abstract class CompiledMolang<Actor> {

    public final MolangInstance<Actor, ?> instance;
    public final int argCount;
    public final int returnCount;

    public CompiledMolang(MolangInstance<Actor, ?> instance, int argCount, int returnCount) {
        this.instance = instance;
        this.argCount = argCount;
        this.returnCount = returnCount;
    }

    // Returns a (potentially large) array.
    // Result values are stored in the first <returnCount> entries of the array.
    protected float[] evaluateImpl() { throw new UnsupportedOperationException("Wrong argument count to CompiledMolang.evaluateImpl()"); }
    protected float[] evaluateImpl(float a) { throw new UnsupportedOperationException("Wrong argument count to CompiledMolang.evaluateImpl()"); }
    protected float[] evaluateImpl(float a, float b) { throw new UnsupportedOperationException("Wrong argument count to CompiledMolang.evaluateImpl()"); }
    protected float[] evaluateImpl(float a, float b, float c) { throw new UnsupportedOperationException("Wrong argument count to CompiledMolang.evaluateImpl()"); }
    protected float[] evaluateImpl(float a, float b, float c, float d) { throw new UnsupportedOperationException("Wrong argument count to CompiledMolang.evaluateImpl()"); }
    protected float[] evaluateImpl(float a, float b, float c, float d, float e) { throw new UnsupportedOperationException("Wrong argument count to CompiledMolang.evaluateImpl()"); }
    protected float[] evaluateImpl(float a, float b, float c, float d, float e, float f) { throw new UnsupportedOperationException("Wrong argument count to CompiledMolang.evaluateImpl()"); }
    protected float[] evaluateImpl(float a, float b, float c, float d, float e, float f, float g) { throw new UnsupportedOperationException("Wrong argument count to CompiledMolang.evaluateImpl()"); }
    protected float[] evaluateImpl(float a, float b, float c, float d, float e, float f, float g, float h) { throw new UnsupportedOperationException("Wrong argument count to CompiledMolang.evaluateImpl()"); }

    // TODO Catch errors around evaluation and error out the molang's owning avatar?

    // Evaluate the expr and return a slice letting you access result values safely
    public final FloatArraySlice evaluate() {
        if (instance.reEntrantFlag < 2) {
            try {
                instance.reEntrantFlag++;
                return new FloatArraySlice(evaluateImpl(), 0, returnCount);
            } finally {
                instance.reEntrantFlag--;
            }
        } else {
            return new FloatArraySlice(evaluateImpl(), 0, returnCount);
        }
    }

    // Compressed copies of the above function, except with different arg counts :P
    public final FloatArraySlice evaluate(float a) { if (instance.reEntrantFlag < 2) { try { instance.reEntrantFlag++; return new FloatArraySlice(evaluateImpl(a), 0, returnCount); } finally { instance.reEntrantFlag--; } } else { return new FloatArraySlice(evaluateImpl(a), 0, returnCount); } }
    public final FloatArraySlice evaluate(float a, float b) { if (instance.reEntrantFlag < 2) { try { instance.reEntrantFlag++; return new FloatArraySlice(evaluateImpl(a, b), 0, returnCount); } finally { instance.reEntrantFlag--; } } else { return new FloatArraySlice(evaluateImpl(a, b), 0, returnCount); } }
    public final FloatArraySlice evaluate(float a, float b, float c) { if (instance.reEntrantFlag < 2) { try { instance.reEntrantFlag++; return new FloatArraySlice(evaluateImpl(a, b, c), 0, returnCount); } finally { instance.reEntrantFlag--; } } else { return new FloatArraySlice(evaluateImpl(a, b, c), 0, returnCount); } }
    public final FloatArraySlice evaluate(float a, float b, float c, float d) { if (instance.reEntrantFlag < 2) { try { instance.reEntrantFlag++; return new FloatArraySlice(evaluateImpl(a, b, c, d), 0, returnCount); } finally { instance.reEntrantFlag--; } } else { return new FloatArraySlice(evaluateImpl(a, b, c, d), 0, returnCount); } }
    public final FloatArraySlice evaluate(float a, float b, float c, float d, float e) { if (instance.reEntrantFlag < 2) { try { instance.reEntrantFlag++; return new FloatArraySlice(evaluateImpl(a, b, c, d, e), 0, returnCount); } finally { instance.reEntrantFlag--; } } else { return new FloatArraySlice(evaluateImpl(a, b, c, d, e), 0, returnCount); } }
    public final FloatArraySlice evaluate(float a, float b, float c, float d, float e, float f) { if (instance.reEntrantFlag < 2) { try { instance.reEntrantFlag++; return new FloatArraySlice(evaluateImpl(a, b, c, d, e, f), 0, returnCount); } finally { instance.reEntrantFlag--; } } else { return new FloatArraySlice(evaluateImpl(a, b, c, d, e, f), 0, returnCount); } }
    public final FloatArraySlice evaluate(float a, float b, float c, float d, float e, float f, float g) { if (instance.reEntrantFlag < 2) { try { instance.reEntrantFlag++; return new FloatArraySlice(evaluateImpl(a, b, c, d, e, f, g), 0, returnCount); } finally { instance.reEntrantFlag--; } } else { return new FloatArraySlice(evaluateImpl(a, b, c, d, e, f, g), 0, returnCount); } }
    public final FloatArraySlice evaluate(float a, float b, float c, float d, float e, float f, float g, float h) { if (instance.reEntrantFlag < 2) { try { instance.reEntrantFlag++; return new FloatArraySlice(evaluateImpl(a, b, c, d, e, f, g, h), 0, returnCount); } finally { instance.reEntrantFlag--; } } else { return new FloatArraySlice(evaluateImpl(a, b, c, d, e, f, g, h), 0, returnCount); } }


    // Don't hold this for long - it keeps a reference to the (possibly large) backing array
    public static class FloatArraySlice {

        private final float[] array;
        private final int start;
        public final int length;

        public FloatArraySlice(float[] array, int start, int length) {
            this.array = array;
            this.start = start;
            this.length = length;
        }

        public float[] copy() { return Arrays.copyOfRange(array, start, start + length); }
        public float get(int index) {
            if (index >= 0 && index < length)
                return array[index + start];
            throw new ArrayIndexOutOfBoundsException("Index must be inside array");
        }

        @Override
        public String toString() {
            return Arrays.toString(copy());
        }
    }

}
