package com.example.borrower_service.repository;

import com.example.borrower_service.model.Holding;
import com.example.borrower_service.model.HoldingId;
import com.example.borrower_service.model.Organization;

import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** JPA access for borrower transactions and existing column-named response objects. */
@Repository
public class BorrowerRepository {
    private final EntityManager entityManager;

    public BorrowerRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @FunctionalInterface
    public interface Condition {
        Predicate predicate(Root<?> root, CriteriaBuilder builder);
    }

    public static Condition eq(String attribute, Object value) {
        return (root, builder) ->
                value == null
                        ? builder.isNull(root.get(attribute))
                        : builder.equal(root.get(attribute), value);
    }

    public static Condition oneOf(String attribute, Object... values) {
        return (root, builder) -> root.get(attribute).in(values);
    }

    public static Condition greaterThan(String attribute, long value) {
        return (root, builder) -> builder.gt(root.get(attribute), value);
    }

    public static Condition either(Condition first, Condition second) {
        return (root, builder) ->
                builder.or(first.predicate(root, builder), second.predicate(root, builder));
    }

    private static Predicate[] predicates(
            Root<?> root, CriteriaBuilder builder, Condition[] conditions) {
        return Arrays.stream(conditions)
                .map(condition -> condition.predicate(root, builder))
                .toArray(Predicate[]::new);
    }

    public <T> List<T> entities(Class<T> type, Sort sort, int limit, Condition... conditions) {
        return select(type, sort, limit, LockModeType.NONE, conditions);
    }

    private <T> List<T> select(
            Class<T> type, Sort sort, int limit, LockModeType lock, Condition... conditions) {
        var builder = entityManager.getCriteriaBuilder();
        var criteria = builder.createQuery(type);
        var root = criteria.from(type);
        criteria.select(root).where(predicates(root, builder, conditions));
        criteria.orderBy(
                sort.stream()
                        .map(
                                order ->
                                        order.isAscending()
                                                ? builder.asc(root.get(order.getProperty()))
                                                : builder.desc(root.get(order.getProperty())))
                        .toList());
        return entityManager
                .createQuery(criteria)
                .setMaxResults(limit)
                .setLockMode(lock)
                .getResultList();
    }

    public List<Map<String, Object>> rows(Class<?> type, Condition... conditions) {
        return rows(type, Sort.unsorted(), Integer.MAX_VALUE, conditions);
    }

    public List<Map<String, Object>> rows(
            Class<?> type, Sort sort, int limit, Condition... conditions) {
        return entities(type, sort, limit, conditions).stream()
                .map(BorrowerRepository::columns)
                .toList();
    }

    public List<Map<String, Object>> lockedRows(Class<?> type, Condition... conditions) {
        return select(type, Sort.unsorted(), 1, LockModeType.PESSIMISTIC_WRITE, conditions).stream()
                .map(BorrowerRepository::columns)
                .toList();
    }

    public Map<String, Object> matchingRow(Class<?> type, Condition... conditions) {
        return rows(type, Sort.unsorted(), 1, conditions).stream()
                .findFirst()
                .orElseThrow(BorrowerRepository::missing);
    }

    public Map<String, Object> row(Class<?> type, Object id) {
        return columns(find(type, id));
    }

    public Map<String, Object> lockedRow(Class<?> type, Object id) {
        return columns(
                Optional.ofNullable(entityManager.find(type, id, LockModeType.PESSIMISTIC_WRITE))
                        .orElseThrow(BorrowerRepository::missing));
    }

    public Map<String, Object> owned(Class<?> type, long organizationId, long id) {
        return matchingRow(type, eq("organizationId", organizationId), eq("id", id));
    }

    public <T> T find(Class<T> type, Object id) {
        return Optional.ofNullable(entityManager.find(type, id))
                .orElseThrow(BorrowerRepository::missing);
    }

    public <T> T persist(Supplier<T> constructor, Consumer<T> initialize) {
        T entity = constructor.get();
        initialize.accept(entity);
        entityManager.persist(entity);
        return entity;
    }

    public <T> long create(Supplier<T> constructor, Consumer<T> initialize) {
        T entity = persist(constructor, initialize);
        return ((Number)
                        entityManager
                                .getEntityManagerFactory()
                                .getPersistenceUnitUtil()
                                .getIdentifier(entity))
                .longValue();
    }

    public <T> void edit(Class<T> type, Object id, Consumer<T> change) {
        change.accept(find(type, id));
    }

    public <T> void editMatching(Class<T> type, Consumer<T> change, Condition... conditions) {
        entities(type, Sort.unsorted(), Integer.MAX_VALUE, conditions).forEach(change);
    }

    public void delete(Class<?> type, Condition... conditions) {
        entities(type, Sort.unsorted(), Integer.MAX_VALUE, conditions)
                .forEach(entityManager::remove);
    }

    public <N extends Number> N sum(
            Class<?> type, String attribute, Class<N> numberType, Condition... conditions) {
        var builder = entityManager.getCriteriaBuilder();
        var criteria = builder.createQuery(numberType);
        var root = criteria.from(type);
        criteria.select(builder.sum(root.<N>get(attribute)))
                .where(predicates(root, builder, conditions));
        N result = entityManager.createQuery(criteria).getSingleResult();
        if (result != null) return result;
        return numberType.cast(numberType == Long.class ? (Number) 0L : BigDecimal.ZERO);
    }

    /** Financial writes for one organization share this database row lock. */
    public void lock(long org) {
        lockedRow(Organization.class, org);
    }

    public void holding(long org, long security) {
        if (entityManager.find(Holding.class, new HoldingId(org, security)) == null) {
            persist(
                    Holding::new,
                    holding -> {
                        holding.setOrganizationId(org);
                        holding.setSecurityId(security);
                    });
        }
    }

    private static final ClassValue<List<Field>> COLUMNS =
            new ClassValue<>() {
                @Override
                protected List<Field> computeValue(Class<?> type) {
                    return Arrays.stream(type.getDeclaredFields())
                            .filter(field -> field.isAnnotationPresent(Column.class))
                            .peek(field -> field.setAccessible(true))
                            .toList();
                }
            };

    public static Map<String, Object> columns(Object entity) {
        var row = new LinkedHashMap<String, Object>();
        for (Field field : COLUMNS.get(entity.getClass())) {
            try {
                row.put(field.getAnnotation(Column.class).name(), field.get(entity));
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(
                        "Cannot read mapped entity field " + field.getName(), e);
            }
        }
        return row;
    }

    private static ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found");
    }

    public static long n(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    public static BigDecimal money(Map<String, Object> row, String key) {
        return new BigDecimal(row.get(key).toString());
    }

    public static Instant instant(Map<String, Object> row, String key) {
        return Instant.parse(row.get(key).toString());
    }

    public static LocalDate date(Map<String, Object> row, String key) {
        return LocalDate.parse(row.get(key).toString());
    }

    public static void require(boolean condition, String message) {
        if (!condition) throw new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    public static BigDecimal amount(BigDecimal value) {
        return value.setScale(6, RoundingMode.HALF_UP);
    }
}
