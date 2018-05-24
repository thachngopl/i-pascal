package com.siberika.idea.pascal.editor.completion;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.siberika.idea.pascal.PascalIcons;
import com.siberika.idea.pascal.lang.lexer.PascalLexer;
import com.siberika.idea.pascal.lang.psi.PasCompoundStatement;
import com.siberika.idea.pascal.lang.psi.PasEntityScope;
import com.siberika.idea.pascal.lang.psi.PasModule;
import com.siberika.idea.pascal.lang.psi.PasRepeatStatement;
import com.siberika.idea.pascal.lang.psi.PasTryStatement;
import com.siberika.idea.pascal.lang.psi.PasTypes;
import com.siberika.idea.pascal.lang.psi.PascalNamedElement;
import com.siberika.idea.pascal.lang.psi.PascalStubElement;
import com.siberika.idea.pascal.lang.references.PasReferenceUtil;
import com.siberika.idea.pascal.lang.stub.PascalSymbolIndex;
import com.siberika.idea.pascal.util.DocUtil;
import com.siberika.idea.pascal.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Author: George Bakhtadze
 * Date: 09/05/2018
 */
public class CompletionUtil {
    private static final Map<String, String> INSERT_MAP = getInsertMap();
    private static final String PLACEHOLDER_FILENAME = "__FILENAME__";
    private static final Collection<String> CLOSING_STATEMENTS = Arrays.asList(PasTypes.END.toString(), PasTypes.EXCEPT.toString(), PasTypes.UNTIL.toString());

    private static final InsertHandler<LookupElement> INSERT_HANDLER = new InsertHandler<LookupElement>() {
        @Override
        public void handleInsert(final InsertionContext context, LookupElement item) {
            String content = INSERT_MAP.get(item.getLookupString());
            if (null != content) {
                content = content.replaceAll(PLACEHOLDER_FILENAME, FileUtilRt.getNameWithoutExtension(context.getFile().getName()));
                int caretPos = context.getEditor().getCaretModel().getOffset();
                DocUtil.adjustDocument(context.getEditor(), caretPos, content);
                context.commitDocument();
                if (CLOSING_STATEMENTS.contains(item.getLookupString())) {
                    PsiElement el = context.getFile().findElementAt(caretPos - 1);
                    PsiElement block = PsiTreeUtil.getParentOfType(el, PasCompoundStatement.class, PasTryStatement.class, PasRepeatStatement.class);
                    if (block != null) {
                        DocUtil.reformat(block, true);
                    }
                } else {
                    DocUtil.reformatInSeparateCommand(context.getProject(), context.getFile(), context.getEditor());
                }
            }
        }
    };
    static final TokenSet TS_DO_THEN_OF = TokenSet.create(PasTypes.DO, PasTypes.THEN, PasTypes.OF, PasTypes.ELSE);
    private static final TokenSet TS_CONTROL_STATEMENT = TokenSet.create(PasTypes.IF_STATEMENT, PasTypes.FOR_STATEMENT, PasTypes.WHILE_STATEMENT, PasTypes.WITH_STATEMENT, PasTypes.CASE_STATEMENT, PasTypes.CASE_ELSE);

    private static Map<IElementType, TokenSet> TOKEN_TO_PAS = initTokenToPasToken();

