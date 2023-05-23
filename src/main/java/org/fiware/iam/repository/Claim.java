package org.fiware.iam.repository;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.Get;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Introspected
@Builder
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

	@OneToMany(mappedBy = "claim", fetch = FetchType.EAGER, cascade = CascadeType.ALL)
	private List<ClaimValue> claimValues;

	public Claim(Integer id, String name, Capability capability,
			@Nullable List<ClaimValue> claimValues) {
		this.id = id;
		this.name = name;
		this.capability = capability;
		this.claimValues = claimValues;
	}
}
