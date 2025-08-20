package org.figuramc.figura_molang.ast;

import org.figuramc.figura_molang.compile.CompilationContext;
import org.figuramc.figura_molang.compile.BytecodeUtil;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.List;

public class VectorConstructor extends MolangExpr {

    private final List<MolangExpr> exprs;

    public VectorConstructor(List<MolangExpr> exprs) {
        this.exprs = exprs;
        if (exprs.size() <= 1) throw new IllegalStateException("Vector constructor expects at least 2 args - this should have already been checked!");
    }

    @Override
    public int computeReturnCount() {
        return exprs.stream().mapToInt(MolangExpr::returnCount).sum();
    }

    @Override
    public void compile(MethodVisitor visitor, int outputArrayIndex, CompilationContext context) {
        int i = outputArrayIndex;
        for (var expr : exprs) {
            if (expr.returnCount() == 1) {
                visitor.visitVarInsn(Opcodes.ALOAD, 1); // Load the array
                BytecodeUtil.constInt(visitor, i);
                expr.compile(visitor, i, context);
                visitor.visitInsn(Opcodes.FASTORE);
            } else {
                expr.compile(visitor, i, context);
            }
            i += expr.returnCount();
        }
    }
}