    private static Map<IElementType, TokenSet> initTokenToPasToken() {
        Map<IElementType, TokenSet> result = new HashMap<>();
        result.put(PascalLexer.PROGRAM, TokenSet.create(PascalLexer.PROGRAM, PasTypes.PROGRAM_MODULE_HEAD));
        result.put(PascalLexer.UNIT, TokenSet.create(PascalLexer.UNIT, PasTypes.UNIT_MODULE_HEAD));
        result.put(PascalLexer.LIBRARY, TokenSet.create(PascalLexer.LIBRARY, PasTypes.LIBRARY_MODULE_HEAD));
        result.put(PascalLexer.PACKAGE, TokenSet.create(PascalLexer.PACKAGE, PasTypes.PACKAGE_MODULE_HEAD));
        result.put(PascalLexer.CONTAINS, TokenSet.create(PascalLexer.CONTAINS, PasTypes.CONTAINS_CLAUSE));
        result.put(PascalLexer.REQUIRES, TokenSet.create(PascalLexer.REQUIRES, PasTypes.REQUIRES_CLAUSE));

        result.put(PascalLexer.INTERFACE, TokenSet.create(PascalLexer.INTERFACE, PasTypes.UNIT_INTERFACE));
        result.put(PascalLexer.IMPLEMENTATION, TokenSet.create(PascalLexer.IMPLEMENTATION, PasTypes.UNIT_IMPLEMENTATION));
        result.put(PascalLexer.INITIALIZATION, TokenSet.create(PascalLexer.INITIALIZATION, PasTypes.UNIT_INITIALIZATION));
        result.put(PascalLexer.FINALIZATION, TokenSet.create(PascalLexer.FINALIZATION, PasTypes.UNIT_FINALIZATION));

        result.put(PascalLexer.USES, TokenSet.create(PascalLexer.USES, PasTypes.USES_CLAUSE));
        result.put(PascalLexer.EXCEPT, TokenSet.create(PasTypes.EXCEPT, PasTypes.FINALLY));
        result.put(PascalLexer.FINALLY, TokenSet.create(PasTypes.FINALLY, PasTypes.EXCEPT));

        result.put(PascalLexer.ELSE, TokenSet.create(PascalLexer.ELSE, PasTypes.CASE_ELSE));
        result.put(PascalLexer.UNTIL, TokenSet.create(PascalLexer.UNTIL));

        result.put(PascalLexer.BEGIN, TokenSet.create(PascalLexer.BEGIN, PasTypes.COMPOUND_STATEMENT, PasTypes.BLOCK_BODY, PasTypes.PROC_BODY_BLOCK));
        return result;
    }

    private static Collection<PascalStubElement> findSymbols(Project project, String key) {
        Collection<PascalStubElement> result = new SmartList<>();
        final MinusculeMatcher matcher = NameUtil.buildMatcher(key).build();
        final GlobalSearchScope scope = ProjectScope.getAllScope(project);
        StubIndex.getInstance().processAllKeys(PascalSymbolIndex.KEY, new Processor<String>() {
            @Override
            public boolean process(final String key) {
                if (matcher.matches(key)) {
                    StubIndex.getInstance().processElements(PascalSymbolIndex.KEY, key, project, scope,
                            PascalNamedElement.class, new Processor<PascalNamedElement>() {
                                @Override
                                public boolean process(PascalNamedElement namedElement) {
                                    result.add((PascalStubElement) namedElement);
                                    return true;
                                }
                            });
                }
                return true;
            }
        }, scope, null);
        return result;
    }

