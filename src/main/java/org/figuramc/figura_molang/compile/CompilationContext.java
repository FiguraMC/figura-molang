package org.figuramc.figura_molang.compile;

import org.objectweb.asm.Label;

import java.util.Stack;

public class CompilationContext {

    private final Stack<Integer> nextLocal = new Stack<>();
    private final Stack<Integer> nextArraySlot = new Stack<>();

    private final Stack<Label> returnLabel = new Stack<>();
    private final Stack<Integer> returnArraySlot = new Stack<>();

    private int maxLocals, maxArraySlots;

    public CompilationContext(int nextLocal, int nextArraySlot) {
        this.nextLocal.push(nextLocal);
        this.nextArraySlot.push(nextArraySlot);
        this.returnLabel.push(null);
        this.returnArraySlot.push(null);
        maxLocals = nextLocal;
        maxArraySlots = Math.max(nextArraySlot, 1);
    }

    public void push() {
        push(returnLabel.peek(), returnArraySlot.peek());
    }

    public void push(Label returnLabel, Integer returnArraySlot) {
        this.nextLocal.push(nextLocal.peek());
        this.nextArraySlot.push(nextArraySlot.peek());
        this.returnLabel.push(returnLabel);
        this.returnArraySlot.push(returnArraySlot);
    }

    public void pop() {
        this.nextLocal.pop();
        this.nextArraySlot.pop();
        this.returnLabel.pop();
        this.returnArraySlot.pop();
    }

    public int reserveLocals(int count) {
        int i = nextLocal.pop();
        nextLocal.push(i + count);
        maxLocals = Math.max(maxLocals, i + count);
        return i;
    }

    public int reserveArraySlots(int count) {
        int i = nextArraySlot.pop();
        nextArraySlot.push(i + count);
        maxArraySlots = Math.max(maxArraySlots, i + count);
        return i;
    }

    public Label getReturnLabel() {
        return returnLabel.peek();
    }

    public int getReturnArraySlot() {
        return returnArraySlot.peek();
    }

    public int getMaxArraySlots() {
        return maxArraySlots;
    }

}
