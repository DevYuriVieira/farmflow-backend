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
}