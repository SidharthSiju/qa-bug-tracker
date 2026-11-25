package com.duc.crqa.jira.dto;

import java.io.Serializable;
import java.util.List;

/** Outcome of an import run: how many change requests were inserted, refreshed and could not be imported. */
public record JiraImportResult(int created, int updated, int failed, List<String> errors) implements Serializable {

    public int total() {
        return created + updated + failed;
    }

    public String summary() {
        return created + " created, " + updated + " updated, " + failed + " failed";
    }
}
