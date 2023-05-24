package org.fiware.iam.repository;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.util.Collection;
import java.util.Date;

/**
 * Data entity to map an issuers credentials
 */
@Introspected
@Data
@Accessors(chain = true)
@Entity
@EqualsAndHashCode(exclude = "trustedIssuer")
@ToString(exclude = "trustedIssuer")
public class Credential {

	@GeneratedValue
	@Id
	private Integer id;

	@Nullable
	private Date validFrom;

	@Nullable
	private Date validTo;

	private String credentialsType;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "trusted_issuer_id")
	private TrustedIssuer trustedIssuer;

	@OneToMany(mappedBy = "credential", fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
	private Collection<Claim> claims;

}
