package org.figuramc.figura_molang.func;

import org.figuramc.figura_molang.ast.MolangExpr;
import org.figuramc.figura_molang.ast.vars.TempVariable;
import org.figuramc.figura_molang.compile.CompilationContext;
import org.figuramc.figura_molang.compile.MolangCompileException;
import org.figuramc.figura_molang.compile.BytecodeUtil;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A function defined via (argCount floats) -> float.
 * Vector args are processed element-wise.
 * Float args are splatted to match vector args.
 * All vector args are expected to be the same size.
 */
public record FloatFunction(String name, int argCount, Consumer<MethodVisitor> floatFunc, boolean usesDouble) implements MolangFunction {

    // Basic operators
    public static final FloatFunction ADD_OP = binop("a + b", Opcodes.FADD);
    public static final FloatFunction SUB_OP = binop("a - b", Opcodes.FSUB);
    public static final FloatFunction MUL_OP = binop("a * b", Opcodes.FMUL);
    public static final FloatFunction DIV_OP = binop("a / b", Opcodes.FDIV);
    public static final FloatFunction MOD_OP = binop("a % b", Opcodes.FREM);
    public static final FloatFunction NEG_OP = unop("-a", Opcodes.FNEG);
    // ! operator

    // Element-wise comparison operators
    public static final FloatFunction EQ = new FloatFunction("math.eq", 2, v -> BytecodeUtil.compareFloats(v, Opcodes.IFNE), false);
    public static final FloatFunction NE = new FloatFunction("math.ne", 2, v -> BytecodeUtil.compareFloats(v, Opcodes.IFEQ), false);
    public static final FloatFunction LT = new FloatFunction("math.lt", 2, v -> BytecodeUtil.compareFloats(v, Opcodes.IFGE), false);
    public static final FloatFunction LE = new FloatFunction("math.le", 2, v -> BytecodeUtil.compareFloats(v, Opcodes.IFGT), false);
    public static final FloatFunction GT = new FloatFunction("math.gt", 2, v -> BytecodeUtil.compareFloats(v, Opcodes.IFLE), false);
    public static final FloatFunction GE = new FloatFunction("math.ge", 2, v -> BytecodeUtil.compareFloats(v, Opcodes.IFLT), false);

