package org.figuramc.figura_molang.ast.control_flow;

import org.figuramc.figura_molang.ast.MolangExpr;
import org.figuramc.figura_molang.compile.jvm.JvmCompilationContext;
import org.figuramc.figura_molang.compile.jvm.BytecodeUtil;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Condition must be a scalar.
 * If the branches are both vectors, they must have the same size. If only one is a vector, the other will be splatted.
 */
public class Ternary extends MolangExpr {

    private final MolangExpr condition, ifTrue, ifFalse;

    public Ternary(MolangExpr condition, MolangExpr ifTrue, MolangExpr ifFalse) {
        // Check arg sizes match up to requirements
        if (condition.isVector()) throw new IllegalStateException("Ternary condition must not be a vector. This should have been already checked!");
        if (ifTrue.returnCount() != ifFalse.returnCount()) throw new IllegalStateException("Ternary branch results should be the same size, got " + ifTrue.returnCount() + " and " + ifFalse.returnCount() + ". This should have been already checked!");
        this.condition = condition;
        this.ifTrue = ifTrue;
        this.ifFalse = ifFalse;
    }

    @Override
    protected int computeReturnCount() {
        return ifTrue.isVector() ? ifTrue.returnCount() : ifFalse.returnCount();
    }

    @Override
    public void compileToJvmBytecode(MethodVisitor visitor, int outputArrayIndex, JvmCompilationContext context) {
        condition.compileToJvmBytecode(visitor, outputArrayIndex, context);
        visitor.visitInsn(Opcodes.FCONST_0);
        visitor.visitInsn(Opcodes.FCMPL);
        BytecodeUtil.ifElse(
                visitor, Opcodes.IFEQ,
                v -> ifTrue.compileToJvmBytecode(v, outputArrayIndex, context),
                v -> ifFalse.compileToJvmBytecode(v, outputArrayIndex, context)
        );
    }
}