    private static Map<String, String> getInsertMap() {
        Map<String, String> res = new HashMap<String, String>();
        res.put(PasTypes.UNIT.toString(), String.format(" %s;\n\ninterface\n\n  %s\nimplementation\n\nend.\n", PLACEHOLDER_FILENAME, DocUtil.PLACEHOLDER_CARET));
        res.put(PasTypes.PROGRAM.toString(), String.format(" %s;\nbegin\n  %s\nend.\n", PLACEHOLDER_FILENAME, DocUtil.PLACEHOLDER_CARET));
        res.put(PasTypes.LIBRARY.toString(), String.format(" %s;\n\nexports %s\n\nbegin\n\nend.\n", PLACEHOLDER_FILENAME, DocUtil.PLACEHOLDER_CARET));
        res.put(PasTypes.PACKAGE.toString(), String.format(" %s;\n\nrequires\n\n contains %s\n\nend.\n", PLACEHOLDER_FILENAME, DocUtil.PLACEHOLDER_CARET));
        res.put(PasTypes.BEGIN.toString(), String.format("\n%s\nend;\n", DocUtil.PLACEHOLDER_CARET));
        res.put(PasTypes.BEGIN.toString() + " ", String.format("\n%s\nend.\n", DocUtil.PLACEHOLDER_CARET));
        res.put(PasTypes.END.toString(), ";");
        res.put(PasTypes.INTERFACE.toString(), String.format("\n  %s\nimplementation\n", DocUtil.PLACEHOLDER_CARET));
        res.put(PasTypes.INITIALIZATION.toString(), String.format("\n  %s\nfinalization\n", DocUtil.PLACEHOLDER_CARET));
        res.put(PasTypes.USES.toString(), String.format(" %s;", DocUtil.PLACEHOLDER_CARET));

        res.put(PasTypes.FOR.toString(), String.format(" %s to do ;", DocUtil.PLACEHOLDER_CARET));
        res.put(PasTypes.WHILE.toString(), String.format(" %s do ;", DocUtil.PLACEHOLDER_CARET));
        res.put(PasTypes.REPEAT.toString(), String.format("\nuntil %s;", DocUtil.PLACEHOLDER_CARET));

        res.put(PasTypes.IF.toString(), String.format(" %s then ;\n", DocUtil.PLACEHOLDER_CARET));
        res.put(PasTypes.CASE.toString(), String.format(" %s of\nend;", DocUtil.PLACEHOLDER_CARET));

        res.put(PasTypes.THEN.toString(), String.format(" %s", DocUtil.PLACEHOLDER_CARET));
        res.put(PasTypes.DO.toString(), String.format(" %s", DocUtil.PLACEHOLDER_CARET));

        res.put(PasTypes.WITH.toString(), String.format(" %s do ;", DocUtil.PLACEHOLDER_CARET));

        res.put(PasTypes.TRY.toString(), String.format("\n  %s\nfinally\nend;", DocUtil.PLACEHOLDER_CARET));

        res.put(PasTypes.RECORD.toString(), String.format("  %s\nend;", DocUtil.PLACEHOLDER_CARET));
        res.put(PasTypes.OBJECT.toString(), String.format("  %s\nend;", DocUtil.PLACEHOLDER_CARET));
        res.put(PasTypes.CLASS.toString(), String.format("(TObject)\nprivate\n%s\npublic\nend;", DocUtil.PLACEHOLDER_CARET));
        res.put(PasTypes.OBJC_CLASS.toString(), String.format("(NSObject)\n%s\npublic\nend;", DocUtil.PLACEHOLDER_CARET));
        res.put(PasTypes.INTERFACE.toString() + " ", String.format("(IUnknown)\n%s\nend;", DocUtil.PLACEHOLDER_CARET));
        res.put(PasTypes.ARRAY.toString(), String.format("[0..%s] of ;", DocUtil.PLACEHOLDER_CARET));
        res.put(PasTypes.SET.toString(), String.format(" of %s;", DocUtil.PLACEHOLDER_CARET));

        res.put(PasTypes.CONSTRUCTOR.toString(), String.format(" Create(%s);", DocUtil.PLACEHOLDER_CARET));
        res.put(PasTypes.DESTRUCTOR.toString(), String.format(" Destroy(%s); override;", DocUtil.PLACEHOLDER_CARET));
        res.put(PasTypes.CONSTRUCTOR.toString() + " ", String.format(" Create(%s);", DocUtil.PLACEHOLDER_CARET));
        res.put(PasTypes.DESTRUCTOR.toString() + " ", String.format(" Destroy(%s); override;", DocUtil.PLACEHOLDER_CARET));
        res.put(PasTypes.FUNCTION.toString(), String.format(" %s(): ;", DocUtil.PLACEHOLDER_CARET));
        res.put(PasTypes.PROCEDURE.toString(), String.format(" %s();", DocUtil.PLACEHOLDER_CARET));

        res.put(PasTypes.VAR.toString(), String.format(" %s: ;", DocUtil.PLACEHOLDER_CARET));
        res.put(PasTypes.THREADVAR.toString(), String.format(" %s: ;", DocUtil.PLACEHOLDER_CARET));
        res.put(PasTypes.CONST.toString(), String.format(" %s = ;", DocUtil.PLACEHOLDER_CARET));
        res.put(PasTypes.RESOURCESTRING.toString(), String.format(" %s = '';", DocUtil.PLACEHOLDER_CARET));
        res.put(PasTypes.TYPE.toString(), String.format(" T%s = ;", DocUtil.PLACEHOLDER_CARET));

        res.put(PasTypes.PROPERTY.toString(), String.format(" %s: read ;", DocUtil.PLACEHOLDER_CARET));

        res.put(PasTypes.PACKED.toString(), " ");

        res.put(PasTypes.UNTIL.toString(), String.format(" %s;", DocUtil.PLACEHOLDER_CARET));
        res.put(PasTypes.ON.toString(), String.format(" E: %s do", DocUtil.PLACEHOLDER_CARET));
        res.put(PasTypes.EXCEPT.toString(), "\n");
        return res;
    }

