package org.figuramc.figura_molang.ast.vars;

import org.figuramc.figura_molang.CompiledMolang;
import org.figuramc.figura_molang.MolangInstance;
import org.figuramc.figura_molang.ast.MolangExpr;
import org.figuramc.figura_molang.compile.jvm.JvmCompilationContext;
import org.figuramc.figura_molang.compile.jvm.BytecodeUtil;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class ActorVariableAssign extends MolangExpr {

    private final ActorVariable variable;
    private final MolangExpr rhs;

    public ActorVariableAssign(ActorVariable variable, MolangExpr rhs) {
        this.variable = variable;
        this.rhs = rhs;
        // Assert that the expr and the variable are the same size
        if (variable.size != rhs.returnCount())
            throw new IllegalStateException("Actor variable \"" + variable.name + "\" is size " + variable.size + ", but you try to assign " + rhs.returnCount() + " elements to it. This should have already been checked!");
    }

    @Override
    protected int computeReturnCount() {
        return 1; // Assignment always evaluates to the scalar 0
    }

    @Override
    public void compileToJvmBytecode(MethodVisitor visitor, int outputArrayIndex, JvmCompilationContext context) {
        if (variable.isVector()) {
            // Reserve space and compile rhs to it
            context.push();
            int tempArraySpace = context.reserveArraySlots(variable.size);
            rhs.compileToJvmBytecode(visitor, tempArraySpace, context);
            // Copy from temp space into variable array
            visitor.visitVarInsn(Opcodes.ALOAD, context.arrayVariableIndex); // [temp]
            BytecodeUtil.constInt(visitor, tempArraySpace); // [temp, src]
            visitor.visitVarInsn(Opcodes.ALOAD, 0);
            visitor.visitFieldInsn(Opcodes.GETFIELD, Type.getInternalName(CompiledMolang.class), "instance", Type.getDescriptor(MolangInstance.class));
            visitor.visitFieldInsn(Opcodes.GETFIELD, Type.getInternalName(MolangInstance.class), "actorVariables", "[F"); // [temp, src, vars]
            BytecodeUtil.constInt(visitor, variable.location); // [temp, src, vars, dst]
            BytecodeUtil.constInt(visitor, variable.size); // [vars, src, out, dst, len]
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V", false); // []
            context.pop();
        } else {
            // Fetch array
            visitor.visitVarInsn(Opcodes.ALOAD, 0);
            visitor.visitFieldInsn(Opcodes.GETFIELD, Type.getInternalName(CompiledMolang.class), "instance", Type.getDescriptor(MolangInstance.class));
            visitor.visitFieldInsn(Opcodes.GETFIELD, Type.getInternalName(MolangInstance.class), "actorVariables", "[F"); // [vars]
            // Push location
            BytecodeUtil.constInt(visitor, variable.location); // [vars, loc]
            // Push rhs to stack
            rhs.compileToJvmBytecode(visitor, outputArrayIndex, context); // [vars, loc, rhs]
            // Store
            visitor.visitInsn(Opcodes.FASTORE); // []
        }
        BytecodeUtil.constFloat(visitor, 0f); // Push 0 to stack, assignment result
    }
}
