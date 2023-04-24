package io.trino.sql.planner.plan;

/**
 * @author ZhangXueJun
 * @Date 2023年04月03日
 */
public abstract class PlanVisitor<R, C> {

    protected abstract R visitPlan(PlanNode node, C context);

    public R visitTableScan(TableScanNode node, C context)
    {
        return visitPlan(node, context);
    }

}
