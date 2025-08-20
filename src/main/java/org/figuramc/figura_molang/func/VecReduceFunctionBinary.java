package org.figuramc.figura_molang.func;

import org.figuramc.figura_molang.ast.MolangExpr;
import org.figuramc.figura_molang.ast.vars.TempVariable;
import org.figuramc.figura_molang.compile.CompilationContext;
import org.figuramc.figura_molang.compile.MolangCompileException;
import org.figuramc.figura_molang.compile.BytecodeUtil;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.List;
import java.util.function.Consumer;

/**
 * Reduction operation converting 2 vectors -> number.
 *
 * @param initial The initial value of the accumulator
 * @param reduce 2 floats are on the stack, convert to 1 float. (used when both are non-vector args)
 * @param preAccum 2 floats are on the stack (a, b). Prepare for the accumulator to be pushed.
 * @param postAccum The result of preAccum and the accumulator are on the stack. Reduce to the accumulator.
 * @param post The accumulator result is on the stack. Post-process it.
 */
public record VecReduceFunctionBinary(String name, float initial, Consumer<MethodVisitor> reduce, Consumer<MethodVisitor> preAccum, Consumer<MethodVisitor> postAccum, Consumer<MethodVisitor> post) implements MolangFunction {

    public static final VecReduceFunctionBinary DOT_PRODUCT = new VecReduceFunctionBinary("math.dot", 0f,
            v -> v.visitInsn(Opcodes.FMUL),
            v -> {},
            v -> v.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "fma", "(FFF)F", false),
            v -> {}
    );
    public static final VecReduceFunctionBinary DISTANCE = new VecReduceFunctionBinary("math.dist", 0f,
            v -> { v.visitInsn(Opcodes.FSUB); v.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "abs", "(F)F", false); },
            v -> { v.visitInsn(Opcodes.FSUB); v.visitInsn(Opcodes.DUP); }, // [a, b] -> [(a - b), (a - b)]
            v -> v.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "fma", "(FFF)F", false), // [(a - b), (a - b), accum] -> [accum + (a - b)^2]
            v -> { v.visitInsn(Opcodes.F2D); v.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "sqrt", "(D)D", false); v.visitInsn(Opcodes.D2F); } // sqrt(sum((a - b)^2))
    );

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
            reduce.accept(visitor);
            return;
        }

        // Store A and B
        context.push();
        int aIdx = store(a, visitor, context);
        int bIdx = store(b, visitor, context);

        // For loop
        int accum = context.reserveLocals(1);
        int counterLocal = context.reserveLocals(1);
        // Store initial value in accumulator
        BytecodeUtil.constFloat(visitor, initial);
        visitor.visitVarInsn(Opcodes.FSTORE, accum);
        // Combine vec values
        BytecodeUtil.repeatNTimes(visitor, Math.max(a.returnCount(), b.returnCount()), counterLocal, v -> {
            // Load A
            if (a.isVector()) {
                v.visitVarInsn(Opcodes.ALOAD, 1); // [temp]
                BytecodeUtil.constInt(v, aIdx); // [temp, aIdx]
                v.visitVarInsn(Opcodes.ILOAD, counterLocal); // [temp, aIdx, counter]
                v.visitInsn(Opcodes.IADD); // [temp, aIdx + counter]
                v.visitInsn(Opcodes.FALOAD); // [temp[aIdx + counter]]
            } else {
                v.visitVarInsn(Opcodes.FLOAD, aIdx);
            }
            // Load B
            if (b.isVector()) {
                v.visitVarInsn(Opcodes.ALOAD, 1); // [temp[aIdx + counter], temp]
                BytecodeUtil.constInt(v, bIdx); // [temp[aIdx + counter], temp, bIdx]
                v.visitVarInsn(Opcodes.ILOAD, counterLocal); // [temp[aIdx + counter], temp, bIdx, counter]
                v.visitInsn(Opcodes.IADD); // [temp[aIdx + counter], temp, bIdx + counter]
                v.visitInsn(Opcodes.FALOAD); // [temp[aIdx + counter], temp[bIdx + counter]]
            } else {
                v.visitVarInsn(Opcodes.FLOAD, bIdx);
            }
            // Pre-accumulator stage
            preAccum.accept(v);
            // Load accumulator
            v.visitVarInsn(Opcodes.FLOAD, accum); // [temp[aIdx + counter], temp[bIdx + counter], accum]
            // Post-accumulator
            postAccum.accept(v);
            // Store accumulator
            v.visitVarInsn(Opcodes.FSTORE, accum); // []
        });
        // Leave accum on stack
        visitor.visitVarInsn(Opcodes.FLOAD, accum);
        // Apply final operation
        post.accept(visitor);
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
