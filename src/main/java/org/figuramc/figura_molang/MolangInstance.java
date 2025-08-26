package org.figuramc.figura_molang;

import org.figuramc.figura_molang.ast.MolangExpr;
import org.figuramc.figura_molang.ast.vars.ActorVariable;
import org.figuramc.figura_molang.compile.CompilationContext;
import org.figuramc.figura_molang.compile.MolangCompileException;
import org.figuramc.figura_molang.compile.MolangParser;
import org.figuramc.figura_molang.func.*;
import org.figuramc.figura_molang.compile.BytecodeUtil;
import org.figuramc.memory_tracker.AllocationTracker;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.*;
import org.objectweb.asm.util.CheckClassAdapter;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Each MolangInstance has its own "v.name" namespace, as well as its own set of supported queries/math functions/etc.
 *
 * Note: Multithreaded access is NOT supported, but we must support re-entrant code.
 * Anything may be called in here *while an expression is running!*.
 */
public class MolangInstance<Actor, OOMErr extends Throwable> {

    public Actor actor;

    // Shared across expressions parsed using this instance, so keep a name -> variable location map.
    // Variables are bound at parse time, so there's no string lookup at runtime.
    public float[] actorVariables = new float[0];
    private float[] tempStack = new float[0]; // Float[] for temporary stack space
    private int nextActorVariable = 0;
    private final Map<String, ActorVariable> actorVariablesByName = new HashMap<>();

    // Functions available when compiling
    private final Map<String, ? extends Query<? super Actor, OOMErr>> queries;

    // If this is true, this is a re-entrant invocation.
    // We may need to do some jank in this case,
    // like allocating a new float[] to act as the tempStack, instead of reusing the field.
    // Re-entrant calls shouldn't happen often.
    // 0 means no calls are happening. 1 means 1 call is happening. 2 means multiple calls. Don't go above 2 to avoid overflow.
    public byte reEntrantFlag;

    // Alloc state
    private final @Nullable AllocationTracker<OOMErr> allocationTracker;
    private final AllocationTracker.State<OOMErr> allocState;

    private static final int SIZE_ESTIMATE =
            AllocationTracker.OBJECT_SIZE
            + AllocationTracker.REFERENCE_SIZE * 8
            + AllocationTracker.INT_SIZE
            + AllocationTracker.BOOLEAN_SIZE;

    // Create a new instance
    public MolangInstance(@Nullable Actor initialActor, @Nullable AllocationTracker<OOMErr> allocationTracker, Map<String, ? extends Query<? super Actor, OOMErr>> queries) throws OOMErr {
        this.actor = initialActor;
        this.queries = queries;
        // Track it
        this.allocationTracker = allocationTracker;
        if (allocationTracker != null) {
            int size = SIZE_ESTIMATE;
            // Estimate size of hashmap entries. default queries are all interned strings/constant objects.
            size += queries.size() * AllocationTracker.REFERENCE_SIZE * 4;
            allocState = allocationTracker.track(this, size);
        } else allocState = null;
    }

    // Check if an actor variable exists
    public @Nullable ActorVariable getActorVariable(String name) {
        return actorVariablesByName.get(name);
    }

    // Used at compile/parse time
    public ActorVariable getOrCreateActorVariable(String variableName, int size) throws OOMErr, MolangCompileException {
        // Debug assertion to ensure name and size match
        assert size > 0;
        assert !variableName.contains("$") && size == 1 || Integer.parseInt(variableName.substring(0, variableName.indexOf('$'))) == size;

        ActorVariable existing = actorVariablesByName.get(variableName);
        if (existing != null) return existing;
        ActorVariable res = new ActorVariable(variableName, size, nextActorVariable);
        actorVariablesByName.put(variableName, res);
        nextActorVariable += size;
        if (nextActorVariable >= actorVariables.length) {
            // Track array grow
            if (allocState != null) allocState.changeSize((nextActorVariable * 2 - actorVariables.length) * AllocationTracker.FLOAT_SIZE);
            actorVariables = Arrays.copyOf(actorVariables, nextActorVariable * 2);
        }

        if (allocationTracker != null) {
            allocState.changeSize(AllocationTracker.REFERENCE_SIZE * 4); // Estimate for change in HashMap internal size?
            allocationTracker.track(res, ActorVariable.SIZE_ESTIMATE);
            allocationTracker.track(variableName);
        }

        return res;
    }

    // If re-entrant (rare, hopefully...), we can't reuse the same array, so make a new one.
    public float[] getTempStack(int requiredSize) throws OOMErr {
        if (reEntrantFlag == 2) {
            float[] arr = new float[requiredSize];
            if (allocationTracker != null) allocationTracker.track(arr);
            return arr;
        } else {
            return tempStack;
        }
    }

    // Get query
    public @Nullable Query<? super Actor, OOMErr> getQuery(String name) { return queries.get(name); }

    // A query accepts some args as input, and outputs a molang expression.
    // Many queries (especially the default queries) can work like macro expansions; for this reason they accept the Parser, so they can declare scopes and vars.
    // Queries accept source, start, and end positions so the can throw MolangCompileException.
    @FunctionalInterface
    public interface Query<Actor, OOMErr extends Throwable> { MolangExpr bind(MolangParser<OOMErr> parser, List<MolangExpr> args, String source, int funcNameStart, int funcNameEnd) throws OOMErr, MolangCompileException; }

    private final CustomClassLoader loader = new CustomClassLoader(this.getClass().getClassLoader());

