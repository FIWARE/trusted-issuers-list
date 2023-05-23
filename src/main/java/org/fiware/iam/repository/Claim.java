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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * Data entity to represent a capabilities claim.
 */
@Introspected
@Accessors(chain = true)
@NoArgsConstructor
@Data
@Entity
@Getter
@EqualsAndHashCode(exclude = "capability")
@ToString(exclude = "capability")
public class Claim {

	@GeneratedValue
	@Id
	private Integer id;

	private String name;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "capability_id")
	private Capability capability;

	@OneToMany(mappedBy = "claim", fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
	private List<ClaimValue> claimValues;

	public Claim(Integer id, String name, Capability capability,
			@Nullable List<ClaimValue> claimValues) {
		this.id = id;
		this.name = name;
		this.capability = capability;
		this.claimValues = claimValues;
	}
}
