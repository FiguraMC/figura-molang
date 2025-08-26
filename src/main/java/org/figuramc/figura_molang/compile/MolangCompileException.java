package org.figuramc.figura_molang.compile;

import org.figuramc.figura_translations.FiguraTranslations;
import org.figuramc.figura_translations.Language;
import org.figuramc.figura_translations.Translatable;
import org.figuramc.figura_translations.TranslatableItems;

public class MolangCompileException extends Exception {

    static {
        FiguraTranslations.registerTranslationResources("org/figuramc/figura_molang/translations/");
    }

    private static final Language EN_US = Language.getInstance("en_us");

    public String sourceSnippet; // The snippet
    public int snippetStart, snippetEnd; // Start/end indices of the highlighted region within the snippet

    public MolangCompileException(Translatable<TranslatableItems.Items0> translatable, String source, int start, int end) {
        this(Translatable.translate(EN_US, translatable), source, start, end);
    }
    public <A> MolangCompileException(Translatable<TranslatableItems.Items1<A>> translatable, A arg, String source, int start, int end) {
        this(Translatable.translate(EN_US, translatable, arg), source, start, end);
    }
    public <A, B> MolangCompileException(Translatable<TranslatableItems.Items2<A, B>> translatable, A arg1, B arg2, String source, int start, int end) {
        this(Translatable.translate(EN_US, translatable, arg1, arg2), source, start, end);
    }
    public <A, B, C> MolangCompileException(Translatable<TranslatableItems.Items3<A, B, C>> translatable, A arg1, B arg2, C arg3, String source, int start, int end) {
        this(Translatable.translate(EN_US, translatable, arg1, arg2, arg3), source, start, end);
    }

    // Provide the problem, the source code, and the region of source where it was wrong.
    private static final int TOTAL_LEN = 50;

    private MolangCompileException(String reason, String source, int start, int end) {
        super(Translatable.translate(EN_US, COMPILE_ERROR, reason));
        // Create the source snippet
        int length = end - start;
        if (length > TOTAL_LEN) length = TOTAL_LEN; // Truncate length if needed
        int totalPadding = TOTAL_LEN - length;
        int startPadding = totalPadding / 2;
        int endPadding = totalPadding - startPadding;
        int end2 = start + length;
        sourceSnippet = source.substring(
                Math.max(0, start - startPadding),
                Math.min(source.length(), end2 + endPadding)
        );
        snippetStart = startPadding;
        snippetEnd = sourceSnippet.length() - endPadding;
    }

