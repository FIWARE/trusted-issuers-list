package org.fiware.iam;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import lombok.SneakyThrows;
import org.fiware.iam.repository.Capability;
import org.fiware.iam.repository.TrustedIssuer;
import org.fiware.iam.til.model.CapabilitiesVO;
import org.fiware.iam.tir.model.IssuerAttributeVO;
import org.fiware.iam.tir.model.IssuerVO;
import org.mapstruct.Mapper;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;

@Mapper(componentModel = "jsr330")
public interface TIRMapper {

	ObjectWriter OBJECT_WRITER = new ObjectMapper().writer();

	CapabilitiesVO map(Capability capability);

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
						byte[] body = OBJECT_WRITER.writeValueAsBytes(capabilitiesVO);
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
