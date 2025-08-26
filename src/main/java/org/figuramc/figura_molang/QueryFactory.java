package org.figuramc.figura_molang;

import org.figuramc.figura_molang.ast.Literal;
import org.figuramc.figura_molang.ast.MolangExpr;
import org.figuramc.figura_molang.ast.VectorConstructor;
import org.figuramc.figura_molang.ast.vars.ContextVariable;
import org.figuramc.figura_molang.compile.CompilationContext;
import org.figuramc.figura_molang.compile.MolangCompileException;
import org.figuramc.figura_molang.compile.BytecodeUtil;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.ArrayList;

/**
 * Class for creating custom queries on actors. They only accept scalars.
 */
public class QueryFactory<Actor> {

    /**
     * The method should be a non-static method, on the Actor class, accepting (paramCount) float args and returning a float (if returnCount == 1), or a float[] otherwise.
     * If the actor is not present, or is not an instance of actorClass, the query will return 0 (or a vector of zeros).
     */
    public static <Actor> MolangInstance.Query<Actor, RuntimeException> fromActorMethod(String name, Class<Actor> actorClass, String methodName, int paramCount, int returnCount) {
        return fromActorMethod(name, actorClass, actorClass, false, methodName, paramCount, returnCount);
    }

    /**
     * Similar to fromActorMethod, but instead of invokevirtual on the Actor, it's invokestatic on methodClass.
     * It still checks that the actor is an instance of actorClass before invoking the static method.
     */
    public static <Actor> MolangInstance.Query<Actor, RuntimeException> fromStaticActorMethod(String name, Class<Actor> actorClass, Class<?> methodClass, String methodName, int paramCount, int returnCount) {
        return fromActorMethod(name, actorClass, methodClass, true, methodName, paramCount, returnCount);
    }

