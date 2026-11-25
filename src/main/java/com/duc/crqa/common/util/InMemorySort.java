package com.duc.crqa.common.util;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.domain.Sort;

public final class InMemorySort {

    private InMemorySort() {
    }

    public static <T> List<T> apply(List<T> rows, Sort sort) {
        if (rows.isEmpty() || sort == null || sort.isUnsorted()) {
            return rows;
        }
        Sort.Order order = sort.iterator().next();
        String property = order.getProperty();
        Comparator<T> comparator = (a, b) -> compareValues(value(a, property), value(b, property));
        if (order.isDescending()) {
            comparator = comparator.reversed();
        }
        List<T> sorted = new ArrayList<>(rows);
        sorted.sort(comparator);
        return sorted;
    }

    private static Object value(Object record, String property) {
        if (record == null) {
            return null;
        }
        RecordComponent[] components = record.getClass().getRecordComponents();
        if (components == null) {
            return null;
        }
        for (RecordComponent component : components) {
            if (component.getName().equals(property)) {
                try {
                    return component.getAccessor().invoke(record);
                } catch (ReflectiveOperationException ex) {
                    return null;
                }
            }
        }
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static int compareValues(Object a, Object b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return 1;
        }
        if (b == null) {
            return -1;
        }
        Comparable left = a instanceof Enum<?> enumValue ? enumValue.name() : (Comparable) a;
        Comparable right = b instanceof Enum<?> enumValue ? enumValue.name() : (Comparable) b;
        return left.compareTo(right);
    }
}
