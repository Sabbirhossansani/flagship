package com.flagship.core.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architectural fitness tests verifying Constraint C6: Pure Java 21 domain models and zero framework dependencies.
 */
class PureDomainArchitectureTest {

    private static JavaClasses coreClasses;

    @BeforeAll
    static void setup() {
        coreClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.flagship.core");
    }

    @Test
    @DisplayName("C6: Core domain must have zero Spring Framework dependencies")
    void domainMustNotDependOnSpring() {
        noClasses().that().resideInAPackage("com.flagship.core..")
                .should().dependOnClassesThat().resideInAPackage("org.springframework..")
                .as("FlagShip Core must be pure Java 21 with zero Spring framework dependencies")
                .check(coreClasses);
    }

    @Test
    @DisplayName("C6: Core domain must have zero Jackson / JSON library dependencies")
    void domainMustNotDependOnJackson() {
        noClasses().that().resideInAPackage("com.flagship.core..")
                .should().dependOnClassesThat().resideInAPackage("com.fasterxml.jackson..")
                .as("FlagShip Core domain models must not be coupled to Jackson serialization annotations")
                .check(coreClasses);
    }

    @Test
    @DisplayName("C6: Core domain must have zero JPA / Hibernate persistence dependencies")
    void domainMustNotDependOnJpa() {
        noClasses().that().resideInAPackage("com.flagship.core..")
                .should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..", "javax.persistence..", "org.hibernate..")
                .as("FlagShip Core domain models must not contain JPA database annotations")
                .check(coreClasses);
    }

    @Test
    @DisplayName("C6: Core domain must have zero framework dependencies (Spring, Jackson, Jakarta, Javax)")
    void domainMustHaveZeroFrameworkDependencies() {
        noClasses().that().resideInAPackage("com.flagship.core..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "com.fasterxml.jackson..",
                        "jakarta..",
                        "javax..",
                        "org.hibernate.."
                )
                .as("FlagShip Core must be pure Java 21 without any framework coupling")
                .check(coreClasses);
    }

    @Test
    @DisplayName("C6: Core domain models must be public and well encapsulated")
    void modelClassesShouldBePublic() {
        classes().that().resideInAPackage("com.flagship.core.model..")
                .should().bePublic()
                .as("Domain entities and records in model package must be publicly accessible")
                .check(coreClasses);
    }
}
