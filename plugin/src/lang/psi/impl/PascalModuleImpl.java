package com.siberika.idea.pascal.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.siberika.idea.pascal.lang.parser.PascalParserUtil;
import com.siberika.idea.pascal.lang.psi.PasEntityScope;
import com.siberika.idea.pascal.lang.psi.PasInvalidScopeException;
import com.siberika.idea.pascal.lang.psi.PasModule;
import com.siberika.idea.pascal.lang.psi.PasNamespaceIdent;
import com.siberika.idea.pascal.lang.psi.PascalNamedElement;
import com.siberika.idea.pascal.lang.references.PasReferenceUtil;
import com.siberika.idea.pascal.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Author: George Bakhtadze
 * Date: 14/09/2013
 */
public class PascalModuleImpl extends PasScopeImpl implements PasEntityScope {

    private Map<String, PasField> privateMembers = null;
    private Map<String, PasField> publicMembers = null;
    private Set<PascalNamedElement> redeclaredPrivateMembers = null;
    private Set<PascalNamedElement> redeclaredPublicMembers = null;
    private List<PasEntityScope> privateUnits = Collections.emptyList();
    private List<PasEntityScope> publicUnits = Collections.emptyList();
    private long buildPrivateStamp = -1;
    private long buildPublicStamp = -1;

    public PascalModuleImpl(ASTNode node) {
        super(node);
    }

    public enum ModuleType {
        UNIT, PROGRAM, LIBRARY, PACKAGE
    }

    public ModuleType getModuleType() {
        PasModule pm = (PasModule) this;
        if (pm.getUnitModuleHead() != null) {
            return ModuleType.UNIT;
        } else if (pm.getLibraryModuleHead() != null) {
            return ModuleType.LIBRARY;
        } else if (pm.getPackageModuleHead() != null) {
            return ModuleType.PACKAGE;
        }
        return ModuleType.PROGRAM;
    }

    @Override
    @Nullable
    public final PasField getField(final String name) throws PasInvalidScopeException {
        PasField result = getPublicField(name);
        if (null == result) {
            result = getPrivateField(name);
        }
        return result;
    }

    @Nullable
    synchronized public final PasField getPublicField(final String name) throws PasInvalidScopeException {
        if (!isCacheActual(publicMembers, buildPublicStamp)) {
            buildPublicMembers();
        }
        return publicMembers.get(name.toUpperCase());
    }

    @Nullable
    synchronized public final PasField getPrivateField(final String name) throws PasInvalidScopeException {
        if (!isCacheActual(privateMembers, buildPrivateStamp)) {
            buildPrivateMembers();
        }
        return privateMembers.get(name.toUpperCase());
    }

    @NotNull
    @Override
    synchronized public Collection<PasField> getAllFields() throws PasInvalidScopeException {
        if (!PsiUtil.checkeElement(this)) {
            return Collections.emptyList();
        }
        if (!isCacheActual(publicMembers, buildPublicStamp)) {
            buildPublicMembers();
        }
        if (!isCacheActual(privateMembers, buildPrivateStamp)) {
            buildPrivateMembers();
        }
        Collection<PasField> result = new LinkedHashSet<PasField>();
        result.addAll(publicMembers.values());
        result.addAll(privateMembers.values());
        return result;
    }

    private void buildPrivateMembers() throws PasInvalidScopeException {
        if (isCacheActual(privateMembers, buildPrivateStamp)) { return; } // TODO: check correctness
        privateMembers = new LinkedHashMap<String, PasField>();
        redeclaredPrivateMembers = new LinkedHashSet<PascalNamedElement>();

        PsiElement section = PsiUtil.getModuleImplementationSection(this);
        if (null == section) section = this;
        if (!PsiUtil.checkeElement(section)) return;

        collectFields(section, PasField.Visibility.PRIVATE, privateMembers, redeclaredPrivateMembers);

        privateUnits = retrieveUsedUnits(section);
        for (PasEntityScope unit : privateUnits) {
            privateMembers.put(unit.getName().toUpperCase(), new PasField(this, unit, unit.getName(), PasField.FieldType.UNIT, PasField.Visibility.PRIVATE));
        }

        buildPrivateStamp = PsiUtil.getFileStamp(getContainingFile());
        LOG.info(String.format("Unit %s private: %d, used: %d", getName(), privateMembers.size(), privateUnits != null ? privateUnits.size() : 0));
    }

