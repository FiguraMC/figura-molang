package org.figuramc.figura_molang.ast;

import org.figuramc.figura_molang.compile.CompilationContext;
import org.objectweb.asm.MethodVisitor;

public abstract class MolangExpr {

    private int cachedReturnCount = -1;

    public final int returnCount() {
        if (cachedReturnCount == -1)
            cachedReturnCount = computeReturnCount();
        return cachedReturnCount;
    }

    public final boolean isVector() {
        return returnCount() > 1;
    }

    // The number of floats that this expr evaluates to
    protected abstract int computeReturnCount();

    // Compile this expression to JVM bytecode.
    // A float[] for temporaries is at local variable <context.arrayVariableIndex>.
    // If this outputs multiple values, write the results to the float[], starting at the given index.
    // If it outputs one value, push it on the stack instead.
    // If we Return a single value, push it on the stack and jump to returnLabel.
    // If we Return multiple values, put them in the array at returnArrayIndex and jump to returnLabel.
    public abstract void compile(MethodVisitor visitor, int outputArrayIndex, CompilationContext context);

}
