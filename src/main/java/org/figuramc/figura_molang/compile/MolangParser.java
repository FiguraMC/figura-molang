package org.figuramc.figura_molang.compile;

import org.figuramc.figura_molang.MolangInstance;
import org.figuramc.figura_molang.ast.FunctionCall;
import org.figuramc.figura_molang.ast.Literal;
import org.figuramc.figura_molang.ast.MolangExpr;
import org.figuramc.figura_molang.ast.VectorConstructor;
import org.figuramc.figura_molang.ast.control_flow.*;
import org.figuramc.figura_molang.ast.vars.*;
import org.figuramc.figura_molang.func.ComparisonOperator;
import org.figuramc.figura_molang.func.FloatFunction;
import org.figuramc.figura_molang.func.MolangFunction;

import java.util.*;

public class MolangParser<OOMErr extends Throwable> {

    private final String source;
    private final MolangInstance<?, OOMErr> instance;
    public final List<String> contextVariables;
    public final Map<String, float[]> constants;
    private int current;

    private final Stack<Compound> scopes = new Stack<>();
    private int maxLocalVariables = 0; // Store maximum JVM local variables used, so temporaries can go past it

    // Only a MolangInstance should ever construct one of these.
    // Please don't try to use this class on your own.
    public MolangParser(String source, MolangInstance<?, OOMErr> instance, List<String> contextVariables, Map<String, float[]> constants) {
        this.source = source;
        this.instance = instance;
        this.contextVariables = contextVariables;
        this.constants = constants;
        this.current = 0;
    }
    
    public MolangExpr parseAll() throws OOMErr, MolangCompileException {
        if (current != 0) throw new UnsupportedOperationException("Cannot parse with a Parser multiple times!");
        return parse();
    }

    // Get the maximum local variables used at any point in this expr, so temporaries can go past it
    // Note this doesn't include the built-in local variables like "this"
    public int getMaxLocalVariables() {
        return maxLocalVariables;
    }

    // ---------------------
    // | PARSING OPERATORS |
    // ---------------------

    private MolangExpr parse() throws OOMErr, MolangCompileException {
        return parseTernary();
    }

    private MolangExpr parseTernary() throws OOMErr, MolangCompileException {
        MolangExpr res = parseLogicalOr();
        if (consume('?', true)) {
            if (res.isVector()) throw new MolangCompileException(MolangCompileException.TERNARY_CONDITION_EXPECTS_SCALAR, source, current - 1, current);
            MolangExpr ifTrue = parseLogicalOr();
            if (!consume(':', true)) throw new MolangCompileException(MolangCompileException.EXPECTED_TERNARY_COLON, source, current - 1, current);
            int falseStart = current;
            MolangExpr ifFalse = parseTernary();
            if (ifTrue.returnCount() != ifFalse.returnCount())
                throw new MolangCompileException(MolangCompileException.TERNARY_BRANCHES_MUST_BE_SAME_SIZE, ifTrue.returnCount(), ifFalse.returnCount(), source, falseStart, current);
            return new Ternary(res, ifTrue, ifFalse);
        }
        return res;
    }

    private MolangExpr parseLogicalOr() throws OOMErr, MolangCompileException {
        MolangExpr res = parseLogicalAnd();
        while (consume("||", true)) {
            int start = current - 2; int end = current;
            MolangExpr rhs = parseLogicalAnd();
            if (res.isVector() || rhs.isVector())
                throw new MolangCompileException(MolangCompileException.LOGICAL_OR_EXPECTS_SCALARS, source, start, end);
            res = new LogicalOr(res, rhs);
        }
        return res;
    }

    private MolangExpr parseLogicalAnd() throws OOMErr, MolangCompileException {
        MolangExpr res = parseEquality();
        while (consume("&&", true)) {
            int start = current - 2; int end = current;
            MolangExpr rhs = parseEquality();
            if (res.isVector() || rhs.isVector())
                throw new MolangCompileException(MolangCompileException.LOGICAL_AND_EXPECTS_SCALARS, source, start, end);
            res = new LogicalAnd(res, rhs);
        }
        return res;
    }

