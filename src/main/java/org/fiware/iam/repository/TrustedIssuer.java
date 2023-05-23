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

import java.util.ArrayList;
import java.util.Collection;

@Introspected
@Data
@Entity
@EqualsAndHashCode
public class TrustedIssuer {

	@Id
	private String did;

	@OneToMany(mappedBy = "trustedIssuer", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
	@Join(value = "claims", type = Join.Type.FETCH)
	private Collection<Capability> capabilities;
}
