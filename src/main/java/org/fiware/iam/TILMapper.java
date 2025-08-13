package org.fiware.iam;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.fiware.iam.repository.Credential;
import org.fiware.iam.repository.Claim;
import org.fiware.iam.repository.ClaimValue;
import org.fiware.iam.repository.TrustedIssuer;
import org.fiware.iam.til.model.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Objects;

/**
 * Responsible for mapping entities from the Trusted Issuers List domain to the internal model.
 */
@Mapper(componentModel = "jsr330")
public interface TILMapper {

	Logger LOGGER = LoggerFactory.getLogger(TIRMapper.class);

	ObjectReader OBJECT_READER = new ObjectMapper().reader();
	ObjectWriter OBJECT_WRITER = new ObjectMapper().writer();

	TrustedIssuer map(TrustedIssuerVO trustedIssuerVO);

	TrustedIssuerVO map(TrustedIssuer trustedIssuerVO);

	@Mapping(target = "validFrom", source = "validFor.from")
	@Mapping(target = "validTo", source = "validFor.to")
	Credential map(CredentialsVO credentialsVO);

	@Mapping(target = "validFor.from", source = "validFrom")
	@Mapping(target = "validFor.to", source = "validTo")
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

	default Claim map(ClaimVO claimVO) {
		return new Claim()
				.setName(claimVO.getName())
				.setPath(claimVO.getPath())
				.setClaimValues(
						claimVO.getAllowedValues().stream().map(value -> {
									try {
										return OBJECT_WRITER.writeValueAsString(value);
									} catch (JsonProcessingException e) {
										LOGGER.warn("Was not able to serialize the claim value {}. Will skip it.", value, e);
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
		LOGGER.debug("Try to read the claimValue {} to its proper object representation.", claimValue);
		try {
			return OBJECT_READER.readValue(claimValue.getValue(), Number.class);
		} catch (IOException ignored) {
			LOGGER.debug("Given value is not a number, try next type.");
		}
		try {
			return OBJECT_READER.readValue(claimValue.getValue(), String.class);
		} catch (IOException ignored) {
			LOGGER.debug("Given value is not a string, try next type.");
		}
		try {
			return OBJECT_READER.readValue(claimValue.getValue(), Boolean.class);
		} catch (IOException ignored) {
			LOGGER.debug("Given value is not a boolean, try next type.");
		}
		try {
			return OBJECT_READER.readValue(claimValue.getValue(), Object.class);
		} catch (IOException e) {
			LOGGER.warn("Was not able to read the claimValue {} to an object. Will return null.", claimValue, e);
			return null;
		}
	}

}
