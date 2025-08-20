package org.figuramc.figura_molang.ast.control_flow;

import org.figuramc.figura_molang.ast.MolangExpr;
import org.figuramc.figura_molang.compile.CompilationContext;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

// Return from the enclosing Compound
public class Return extends MolangExpr {

    public final MolangExpr expr;

    public Return(MolangExpr expr) {
        this.expr = expr;
    }

    // Technically this has no return count! But we'll say 1, since that's least likely to cause issues.
    @Override
    protected int computeReturnCount() {
        return 1;
    }

    @Override
    public void compile(MethodVisitor visitor, int outputArrayIndex, CompilationContext context) {
        // Compile the expression, putting its output at the return array slot (or pushing it on the stack if it's a scalar)
        expr.compile(visitor, context.getReturnArraySlot(), context);
        // Jump to the return label.
        // We don't worry about stack height issues, since all Returns in a given Compound must return the same number of items.
        visitor.visitJumpInsn(Opcodes.GOTO, context.getReturnLabel());
        // Should we push 0 to be consistent with our "return count"(?) TODO figure out if this breaks things
    }
}
