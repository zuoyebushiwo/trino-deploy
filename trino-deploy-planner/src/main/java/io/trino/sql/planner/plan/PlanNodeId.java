package io.trino.sql.planner.plan;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import javax.annotation.concurrent.Immutable;

import static java.util.Objects.requireNonNull;

/**
 * 执行计划节点Id
 *
 * @author ZhangXueJun
 * @Date 2023年04月03日
 */
@Immutable
public class PlanNodeId {

    private final String id;

    @JsonCreator
    public PlanNodeId(String id) {
        requireNonNull(id, "id is null");
        this.id = id;
    }

    @Override
    @JsonValue
    public String toString() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        PlanNodeId that = (PlanNodeId) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
