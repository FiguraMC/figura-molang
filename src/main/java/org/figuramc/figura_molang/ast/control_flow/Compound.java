package org.figuramc.figura_molang.ast.control_flow;

import org.figuramc.figura_molang.ast.MolangExpr;
import org.figuramc.figura_molang.ast.vars.TempVariable;
import org.figuramc.figura_molang.compile.CompilationContext;
import org.figuramc.figura_molang.compile.BytecodeUtil;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;

// Built during parsing, tracks state to ensure consistency
public class Compound extends MolangExpr {

    public final ArrayList<MolangExpr> exprs = new ArrayList<>();
    public final ArrayList<TempVariable> tempVars = new ArrayList<>();
    private int returnCount = 1;
    private boolean finalized = false;

    public int getCurrentReturnCount() {
        return returnCount;
    }

    public void setCurrentReturnCount(int returnCount) {
        if (finalized) throw new IllegalStateException("Attempt to set return count of Compound after finalized");
        this.returnCount = returnCount;
    }

    // Finish after parsing all exprs inside
    public void finish() {
        finalized = true;
    }

    @Override
    protected int computeReturnCount() {
        if (!finalized) throw new IllegalStateException("Attempt to compute return count of Compound before it's finalized!");
        return returnCount;
    }

    @Override
    public void compile(MethodVisitor visitor, int outputArrayIndex, CompilationContext context) {
        if (!finalized) throw new IllegalStateException("Attempt to compile Compound before it's finalized!");
        Label newReturnLabel = new Label(); // New return label for exprs inside
        context.push(newReturnLabel, outputArrayIndex); // Push context; the return index is the compound's output index

        // Compile each expr
        for (MolangExpr expr : exprs) {
            expr.compile(visitor, outputArrayIndex, context);
            // If the expr returned 1 value, pop it.
            if (!expr.isVector())
                visitor.visitInsn(Opcodes.POP);
        }

        // If it didn't return, in which case it would jump past this, push 0s
        if (isVector()) {
            // Store 0s with Arrays.fill
            visitor.visitVarInsn(Opcodes.ALOAD, context.arrayVariableIndex);
            BytecodeUtil.constInt(visitor, outputArrayIndex);
            BytecodeUtil.constInt(visitor, outputArrayIndex + returnCount());
            visitor.visitInsn(Opcodes.FCONST_0);
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Arrays", "fill", "([FIIF)V", false);
        } else {
            // Push 0 to stack
            visitor.visitInsn(Opcodes.FCONST_0);
        }
        visitor.visitLabel(newReturnLabel); // Ending label
        context.pop(); // Pop context
    }
}
