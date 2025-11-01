package org.figuramc.figura_molang.ast.vars;

import org.figuramc.figura_molang.ast.MolangExpr;
import org.figuramc.figura_molang.compile.jvm.JvmCompilationContext;
import org.figuramc.figura_molang.compile.jvm.BytecodeUtil;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

// Assign to a temp variable
public class TempVariableAssign extends MolangExpr {

    private final TempVariable variable; // Which variable to assign to
    private final MolangExpr rhs; // What to assign to the variable

    public TempVariableAssign(TempVariable variable, MolangExpr rhs) {
        this.variable = variable;
        this.rhs = rhs;
        // Assert that the expr and the variable are the same size
        if (variable.size != rhs.returnCount())
            throw new IllegalStateException("Temp variable \"" + variable.name + "\" is size " + variable.size + ", but you try to assign " + rhs.returnCount() + " elements to it. This should have already been checked!");
    }

    @Override
    protected int computeReturnCount() {
        return 1; // Assignment always evaluates to the scalar 0
    }

    @Override
    public void compileToJvmBytecode(MethodVisitor visitor, int outputArrayIndex, JvmCompilationContext context) {
        // Compile the expr, putting its result into the variable
        if (variable.isVector()) {
            // If vector, compile and place result there
            rhs.compileToJvmBytecode(visitor, variable.getRealLocation(context), context);
        } else {
            // If scalar, compile which pushes to stack, then store in local
            rhs.compileToJvmBytecode(visitor, outputArrayIndex, context);
            visitor.visitVarInsn(Opcodes.FSTORE, variable.getRealLocation(context));
        }
        // Push scalar 0 on the stack
        BytecodeUtil.constFloat(visitor, 0f);
    }

}
