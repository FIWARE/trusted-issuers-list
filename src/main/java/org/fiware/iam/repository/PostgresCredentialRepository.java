package org.fiware.iam.repository;

import io.micronaut.context.annotation.Requires;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;

/**
 * Extension of the {@link CredentialRepository} for the Postgres-dialect
 */
@Requires(property = "datasources.default.dialect", value = "POSTGRES")
@JdbcRepository(dialect = Dialect.POSTGRES)
public interface PostgresCredentialRepository extends CredentialRepository {

}
