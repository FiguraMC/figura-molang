package org.figuramc.figura_molang.func;


import org.figuramc.figura_molang.ast.MolangExpr;
import org.figuramc.figura_molang.ast.vars.TempVariable;
import org.figuramc.figura_molang.compile.CompilationContext;
import org.figuramc.figura_molang.compile.MolangCompileException;
import org.figuramc.figura_molang.compile.BytecodeUtil;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.List;
import java.util.function.BiConsumer;

// Used for operations like ==, <=, etc, which always yield a scalar.
// Two values are on the stack in the tester.
// If the comparison fails, and we should yield false, jump to the label.
public record ComparisonOperator(String name, BiConsumer<MethodVisitor, Label> tester) implements MolangFunction {

    public static final ComparisonOperator EQ_OP = new ComparisonOperator("a == b", (v, fail) -> { v.visitInsn(Opcodes.FCMPL); v.visitJumpInsn(Opcodes.IFNE, fail); });
    public static final ComparisonOperator NE_OP = new ComparisonOperator("a != b", (v, fail) -> { v.visitInsn(Opcodes.FCMPL); v.visitJumpInsn(Opcodes.IFEQ, fail); });
    public static final ComparisonOperator LT_OP = new ComparisonOperator("a < b", (v, fail) -> { v.visitInsn(Opcodes.FCMPG); v.visitJumpInsn(Opcodes.IFGE, fail); });
    public static final ComparisonOperator LE_OP = new ComparisonOperator("a <= b", (v, fail) -> { v.visitInsn(Opcodes.FCMPG); v.visitJumpInsn(Opcodes.IFGT, fail); });
    public static final ComparisonOperator GT_OP = new ComparisonOperator("a > b", (v, fail) -> { v.visitInsn(Opcodes.FCMPL); v.visitJumpInsn(Opcodes.IFLE, fail); });
    public static final ComparisonOperator GE_OP = new ComparisonOperator("a >= b", (v, fail) -> { v.visitInsn(Opcodes.FCMPL); v.visitJumpInsn(Opcodes.IFLT, fail); });

    @Override
    public void checkArgs(List<MolangExpr> args, String source, int funcNameStart, int funcNameEnd) throws MolangCompileException {
        if (args.size() != 2)
            throw new MolangCompileException(MolangCompileException.WRONG_ARG_COUNT, name(), "2", String.valueOf(args.size()), source, funcNameStart, funcNameEnd);
        if (args.get(0).isVector() && args.get(1).isVector() && args.get(0).returnCount() != args.get(1).returnCount())
            throw new MolangCompileException(MolangCompileException.VECTOR_ARGS_SAME_SIZE, name(), args.get(0).returnCount(), args.get(1).returnCount(), source, funcNameStart, funcNameEnd);
    }

    @Override
    public int returnCount(List<MolangExpr> args) {
        return 1;
    }

    @Override
    public void compile(MethodVisitor visitor, List<MolangExpr> args, int outputArrayIndex, CompilationContext context) {
        MolangExpr a = args.get(0);
        MolangExpr b = args.get(1);

        // Handle the trivial case
        if (!a.isVector() && !b.isVector()) {
            a.compile(visitor, outputArrayIndex, context);
            b.compile(visitor, outputArrayIndex, context);
            Label fail = new Label();
            Label end = new Label();
            tester.accept(visitor, fail);
            BytecodeUtil.constFloat(visitor, 1.0f); // Success
            visitor.visitJumpInsn(Opcodes.GOTO, end);
            visitor.visitLabel(fail);
            BytecodeUtil.constFloat(visitor, 0.0f); // Failure
            visitor.visitLabel(end);
            return;
        }

        // Get locations for A and B
        context.push();
        int aIdx = store(a, visitor, context);
        int bIdx = store(b, visitor, context);

        Label fail = new Label();
        Label end = new Label();

        // For loop
        int counterLocal = context.reserveLocals(1);
        // Combine vec values
        BytecodeUtil.repeatNTimes(visitor, a.returnCount(), counterLocal, v -> {
            // Load A
            if (a.isVector()) {
                v.visitVarInsn(Opcodes.ALOAD, 1); // [temp]
                BytecodeUtil.constInt(v, aIdx); // [temp, aIdx]
                v.visitVarInsn(Opcodes.ILOAD, counterLocal); // [temp, aIdx, counter]
                v.visitInsn(Opcodes.IADD); // [temp, aIdx + counter]
                v.visitInsn(Opcodes.FALOAD); // [a]
            } else {
                v.visitVarInsn(Opcodes.FLOAD, aIdx); // [a]
            }
            // Load B
            if (b.isVector()) {
                // Load elem of B
                v.visitVarInsn(Opcodes.ALOAD, 1); // [a, temp]
                BytecodeUtil.constInt(v, bIdx); // [a, temp, bIdx]
                v.visitVarInsn(Opcodes.ILOAD, counterLocal); // [a, temp, bIdx, counter]
                v.visitInsn(Opcodes.IADD); // [a, temp, bIdx + counter]
                v.visitInsn(Opcodes.FALOAD); // [a, b]
            } else {
                v.visitVarInsn(Opcodes.FLOAD, bIdx); // [a, b]
            }
            // Run the tester. If the comparison fails, we jump to fail.
            tester.accept(v, fail);
        });
        BytecodeUtil.constFloat(visitor, 1.0f); // Success
        visitor.visitJumpInsn(Opcodes.GOTO, end);
        visitor.visitLabel(fail);
        BytecodeUtil.constFloat(visitor, 0.0f); // Failure
        visitor.visitLabel(end);

        // Pop
        context.pop();
    }

    private static int store(MolangExpr expr, MethodVisitor visitor, CompilationContext context) {
        if (expr instanceof TempVariable temp) {
            return temp.getRealLocation();
        } else if (!expr.isVector()) {
            int idx = context.reserveLocals(1);
            expr.compile(visitor, -1, context);
            visitor.visitVarInsn(Opcodes.FSTORE, idx);
            return idx;
        } else {
            int idx = context.reserveArraySlots(expr.returnCount());
            expr.compile(visitor, idx, context);
            return idx;
        }
    }
}
