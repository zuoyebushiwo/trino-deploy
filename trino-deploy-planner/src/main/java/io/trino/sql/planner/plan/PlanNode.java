package io.trino.sql.planner.plan;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.trino.sql.planner.Symbol;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * 执行计划节点
 *
 * @author ZhangXueJun
 * @Date 2023年04月03日
 */
public abstract class PlanNode {

    private final PlanNodeId id;

    public PlanNode(PlanNodeId id) {
        requireNonNull(id, "id is null");
        this.id = id;
    }

    @JsonProperty("id")
    public PlanNodeId getId()
    {
        return id;
    }

    /**
     * 获取上游执行计划节点
     *
     * @return
     */
    public abstract List<PlanNode> getSources();

    public abstract List<Symbol> getOutputSymbols();

    public abstract PlanNode replaceChildren(List<PlanNode> newChildren);

    public <R, C> R accept(PlanVisitor<R, C> visitor, C context) {
        return visitor.visitPlan(this, context);
    }
}