    // Parse the source and compile into java bytecode, creating a CompiledMolang
    public CompiledMolang<Actor> compile(String source, List<String> contextVariables, Map<String, float[]> constants) throws OOMErr, MolangCompileException {
        int argCount = contextVariables.size();
        if (argCount > 8) throw new IllegalArgumentException("Must have at most 8 context variables");

        // Parse:
        MolangParser<OOMErr> parser = new MolangParser<>(source, this, contextVariables, constants);
        MolangExpr expr = parser.parseAll();
        int arrayVariableIndex = argCount + 1;
        int firstUnusedLocal = arrayVariableIndex + 1 + parser.getMaxLocalVariables();

        try {
            // Compile to bytecode:
            String name = loader.fetchUniqueName();

            ClassVisitor classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            classWriter = new TraceClassVisitor(new CheckClassAdapter(classWriter), new PrintWriter(System.out));
            classWriter.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name, null, Type.getInternalName(CompiledMolang.class), null);

            // Constructor
            MethodVisitor constructor = classWriter.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(" + Type.getDescriptor(MolangInstance.class) + "II)V", null, null);
            constructor.visitCode();
            constructor.visitVarInsn(Opcodes.ALOAD, 0);
            constructor.visitVarInsn(Opcodes.ALOAD, 1);
            constructor.visitVarInsn(Opcodes.ILOAD, 2);
            constructor.visitVarInsn(Opcodes.ILOAD, 3);
            constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, Type.getInternalName(CompiledMolang.class), "<init>", "(" + Type.getDescriptor(MolangInstance.class) + "II)V", false);
            constructor.visitInsn(Opcodes.RETURN);
            constructor.visitMaxs(0, 0);
            constructor.visitEnd();

            // evaluateImpl method, with the appropriate arg count
            String evaluateImplDesc = "(" + "F".repeat(argCount) + ")[F";
            MethodVisitor evaluateMethod = classWriter.visitMethod(Opcodes.ACC_PROTECTED, "evaluateImpl", evaluateImplDesc, null, null);
            evaluateMethod.visitCode();

            // Cursed garbage required for re-entrancy support, plus our compiler is bad so it doesn't know how much space
            // is needed until after compiling it
            Label runCode = new Label();
            Label setupFloatArrayLocal = new Label();
            Label end = new Label();

            // Jump to set up the float array local
            evaluateMethod.visitJumpInsn(Opcodes.GOTO, setupFloatArrayLocal);
            evaluateMethod.visitLabel(runCode);
            // Run code, then jump to end
            if (expr.returnCount() == 1) {
                evaluateMethod.visitVarInsn(Opcodes.ALOAD, arrayVariableIndex);
                BytecodeUtil.constInt(evaluateMethod, 0);
            }
            CompilationContext ctx = new CompilationContext(arrayVariableIndex, firstUnusedLocal, 0);
            expr.compile(evaluateMethod, 0, ctx);
            if (expr.returnCount() == 1) {
                evaluateMethod.visitInsn(Opcodes.FASTORE);
            }
            evaluateMethod.visitJumpInsn(Opcodes.GOTO, end);
            // Set up float array local at index 1
            evaluateMethod.visitLabel(setupFloatArrayLocal);
            evaluateMethod.visitVarInsn(Opcodes.ALOAD, 0);
            evaluateMethod.visitFieldInsn(Opcodes.GETFIELD, Type.getInternalName(CompiledMolang.class), "instance", Type.getDescriptor(MolangInstance.class));
            BytecodeUtil.constInt(evaluateMethod, ctx.getMaxArraySlots());
            evaluateMethod.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(MolangInstance.class), "getTempStack", "(I)[F", false);
            evaluateMethod.visitVarInsn(Opcodes.ASTORE, arrayVariableIndex);
            // Run the code now
            evaluateMethod.visitJumpInsn(Opcodes.GOTO, runCode);
            // End
            evaluateMethod.visitLabel(end);

            // Return the float array
            evaluateMethod.visitVarInsn(Opcodes.ALOAD, arrayVariableIndex);
            evaluateMethod.visitInsn(Opcodes.ARETURN);
            evaluateMethod.visitMaxs(0, 0);
            evaluateMethod.visitEnd();

            // Resize tempStack array if needed
            if (tempStack.length < ctx.getMaxArraySlots()) {
                tempStack = Arrays.copyOf(tempStack, ctx.getMaxArraySlots());
                if (allocationTracker != null)
                    allocationTracker.track(tempStack);
            }

            classWriter.visitEnd();

            byte[] classBytes = ((ClassWriter) classWriter.getDelegate().getDelegate()).toByteArray();

            // Pay for those bytes, plus even more because of all the other mem taken up by loaded classes in JIT and whatever (just an estimate here)
            if (allocState != null) allocState.changeSize(classBytes.length * 4);

            Class<? extends CompiledMolang> clazz = loader.create(name, classBytes);
            return clazz.getDeclaredConstructor(MolangInstance.class, int.class, int.class).newInstance(this, argCount, expr.returnCount());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compile molang", ex);
        }
    }

    private static class CustomClassLoader extends ClassLoader {
        public CustomClassLoader(ClassLoader parent) {
            super(parent);
        }
        private int nextId;
        public String fetchUniqueName() {
            return "__CompiledMolang__" + (nextId++);
        }
        @SuppressWarnings("unchecked")
        public Class<? extends CompiledMolang> create(String name, byte[] bytes) {
            return (Class<? extends CompiledMolang>) defineClass(name, bytes, 0, bytes.length);
        }
    }

}