    private MolangExpr parseEquality() throws OOMErr, MolangCompileException {
        MolangExpr res = parseComparison();
        while (consume("==", true) || consume("!=", true))
            res = new FunctionCall(switch (last(2)) {
                case "==" -> ComparisonOperator.EQ_OP;
                case "!=" -> ComparisonOperator.NE_OP;
                default -> throw new IllegalStateException();
            }, List.of(res, parseComparison()));
        return res;
    }

    private MolangExpr parseComparison() throws OOMErr, MolangCompileException {
        MolangExpr res = parseSum();
        while (consumeAny("><", true)) {
            if (consume('=', false)) {
                res = new FunctionCall(switch (last(2)) {
                    case "<=" -> ComparisonOperator.LE_OP;
                    case ">=" -> ComparisonOperator.GE_OP;
                    default -> throw new IllegalStateException();
                }, List.of(res, parseSum()));
            } else {
                res = new FunctionCall(switch (last()) {
                    case "<" -> ComparisonOperator.LT_OP;
                    case ">" -> ComparisonOperator.GT_OP;
                    default -> throw new IllegalStateException();
                }, List.of(res, parseSum()));
            }
        }
        return res;
    }

    private MolangExpr parseSum() throws OOMErr, MolangCompileException {
        MolangExpr res = parseProduct();
        while (consumeAny("+-", true))
            res = new FunctionCall(switch (last()) {
                case "+" -> FloatFunction.ADD_OP;
                case "-" -> FloatFunction.SUB_OP;
                default -> throw new IllegalStateException();
            }, List.of(res, parseProduct()));
        return res;
    }

    private MolangExpr parseProduct() throws OOMErr, MolangCompileException {
        MolangExpr res = parseUnary();
        while (consumeAny("*/%", true))
            res = new FunctionCall(switch (last()) {
                case "*" -> FloatFunction.MUL_OP;
                case "/" -> FloatFunction.DIV_OP;
                case "%" -> FloatFunction.MOD_OP;
                default -> throw new IllegalStateException();
            }, List.of(res, parseUnary()));
        return res;
    }

    private MolangExpr parseUnary() throws OOMErr, MolangCompileException {
        if (consumeAny("-!", true))
            return new FunctionCall(switch (last()) {
                case "-" -> FloatFunction.NEG_OP;
                case "!" -> throw new UnsupportedOperationException("TODO");
                default -> throw new IllegalStateException();
            }, List.of(parseUnary()));
        return parseAtom();
    }

    // ---------
    // | ATOMS |
    // ---------

    private MolangExpr parseAtom() throws OOMErr, MolangCompileException {
        if (consumeDigit(true)) return finishNumber();
        if (consume("math.", true)) return finishMath();
        // Test constants
        for (var constant : constants.entrySet()) {
            if (consume(constant.getKey(), true)) {
                return switch (constant.getValue().length) {
                    case 0 -> throw new IllegalStateException("Constants must have at least 1 size");
                    case 1 -> new Literal(constant.getValue()[0]);
                    default -> {
                        List<Literal> list = new ArrayList<>(constant.getValue().length);
                        for (int i = 0; i < constant.getValue().length; i++)
                            list.add(new Literal(constant.getValue()[i]));
                        yield new VectorConstructor(list);
                    }
                };
            }
        }
        if (consume('q', true)) return finishQuery();
        if (consume('c', true)) return finishContextVar();
        if (consume('t', true)) return finishTemp();
        if (consume('v', true)) return finishActorVar();
        if (consume('(', true)) return finishParen();
        if (consume('{', true)) return finishBlock();
        if (consume('[', true)) return finishVectorConstructor();
        if (consume("return ", true)) {
            int pre = current - 7;
            // Ensure we're inside a block before returning
            if (scopes.isEmpty())
                throw new MolangCompileException(MolangCompileException.RETURN_OUTSIDE_BLOCK, source, pre, current - 1);
            // Ensure return size lines up
            MolangExpr e = parse();
            if (e.isVector()) {
                // If it's a vector, ensure it doesn't conflict with existing vectors being returned
                int retCount = e.returnCount();
                int prevRetCount = scopes.peek().getCurrentReturnCount();
                if (prevRetCount == 1) {
                    scopes.peek().setCurrentReturnCount(retCount);
                } else if (prevRetCount != retCount) {
                    throw new MolangCompileException(MolangCompileException.DIFF_RETURN_SIZES, prevRetCount, retCount, source, pre, current);
                }
            }
            return new Return(e);
        }
        throw new MolangCompileException(MolangCompileException.EXPECTED_EXPRESSION, source, current - 1, current);
    }

