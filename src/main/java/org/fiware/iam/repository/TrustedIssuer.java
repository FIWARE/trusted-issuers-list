package org.fiware.iam.repository;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.annotation.Join;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.Collection;

/**
 * Data entity to represent a trusted issuer
 */
@Introspected
@Accessors(chain = true)
@Data
@Entity
@EqualsAndHashCode
public class TrustedIssuer {

	@Id
	private String did;

	@OneToMany(mappedBy = "trustedIssuer", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
	private Collection<Capability> capabilities;
}
