package org.fiware.iam;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.fiware.iam.repository.Capability;
import org.fiware.iam.repository.Claim;
import org.fiware.iam.repository.ClaimValue;
import org.fiware.iam.repository.TrustedIssuer;
import org.fiware.iam.til.model.CapabilitiesVO;
import org.fiware.iam.til.model.ClaimVO;
import org.fiware.iam.til.model.TrustedIssuerVO;
import org.fiware.iam.tir.model.IssuerAttributeVO;
import org.fiware.iam.tir.model.IssuerVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

@Mapper(componentModel = "jsr330")
public interface TrustedIssuerMapper {

	ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	TrustedIssuer map(TrustedIssuerVO trustedIssuerVO);

	@Mapping(target = "validFrom", source = "validFor.from")
	@Mapping(target = "validTo", source = "validFor.to")
	Capability map(CapabilitiesVO capabilitiesVO);

	CapabilitiesVO map(Capability capability);

	default ClaimVO map(Claim claim) {
		return new ClaimVO()
				.name(claim.getName())
				.allowedValues(
						claim.getClaimValues().stream().map(
										this::readToObject)
								.filter(Objects::nonNull)
								.toList());
	}

	// in order to also properly read primitives(string,number,boolean) we try to read the value as such first and
	// ignore potential exceptions and read it as an object just as a last step.
	private Object readToObject(ClaimValue claimValue) {
		try {
			return OBJECT_MAPPER.readValue(claimValue.getValue(), Number.class);
		} catch (JsonProcessingException ignored) {
		}
		try {
			return OBJECT_MAPPER.readValue(claimValue.getValue(), String.class);
		} catch (JsonProcessingException ignored) {
		}
		try {
			return OBJECT_MAPPER.readValue(claimValue.getValue(), Boolean.class);
		} catch (JsonProcessingException ignored) {
		}
		try {
			return OBJECT_MAPPER.readValue(claimValue.getValue(), Object.class);
		} catch (JsonProcessingException e) {
			return null;
		}
	}

	default Claim map(ClaimVO claimVO) {
		return Claim.builder()
				.name(claimVO.getName())
				.claimValues(
						claimVO.getAllowedValues().stream().map(value -> {
									try {
										return OBJECT_MAPPER.writeValueAsString(value);
									} catch (JsonProcessingException e) {
										return null;
									}
								})
								.filter(Objects::nonNull)
								.map(valueString -> ClaimValue.builder().value(valueString).build())
								.toList()).build();
	}

	default IssuerVO map(TrustedIssuer trustedIssuer) {
		IssuerVO issuerVO = new IssuerVO().did(trustedIssuer.getDid());

		List<IssuerAttributeVO> issuerAttributeVOS = trustedIssuer
				.getCapabilities()
				.stream()
				.map(this::map)
				.map(capabilitiesVO -> {
					IssuerAttributeVO issuerAttributeVO = new IssuerAttributeVO();
					issuerAttributeVO.issuerType(IssuerAttributeVO.IssuerType.UNDEFINED);
					try {
						byte[] body = OBJECT_MAPPER.writeValueAsBytes(capabilitiesVO);
						issuerAttributeVO.body(
								Base64.getEncoder().encodeToString(body));
						issuerAttributeVO.hash(Base64.getEncoder().encodeToString(getSHA256(body)));
					} catch (JsonProcessingException ignored) {
					}
					return issuerAttributeVO;
				}).toList();
		issuerVO.attributes(issuerAttributeVOS);
		return issuerVO;
	}

	@SneakyThrows default byte[] getSHA256(byte[] toHash) {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		return digest.digest(toHash);
	}
}
