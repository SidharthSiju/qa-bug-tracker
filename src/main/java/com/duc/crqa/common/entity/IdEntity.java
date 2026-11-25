package com.duc.crqa.common.entity;

import jakarta.persistence.MappedSuperclass;

/**
 * Marker superclass for entities with a generated id. Each entity declares its own {@code id} field so it can use a
 * table-specific sequence ({@code IT_<TABLE>_SEQ}); see {@link AuditedEntity} for the timestamped variant.
 */
@MappedSuperclass
public abstract class IdEntity {
}
