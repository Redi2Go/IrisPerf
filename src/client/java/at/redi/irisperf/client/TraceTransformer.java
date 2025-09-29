package at.redi.irisperf.client;

import io.github.douira.glsl_transformer.GLSLParser;
import io.github.douira.glsl_transformer.ast.data.ChildNodeList;
import io.github.douira.glsl_transformer.ast.node.TranslationUnit;
import io.github.douira.glsl_transformer.ast.node.Version;
import io.github.douira.glsl_transformer.ast.node.external_declaration.ExtensionDirective;
import io.github.douira.glsl_transformer.ast.node.external_declaration.ExternalDeclaration;
import io.github.douira.glsl_transformer.ast.node.external_declaration.FunctionDefinition;
import io.github.douira.glsl_transformer.ast.node.statement.CompoundStatement;
import io.github.douira.glsl_transformer.ast.node.statement.Statement;
import io.github.douira.glsl_transformer.ast.node.statement.terminal.ReturnStatement;
import io.github.douira.glsl_transformer.ast.print.ASTPrinter;
import io.github.douira.glsl_transformer.ast.query.Root;
import io.github.douira.glsl_transformer.ast.transform.ASTBuilder;
import io.github.douira.glsl_transformer.ast.transform.ASTParser;
import io.github.douira.glsl_transformer.ast.traversal.ASTListener;
import io.github.douira.glsl_transformer.ast.traversal.ASTWalker;
import io.github.douira.glsl_transformer.parser.ParseShape;

import java.util.*;
import java.util.stream.Stream;

public class TraceTransformer {
    private static final ParseShape<GLSLParser.ExtensionDirectiveContext, ExtensionDirective> EXTENSION_DIRECTIVE = new ParseShape<>(
            GLSLParser.ExtensionDirectiveContext.class,
            GLSLParser::extensionDirective,
            ASTBuilder::visitExtensionDirective
    );

    public static void transform(ShaderProfile shaderProfile, ASTParser parser, Root root, TranslationUnit translationUnit) {
        translationUnit.getChildren().add(0, parser.parseExternalDeclaration(root,
                "bool irisPerf_sample = ivec2(gl_FragCoord.xy) == ivec2(427, 240);"
        ));

        List<FunctionDefinition> functions = translationUnit
                .getChildren()
                .stream()
                .filter(child -> child.getExternalDeclarationType() == ExternalDeclaration.ExternalDeclarationType.FUNCTION_DEFINITION)
                .map(child -> (FunctionDefinition) child)
                .filter(child -> !child.getBody().getStatements().isEmpty())
                .toList();

        for (FunctionDefinition function : functions) {
            int functionIndex = shaderProfile.functionCount++;
            shaderProfile.functionNames[functionIndex] = ASTPrinter.printCompact(function.getFunctionPrototype());

            ChildNodeList<Statement> functionStatements = function.getBody().getChildren();
            functionStatements.add(0, parser.parseStatement(root, "uint64_t irisPerf_begin = clockARB();"));

            List<ReturnStatement> returnStatements = new LinkedList<>();

            final boolean[] lastIsReturnStatement = { false };
            ASTWalker.walk(new ASTListener() {
                @Override
                public void enterStatement(Statement node) {
                    boolean isReturnStatement = node instanceof ReturnStatement;
                    lastIsReturnStatement[0] = isReturnStatement;

                    if (!isReturnStatement)
                        return;

                    returnStatements.add((ReturnStatement) node);
                }
            }, function);

            String writeStatement = "if (irisPerf_sample) irisPerf_trace_array[" + functionIndex + "] += int(clockARB() - irisPerf_begin);";

            for (ReturnStatement returnStatement : returnStatements) {
                CompoundStatement compoundStatement = new CompoundStatement(Stream.empty());
                returnStatement.replaceByAndDelete(compoundStatement);

                if (returnStatement.getExpression() != null) {
                    String returnType = ASTPrinter.printCompact(function.getFunctionPrototype().getReturnType());
                    String returnExpression = ASTPrinter.printCompact(returnStatement.getExpression());
                    String tmpVariable = returnType + " irisPerf_tmp = " + returnExpression + ";";

                    String returnStatementString = "return irisPerf_tmp;";

                    compoundStatement.getChildren().addAll(parser.parseStatements(root, tmpVariable, writeStatement, returnStatementString));
                } else {
                    compoundStatement.getChildren().addAll(parser.parseStatements(root, writeStatement, "return;"));
                }
            }

            if (!lastIsReturnStatement[0])
                function.getBody().getChildren().add(parser.parseStatement(root, writeStatement));
        }

        translationUnit.getVersionStatement().version = Version.GLSL45;

        translationUnit.getChildren().add(0, parseExtensionDirective(parser, root, "#extension GL_ARB_shader_clock : require"));
        translationUnit.getChildren().add(0, parseExtensionDirective(parser, root, "#extension GL_ARB_gpu_shader_int64 : enable"));

        int extensionCount = (int) translationUnit.getChildren().stream().filter(externalDeclaration -> externalDeclaration.getExternalDeclarationType() == ExternalDeclaration.ExternalDeclarationType.EXTENSION_DIRECTIVE).count();
        translationUnit.getChildren().add(extensionCount, parser.parseExternalDeclaration(root,
                """
                        layout (std430) buffer irisPerf_trace_block {
                            int irisPerf_trace_array[1024];
                        };
                     """));
    }

    /*private static void applyLegacySamplerPatch(RootSupplier rootSupplier, TranslationUnit translationUnit) {
        Set<String> functionNames = new HashSet<>();

        ASTWalker.walk(new ASTListener() {
            @Override
            public void enterFunctionDefinition(FunctionDefinition node) {
                functionNames.add(node.getFunctionPrototype().getName().getName());
            }
        }, translationUnit);

        ASTWalker.walk(new ASTListener() {
            @Override
            public void enterFunctionCallExpression(FunctionCallExpression node) {
                if (node.getFunctionName() == null)
                    return;

                boolean isLegacyName = node.getFunctionName().getName().equals("shadow2D");
                if (!isLegacyName)
                    return;

                if (functionNames.contains(node.getFunctionName().getName()))
                    return;

                node.getFunctionName().setName("texture");
            }
        }, translationUnit);
    }*/

    private static ExtensionDirective parseExtensionDirective(ASTParser parser, Root root, String source) {
        return parser.parseNode(root, EXTENSION_DIRECTIVE, source + "\n");
    }
}
