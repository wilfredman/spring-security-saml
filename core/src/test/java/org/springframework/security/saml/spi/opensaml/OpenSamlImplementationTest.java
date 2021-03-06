package org.springframework.security.saml.spi.opensaml;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.saml.saml2.authentication.AuthenticationRequest;
import org.springframework.security.saml.saml2.authentication.Issuer;
import org.springframework.security.saml.saml2.authentication.Scoping;
import org.springframework.security.saml.saml2.metadata.Binding;
import org.springframework.security.saml.saml2.metadata.Endpoint;
import org.springframework.util.StreamUtils;
import org.w3c.dom.Node;

import java.io.IOException;
import java.time.Clock;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.saml.util.XmlTestUtil.assertNodeCount;
import static org.springframework.security.saml.util.XmlTestUtil.getNodes;

class OpenSamlImplementationTest {

	private OpenSamlImplementation subject = new OpenSamlImplementation(Clock.systemDefaultZone());

	{
		subject.bootstrap();
	}

	@Test
	public void authenticationRequestWithScopingToXml() {
		AuthenticationRequest authenticationRequest = new AuthenticationRequest();
		String requesterId = "http://requesterId";
		String idpId = "http://idp";
		authenticationRequest
			.setBinding(Binding.REDIRECT)
			.setScoping(new Scoping(
				Collections.singletonList(idpId),
				Collections.singletonList(requesterId),
				new Integer(5)))
			.setAssertionConsumerService(endpoint("http://assertionConsumerService"))
			.setDestination(endpoint("http://destination"))
			.setIssuer(new Issuer());

		String xml = subject.toXml(authenticationRequest);

		assertNodeCount(xml, "//saml2p:Scoping", 1);

		Iterable<Node> nodes = getNodes(xml, "//saml2p:Scoping");
		String textContent = nodes.iterator().next().getAttributes().getNamedItem("ProxyCount").getTextContent();
		assertEquals("5", textContent);

		nodes = getNodes(xml, "//saml2p:RequesterID");
		textContent = nodes.iterator().next().getTextContent();
		assertEquals(requesterId, textContent);

		nodes = getNodes(xml, "//saml2p:IDPEntry");
		textContent = nodes.iterator().next().getAttributes().getNamedItem("ProviderID").getTextContent();
		assertEquals(idpId, textContent);
	}

	@Test
	public void resolveAuthnRequestWithScoping() throws IOException {
		Scoping scoping =
			parseScoping("authn_request_with_scoping.xml");

		List<String> idpList = scoping.getIdpList();
		assertEquals(1, idpList.size());
		assertEquals("http://idp", idpList.get(0));

		List<String> requesterIds = scoping.getRequesterIds();
		assertEquals(1, requesterIds.size());
		assertEquals("http://requesterId", requesterIds.get(0));

		assertEquals(5, scoping.getProxyCount().intValue());
	}

	@Test
	public void resolveAuthnRequestWithEmptyScoping() throws IOException {
		Scoping scoping =
			parseScoping("authn_request_with_empty_scoping.xml");

		List<String> idpList = scoping.getIdpList();
		assertEquals(0, idpList.size());

		List<String> requesterIds = scoping.getRequesterIds();
		assertEquals(0, requesterIds.size());

		assertNull(scoping.getProxyCount());
	}

	@Test
	public void resolveAuthnRequestWithNoScoping() throws IOException {
		Scoping scoping =
			parseScoping("authn_request_with_no_scoping.xml");

		assertNull(scoping);
	}

	private Scoping parseScoping(String fileName) throws IOException {
		byte[] xml = StreamUtils.copyToByteArray(
			new ClassPathResource(String.format("authn_requests/%s", fileName)).getInputStream());
		return ((AuthenticationRequest)
			subject.resolve(xml, Collections.emptyList(), Collections.emptyList())).getScoping();

	}

	private Endpoint endpoint(String location) {
		Endpoint endpoint = new Endpoint();
		endpoint.setLocation(location);
		return endpoint;
	}
}
