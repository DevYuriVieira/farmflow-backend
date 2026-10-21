package com.farmflow.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.farmflow", importOptions = {ImportOption.DoNotIncludeTests.class})
public class ArchitectureRulesTest {

    @ArchTest
    static final ArchRule controllersShouldOnlyBeAccessedByConfigurationOrTests =
        classes()
            .that().resideInAPackage("..controller..")
            .should().onlyBeAccessed().byAnyPackage("..controller..", "..config..", "..security..");

    @ArchTest
    static final ArchRule controllersShouldNotDependOnRepositoriesDirectly =
        noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAPackage("..repository..");

    @ArchTest
    static final ArchRule entitiesShouldNotDependOnControllersOrServices =
        noClasses()
            .that().resideInAPackage("..entity..")
            .should().dependOnClassesThat().resideInAnyPackage("..controller..", "..service..");

    @ArchTest
    static final ArchRule dtosShouldNotDependOnControllersOrServices =
        noClasses()
            .that().resideInAPackage("..dto..")
            .should().dependOnClassesThat().resideInAnyPackage("..controller..", "..service..");

    @ArchTest
    static final ArchRule controllerClassesShouldHaveRestControllerAnnotation =
        classes()
            .that().haveSimpleNameEndingWith("Controller")
            .and().areNotInterfaces()
            .should().beAnnotatedWith(RestController.class);

    @ArchTest
    static final ArchRule serviceClassesShouldHaveServiceAnnotation =
        classes()
            .that().haveSimpleNameEndingWith("Service")
            .and().areNotInterfaces()
            .should().beAnnotatedWith(Service.class);
}