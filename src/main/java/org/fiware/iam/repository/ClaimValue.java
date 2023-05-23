package org.fiware.iam.repository;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * Data entity to represent the value of a claim. Could be of multiple types, thus will be stored as a json-stirng
 */
@Accessors(chain = true)
@Data
@NoArgsConstructor
@Introspected
@Getter
@Entity
@EqualsAndHashCode(exclude = "claim")
@ToString(exclude = "claim")
public class ClaimValue {

	@Id
	@GeneratedValue
	private Integer id;

	// should be serialized json
	private String value;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "claim_id")
	private Claim claim;

	public ClaimValue(Integer id, String value, @Nullable Claim claim) {
		this.id = id;
		this.value = value;
		this.claim = claim;
	}
}
