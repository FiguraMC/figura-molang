package org.figuramc.figura_molang.ast.control_flow;

import org.figuramc.figura_molang.ast.MolangExpr;
import org.figuramc.figura_molang.compile.jvm.JvmCompilationContext;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

// Both arguments must be scalars, because this is a logical operation and we want short-circuiting.
public class LogicalOr extends MolangExpr {

    private final MolangExpr left, right;

    public LogicalOr(MolangExpr left, MolangExpr right) {
        if (left.isVector() || right.isVector()) throw new IllegalStateException("Logical or (||) requires non-vector arguments. Should have already been checked!");
        this.left = left;
        this.right = right;
    }

    @Override
    protected int computeReturnCount() {
        return 1;
    }

    @Override
    public void compileToJvmBytecode(MethodVisitor visitor, int outputArrayIndex, JvmCompilationContext context) {
        Label one = new Label();
        Label end = new Label();
        // Compare left against 0
        left.compileToJvmBytecode(visitor, outputArrayIndex, context);
        visitor.visitInsn(Opcodes.FCONST_0);
        visitor.visitInsn(Opcodes.FCMPL);
        // If left != 0, goto one
        visitor.visitJumpInsn(Opcodes.IFNE, one);
        // Otherwise compute right, compare against 0
        right.compileToJvmBytecode(visitor, outputArrayIndex, context);
        visitor.visitInsn(Opcodes.FCONST_0);
        visitor.visitInsn(Opcodes.FCMPL);
        // If right != 0, goto one
        visitor.visitJumpInsn(Opcodes.IFNE, one);
        // Push zero, and goto end
        visitor.visitInsn(Opcodes.FCONST_0);
        visitor.visitJumpInsn(Opcodes.GOTO, end);
        // One:
        visitor.visitLabel(one);
        visitor.visitInsn(Opcodes.FCONST_1);
        // End:
        visitor.visitLabel(end);
    }
}