    // First digit was just consumed
    private MolangExpr finishNumber() throws MolangCompileException {
        int start = current - 1;
        boolean foundDot = false;
        while (true) {
            if (consume('.', false)) {
                if (foundDot) throw new MolangCompileException(MolangCompileException.NUMBER_PARSE, source, start, current);
                foundDot = true;
            }
            if (!consumeDigit(false)) {
                return new Literal(Float.parseFloat(source.substring(start, current)));
            }
        }
    }

    // "math." was already parsed
    private MolangExpr finishMath() throws OOMErr, MolangCompileException {
        int start = current - 5;
        String s = expectIdent();
        int funcNameEnd = current;
        MolangFunction function = MolangFunction.ALL_MATH_FUNCTIONS.get(s);
        if (function == null) throw new MolangCompileException(MolangCompileException.UNKNOWN_MATH, s, source, start, current);
        List<MolangExpr> args = parseParams();
        function.checkArgs(args, source, start, funcNameEnd);
        return new FunctionCall(function, args);
    }

    // "t" was already parsed
    private MolangExpr finishTemp() throws OOMErr, MolangCompileException {
        int start = current - 1;
        // Get variable name
        if (!(consume('.', false) || consume("emp.", false)))
            throw new MolangCompileException(MolangCompileException.EXPECTED_TEMP_VAR, source, start, current);
        String varName = expectIdent();
        // Find existing variable
        Optional<TempVariable> existing = scopes.stream().map(x -> x.tempVars).flatMap(List::stream).filter(it -> it.name.equals(varName)).findAny();
        // Check if this is an assignment
        if (!check("==", true) && consume('=', true)) {
            int equals = current - 1;
            // Parse RHS
            MolangExpr rhs = parse();
            // If the variable already exists, ensure size matches then emit assignment for it
            if (existing.isPresent()) {
                TempVariable existingVar = existing.get();
                if (existingVar.size != rhs.returnCount())
                    throw new MolangCompileException(MolangCompileException.INCOMPATIBLE_VAR_SIZE, "t." + varName, existingVar.size, rhs.returnCount(), source, equals, equals + 1);
                return new TempVariableAssign(existingVar, rhs);
            }
            // Otherwise, declare it
            TempVariable newVariable = declareTempVar(varName, rhs.returnCount(), start, equals);
            scopes.peek().tempVars.add(newVariable);
            // Return assignment
            return new TempVariableAssign(newVariable, rhs);
        } else {
            // If this isn't an assignment, but the var doesn't exist, error
            if (existing.isEmpty())
                throw new MolangCompileException(MolangCompileException.NONEXISTENT_TEMP_VAR, varName, source, start, current);
            // Return variable
            return existing.get();
        }
    }

