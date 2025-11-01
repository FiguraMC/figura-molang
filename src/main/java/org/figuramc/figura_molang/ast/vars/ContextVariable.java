package org.figuramc.figura_molang.ast.vars;

import org.figuramc.figura_molang.ast.MolangExpr;
import org.figuramc.figura_molang.compile.jvm.JvmCompilationContext;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class ContextVariable extends MolangExpr {

    public final String name;
    public final int index;

    public ContextVariable(String name, int index) {
        this.name = name;
        this.index = index;
    }

    @Override
    protected int computeReturnCount() {
        return 1;
    }

    @Override
    public void compileToJvmBytecode(MethodVisitor visitor, int outputArrayIndex, JvmCompilationContext context) {
        // Just load the local variable to the stack.
        // Offset by 1 because that's where the "this" instance of CompiledMolang is stored.
        visitor.visitVarInsn(Opcodes.FLOAD, 1 + this.index);
    }
}