    // Math functions
    public static final FloatFunction ABS = math("math.abs", 1, "abs", false);
    public static final FloatFunction ACOS = math("math.acos", 1, "acos", true, false, true);
    public static final FloatFunction ASIN = math("math.asin", 1, "asin", true, false, true);
    public static final FloatFunction ATAN = math("math.atan", 1, "atan", true, false, true);
    public static final FloatFunction ATAN2 = math("math.atan2", 2, "atan2", true, false, true);
    public static final FloatFunction CEIL = math("math.ceil", 1, "ceil", true);
    public static final FloatFunction CLAMP = math("math.clamp", 3, "clamp", false);
    public static final FloatFunction COS = math("math.cos", 1, "cos", true, true, false);
    // Die roll
    // Die roll integer
    public static final FloatFunction EXP = math("math.exp", 1, "exp", true);
    public static final FloatFunction FLOOR = math("math.floor", 1, "floor", true);
    // Hermite blend
    // Lerp
    // Lerp rotate
    public static final FloatFunction LN = math("math.ln", 1, "log", true);
    public static final FloatFunction MAX = math("math.max", 2, "max", false);
    // Min Angle
    public static final FloatFunction MIN = math("math.min", 2, "min", false);
    public static final FloatFunction MOD = new FloatFunction("math.mod", 2, v -> v.visitInsn(Opcodes.FREM), false);
    public static final FloatFunction POW = math("math.pow", 2, "pow", true);
    // Random
    // Random integer
    public static final FloatFunction ROUND = new FloatFunction("math.round", 1, v -> {
        v.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "round", "(F)F", false);
        v.visitInsn(Opcodes.I2F);
    }, false);
    public static final FloatFunction SIN = math("math.sin", 1, "sin", true, true, false);
    public static final FloatFunction SQRT = math("math.sqrt", 1, "sqrt", true);
    public static final FloatFunction TRUNC = new FloatFunction("math.trunc", 1, v -> {
        v.visitInsn(Opcodes.F2I);
        v.visitInsn(Opcodes.I2F);
    }, false);


    private static FloatFunction math(String  name, int argCount, String jvmName, boolean usesDouble) {
        return math(name, argCount, jvmName, usesDouble, false, false);
    }

    // inputToRadians: Whether to convert the input to radians first (the function accepts radians, but molang spec uses degrees)
    // outputToDegrees: Whether to convert the output to degrees (the function returns radians, but molang spec uses degrees)
    private static FloatFunction math(String name, int argCount, String jvmName, boolean usesDouble, boolean inputToRadians, boolean outputToDegrees) {
        if (inputToRadians && argCount != 1) throw new IllegalStateException("inputToRadians arg should only be used on 1-arg calls");
        String desc = usesDouble ? "D" : "F";
        String fullDesc =  "(" + desc.repeat(argCount) + ")" + desc;
        return new FloatFunction(name, argCount, v -> {
            if (inputToRadians) {
                if (usesDouble) {
                    v.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "toRadians", "(D)D", false);
                } else {
                    BytecodeUtil.constFloat(v, (float) (Math.PI / 180));
                    v.visitInsn(Opcodes.FMUL);
                }
            }
            v.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", jvmName, fullDesc, false);
            if (outputToDegrees) {
                if (usesDouble) {
                    v.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "toDegrees", "(D)D", false);
                } else {
                    BytecodeUtil.constFloat(v, (float) (180 / Math.PI));
                    v.visitInsn(Opcodes.FMUL);
                }
            }
        }, usesDouble);
    }

    private static FloatFunction binop(String name, int opcode) {
        return new FloatFunction(name, 2, v -> v.visitInsn(opcode), false);
    }

    private static FloatFunction unop(String name, int opcode) {
        return new FloatFunction(name, 1, v -> v.visitInsn(opcode), false);
    }


    @Override
    public void checkArgs(List<MolangExpr> args, String source, int funcNameStart, int funcNameEnd) throws MolangCompileException {
        if (args.size() != argCount)
            throw new MolangCompileException(MolangCompileException.WRONG_ARG_COUNT, name(), String.valueOf(argCount), String.valueOf(args.size()), source, funcNameStart, funcNameEnd);
        int vecSize = 1;
        for (var expr : args) {
            int size = expr.returnCount();
            if (size != 1) {
                if (vecSize == 1) vecSize = size;
                else if (size != vecSize) {
                    throw new MolangCompileException(MolangCompileException.VECTOR_ARGS_SAME_SIZE, name(), vecSize, size, source, funcNameStart, funcNameEnd);
                }
            }
        }
    }

    @Override
    public int returnCount(List<MolangExpr> args) {
        for (var expr : args) {
            int size = expr.returnCount();
            if (size != 1) return size;
        }
        return 1;
    }

    @Override
    public void compile(MethodVisitor visitor, List<MolangExpr> args, int outputArrayIndex, CompilationContext context) {
        context.push();
        // Check if there are any vector args.
        if (args.stream().noneMatch(MolangExpr::isVector)) {
            // There are no vector args:
            // Compile each arg, pushing it to the stack:
            for (MolangExpr arg : args) {
                arg.compile(visitor, -1, context);
                // If we need to use doubles, convert each to a double:
                if (usesDouble) visitor.visitInsn(Opcodes.F2D);
            }
            // Run the float function, which will pop them from the stack and produce the output float.
            floatFunc.accept(visitor);
            // If we use doubles, convert the result back to float
            if (usesDouble) visitor.visitInsn(Opcodes.D2F);
        } else {
            // TODO DOUBLES HERE!!!
            // There are some vector args. We need to set up a loop.

            // Collect args into local variables or float[] storage
            List<Integer> locations = new ArrayList<>();
            for (MolangExpr arg : args) {
                if (arg instanceof TempVariable tempVar) {
                    // Already stored somewhere, use that location
                    locations.add(tempVar.getRealLocation());
                } else if (arg.isVector()) {
                    // Compile it to scratch space
                    int loc = context.reserveArraySlots(arg.returnCount());
                    locations.add(loc);
                    System.gc();
                    arg.compile(visitor, loc, context); // Compile to loc
                } else {
                    // Compile it to push to stack, then store in a local variable
                    int loc = context.reserveLocals(1);
                    locations.add(loc);
                    arg.compile(visitor, -1, context);
                    visitor.visitVarInsn(Opcodes.FSTORE, loc);
                }
            }

            int counterLocal = context.reserveLocals(1);
            BytecodeUtil.repeatNTimes(visitor, returnCount(args), counterLocal, v -> {
                // Prepare float[] and output location:
                v.visitVarInsn(Opcodes.ALOAD, 1);
                v.visitVarInsn(Opcodes.ILOAD, counterLocal);
                BytecodeUtil.constInt(v, outputArrayIndex);
                v.visitInsn(Opcodes.IADD);

                // Load all the args:
                for (int j = 0; j < args.size(); j++) {
                    MolangExpr arg = args.get(j);
                    int location = locations.get(j);
                    if (arg.isVector()) {
                        // If it's a vector, load the i'th term from its location:
                        v.visitVarInsn(Opcodes.ALOAD, 1);
                        v.visitVarInsn(Opcodes.ILOAD, counterLocal);
                        BytecodeUtil.constInt(v, location);
                        v.visitInsn(Opcodes.IADD);
                        v.visitInsn(Opcodes.FALOAD);
                    } else {
                        // If it's a scalar, load the location'th local variable
                        v.visitVarInsn(Opcodes.FLOAD, location);
                    }
                }
                // Invoke the function, pushing result to the stack
                floatFunc.accept(v);
                // Store in the float[] at the previously prepared location
                v.visitInsn(Opcodes.FASTORE);
            });
        }
        context.pop();
    }

}
