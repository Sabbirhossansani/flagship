package com.flagship.server.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architectural fitness tests verifying server module layering, package boundaries, and naming conventions (C6).
 */
class ServerLayeringArchitectureTest {

    private static JavaClasses serverClasses;

    @BeforeAll
    static void setup() {
        serverClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.flagship.server");
    }

    @Test
    @DisplayName("Layering: Domain layer must not depend on Web presentation layer")
    void domainMustNotDependOnWeb() {
        noClasses().that().resideInAPackage("com.flagship.server.domain..")
                .should().dependOnClassesThat().resideInAPackage("com.flagship.server.web..")
                .as("Domain entities and repositories must not access controllers in web layer")
                .check(serverClasses);
    }

    @Test
    @DisplayName("Layering: Controllers must reside in web package")
    void controllersMustResideInWebPackage() {
        classes().that().areAnnotatedWith(RestController.class)
                .should().resideInAPackage("com.flagship.server.web..")
                .as("All Spring REST controllers must reside in com.flagship.server.web")
                .check(serverClasses);
    }

    @Test
    @DisplayName("Naming: Controllers must follow *Controller naming convention")
    void controllersShouldHaveSuffix() {
        classes().that().areAnnotatedWith(RestController.class)
                .should().haveSimpleNameEndingWith("Controller")
                .as("REST controllers must be suffixed with 'Controller'")
                .check(serverClasses);
    }

    @Test
    @DisplayName("Naming: Services must follow *Service naming convention")
    void servicesShouldHaveSuffix() {
        classes().that().areAnnotatedWith(Service.class)
                .should().haveSimpleNameEndingWith("Service")
                .as("Spring @Service components must be suffixed with 'Service'")
                .check(serverClasses);
    }

    @Test
    @DisplayName("Naming: Repositories must follow *Repository naming convention")
    void repositoriesShouldHaveSuffix() {
        classes().that().areAssignableTo(org.springframework.data.repository.Repository.class)
                .should().haveSimpleNameEndingWith("Repository")
                .as("Spring Data repositories must be suffixed with 'Repository'")
                .check(serverClasses);
    }
}
