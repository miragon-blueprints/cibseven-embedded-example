package io.miragon.common.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import java.io.PrintStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * General coding guidelines that rely on the resolved bytecode graph (package structure, freedom of
 * cycles, no {@code println}).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BasicCodingGuidelinesTest {

    protected abstract String rootPackage();

    private JavaClasses allClasses;
    private JavaClasses productionClasses;

    private JavaClasses allClasses() {
        if (allClasses == null) {
            allClasses = new ClassFileImporter().importPackages(rootPackage());
        }
        return allClasses;
    }

    private JavaClasses productionClasses() {
        if (productionClasses == null) {
            productionClasses = new ClassFileImporter()
                    .withImportOption(new ImportOption.DoNotIncludeTests())
                    .importPackages(rootPackage());
        }
        return productionClasses;
    }

    @Test
    void eachClassHasPackageDeclaration() {
        ArchRuleDefinition.classes()
                .should().resideInAnyPackage(rootPackage() + "..")
                .because("All classes should be in the specified package structure")
                .check(allClasses());
    }

    @Test
    void classesAreFreeOfCycles() {
        SlicesRuleDefinition.slices()
                .matching(rootPackage() + ".(**)")
                .should().beFreeOfCycles()
                .because("Classes should not have circular dependencies")
                .check(allClasses());
    }

    @Test
    void productionCodeDoesNotUsePrintlnOrSystemOut() {
        ArchRuleDefinition.noClasses()
                .should().callMethod(PrintStream.class, "println", Object.class)
                .because("Use a logger instead of println or System.out for diagnostic output")
                .check(productionClasses());
    }
}
