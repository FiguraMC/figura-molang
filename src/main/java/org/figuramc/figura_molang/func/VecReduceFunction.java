package org.figuramc.figura_molang.func;

import org.figuramc.figura_molang.ast.MolangExpr;
import org.figuramc.figura_molang.ast.vars.TempVariable;
import org.figuramc.figura_molang.compile.jvm.JvmCompilationContext;
import org.figuramc.figura_molang.compile.MolangCompileException;
import org.figuramc.figura_molang.compile.jvm.BytecodeUtil;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.List;
import java.util.function.Consumer;

/**
 * Reduction operation converting a vector -> number.
 *
 * @param initial The initial value of the accumulator.
 * @param preAccum An element of the vec is on the stack. Prep it for the accumulator.
 * @param postAccum The result of preAccum and the accumulator are on the stack. Reduce to just the accumulator.
 * @param post The accumulator result is on the stack. Post-process it.
 */
public record VecReduceFunction(String name, float initial, Consumer<MethodVisitor> ifScalar, Consumer<MethodVisitor> preAccum, Consumer<MethodVisitor> postAccum, Consumer<MethodVisitor> post) implements MolangFunction {

    public static final VecReduceFunction SUM = new VecReduceFunction("math.sum", 0f,
            v -> {},
            v -> {},
            v -> v.visitInsn(Opcodes.FADD),
            v -> {}
    );
    public static final VecReduceFunction PRODUCT = new VecReduceFunction("math.product", 1f,
            v -> {},
            v -> {},
            v -> v.visitInsn(Opcodes.FMUL),
            v -> {}
    );
    public static final VecReduceFunction MIN_ELEM = new VecReduceFunction("math.min_elem", Float.POSITIVE_INFINITY,
            v -> {},
            v -> {},
            v -> v.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "min", "(FF)F", false),
            v -> {}
    );
    public static final VecReduceFunction MAX_ELEM = new VecReduceFunction("math.max_elem", Float.NEGATIVE_INFINITY,
            v -> {},
            v -> {},
            v -> v.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "max", "(FF)F", false),
            v -> {}
    );



    @Override
    public void checkArgs(List<MolangExpr> args, String source, int funcNameStart, int funcNameEnd) throws MolangCompileException {
        if (args.size() != 1) throw new MolangCompileException(MolangCompileException.WRONG_ARG_COUNT, name(), "1", String.valueOf(args.size()), source, funcNameStart, funcNameEnd);
    }

    @Override
    public int returnCount(List<MolangExpr> args) {
        return 1;
    }

    @Override
    public void compile(MethodVisitor visitor, List<MolangExpr> args, int outputArrayIndex, JvmCompilationContext context) {
        MolangExpr arg = args.getFirst();
        // Test easy case first
        if (!arg.isVector()) {
            // If not a vector, just compile and apply ifScalar
            arg.compileToJvmBytecode(visitor, outputArrayIndex, context);
            ifScalar.accept(visitor);
            return;
        }

        context.push();
        int arrayLocation;
        // If already in temp variable,
        if (arg instanceof TempVariable tempVar) {
            arrayLocation = tempVar.getRealLocation(context);
        } else {
            arrayLocation = context.reserveArraySlots(arg.returnCount());
            arg.compileToJvmBytecode(visitor, arrayLocation, context);
        }
        int accum = context.reserveLocals(1);
        int counterLocal = context.reserveLocals(1);
        // Store initial in accumulator
        BytecodeUtil.constFloat(visitor, initial);
        visitor.visitVarInsn(Opcodes.FSTORE, accum);
        // Reduce all values from vec
        BytecodeUtil.repeatNTimes(visitor, arg.returnCount(), counterLocal, v -> {
            v.visitVarInsn(Opcodes.ALOAD, context.arrayVariableIndex); // [temp]
            BytecodeUtil.constInt(v, arrayLocation); // [temp, varloc]
            v.visitVarInsn(Opcodes.ILOAD, counterLocal); // [temp, varloc, counter]
            v.visitInsn(Opcodes.IADD); // [temp, varloc + counter]
            v.visitInsn(Opcodes.FALOAD); // [temp[varloc + counter]]
            preAccum.accept(v);
            v.visitVarInsn(Opcodes.FLOAD, accum); // [temp[varloc + counter], accum]
            postAccum.accept(v);
            v.visitVarInsn(Opcodes.FSTORE, accum); // []
        });
        // Leave accum on stack
        visitor.visitVarInsn(Opcodes.FLOAD, accum);
        post.accept(visitor); // Apply post process
        context.pop();
    }

}
