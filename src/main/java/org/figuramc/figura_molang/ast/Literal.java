package org.figuramc.figura_molang.ast;

import org.figuramc.figura_molang.compile.jvm.JvmCompilationContext;
import org.figuramc.figura_molang.compile.jvm.BytecodeUtil;
import org.objectweb.asm.MethodVisitor;

// A literal of a floating point value
public class Literal extends MolangExpr {

    public final float value;

    public Literal(float value) {
        this.value = value;
    }

    @Override
    protected int computeReturnCount() {
        return 1;
    }

    @Override
    public void compileToJvmBytecode(MethodVisitor visitor, int outputArrayIndex, JvmCompilationContext context) {
        BytecodeUtil.constFloat(visitor, value);
    }
}
