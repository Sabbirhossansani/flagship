package com.flagship.server.dto;

import com.flagship.core.rule.CompositeRuleNode;
import com.flagship.core.rule.LeafRuleNode;
import com.flagship.core.rule.LogicalOperator;
import com.flagship.core.rule.RuleNode;
import com.flagship.core.rule.RuleOperatorType;
import com.flagship.core.rule.SegmentReferenceNode;

import java.util.List;

/**
 * JSON-friendly representation of the Composite {@link RuleNode} tree (FR2).
 */
public record RuleNodeDto(
        String type,
        LogicalOperator logicalOperator,
        List<RuleNodeDto> children,
        String attribute,
        RuleOperatorType operator,
        List<String> values,
        String segmentKey
) {
    public static final String TYPE_COMPOSITE = "COMPOSITE";
    public static final String TYPE_LEAF = "LEAF";
    public static final String TYPE_SEGMENT = "SEGMENT";

    public static RuleNodeDto composite(LogicalOperator logicalOperator, List<RuleNodeDto> children) {
        return new RuleNodeDto(TYPE_COMPOSITE, logicalOperator, children, null, null, null, null);
    }

    public static RuleNodeDto leaf(String attribute, RuleOperatorType operator, List<String> values) {
        return new RuleNodeDto(TYPE_LEAF, null, null, attribute, operator, values, null);
    }

    public static RuleNodeDto segment(String segmentKey) {
        return new RuleNodeDto(TYPE_SEGMENT, null, null, null, null, null, segmentKey);
    }

    public RuleNode toDomain() {
        String resolvedType = resolveType();
        return switch (resolvedType) {
            case TYPE_COMPOSITE -> {
                if (logicalOperator == null) {
                    throw new IllegalArgumentException("CompositeRuleNode requires logicalOperator (AND or OR)");
                }
                if (children != null) {
                    for (RuleNodeDto child : children) {
                        if (child == null) {
                            throw new IllegalArgumentException("CompositeRuleNode children must not contain null elements");
                        }
                    }
                }
                List<RuleNode> childNodes = children == null
                        ? List.of()
                        : children.stream().map(RuleNodeDto::toDomain).toList();
                yield new CompositeRuleNode(logicalOperator, childNodes);
            }
            case TYPE_SEGMENT -> {
                if (segmentKey == null) {
                    throw new IllegalArgumentException("SegmentReferenceNode requires segmentKey");
                }
                yield new SegmentReferenceNode(segmentKey);
            }
            case TYPE_LEAF -> {
                if (attribute == null || operator == null) {
                    throw new IllegalArgumentException("LeafRuleNode requires attribute and operator");
                }
                if (values != null) {
                    for (String val : values) {
                        if (val == null) {
                            throw new IllegalArgumentException("LeafRuleNode values must not contain null elements");
                        }
                    }
                }
                yield new LeafRuleNode(attribute, operator, values != null ? values : List.of());
            }
            default -> throw new IllegalArgumentException("Unsupported rule node type: " + resolvedType);
        };
    }

    private String resolveType() {
        if (type != null && !type.isBlank()) {
            return type.trim().toUpperCase();
        }
        if (logicalOperator != null || (children != null && !children.isEmpty())) {
            return TYPE_COMPOSITE;
        }
        if (segmentKey != null && !segmentKey.isBlank()) {
            return TYPE_SEGMENT;
        }
        return TYPE_LEAF;
    }

    public static RuleNodeDto fromDomain(RuleNode node) {
        if (node == null) {
            return null;
        }
        if (node instanceof CompositeRuleNode composite) {
            List<RuleNodeDto> childDtos = composite.children().stream()
                    .map(RuleNodeDto::fromDomain)
                    .toList();
            return composite(composite.logicalOperator(), childDtos);
        }
        if (node instanceof LeafRuleNode leaf) {
            return leaf(leaf.attribute(), leaf.operator(), leaf.values());
        }
        if (node instanceof SegmentReferenceNode segmentRef) {
            return segment(segmentRef.segmentKey());
        }
        throw new IllegalArgumentException("Unknown RuleNode subtype: " + node.getClass().getName());
    }
}
