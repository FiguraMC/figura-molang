package org.figuramc.figura_molang.ast.control_flow;

import org.figuramc.figura_molang.ast.MolangExpr;
import org.figuramc.figura_molang.compile.CompilationContext;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

// Both arguments must be scalars, because this is a logical operation and we want short-circuiting.
public class LogicalAnd extends MolangExpr {

    private final MolangExpr left, right;

    public LogicalAnd(MolangExpr left, MolangExpr right) {
        if (left.isVector() || right.isVector()) throw new IllegalStateException("Logical and (&&) requires non-vector arguments. Should have already been checked!");
        this.left = left;
        this.right = right;
    }

    @Override
    protected int computeReturnCount() {
        return 1;
    }

    @Override
    public void compile(MethodVisitor visitor, int outputArrayIndex, CompilationContext context) {
        Label zero = new Label();
        Label end = new Label();
        // Compare left against 0
        left.compile(visitor, outputArrayIndex, context);
        visitor.visitInsn(Opcodes.FCONST_0);
        visitor.visitInsn(Opcodes.FCMPL);
        // If left == 0, goto zero
        visitor.visitJumpInsn(Opcodes.IFEQ, zero);
        // Otherwise compute right, compare against 0
        right.compile(visitor, outputArrayIndex, context);
        visitor.visitInsn(Opcodes.FCONST_0);
        visitor.visitInsn(Opcodes.FCMPL);
        // If right == 0, goto zero
        visitor.visitJumpInsn(Opcodes.IFEQ, zero);
        // Push one, and goto end
        visitor.visitInsn(Opcodes.FCONST_1);
        visitor.visitJumpInsn(Opcodes.GOTO, end);
        // Zero:
        visitor.visitLabel(zero);
        visitor.visitInsn(Opcodes.FCONST_0);
        // End:
        visitor.visitLabel(end);
    }
}
