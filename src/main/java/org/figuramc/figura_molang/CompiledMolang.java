package org.figuramc.figura_molang;


import java.util.Arrays;

public abstract class CompiledMolang<Actor> {

    public final MolangInstance<Actor, ?> instance;
    public final int returnCount;

    public CompiledMolang(MolangInstance<Actor, ?> instance, int returnCount) {
        this.instance = instance;
        this.returnCount = returnCount;
    }

    // Returns a (potentially large) array.
    // Result values are stored in the first <returnCount> entries of the array.
    protected abstract float[] evaluateImpl();

    // TODO Catch errors around evaluateImpl and error out the molang's owning avatar!

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

    // Evaluate the expr with a given actor.
    // The actor is reset to its previous value after evaluation.
    public final FloatArraySlice evaluate(Actor actor) {
        Actor oldActor = instance.actor;
        try {
            instance.actor = actor;
            return evaluate();
        } finally {
            instance.actor = oldActor;
        }
    }

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
