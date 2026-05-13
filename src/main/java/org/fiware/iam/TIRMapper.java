package org.fiware.iam;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import lombok.SneakyThrows;
import org.fiware.iam.repository.Claim;
import org.fiware.iam.repository.ClaimValue;
import org.fiware.iam.repository.Credential;
import org.fiware.iam.repository.TrustedIssuer;
import org.fiware.iam.til.model.ClaimVO;
import org.fiware.iam.til.model.CredentialsVO;
import org.fiware.iam.tir.model.IssuerAttributeVO;
import org.fiware.iam.tir.model.IssuerVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.logging.LogManager;

/**
 * Responsible for mapping entities from the (EBSI-) Trusted Issuers Registry domain to the internal model.
 */
@Mapper(componentModel = "jsr330")
public interface TIRMapper {

	Logger LOGGER = LoggerFactory.getLogger(TIRMapper.class);
	ObjectWriter OBJECT_WRITER = new ObjectMapper().writer();

	CredentialsVO map(Credential credential);

	default ClaimVO map(Claim claim) {
		return new ClaimVO()
				.name(claim.getName())
				.path(claim.getPath())
				.allowedValues(
						claim.getClaimValues().stream().map(
										TILMapper::readToObject)
								.filter(Objects::nonNull)
								.toList());
	}

	/**
	 * Map an internal trusted issuer to a proper issuerVO. Will handle the hashing and encoding of the attributes.
	 */
	default IssuerVO map(TrustedIssuer trustedIssuer) {
		IssuerVO issuerVO = new IssuerVO().did(trustedIssuer.getDid());

		List<IssuerAttributeVO> issuerAttributeVOS = trustedIssuer
				.getCredentials()
				.stream()
				.map(this::mapToAttribute)
				.toList();
		issuerVO.attributes(issuerAttributeVOS);
		return issuerVO;
	}

	/**
	 * Map a credential entity directly to an IssuerAttributeVO, preserving issuerType, tao, and rootTao.
	 * The credential body is serialized from its CredentialsVO representation, then Base64-encoded.
	 *
	 * @param credential the credential entity
	 * @return the mapped issuer attribute VO
	 */
	default IssuerAttributeVO mapToAttribute(Credential credential) {
		IssuerAttributeVO issuerAttributeVO = new IssuerAttributeVO();
		issuerAttributeVO.issuerType(mapIssuerType(credential.getIssuerType()));
		issuerAttributeVO.tao(credential.getTao());
		issuerAttributeVO.rootTao(credential.getRootTao());
		try {
			CredentialsVO credentialsVO = map(credential);
			byte[] body = OBJECT_WRITER.writeValueAsBytes(credentialsVO);
			issuerAttributeVO.body(
					Base64.getEncoder().encodeToString(body));
			issuerAttributeVO.hash(Base64.getEncoder().encodeToString(getSHA256(body)));
		} catch (JsonProcessingException jpe) {
			LOGGER.warn("Was not able to process the given credential {}. Will not include it into the issuer.",
					credential, jpe);
		}
		return issuerAttributeVO;
	}

	/**
	 * Convert a stored issuerType string to the v4 IssuerType enum.
	 * Falls back to {@link IssuerAttributeVO.IssuerType#UNDEFINED} for null or unrecognized values.
	 *
	 * @param issuerType the stored issuer type string (may be null)
	 * @return the corresponding enum value
	 */
	default IssuerAttributeVO.IssuerType mapIssuerType(String issuerType) {
		if (issuerType == null) {
			return IssuerAttributeVO.IssuerType.UNDEFINED;
		}
		try {
			return IssuerAttributeVO.IssuerType.toEnum(issuerType);
		} catch (IllegalArgumentException e) {
			LOGGER.warn("Unknown issuerType value '{}', defaulting to UNDEFINED.", issuerType);
			return IssuerAttributeVO.IssuerType.UNDEFINED;
		}
	}

	/**
	 * Builds a sha-256 hash for the given byte array
	 *
	 * @param toHash the array to hash
	 * @return the hash
	 */
	@SneakyThrows default byte[] getSHA256(byte[] toHash) {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		return digest.digest(toHash);
	}
}
