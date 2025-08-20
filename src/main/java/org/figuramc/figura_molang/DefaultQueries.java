package org.figuramc.figura_molang;

import org.figuramc.figura_molang.ast.FunctionCall;
import org.figuramc.figura_molang.ast.Literal;
import org.figuramc.figura_molang.ast.MolangExpr;
import org.figuramc.figura_molang.ast.control_flow.Compound;
import org.figuramc.figura_molang.ast.control_flow.LogicalAnd;
import org.figuramc.figura_molang.ast.control_flow.LogicalOr;
import org.figuramc.figura_molang.ast.control_flow.Return;
import org.figuramc.figura_molang.ast.vars.TempVariable;
import org.figuramc.figura_molang.ast.vars.TempVariableAssign;
import org.figuramc.figura_molang.compile.MolangCompileException;
import org.figuramc.figura_molang.func.ComparisonOperator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class for holding all the queries defined by Figura
 */
public class DefaultQueries {

    // Queries for all Actor types
    private static final Map<String, MolangInstance.Query<? super Object, RuntimeException>> DEFAULT_QUERIES = new HashMap<>();

    // Java's type system around exceptions is cringe, so we can't use those queries normally.
    // We have to perform an unchecked cast to get the queries with a different error type.
    @SuppressWarnings("unchecked")
    public static <Err extends Throwable> Map<String, MolangInstance.Query<? super Object, Err>> getDefaultQueries() {
        return (Map<String, MolangInstance.Query<? super Object, Err>>) (Object) DEFAULT_QUERIES;
    }

    static {
        // Default molang queries
        DEFAULT_QUERIES.put("all", (parser, args, source, funcNameStart, funcNameEnd) -> {
            // query.all(a, b, c, d) desugars to { temp = a; return temp == b && temp == c && temp == d }
            if (args.size() < 3)
                throw new MolangCompileException(MolangCompileException.WRONG_ARG_COUNT, "query.all", "at least 3", String.valueOf(args.size()), source, funcNameStart, funcNameEnd);
            List<Integer> distinctSizes = args.stream().map(MolangExpr::returnCount).filter(x -> x != 1).distinct().toList();
            if (distinctSizes.size() >= 2)
                throw new MolangCompileException(MolangCompileException.VECTOR_ARGS_SAME_SIZE, "query.all", distinctSizes.get(0), distinctSizes.get(1), source, funcNameStart, funcNameEnd);
            Compound res = parser.pushScope();
            TempVariable temp = parser.declareTempVar("$temp", args.getFirst().returnCount(), -1, -1);
            res.exprs.add(new TempVariableAssign(temp, args.getFirst()));
            MolangExpr andChain = new FunctionCall(ComparisonOperator.EQ_OP, List.of(temp, args.getLast())); // temp == d
            for (int i = args.size() - 2; i > 0; i--) {
                andChain = new LogicalAnd(new FunctionCall(ComparisonOperator.EQ_OP, List.of(temp, args.get(i))), andChain);
            }
            res.exprs.add(new Return(andChain));
            parser.popScope();
            return res;
        });
        DEFAULT_QUERIES.put("any", (parser, args, source, funcNameStart, funcNameEnd) -> {
            // query.any(a, b, c, d) desugars to { temp = a; return temp == b || temp == c || temp == d }
            if (args.size() < 3)
                throw new MolangCompileException(MolangCompileException.WRONG_ARG_COUNT, "query.any", "at least 3", String.valueOf(args.size()), source, funcNameStart, funcNameEnd);
            List<Integer> distinctSizes = args.stream().map(MolangExpr::returnCount).filter(x -> x != 1).distinct().toList();
            if (distinctSizes.size() >= 2)
                throw new MolangCompileException(MolangCompileException.VECTOR_ARGS_SAME_SIZE, "query.any", distinctSizes.get(0), distinctSizes.get(1), source, funcNameStart, funcNameEnd);
            Compound res = parser.pushScope();
            TempVariable temp = parser.declareTempVar("$temp", args.getFirst().returnCount(), -1, -1);
            res.exprs.add(new TempVariableAssign(temp, args.getFirst()));
            MolangExpr orChain = new FunctionCall(ComparisonOperator.EQ_OP, List.of(temp, args.getLast())); // temp == d
            for (int i = args.size() - 2; i > 0; i--) {
                orChain = new LogicalOr(new FunctionCall(ComparisonOperator.EQ_OP, List.of(temp, args.get(i))), orChain);
            }
            res.exprs.add(new Return(orChain));
            parser.popScope();
            return res;
        });
        DEFAULT_QUERIES.put("in_range", (parser, args, source, funcNameStart, funcNameEnd) -> {
            // query.in_range(a, b, c) desugars to { temp = a; return a >= b && a <= c }
            if (args.size() != 3)
                throw new MolangCompileException(MolangCompileException.WRONG_ARG_COUNT, "query.in_range", String.valueOf(3), String.valueOf(args.size()), source, funcNameStart, funcNameEnd);
            List<Integer> distinctSizes = args.stream().map(MolangExpr::returnCount).filter(x -> x != 1).distinct().toList();
            if (distinctSizes.size() >= 2)
                throw new MolangCompileException(MolangCompileException.VECTOR_ARGS_SAME_SIZE, "query.in_range", distinctSizes.get(0), distinctSizes.get(1), source, funcNameStart, funcNameEnd);
            Compound res = parser.pushScope();
            TempVariable temp = parser.declareTempVar("$temp", args.getFirst().returnCount(), -1, -1);
            res.exprs.add(new TempVariableAssign(temp, args.getFirst()));
            res.exprs.add(new Return(
                    new LogicalAnd(
                            new FunctionCall(ComparisonOperator.GE_OP, List.of(temp, args.get(1))),
                            new FunctionCall(ComparisonOperator.LE_OP, List.of(temp, args.get(2)))
                    )
            ));
            parser.popScope();
            return res;
        });
        DEFAULT_QUERIES.put("approx_eq", DEFAULT_QUERIES.get("all")); // Epsilon is implementation defined, so we'll say it is 0 :D (todo consider changing this)
        DEFAULT_QUERIES.put("count", (__, args, ___, ____, _____) -> new Literal(args.stream().mapToInt(MolangExpr::returnCount).sum())); // Unpacks any vector args
        
    }



}
