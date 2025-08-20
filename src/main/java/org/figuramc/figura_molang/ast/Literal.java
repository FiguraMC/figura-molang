package org.figuramc.figura_molang.ast;

import org.figuramc.figura_molang.compile.CompilationContext;
import org.figuramc.figura_molang.compile.BytecodeUtil;
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
    public void compile(MethodVisitor visitor, int outputArrayIndex, CompilationContext context) {
        BytecodeUtil.constFloat(visitor, value);
    }
}
