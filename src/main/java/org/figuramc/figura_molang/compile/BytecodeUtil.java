package org.figuramc.figura_molang.compile;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.function.Consumer;

public class BytecodeUtil {

    // The condition variable(s) should be on top of the stack.
    // Pass the *opposite* of the jump opcode which represents the condition.
    // For example to emulate "if (a == b) { ifTrue } else { ifFalse }", pass Opcodes.IF_ICMPNE.
    public static void ifElse(MethodVisitor methodVisitor, int jumpOpcodeOpposite, Consumer<MethodVisitor> ifTrue, Consumer<MethodVisitor> ifFalse) {
        Label elseBlock = new Label();
        Label endLabel = new Label();
        methodVisitor.visitJumpInsn(jumpOpcodeOpposite, elseBlock);
        ifTrue.accept(methodVisitor);
        methodVisitor.visitJumpInsn(Opcodes.GOTO, endLabel);
        methodVisitor.visitLabel(elseBlock);
        ifFalse.accept(methodVisitor);
        methodVisitor.visitLabel(endLabel);
    }

    public static void whileLoop(MethodVisitor methodVisitor, Consumer<MethodVisitor> predicate, int jumpOpcodeOpposite, Consumer<MethodVisitor> body) {
        Label predicateLabel = new Label();
        Label endLabel = new Label();
        methodVisitor.visitLabel(predicateLabel);
        predicate.accept(methodVisitor);
        methodVisitor.visitJumpInsn(jumpOpcodeOpposite, endLabel);
        body.accept(methodVisitor);
        methodVisitor.visitJumpInsn(Opcodes.GOTO, predicateLabel);
        methodVisitor.visitLabel(endLabel);
    }

    // Reserves a local variable slot in counterLocalSlot!
    // for (int i = 0; i < repeatCount; i++) {
    //   body
    // }
    public static void repeatNTimes(MethodVisitor methodVisitor, int repeatCount, int counterLocalSlot, Consumer<MethodVisitor> body) {
        if (repeatCount == 0) return;
        if (repeatCount == 1) {
            BytecodeUtil.constInt(methodVisitor, 0);
            methodVisitor.visitVarInsn(Opcodes.ISTORE, counterLocalSlot);
            body.accept(methodVisitor);
            return;
        }

        BytecodeUtil.constInt(methodVisitor, 0);
        methodVisitor.visitVarInsn(Opcodes.ISTORE, counterLocalSlot);
        Label startLabel = new Label();
        Label endLabel = new Label();
        methodVisitor.visitLabel(startLabel);
        methodVisitor.visitVarInsn(Opcodes.ILOAD, counterLocalSlot);
        BytecodeUtil.constInt(methodVisitor, repeatCount);
        methodVisitor.visitJumpInsn(Opcodes.IF_ICMPGE, endLabel);
        body.accept(methodVisitor);
        methodVisitor.visitIincInsn(counterLocalSlot, 1);
        methodVisitor.visitJumpInsn(Opcodes.GOTO, startLabel);
        methodVisitor.visitLabel(endLabel);
    }

    public static void constInt(MethodVisitor methodVisitor, int value) {
        switch (value) {
            case -1 -> methodVisitor.visitInsn(Opcodes.ICONST_M1);
            case 0 -> methodVisitor.visitInsn(Opcodes.ICONST_0);
            case 1 -> methodVisitor.visitInsn(Opcodes.ICONST_1);
            case 2 -> methodVisitor.visitInsn(Opcodes.ICONST_2);
            case 3 -> methodVisitor.visitInsn(Opcodes.ICONST_3);
            case 4 -> methodVisitor.visitInsn(Opcodes.ICONST_4);
            case 5 -> methodVisitor.visitInsn(Opcodes.ICONST_5);
            default -> {
                if ((byte) value == value) methodVisitor.visitIntInsn(Opcodes.BIPUSH, value);
                else if ((short) value == value) methodVisitor.visitIntInsn(Opcodes.SIPUSH, value);
                else methodVisitor.visitLdcInsn(value);
            }
        }
    }

    public static void constFloat(MethodVisitor methodVisitor, float value) {
        if (value == 0f) methodVisitor.visitInsn(Opcodes.FCONST_0);
        else if (value == 1f) methodVisitor.visitInsn(Opcodes.FCONST_1);
        else if (value == 2f) methodVisitor.visitInsn(Opcodes.FCONST_2);
        else methodVisitor.visitLdcInsn(value);
    }

    // For testing if two are equal, pass IFNE, etc.
    public static void compareFloats(MethodVisitor methodVisitor, int jumpOpcodeOpposite) {
        int cmpOpcode = switch (jumpOpcodeOpposite) {
            case Opcodes.IFNE, Opcodes.IFEQ, Opcodes.IFLE, Opcodes.IFLT -> Opcodes.FCMPL;
            case Opcodes.IFGE, Opcodes.IFGT -> Opcodes.FCMPG;
            default -> throw new IllegalArgumentException();
        };
        Label fail = new Label();
        Label end = new Label();
        methodVisitor.visitInsn(cmpOpcode);
        methodVisitor.visitJumpInsn(jumpOpcodeOpposite, fail);
        methodVisitor.visitInsn(Opcodes.FCONST_1);
        methodVisitor.visitJumpInsn(Opcodes.GOTO, end);
        methodVisitor.visitLabel(fail);
        methodVisitor.visitInsn(Opcodes.FCONST_0);
        methodVisitor.visitLabel(end);
    }

}
