package io.miragon.common.architecture.condition;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;

public final class InterfaceImplementationConditions {

    private InterfaceImplementationConditions() {
    }

    /**
     * Ensures that a class implements exactly one interface from a specified package.
     * A typical use-case is to enforce that application services implement exactly one inbound port.
     *
     * @param packagePattern the package pattern the interface should reside in
     * @return ArchCondition that can be used in {@code .should(...)}
     */
    public static ArchCondition<JavaClass> implementExactlyOneInterfaceFrom(String packagePattern) {
        return new ArchCondition<>("implement exactly one interface from " + packagePattern) {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                List<JavaType> useCasesOrQueries = item.getInterfaces().stream()
                        .filter(interfaceType -> isLocatedInPackage(interfaceType, packagePattern))
                        .toList();
                if (useCasesOrQueries.size() != 1) {
                    String error = item.getSimpleName()
                            + " should implement exactly one interface, but implements '"
                            + useCasesOrQueries.size() + "'";
                    events.add(SimpleConditionEvent.violated(item, error));
                } else {
                    events.add(SimpleConditionEvent.satisfied(item, item.getSimpleName() + " is OK"));
                }
            }
        };
    }

    private static boolean isLocatedInPackage(JavaType type, String packagePattern) {
        return type.toErasure().getPackageName().contains(packagePattern);
    }
}
