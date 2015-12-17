package com.siberika.idea.pascal.lang;

import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.siberika.idea.pascal.editor.PascalActionDeclare;
import com.siberika.idea.pascal.editor.PascalRoutineActions;
import com.siberika.idea.pascal.ide.actions.SectionToggle;
import com.siberika.idea.pascal.lang.parser.NamespaceRec;
import com.siberika.idea.pascal.lang.psi.PasModule;
import com.siberika.idea.pascal.lang.psi.PasNamespaceIdent;
import com.siberika.idea.pascal.lang.psi.PascalNamedElement;
import com.siberika.idea.pascal.lang.psi.impl.PasExportedRoutineImpl;
import com.siberika.idea.pascal.lang.psi.impl.PasField;
import com.siberika.idea.pascal.lang.psi.impl.PasRoutineImplDeclImpl;
import com.siberika.idea.pascal.lang.psi.impl.PascalModule;
import com.siberika.idea.pascal.lang.references.PasReferenceUtil;
import com.siberika.idea.pascal.util.PsiUtil;
import com.siberika.idea.pascal.util.StrUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

import static com.siberika.idea.pascal.PascalBundle.message;

/**
 * Author: George Bakhtadze
 * Date: 12/14/12
 */
public class PascalAnnotator implements Annotator {

    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if ((element instanceof PascalNamedElement) && (PsiUtil.isRoutineName((PascalNamedElement) element))) {
            PsiElement parent = element.getParent();
            if (parent.getClass() == PasExportedRoutineImpl.class) {
                annotateRoutineInInterface((PasExportedRoutineImpl) parent, holder);
            } else if (parent.getClass() == PasRoutineImplDeclImpl.class) {
                annotateRoutineInImplementation((PasRoutineImplDeclImpl) parent, holder);
            }
        }

        if (PsiUtil.isEntityName(element) && !PsiUtil.isLastPartOfMethodImplName((PascalNamedElement) element)) {
            PascalNamedElement namedElement = (PascalNamedElement) element;
            Collection<PasField> refs = PasReferenceUtil.resolveExpr(NamespaceRec.fromElement(element), PasField.TYPES_ALL, true, 0);
            if (refs.isEmpty()) {
                Annotation ann = holder.createErrorAnnotation(element, message("ann.error.undeclared.identifier"));
                String name = namedElement.getName();
                if (!StrUtil.hasLowerCaseChar(name)) {
                    ann.registerFix(new PascalActionDeclare.ActionCreateConst(message("action.createConst"), namedElement));
                } else {
                    ann.registerFix(new PascalActionDeclare.ActionCreateVar(message("action.createVar"), namedElement));
                }
                if (name.startsWith("T") || PsiUtil.isTypeName(element)) {
                    ann.registerFix(new PascalActionDeclare.ActionCreateType(message("action.createType"), namedElement));
                }
            }
        }

        if ((element instanceof PascalNamedElement) && PsiUtil.isUsedUnitName(element.getParent())) {
            annotateUnit(holder, (PasNamespaceIdent) element.getParent());
        }
    }

    private void annotateUnit(AnnotationHolder holder, PasNamespaceIdent element) {
        PsiElement prev = element.getPrevSibling();
        if ((prev instanceof PsiComment) && "{!}".equals(prev.getText())) {
            return;
        }
        PascalModule module = PsiUtil.getElementPasModule(element);
        if ((module != null)) {
            Pair<List<PascalNamedElement>, List<PascalNamedElement>> idents = module.getIdentsFrom(element.getName());
            if (PsiUtil.belongsToInterface(element)) {
                if (idents.getFirst().size() + idents.getSecond().size() == 0) {
                    Annotation ann = holder.createWarningAnnotation(element, message("ann.warn.unused.unit"));
                } else if (idents.getFirst().size() == 0) {
                    Annotation ann = holder.createWarningAnnotation(element, message("ann.warn.unused.unit.interface"));
                }
            } else if (idents.getSecond().size() == 0) {
                Annotation ann = holder.createWarningAnnotation(element, message("ann.warn.unused.unit"));
            }
        }
    }

    /**
     * # unimplemented routine error
     * # unimplemented method  error
     * # filter external/abstract routines/methods
     * # implement routine fix
     * # implement method fix
     * error on class if not all methods implemented
     * implement all methods fix
     */
    private void annotateRoutineInInterface(PasExportedRoutineImpl routine, AnnotationHolder holder) {
        if (!PsiUtil.isFromBuiltinsUnit(routine) && PsiUtil.needImplementation(routine) && (null == SectionToggle.getRoutineTarget(routine))) {
            Annotation ann = holder.createErrorAnnotation(routine, message("ann.error.missing.implementation"));
            ann.registerFix(new PascalRoutineActions.ActionImplement(message("action.implement"), routine));
            ann.registerFix(new PascalRoutineActions.ActionImplementAll(message("action.implement.all"), routine));
        }
    }

    /**
     * error on method in implementation only
     * add method to class declaration fix
     * add to interface section fix for routines in implementation section only
     */
    private void annotateRoutineInImplementation(PasRoutineImplDeclImpl routine, AnnotationHolder holder) {
        if (null == SectionToggle.getRoutineTarget(routine)) {
            if (routine.getContainingScope() instanceof PasModule) {
                if (((PasModule) routine.getContainingScope()).getUnitInterface() != null) {
                    Annotation ann = holder.createWeakWarningAnnotation(routine.getNameIdentifier() != null ? routine.getNameIdentifier() : routine, message("ann.error.missing.routine.declaration"));
                    ann.registerFix(new PascalRoutineActions.ActionDeclare(message("action.declare.routine"), routine));
                    ann.registerFix(new PascalRoutineActions.ActionDeclareAll(message("action.declare.routine.all"), routine));
                }
            } else {
                Annotation ann = holder.createErrorAnnotation(routine.getNameIdentifier() != null ? routine.getNameIdentifier() : routine, message("ann.error.missing.method.declaration"));
                ann.registerFix(new PascalRoutineActions.ActionDeclare(message("action.declare.method"), routine));
                ann.registerFix(new PascalRoutineActions.ActionDeclareAll(message("action.declare.method.all"), routine));
            }
        }
    }

}
