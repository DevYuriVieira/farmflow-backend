package com.farmflow.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.farmflow", importOptions = {ImportOption.DoNotIncludeTests.class})
public class ArchitectureRulesTest {

    @ArchTest
    static final ArchRule controllersShouldOnlyBeAccessedByConfigurationOrTests =
        classes()
            .that().resideInAPackage("..controller..")
            .should().onlyBeAccessed().byAnyPackage("..controller..", "..config..");

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

    @ArchTest
    static final ArchRule domainAggregatesShouldNotHavePublicSetters =
        classes()
            .that().haveSimpleName("Machinery")
            .or().haveSimpleName("Plantation")
            .or().haveSimpleName("FarmActivity")
            .or().haveSimpleName("InventoryItem")
            .should(new ArchCondition<JavaClass>("not declare public setter methods") {
                @Override
                public void check(JavaClass item, ConditionEvents events) {
                    for (JavaMethod method : item.getAllMethods()) {
                        if (method.getName().startsWith("set") && method.getModifiers().contains(JavaModifier.PUBLIC)) {
                            events.add(SimpleConditionEvent.violated(method, "Method " + method.getFullName() + " is public"));
                        }
                    }
                }
            });

    @ArchTest
    static final ArchRule controllersShouldNotBeTransactional =
        classes()
            .that().resideInAPackage("..controller..")
            .should().notBeAnnotatedWith(Transactional.class);

    @ArchTest
    static final ArchRule servicesShouldNotDependOnControllers =
        noClasses()
            .that().resideInAPackage("..service..")
            .should().dependOnClassesThat().resideInAPackage("..controller..");

    @ArchTest
    static final ArchRule consumersShouldNotDependOnControllers =
        noClasses()
            .that().resideInAPackage("..consumer..")
            .should().dependOnClassesThat().resideInAPackage("..controller..");

    @ArchTest
    static final ArchRule domainClassesShouldNotDependOnSpringWeb =
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework.web..");

    @ArchTest
    static final ArchRule controllerEndpointsShouldBeProtectedWithPreAuthorize =
        classes()
            .that().resideInAPackage("..controller..")
            .and().areAnnotatedWith(RestController.class)
            .and().doNotHaveSimpleName("AuthController")
            .and().doNotHaveSimpleName("HealthProbeController")
            .should(new ArchCondition<JavaClass>("have @PreAuthorize on all public endpoint methods") {
                @Override
                public void check(JavaClass item, ConditionEvents events) {
                    boolean classHasPreAuth = item.isAnnotatedWith(org.springframework.security.access.prepost.PreAuthorize.class);
                    if (classHasPreAuth) {
                        return;
                    }
                    for (JavaMethod method : item.getAllMethods()) {
                        if (method.getModifiers().contains(JavaModifier.PUBLIC)
                                && (method.isAnnotatedWith(org.springframework.web.bind.annotation.GetMapping.class)
                                || method.isAnnotatedWith(org.springframework.web.bind.annotation.PostMapping.class)
                                || method.isAnnotatedWith(org.springframework.web.bind.annotation.PutMapping.class)
                                || method.isAnnotatedWith(org.springframework.web.bind.annotation.PatchMapping.class)
                                || method.isAnnotatedWith(org.springframework.web.bind.annotation.DeleteMapping.class)
                                || method.isAnnotatedWith(org.springframework.web.bind.annotation.RequestMapping.class))) {
                            if (!method.isAnnotatedWith(org.springframework.security.access.prepost.PreAuthorize.class)) {
                                events.add(SimpleConditionEvent.violated(method, "Method " + method.getFullName() + " lacks @PreAuthorize annotation"));
                            }
                        }
                    }
                }
            });

    @ArchTest
    static final ArchRule domainStateShouldNotBeManagedThroughNotesConcat =
        classes()
            .that().resideInAPackage("..entity..")
            .should(new ArchCondition<JavaClass>("not expose note concatenation for state changes") {
                @Override
                public void check(JavaClass item, ConditionEvents events) {
                    for (JavaMethod method : item.getAllMethods()) {
                        if (method.getName().toLowerCase().contains("appendnote") || method.getName().toLowerCase().contains("concatnote")) {
                            events.add(SimpleConditionEvent.violated(method, "Domain aggregate " + item.getName() + " exposes note-based state mutator: " + method.getName()));
                        }
                    }
                }
            });
}