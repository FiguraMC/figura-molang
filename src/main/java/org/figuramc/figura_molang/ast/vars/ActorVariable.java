package org.figuramc.figura_molang.ast.vars;

import org.figuramc.figura_molang.CompiledMolang;
import org.figuramc.figura_molang.MolangInstance;
import org.figuramc.figura_molang.ast.MolangExpr;
import org.figuramc.figura_molang.compile.CompilationContext;
import org.figuramc.figura_molang.compile.BytecodeUtil;
import org.figuramc.memory_tracker.AllocationTracker;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class ActorVariable extends MolangExpr {

    public final String name;
    public final int size;
    public final int location;

    public static final int SIZE_ESTIMATE =
            AllocationTracker.OBJECT_SIZE
            + AllocationTracker.REFERENCE_SIZE
            + AllocationTracker.INT_SIZE * 2;

    public ActorVariable(String name, int size, int location) {
        this.name = name;
        this.size = size;
        this.location = location;
    }

    @Override
    protected int computeReturnCount() {
        return size;
    }

    @Override
    public void compile(MethodVisitor visitor, int outputArrayIndex, CompilationContext context) {
        // Fetch array
        visitor.visitVarInsn(Opcodes.ALOAD, 0);
        visitor.visitFieldInsn(Opcodes.GETFIELD, Type.getInternalName(CompiledMolang.class), "instance", Type.getDescriptor(MolangInstance.class));
        visitor.visitFieldInsn(Opcodes.GETFIELD, Type.getInternalName(MolangInstance.class), "actorVariables", "[F");
        if (isVector()) {
            // Copy from array into outputArrayIndex
            BytecodeUtil.constInt(visitor, location); // [vars, src]
            visitor.visitVarInsn(Opcodes.ALOAD, context.arrayVariableIndex); // [vars, src, out]
            BytecodeUtil.constInt(visitor, outputArrayIndex); // [vars, src, out, dst]
            BytecodeUtil.constInt(visitor, size); // [vars, src, out, dst, len]
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V", false); // []
        } else {
            // Index array, put result on stack
            BytecodeUtil.constInt(visitor, location);
            visitor.visitInsn(Opcodes.FALOAD);
        }
    }
}