    /**
     * From a generic static method, does not use an Actor.
     */
    public static MolangInstance.Query<Object, RuntimeException> fromStaticMethod(String name, Class<?> methodOwnerClass, String methodName, int paramCount, int returnCount) {
        return (parser, args, source, funcNameStart, funcNameEnd) -> {
            // Verify args
            if (args.size() != paramCount) throw new MolangCompileException(MolangCompileException.WRONG_ARG_COUNT, name, String.valueOf(paramCount), String.valueOf(args.size()), source, funcNameStart, funcNameEnd);
            if (args.stream().anyMatch(MolangExpr::isVector)) throw new MolangCompileException(MolangCompileException.SCALAR_ARGS_ONLY, name, source, funcNameStart, funcNameEnd);
            // Return
            return new MolangExpr() {
                @Override
                protected int computeReturnCount() {
                    return returnCount;
                }
                @Override
                public void compile(MethodVisitor visitor, int outputArrayIndex, CompilationContext context) {
                    // Call the method.
                    for (MolangExpr arg : args) arg.compile(visitor, outputArrayIndex, context);
                    String descriptor = "(" + "F".repeat(paramCount) + ")" + (returnCount == 1 ? "F" : "[F");
                    visitor.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(methodOwnerClass), methodName, descriptor, false);
                    // If it returned 1 float, we're done, otherwise copy from float[] into output
                    if (returnCount != 1) {
                        BytecodeUtil.constInt(visitor, 0); // [arr, 0]
                        visitor.visitVarInsn(Opcodes.ALOAD, 1); // [arr, 0, temp]
                        BytecodeUtil.constInt(visitor, outputArrayIndex); // [arr, 0, temp, dst]
                        BytecodeUtil.constInt(visitor, returnCount); // [arr, 0, temp, dst, count]
                        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V", false);
                    }
                }
            };
        };
    }

    /**
     * Create a query q.queryName that just wraps the context variable c.contextVarName.
     * Useful when Molang spec indicates something should be a query (q.anim_time) but you determine it should
     * be a context variable for performance reasons.
     * If the context variable does not exist in this parser, the query will evaluate to 0.
     */
    public static MolangInstance.Query<Object, RuntimeException> fromContextVariable(String queryName, String contextVarName) {
        return (parser, args, source, funcNameStart, funcNameEnd) -> {
            // Verify there's no args
            if (!args.isEmpty()) throw new MolangCompileException(MolangCompileException.WRONG_ARG_COUNT, queryName, String.valueOf(0), String.valueOf(args.size()), source, funcNameStart, funcNameEnd);
            // Fetch the context var from the parser
            int index = parser.contextVariables.indexOf(contextVarName);
            if (index == -1) return new Literal(0f);
            else return new ContextVariable(contextVarName, index);
        };
    }

    public static MolangInstance.Query<Object, RuntimeException> fromConstant(String queryName, String constantName, int constantLen) {
        return (parser, args, source, funcNameStart, funcNameEnd) -> {
            // Verify there's no args
            if (!args.isEmpty()) throw new MolangCompileException(MolangCompileException.WRONG_ARG_COUNT, queryName, String.valueOf(0), String.valueOf(args.size()), source, funcNameStart, funcNameEnd);
            // Fetch the constant from the parser
            float[] values = parser.constants.get(constantName);
            if (values == null || values.length != constantLen) values = new float[constantLen];
            if (values.length == 1) {
                return new Literal(values[0]);
            } else {
                ArrayList<Literal> literals = new ArrayList<>(values.length);
                for (int i = 0; i < values.length; i++)
                    literals.add(new Literal(values[i]));
                return new VectorConstructor(literals);
            }
        };
    }


    private static <Actor> MolangInstance.Query<Actor, RuntimeException> fromActorMethod(String name, Class<Actor> actorClass, Class<?> methodOwnerClass, boolean isStatic, String methodName, int paramCount, int returnCount) {
        return (parser, args, source, funcNameStart, funcNameEnd) -> {
            // Verify args
            if (args.size() != paramCount) throw new MolangCompileException(MolangCompileException.WRONG_ARG_COUNT, name, String.valueOf(paramCount), String.valueOf(args.size()), source, funcNameStart, funcNameEnd);
            if (args.stream().anyMatch(MolangExpr::isVector)) throw new MolangCompileException(MolangCompileException.SCALAR_ARGS_ONLY, name, source, funcNameStart, funcNameEnd);
            // Return
            return new MolangExpr() {
                @Override
                protected int computeReturnCount() {
                    return returnCount;
                }
                @Override
                public void compile(MethodVisitor visitor, int outputArrayIndex, CompilationContext context) {
                    // Test if actor instanceof actorClass
                    visitor.visitVarInsn(Opcodes.ALOAD, 0);
                    visitor.visitFieldInsn(Opcodes.GETFIELD, Type.getInternalName(CompiledMolang.class), "instance", Type.getDescriptor(MolangInstance.class));
                    visitor.visitFieldInsn(Opcodes.GETFIELD, Type.getInternalName(MolangInstance.class), "actor", Type.getDescriptor(Object.class));
                    visitor.visitInsn(Opcodes.DUP);
                    visitor.visitTypeInsn(Opcodes.INSTANCEOF, Type.getInternalName(actorClass));
                    BytecodeUtil.ifElse(visitor, Opcodes.IFEQ, v -> {
                        // If it's an instance, call the method
                        visitor.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(actorClass));
                        for (MolangExpr arg : args) arg.compile(v, outputArrayIndex, context);
                        if (isStatic) {
                            String descriptor = "(" + Type.getDescriptor(actorClass) + "F".repeat(paramCount) + ")" + (returnCount == 1 ? "F" : "[F");
                            v.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(methodOwnerClass), methodName, descriptor, false);
                        } else {
                            String descriptor = "(" + "F".repeat(paramCount) + ")" + (returnCount == 1 ? "F" : "[F");
                            v.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(actorClass), methodName, descriptor, false);
                        }
                        // If it returned 1 float, we're done, otherwise copy from float[] into output
                        if (returnCount != 1) {
                            BytecodeUtil.constInt(v, 0); // [arr, 0]
                            v.visitVarInsn(Opcodes.ALOAD, 1); // [arr, 0, temp]
                            BytecodeUtil.constInt(v, outputArrayIndex); // [arr, 0, temp, dst]
                            BytecodeUtil.constInt(v, returnCount); // [arr, 0, temp, dst, count]
                            v.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V", false);
                        }
                    }, v -> {
                        // Pop the extra reference
                        v.visitInsn(Opcodes.POP);
                        // Either push 0, or fill the result slice with 0.
                        if (returnCount == 1) {
                            BytecodeUtil.constFloat(v, 0);
                        } else {
                            v.visitVarInsn(Opcodes.ALOAD, 1);
                            BytecodeUtil.constInt(v, outputArrayIndex);
                            BytecodeUtil.constInt(v, outputArrayIndex + returnCount);
                            BytecodeUtil.constFloat(v, 0);
                            v.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Arrays", "fill", "([FIIF)V", false);
                        }
                    });
                }
            };
        };
    }


}