    @SuppressWarnings("unchecked")
    private List<PasEntityScope> retrieveUsedUnits(PsiElement section) {
        List<PasEntityScope> result;
        List<PasNamespaceIdent> usedNames = PsiUtil.getUsedUnits(section);
        result = new ArrayList<PasEntityScope>(usedNames.size());
        List<VirtualFile> unitFiles = PasReferenceUtil.findUnitFiles(section.getProject(), ModuleUtilCore.findModuleForPsiElement(section));
        for (PasNamespaceIdent ident : usedNames) {
            addUnit(result, PasReferenceUtil.findUnit(section.getProject(), unitFiles, ident.getName()));
        }
        for (String unitName : PascalParserUtil.EXPLICIT_UNITS) {
            if (!unitName.equalsIgnoreCase(getName())) {
                addUnit(result, PasReferenceUtil.findUnit(section.getProject(), unitFiles, unitName));
            }
        }
        return result;
    }

    private void addUnit(List<PasEntityScope> result, PasEntityScope unit) {
        if (unit != null) {
            result.add(unit);
        }
    }

    private void buildPublicMembers() throws PasInvalidScopeException {
        if (isCacheActual(publicMembers, buildPublicStamp)) { return; } // TODO: check correctness
        publicMembers = new LinkedHashMap<String, PasField>();
        redeclaredPublicMembers = new LinkedHashSet<PascalNamedElement>();

        publicMembers.put(getName().toUpperCase(), new PasField(this, this, getName(), PasField.FieldType.UNIT, PasField.Visibility.PUBLIC));

        PsiElement section = PsiUtil.getModuleInterfaceSection(this);
        if (null == section) return;
        if (!PsiUtil.checkeElement(section)) return;

        collectFields(section, PasField.Visibility.PRIVATE, publicMembers, redeclaredPublicMembers);

        publicUnits = retrieveUsedUnits(section);
        for (PasEntityScope unit : publicUnits) {
            publicMembers.put(unit.getName().toUpperCase(), new PasField(this, unit, unit.getName(), PasField.FieldType.UNIT, PasField.Visibility.PRIVATE));
        }

        buildPublicStamp = PsiUtil.getFileStamp(getContainingFile());
        LOG.info(String.format("Unit %s public: %d, used: %d", getName(), publicMembers.size(), publicUnits != null ? publicUnits.size() : 0));
    }

    private boolean isCacheActual(Map<String, PasField> cache, long stamp) throws PasInvalidScopeException {
        if (!PsiUtil.checkeElement(this)) {
            return false;
        }
        if (null == getContainingFile()) {
            PascalPsiImplUtil.logNullContainingFile(this);
            return false;
        }
        return (cache != null) && (PsiUtil.getFileStamp(getContainingFile()) == stamp);
    }

    synchronized public List<PasEntityScope> getPrivateUnits() throws PasInvalidScopeException {
        if (!PsiUtil.checkeElement(this)) {
            return Collections.emptyList();
        }
        if (!isCacheActual(privateMembers, buildPrivateStamp)) {
            buildPrivateMembers();
        }
        return privateUnits;
    }

    synchronized public List<PasEntityScope> getPublicUnits() throws PasInvalidScopeException {
        if (!PsiUtil.checkeElement(this)) {
            return Collections.emptyList();
        }
        if (!isCacheActual(publicMembers, buildPublicStamp)) {
            buildPublicMembers();
        }
        return publicUnits;
    }

    @NotNull
    @Override
    public List<PasEntityScope> getParentScope() throws PasInvalidScopeException {
        return Collections.emptyList();
    }

    @Nullable
    @Override
    public PasEntityScope getContainingScope() throws PasInvalidScopeException {
        return null;
    }

    @Override
    synchronized public void invalidateCache() {
        LOG.info("WARNING: invalidating cache");
        privateMembers = null;
        publicMembers = null;
        containingScope = null;
    }

}
