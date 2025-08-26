package org.figuramc.figura_molang.func;

import org.figuramc.figura_molang.ast.MolangExpr;
import org.figuramc.figura_molang.compile.CompilationContext;
import org.figuramc.figura_molang.compile.MolangCompileException;
import org.objectweb.asm.MethodVisitor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface MolangFunction {

    // Name of this function for error reporting
    String name();

    // Check that the given args are valid; if not, then error at the given spot
    void checkArgs(List<MolangExpr> args, String source, int funcNameStart, int funcNameEnd) throws MolangCompileException;

    // How many values to return, given these args
    int returnCount(List<MolangExpr> args);

    // Compile given these args.
    // If this has multiple outputs, store them at the given arrayIndex in the float[].
    // If this has one output, push it on the stack.
    void compile(MethodVisitor visitor, List<MolangExpr> args, int outputArrayIndex, CompilationContext context);

    // All the math functions! :D
    Map<String, MolangFunction> ALL_MATH_FUNCTIONS = new HashMap<>() {{
        // Molang
        put("abs", FloatFunction.ABS);
        put("acos", FloatFunction.ACOS);
        put("asin", FloatFunction.ASIN);
        put("atan", FloatFunction.ATAN);
        put("atan2", FloatFunction.ATAN2);
        put("ceil", FloatFunction.CEIL);
        put("clamp", FloatFunction.CLAMP);
        put("cos", FloatFunction.COS);
        put("exp", FloatFunction.EXP);
        put("floor", FloatFunction.FLOOR);
        put("lerp", FloatFunction.LERP);
        put("ln", FloatFunction.LN);
        put("max", FloatFunction.MAX);
        put("min", FloatFunction.MIN);
        put("mod", FloatFunction.MOD);
        put("pow", FloatFunction.POW);
        put("round", FloatFunction.ROUND);
        put("sin", FloatFunction.SIN);
        put("sqrt", FloatFunction.SQRT);
        put("trunc", FloatFunction.TRUNC);
        // Custom
        put("eq", FloatFunction.EQ);
        put("ne", FloatFunction.NE);
        put("lt", FloatFunction.LT);
        put("le", FloatFunction.LE);
        put("gt", FloatFunction.GT);
        put("ge", FloatFunction.GE);
        // Vector reduction
        put("sum", VecReduceFunction.SUM);
        put("product", VecReduceFunction.PRODUCT);
        put("min_elem", VecReduceFunction.MIN_ELEM);
        put("max_elem", VecReduceFunction.MAX_ELEM);
        // Vector pair reduction
        put("dot", VecReduceFunctionBinary.DOT_PRODUCT);
        put("dist", VecReduceFunctionBinary.DISTANCE);
    }};


}
