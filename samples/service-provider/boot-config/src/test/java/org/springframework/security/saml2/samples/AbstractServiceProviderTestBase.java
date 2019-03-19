/*
 * Copyright 2002-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.springframework.security.saml2.samples;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.security.saml2.Saml2Transformer;
import org.springframework.security.saml2.boot.configuration.RemoteIdentityProviderConfiguration;
import org.springframework.security.saml2.boot.configuration.SamlBootConfiguration;
import org.springframework.security.saml2.configuration.ExternalIdentityProviderConfiguration;
import org.springframework.security.saml2.configuration.HostedServiceProviderConfiguration;
import org.springframework.security.saml2.model.Saml2Object;
import org.springframework.security.saml2.model.authentication.AuthenticationRequest;
import org.springframework.security.saml2.model.metadata.Metadata;
import org.springframework.security.saml2.model.metadata.ServiceProviderMetadata;
import org.springframework.security.saml2.serviceprovider.ServiceProviderConfigurationResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.saml2.helper.SamlTestObjectHelper.queryParams;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

abstract class AbstractServiceProviderTestBase {
	@Autowired
	MockMvc mockMvc;

	@Autowired
	Saml2Transformer transformer;

	@Autowired
	SamlBootConfiguration bootConfiguration;

	@SpyBean
	ServiceProviderConfigurationResolver configuration;

	@BeforeEach
	void setUp() {
	}

	@AfterEach
	void reset() {
	}

	AuthenticationRequest getAuthenticationRequestRedirect(String idpEntityId) throws Exception {
		MvcResult result = mockMvc.perform(
			get("/saml/sp/authenticate")
				.param("idp", idpEntityId)
		)
			.andExpect(status().is3xxRedirection())
			.andReturn();

		String location = result.getResponse().getHeader("Location");
		Map<String, String> params = queryParams(new URI(location));
		String request = params.get("SAMLRequest");
		assertNotNull(request);
		String xml = transformer.samlDecode(request, true);
		Saml2Object saml2Object = transformer.fromXml(
			xml,
			bootConfiguration.getServiceProvider().getKeys().toList(),
			bootConfiguration.getServiceProvider().getKeys().toList()
		);
		assertNotNull(saml2Object);
		assertThat(saml2Object.getClass(), equalTo(AuthenticationRequest.class));
		return (AuthenticationRequest) saml2Object;
	}

	AuthenticationRequest getAuthenticationRequestPost(String idpEntityId) throws Exception {
		MvcResult result = mockMvc.perform(
			get("/saml/sp/authenticate")
				.param("idp", idpEntityId)
		)
			.andExpect(status().isOk())
			.andReturn();

		String content = result.getResponse().getContentAsString();
		String request = extractResponse(content, "SAMLRequest");


		assertNotNull(request);
		String xml = transformer.samlDecode(request, false);
		Saml2Object saml2Object = transformer.fromXml(
			xml,
			bootConfiguration.getServiceProvider().getKeys().toList(),
			bootConfiguration.getServiceProvider().getKeys().toList()
		);
		assertNotNull(saml2Object);
		assertThat(saml2Object.getClass(), equalTo(AuthenticationRequest.class));
		return (AuthenticationRequest) saml2Object;
	}

	void mockConfig(Consumer<HostedServiceProviderConfiguration.Builder> modifier) {
		Mockito.doAnswer(
			invocation -> {
				HostedServiceProviderConfiguration config =
					(HostedServiceProviderConfiguration) invocation.callRealMethod();
				HostedServiceProviderConfiguration.Builder builder =
					HostedServiceProviderConfiguration.builder(config);
				modifier.accept(builder);
				return builder.build();
			}
		)
			.when(configuration).getConfiguration(ArgumentMatchers.any(HttpServletRequest.class));
	}

	ServiceProviderMetadata getServiceProviderMetadata() throws Exception {
		String xml = mockMvc.perform(get("/saml/sp/metadata"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertNotNull(xml);
		Metadata m = (Metadata) transformer.fromXml(xml, null, null);
		assertNotNull(m);
		assertThat(m.getClass(), equalTo(ServiceProviderMetadata.class));
		return (ServiceProviderMetadata) m;
	}

	String extractResponse(String html, String name) {
		Pattern p = Pattern.compile(" name=\"(.*?)\" value=\"(.*?)\"");
		Matcher m = p.matcher(html);
		while (m.find()) {
			String pname = m.group(1);
			String value = m.group(2);
			if (name.equals(pname)) {
				return value;
			}
		}
		return null;
	}

	List<ExternalIdentityProviderConfiguration> modifyIdpProviders(Consumer<RemoteIdentityProviderConfiguration> c) {
		return bootConfiguration.getServiceProvider().getProviders().stream()
			.map(p -> {
				c.accept(p);
				return p.toExternalIdentityProviderConfiguration();
			})
			.collect(Collectors.toList());
	}
}
