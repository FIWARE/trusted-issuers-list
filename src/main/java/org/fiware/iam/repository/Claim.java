package org.fiware.iam.repository;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * Data entity to represent a credentials claim.
 */
@Introspected
@Accessors(chain = true)
@NoArgsConstructor
@Data
@Entity
@Getter
@EqualsAndHashCode(exclude = "credential")
@ToString(exclude = "credential")
public class Claim {

	@GeneratedValue
	@Id
	private Integer id;

	private String name;
	private String path;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "credential_id")
	private Credential credential;

	@OneToMany(mappedBy = "claim", fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
	private List<ClaimValue> claimValues;
	
}
