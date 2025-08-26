package org.figuramc.figura_molang.ast.vars;

import org.figuramc.figura_molang.ast.MolangExpr;
import org.figuramc.figura_molang.compile.CompilationContext;
import org.figuramc.figura_molang.compile.BytecodeUtil;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

// Instanceof checks can be used to fetch the location, increasing efficiency of calls.
// If this is a vector, the location is an index in the float[] where the values start.
// If it's a scalar, it's a local variable index.
public class TempVariable extends MolangExpr {

    public final String name;
    public final int size;
    private final int location;

    public TempVariable(String name, int size, int location) {
        this.name = name;
        this.size = size;
        this.location = location;
    }

    @Override
    protected int computeReturnCount() {
        return size;
    }

    public int getLogicalLocation() {
        return location; // Location within compilation, ignoring offsets
    }

    public int getRealLocation(CompilationContext context) {
        // Offset for reserved space
        return isVector() ? location : location + context.arrayVariableIndex + 1;
    }

    // Not always required to run; some code can use it directly from its local variable/array location without a copy
    @Override
    public void compile(MethodVisitor visitor, int outputArrayIndex, CompilationContext context) {
        if (isVector()) {
            // If variable is already at the right location, don't need to do anything!
            if (outputArrayIndex == getRealLocation(context)) return;
            // Copy to the output location, use System.arraycopy()
            BytecodeUtil.constInt(visitor, getRealLocation(context));
            visitor.visitVarInsn(Opcodes.ALOAD, context.arrayVariableIndex);
            visitor.visitInsn(Opcodes.DUP_X1);
            BytecodeUtil.constInt(visitor, outputArrayIndex);
            BytecodeUtil.constInt(visitor, size);
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V", false);
        } else {
            // Load the local to the stack
            visitor.visitVarInsn(Opcodes.FLOAD, getRealLocation(context));
        }
    }
}