    // Compile error header
    public static final Translatable<TranslatableItems.Items1<String>> COMPILE_ERROR = Translatable.create("figura_molang.error.compile", String.class);
    // Compile error reasons
    public static final Translatable<TranslatableItems.Items0> RETURN_OUTSIDE_BLOCK = Translatable.create("figura_molang.error.compile.return_outside_block");
    public static final Translatable<TranslatableItems.Items1<String>> TEMP_VAR_OUTSIDE_BLOCK = Translatable.create("figura_molang.error.compile.temp_var_outside_block", String.class);
    public static final Translatable<TranslatableItems.Items2<Integer, Integer>> DIFF_RETURN_SIZES = Translatable.create("figura_molang.error.compile.diff_return_sizes", Integer.class, Integer.class);
    public static final Translatable<TranslatableItems.Items0> EXPECTED_EXPRESSION = Translatable.create("figura_molang.error.compile.expected_expression");
    public static final Translatable<TranslatableItems.Items0> NUMBER_PARSE = Translatable.create("figura_molang.error.compile.number_parse");
    public static final Translatable<TranslatableItems.Items1<String>> UNKNOWN_MATH = Translatable.create("figura_molang.error.compile.unknown_math", String.class);
    public static final Translatable<TranslatableItems.Items0> EXPECTED_TEMP_VAR = Translatable.create("figura_molang.error.compile.expected_temp_var");
    public static final Translatable<TranslatableItems.Items1<String>> NONEXISTENT_TEMP_VAR = Translatable.create("figura_molang.error.compile.nonexistent_temp_var", String.class);
    public static final Translatable<TranslatableItems.Items0> EXPECTED_ACTOR_VAR = Translatable.create("figura_molang.error.compile.expected_actor_var");
    public static final Translatable<TranslatableItems.Items0> VAR_SIZE_TOO_LOW = Translatable.create("figura_molang.error.compile.var_size_too_low");
    public static final Translatable<TranslatableItems.Items1<String>> EXPECT_DOLLAR_AFTER_VAR_SIZE = Translatable.create("figura_molang.error.compile.expect_dollar_after_var_size", String.class);
    public static final Translatable<TranslatableItems.Items0> EXPECTED_QUERY = Translatable.create("figura_molang.error.compile.expected_query");
    public static final Translatable<TranslatableItems.Items1<String>> UNKNOWN_QUERY = Translatable.create("figura_molang.error.compile.unknown_query", String.class);
    public static final Translatable<TranslatableItems.Items0> EXPECTED_CONTEXT_VAR = Translatable.create("figura_molang.error.compile.expected_context_var");
    public static final Translatable<TranslatableItems.Items1<String>> UNKNOWN_CONTEXT_VAR = Translatable.create("figura_molang.error.compile.unknown_context_var", String.class);
    public static final Translatable<TranslatableItems.Items0> EXPECTED_CLOSE_PAREN = Translatable.create("figura_molang.error.compile.expected_close_paren");
    public static final Translatable<TranslatableItems.Items0> EXPECTED_TERNARY_COLON = Translatable.create("figura_molang.error.compile.expected_ternary_colon");
    public static final Translatable<TranslatableItems.Items0> EXPECTED_NAME = Translatable.create("figura_molang.error.compile.expected_name");
    public static final Translatable<TranslatableItems.Items2<String, String>> EXPECTED_LIST = Translatable.create("figura_molang.error.compile.expected_list", String.class, String.class);
    public static final Translatable<TranslatableItems.Items0> LOGICAL_AND_EXPECTS_SCALARS = Translatable.create("figura_molang.error.compile.logical_and_expects_scalars");
    public static final Translatable<TranslatableItems.Items0> LOGICAL_OR_EXPECTS_SCALARS = Translatable.create("figura_molang.error.compile.logical_or_expects_scalars");
    public static final Translatable<TranslatableItems.Items0> TERNARY_CONDITION_EXPECTS_SCALAR = Translatable.create("figura_molang.error.compile.ternary_condition_expects_scalar");
    public static final Translatable<TranslatableItems.Items2<Integer, Integer>> TERNARY_BRANCHES_MUST_BE_SAME_SIZE = Translatable.create("figura_molang.error.compile.ternary_branches_must_be_same_size", Integer.class, Integer.class);
    public static final Translatable<TranslatableItems.Items3<String, Integer, Integer>> INCOMPATIBLE_VAR_SIZE = Translatable.create("figura_molang.error.compile.incompatible_var_size", String.class, Integer.class, Integer.class);
    public static final Translatable<TranslatableItems.Items0> VECTOR_CONSTRUCTOR_EXPECTS_TWO_ARGS = Translatable.create("figura_molang.error.compile.vector_constructor_expects_two_args");
    public static final Translatable<TranslatableItems.Items3<String, String, String>> WRONG_ARG_COUNT = Translatable.create("figura_molang.error.compile.wrong_arg_count", String.class, String.class, String.class);
    public static final Translatable<TranslatableItems.Items3<String, Integer, Integer>> VECTOR_ARGS_SAME_SIZE = Translatable.create("figura_molang.error.compile.vector_args_same_size", String.class, Integer.class, Integer.class);
    public static final Translatable<TranslatableItems.Items1<String>> SCALAR_ARGS_ONLY = Translatable.create("figura_molang.error.compile.scalar_args_only", String.class);



}
