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
	 * Map an internal trusted issuer to a proper issuerVO. Will handle the hashing and encoding of the attributes
	 */
	default IssuerVO map(TrustedIssuer trustedIssuer) {
		IssuerVO issuerVO = new IssuerVO().did(trustedIssuer.getDid());

		List<IssuerAttributeVO> issuerAttributeVOS = trustedIssuer
				.getCredentials()
				.stream()
				.map(this::map)
				.map(this::map)
				.toList();
		issuerVO.attributes(issuerAttributeVOS);
		return issuerVO;
	}

	default IssuerAttributeVO map(CredentialsVO credentialsVO) {
		IssuerAttributeVO issuerAttributeVO = new IssuerAttributeVO();
		issuerAttributeVO.issuerType(IssuerAttributeVO.IssuerType.UNDEFINED);
		try {
			byte[] body = OBJECT_WRITER.writeValueAsBytes(credentialsVO);
			issuerAttributeVO.body(
					Base64.getEncoder().encodeToString(body));
			issuerAttributeVO.hash(Base64.getEncoder().encodeToString(getSHA256(body)));
		} catch (JsonProcessingException jpe) {
			LOGGER.warn("Was not able to process the given credential {}. Will not include it into the issuer.",
					credentialsVO, jpe);
		}
		return issuerAttributeVO;
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