    static void appendTokenSet(CompletionResultSet result, TokenSet tokenSet) {
        for (IElementType op : tokenSet.getTypes()) {
            result.caseInsensitive().addElement(getElement(op.toString()));
        }
    }

    static LookupElement getElement(String s) {
        return LookupElementBuilder.create(s).withIcon(PascalIcons.GENERAL).withStrikeoutness(s.equals(PasTypes.GOTO.toString())).withInsertHandler(INSERT_HANDLER);
    }

    static void appendTokenSetIfAbsent(CompletionResultSet result, TokenSet tokenSet, PsiElement position, Class... classes) {
        if (PsiTreeUtil.findChildOfAnyType(position, classes) == null) {
            for (IElementType op : tokenSet.getTypes()) {
                LookupElementBuilder el = LookupElementBuilder.create(op.toString()).withIcon(PascalIcons.GENERAL).withStrikeoutness(op.equals(PasTypes.GOTO)).withInsertHandler(INSERT_HANDLER);
                result.caseInsensitive().addElement(el);
            }
        }
    }

    static void appendTokenSetUnique(CompletionResultSet result, TokenSet tokenSet, PsiElement position) {
        for (IElementType op : tokenSet.getTypes()) {
            appendTokenSetUnique(result, op, position);
        }
    }

    static void appendTokenSetUnique(CompletionResultSet result, IElementType op, PsiElement position) {
        TokenSet tokensToFind = TOKEN_TO_PAS.get(op) != null ? TOKEN_TO_PAS.get(op) : TokenSet.create(op);
        if (position.getNode().getChildren(tokensToFind).length > 0) {  //===*** TODO: remove
            return;
        }
        PsiErrorElement error = PsiTreeUtil.getChildOfType(position, PsiErrorElement.class);
        if (error != null && error.getNode().getChildren(tokensToFind).length > 0) {
            return;
        }
        /*for (PsiElement psiElement : position.getChildren()) {
            if (psiElement.getNode().findLeafElementAt(0).getElementType() == op) {
                return;
            }
        }*/
//        if ((TOKEN_TO_PSI.get(op) == null) || (PsiTreeUtil.findChildOfType(position, TOKEN_TO_PSI.get(op), true) == null)) {
            LookupElementBuilder el = LookupElementBuilder.create(op.toString()).withIcon(PascalIcons.GENERAL).withStrikeoutness(op.equals(PasTypes.GOTO)).withInsertHandler(INSERT_HANDLER);
            result.caseInsensitive().addElement(el);
//        }
    }

    static void handleUses(CompletionResultSet result, @NotNull PsiElement pos) {
        PasModule module = PsiUtil.getElementPasModule(pos);
        Set<String> excludedUnits = new HashSet<String>();
        if (module != null) {
            excludedUnits.add(module.getName().toUpperCase());
            for (SmartPsiElementPointer<PasEntityScope> scopePtr : module.getPublicUnits()) {
                if (scopePtr.getElement() != null) {
                    excludedUnits.add(scopePtr.getElement().getName().toUpperCase());
                }
            }
            for (SmartPsiElementPointer<PasEntityScope> scopePtr : module.getPrivateUnits()) {
                if (scopePtr.getElement() != null) {
                    excludedUnits.add(scopePtr.getElement().getName().toUpperCase());
                }
            }
        }
        for (VirtualFile file : PasReferenceUtil.findUnitFiles(pos.getProject(), com.intellij.openapi.module.ModuleUtil.findModuleForPsiElement(pos))) {
            if (!excludedUnits.contains(file.getNameWithoutExtension().toUpperCase())) {
                LookupElementBuilder lookupElement = LookupElementBuilder.create(file.getNameWithoutExtension());
                result.caseInsensitive().addElement(lookupElement.withTypeText(file.getExtension() != null ? file.getExtension() : "", false));
            }
        }
    }

    static boolean isControlStatement(PsiElement pos) {
        return TS_CONTROL_STATEMENT.contains(pos.getNode().getElementType()) || pos.getNode().getElementType() == PasTypes.IF_THEN_STATEMENT;
    }

    static ASTNode getDoThenOf(PsiElement statement) {
        ASTNode[] cand = statement.getNode().getChildren(TS_DO_THEN_OF);
        return cand.length > 0 ? cand[0] : null;
    }

    static void appendText(CompletionResultSet result, String s) {
        result.caseInsensitive().addElement(getElement(s));
    }
}
