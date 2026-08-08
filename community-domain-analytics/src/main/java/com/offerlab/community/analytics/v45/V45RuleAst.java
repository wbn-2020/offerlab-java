package com.offerlab.community.analytics.v45;

import java.util.List;
import java.util.Objects;

public sealed interface V45RuleAst permits V45RuleAst.Logical, V45RuleAst.Predicate {

    record Logical(V45LogicalOperator operator, List<V45RuleAst> children) implements V45RuleAst {
        public Logical {
            Objects.requireNonNull(operator, "operator");
            children = List.copyOf(Objects.requireNonNull(children, "children"));
            if (children.isEmpty() || children.size() > 16) {
                throw new IllegalArgumentException("V45 logical node child count is outside the allow-list");
            }
        }
    }

    record Predicate(V45RuleField field, V45RuleOperator operator, String value,
                     List<String> values) implements V45RuleAst {
        public Predicate {
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(operator, "operator");
            if (operator == V45RuleOperator.EXISTS) {
                if (value != null || (values != null && !values.isEmpty())) {
                    throw new IllegalArgumentException("EXISTS cannot carry a value");
                }
            } else if (operator == V45RuleOperator.IN || operator == V45RuleOperator.NOT_IN) {
                values = List.copyOf(Objects.requireNonNull(values, "values"));
                if (values.isEmpty() || values.size() > 32 || value != null) {
                    throw new IllegalArgumentException("set operator requires a bounded value set");
                }
            } else if (value == null || value.isBlank() || (values != null && !values.isEmpty())) {
                throw new IllegalArgumentException("scalar operator requires one value");
            }
        }
    }
}
