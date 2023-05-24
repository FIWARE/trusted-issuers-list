package org.fiware.iam;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.fiware.iam.repository.Credential;
import org.fiware.iam.repository.Claim;
import org.fiware.iam.repository.ClaimValue;
import org.fiware.iam.repository.TrustedIssuer;
import org.fiware.iam.til.model.ClaimVO;
import org.fiware.iam.til.model.CredentialsVO;
import org.fiware.iam.til.model.TrustedIssuerVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.io.IOException;
import java.util.Objects;

/**
 * Responsible for mapping entities from the Trusted Issuers List domain to the internal model.
 */
@Mapper(componentModel = "jsr330")
public interface TILMapper {

	ObjectReader OBJECT_READER = new ObjectMapper().reader();
	ObjectWriter OBJECT_WRITER = new ObjectMapper().writer();

	TrustedIssuer map(TrustedIssuerVO trustedIssuerVO);

	TrustedIssuerVO map(TrustedIssuer trustedIssuerVO);

	@Mapping(target = "validFrom", source = "validFor.from")
	@Mapping(target = "validTo", source = "validFor.to")
	Credential map(CredentialsVO credentialsVO);

	CredentialsVO map(Credential credential);

	default ClaimVO map(Claim claim) {
		return new ClaimVO()
				.name(claim.getName())
				.allowedValues(
						claim.getClaimValues().stream().map(
										TILMapper::readToObject)
								.filter(Objects::nonNull)
								.toList());
	}

	default Claim map(ClaimVO claimVO) {
		return new Claim()
				.setName(claimVO.getName())
				.setClaimValues(
						claimVO.getAllowedValues().stream().map(value -> {
									try {
										return OBJECT_WRITER.writeValueAsString(value);
									} catch (JsonProcessingException e) {
										return null;
									}
								})
								.filter(Objects::nonNull)
								.map(valueString -> new ClaimValue().setValue(valueString))
								.toList());
	}

	// in order to also properly read primitives(string,number,boolean) we try to read the value as such first and
	// ignore potential exceptions and read it as an object just as a last step.
	static Object readToObject(ClaimValue claimValue) {
		try {
			return OBJECT_READER.readValue(claimValue.getValue(), Number.class);
		} catch (IOException ignored) {
		}
		try {
			return OBJECT_READER.readValue(claimValue.getValue(), String.class);
		} catch (IOException ignored) {
		}
		try {
			return OBJECT_READER.readValue(claimValue.getValue(), Boolean.class);
		} catch (IOException ignored) {
		}
		try {
			return OBJECT_READER.readValue(claimValue.getValue(), Object.class);
		} catch (IOException e) {
			return null;
		}
	}

}