    // "v" was parsed
    private MolangExpr finishActorVar() throws OOMErr, MolangCompileException {
        int start = current - 1;
        if (!(consume('.', false) || consume("ariable.", false)))
            throw new MolangCompileException(MolangCompileException.EXPECTED_ACTOR_VAR, source, start, current);
        // To support vectors, we need to know at compile time how many elements are in this variable.
        // We use the syntax "v.size_integer.name" to facilitate this. When the integer is not present, size is assumed to be 1.
        int varSize = 1;
        if (consumeDigit(false)) {
            int countStart = current - 1;
            while (consumeDigit(false));
            String s = source.substring(countStart, current);
            varSize = Integer.parseInt(s);
            if (varSize <= 1) throw new MolangCompileException(MolangCompileException.VAR_SIZE_TOO_LOW, source, countStart, current);
            if (!consume('$', false)) throw new MolangCompileException(MolangCompileException.EXPECT_DOLLAR_AFTER_VAR_SIZE, s, source, countStart, current);
        }
        String varName = (varSize == 1 ? expectIdent() : varSize + "$" + expectIdent());
        ActorVariable variable = instance.getOrCreateActorVariable(varName, varSize); // Get the variable
        if (!check("==", true) && consume('=', true)) {
            int equals = current - 1;
            MolangExpr rhs = parse();
            if (rhs.returnCount() != variable.size)
                throw new MolangCompileException(MolangCompileException.INCOMPATIBLE_VAR_SIZE, "v." + varName, variable.size, rhs.returnCount(), source, equals, equals + 1);
            return new ActorVariableAssign(variable, parse());
        } else {
            return variable;
        }
    }

    // "q" was parsed
    private MolangExpr finishQuery() throws OOMErr, MolangCompileException {
        int start = current - 1;
        if (!(consume('.', false) || consume("uery.", false)))
            throw new MolangCompileException(MolangCompileException.EXPECTED_QUERY, source, start, current);
        String queryName = expectIdent();
        int afterFuncName = current;
        MolangInstance.Query<?, OOMErr> query = instance.getQuery(queryName);
        if (query == null) throw new MolangCompileException(MolangCompileException.UNKNOWN_QUERY, queryName, source, start, current);
        return query.bind(this, parseParams(), source, start, afterFuncName);
    }

    // "c" was parsed
    private MolangExpr finishContextVar() throws OOMErr, MolangCompileException {
        int start = current - 1;
        if (!(consume('.', false) || consume("ontext.", false)))
            throw new MolangCompileException(MolangCompileException.EXPECTED_CONTEXT_VAR, source, start, current);
        String contextVarName = expectIdent();
        int varIndex = contextVariables.indexOf(contextVarName);
        if (varIndex == -1) throw new MolangCompileException(MolangCompileException.UNKNOWN_CONTEXT_VAR, contextVarName, source, start, current);
        return new ContextVariable(contextVarName, varIndex);
    }

    // ( was already consumed
    private MolangExpr finishParen() throws OOMErr, MolangCompileException {
        MolangExpr res = parse();
        if (!consume(')', true))
            throw new MolangCompileException(MolangCompileException.EXPECTED_CLOSE_PAREN, source, current - 1, current);
        return res;
    }

    // { was already consumed
    private MolangExpr finishBlock() throws OOMErr, MolangCompileException {
        Compound c = pushScope();
        separatedList(';', '}', true, () -> c.exprs.add(parse()));
        popScope();
        return c;
    }

    // [ was already consumed
    private MolangExpr finishVectorConstructor() throws OOMErr, MolangCompileException {
        int start = current - 1;
        List<MolangExpr> exprs = separatedList(',', ']', true, this::parse);
        if (exprs.size() <= 1)
            throw new MolangCompileException(MolangCompileException.VECTOR_CONSTRUCTOR_EXPECTS_TWO_ARGS, source, start, current);
        return new VectorConstructor(exprs);
    }

    // ------------------
    // | FUNCTION CALLS |
    // ------------------

    private List<MolangExpr> parseParams() throws OOMErr, MolangCompileException {
        if (!consume('(', true)) return List.of();
        return separatedList(',', ')', false, this::parse);
    }

    private <T> List<T> separatedList(char separator, char end, boolean allowTrailingSeparator, BiThrowingSupplier<T, OOMErr, MolangCompileException> parser) throws OOMErr, MolangCompileException {
        if (consume(end, true)) return List.of();
        ArrayList<T> res = new ArrayList<>();
        while (true) {
            res.add(parser.get());
            if (!consume(separator, true)) {
                if (!consume(end, true))
                    throw new MolangCompileException(MolangCompileException.EXPECTED_LIST, String.valueOf(separator), String.valueOf(end), source, current - 1, current);
                return res;
            } else if (allowTrailingSeparator) {
                if (consume(end, true)) return res;
            }
        }
    }

    // -------------------
    // | EXPOSED HELPERS |
    // -------------------

    public Compound pushScope() {
        Compound res = new Compound();
        scopes.push(res);
        return res;
    }

    public void popScope() {
        scopes.pop().finish();
    }

    // Pass error locations. When calling from outside the parser, just pass -1 for varStart and equalsSign, since it can't error.
    public TempVariable declareTempVar(String name, int size, int varStart, int equalsSign) throws MolangCompileException {
        // If there are no scopes, error
        if (scopes.isEmpty())
            throw new MolangCompileException(MolangCompileException.TEMP_VAR_OUTSIDE_BLOCK, name, source, varStart, equalsSign);
        // Add it to scope. Find the next unused index:
        int nextIndex = scopes.reversed().stream().map(x -> x.tempVars).map(List::reversed).flatMap(List::stream).filter(v -> v.isVector() == (size != 1)).findFirst().map(it -> it.getLogicalLocation() + it.size).orElse(0);
        if (size == 1) maxLocalVariables = Math.max(maxLocalVariables, nextIndex + 1);
        return new TempVariable(name, size, nextIndex);
    }

    @FunctionalInterface
    public interface BiThrowingSupplier<T, E1 extends Throwable, E2 extends Throwable> {
        T get() throws E1, E2;
    }


    // ----------
    // | LEXING |
    // ----------

    private boolean consumeAny(String toks, boolean skipWhitespace) {
        if (skipWhitespace) skipWhitespace();
        if (current == source.length()) return false;
        return (toks.indexOf(source.charAt(current)) != -1) && advance();
    }

    private boolean consume(char tok, boolean skipWhitespace) {
        if (skipWhitespace) skipWhitespace();
        if (current == source.length()) return false;
        return source.charAt(current) == tok && advance();
    }

    private boolean consume(String tok, boolean skipWhitespace) {
        if (skipWhitespace) skipWhitespace();
        int start = current;
        for (int i = 0; i < tok.length(); i++) {
            if (!consume(tok.charAt(i), false)) {
                current = start;
                return false;
            }
        }
        return true;
    }

    private boolean check(String tok, boolean skipWhitespace) {
        int start = current;
        boolean success = consume(tok, skipWhitespace);
        if (success) current = start;
        return success;
    }

    private boolean consumeDigit(boolean skipWhitespace) {
        if (skipWhitespace) skipWhitespace();
        if (current == source.length()) return false;
        return isDigit(source.charAt(current)) && advance();
    }
    private boolean consumeIdentChar(boolean skipWhitespace) {
        if (skipWhitespace) skipWhitespace();
        if (current == source.length()) return false;
        return isIdentChar(source.charAt(current)) && advance();
    }

    private String expectIdent() throws MolangCompileException {
        int start = current;
        while (consumeIdentChar(false));
        if (start == current) throw new MolangCompileException(MolangCompileException.EXPECTED_NAME, source, current - 1, current);
        return source.substring(start, current);
    }

    private void skipWhitespace() {
        while (current < source.length() && isWhitespace(source.charAt(current)))
            current++;
    }

    private boolean advance() {
        current++;
        return true;
    }

    private String last() {
        return last(1);
    }
    private String last(int count) {
        return source.substring(current - count, current);
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isIdentChar(char c) {
        return c >= 'a' && c <= 'z' || c == '.' || c == '_';
    }

    private static boolean isWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\n';
    }

}
